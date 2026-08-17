package com.familytree.core.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.familytree.core.common.di.Dispatcher
import com.familytree.core.common.di.FtDispatcher
import com.familytree.core.domain.repository.PremiumOffer
import com.familytree.core.domain.repository.PremiumRepository
import com.familytree.core.domain.repository.PurchaseOutcome
import com.familytree.core.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * The premium purchase, through Google Play.
 *
 * Entitlement is written into the app's own settings once the store confirms it, so the
 * feature keeps working offline and on a plane. That is a deliberate trade: it also means
 * a determined user can unlock it by editing the app's data. Verifying server-side would
 * mean running a server, which this app deliberately does not do — and for a one-off
 * purchase in a genealogy app the honest answer is that the lock is a courtesy, not a vault.
 */
@Singleton
class PlayPremiumRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    @Dispatcher(FtDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : PremiumRepository {

    private val purchaseResults = MutableStateFlow<PurchaseOutcome?>(null)

    override val isPremium: Flow<Boolean> = settings.settings.map { it.premium }

    private val listener = PurchasesUpdatedListener { result, purchases ->
        purchaseResults.value = when {
            result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null ->
                if (purchases.any { it.isPremium() }) PurchaseOutcome.Purchased else PurchaseOutcome.Cancelled

            result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED ->
                PurchaseOutcome.Cancelled

            result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
                PurchaseOutcome.AlreadyOwned

            else -> PurchaseOutcome.Failed(result.debugMessage)
        }
    }

    private val client: BillingClient by lazy {
        BillingClient.newBuilder(context)
            .setListener(listener)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
    }

    /** Connects if needed. Returns false when the store simply is not there. */
    private suspend fun connect(): Boolean = withContext(ioDispatcher) {
        if (client.isReady) return@withContext true
        suspendCancellableCoroutine { continuation ->
            client.startConnection(
                object : com.android.billingclient.api.BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        if (continuation.isActive) {
                            continuation.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                        }
                    }

                    override fun onBillingServiceDisconnected() {
                        if (continuation.isActive) continuation.resume(false)
                    }
                },
            )
        }
    }

    override suspend fun offer(): PremiumOffer? = withContext(ioDispatcher) {
        if (!connect()) return@withContext null
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()

        val details = runCatching { client.queryProductDetails(params) }.getOrNull()
            ?.productDetailsList
            ?.firstOrNull()
            ?: return@withContext null

        PremiumOffer(
            productId = details.productId,
            formattedPrice = details.oneTimePurchaseOfferDetails?.formattedPrice.orEmpty(),
            title = details.title,
            description = details.description,
        )
    }

    override suspend fun purchase(activity: Any): PurchaseOutcome {
        val host = activity as? Activity ?: return PurchaseOutcome.Failed("No activity to show the store over.")
        if (!connect()) return PurchaseOutcome.Unavailable

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        val details = runCatching { client.queryProductDetails(params) }.getOrNull()
            ?.productDetailsList
            ?.firstOrNull()
            ?: return PurchaseOutcome.Unavailable

        purchaseResults.value = null
        client.launchBillingFlow(
            host,
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(details)
                            .build(),
                    ),
                )
                .build(),
        )

        // The result arrives on the listener, not from the call above.
        val outcome = purchaseResults.filterNotNull().first()
        if (outcome is PurchaseOutcome.Purchased || outcome is PurchaseOutcome.AlreadyOwned) {
            restorePurchases()
        }
        return outcome
    }

    override suspend fun restorePurchases(): Result<Boolean> = withContext(ioDispatcher) {
        runCatching {
            if (!connect()) return@runCatching false
            val purchases = client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build(),
            ).purchasesList

            val owned = purchases.filter { it.isPremium() }
            // Acknowledge within three days or Play refunds the purchase automatically —
            // the one billing rule that silently takes money back.
            owned.filterNot { it.isAcknowledged }.forEach { purchase ->
                client.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build(),
                )
            }

            val entitled = owned.isNotEmpty()
            settings.update { it.copy(premium = entitled || it.premium) }
            entitled
        }
    }

    private fun Purchase.isPremium(): Boolean =
        products.contains(PRODUCT_ID) && purchaseState == Purchase.PurchaseState.PURCHASED

    private companion object {
        const val PRODUCT_ID = "familytree_premium"
    }
}
