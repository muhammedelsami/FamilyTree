package com.familytree.core.billing

import android.app.Activity
import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        purchaseResults.value = result.toOutcome(purchases)
    }

    private val client: BillingClient by lazy {
        BillingClient.newBuilder(context)
            .setListener(listener)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
    }

    private val connecting = Mutex()

    /**
     * Connects if needed. Returns false when the store simply is not there.
     *
     * One attempt at a time. The settings screen asks for the price and restores purchases
     * in the same instant, and a second `startConnection` while the first is still in
     * flight is answered at once with DEVELOPER_ERROR — so whichever call lost the race
     * reported a store that was in fact there.
     */
    private suspend fun connect(): Boolean = withContext(ioDispatcher) {
        connecting.withLock { client.isReady || startConnection() }
    }

    private suspend fun startConnection(): Boolean =
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

        val result = runCatching { client.queryProductDetails(params) }.getOrNull()
        val details = result?.productDetailsList?.firstOrNull()
        if (details == null) {
            // The card can only say "the store is not available", but a reachable store
            // that does not know the product is a different fault with a different fix —
            // one in the Play Console, not on the device. This line is how to tell them apart.
            Log.w(TAG, "No details for $PRODUCT_ID: ${result?.billingResult?.responseCode} ${result?.billingResult?.debugMessage}")
            return@withContext null
        }

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
        val launched = client.launchBillingFlow(
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

        // A sheet that opened reports on the listener. One that never opened reports only
        // here — the listener is not told — and waiting for it would leave the buy button
        // disabled until the screen is left.
        val outcome = if (launched.responseCode == BillingClient.BillingResponseCode.OK) {
            purchaseResults.filterNotNull().first()
        } else {
            launched.toOutcome(purchases = null)
        }
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

    private fun BillingResult.toOutcome(purchases: List<Purchase>?): PurchaseOutcome = when (responseCode) {
        BillingClient.BillingResponseCode.OK ->
            if (purchases.orEmpty().any { it.isPremium() }) PurchaseOutcome.Purchased else PurchaseOutcome.Cancelled

        BillingClient.BillingResponseCode.USER_CANCELED -> PurchaseOutcome.Cancelled
        BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> PurchaseOutcome.AlreadyOwned
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
        -> PurchaseOutcome.Unavailable

        else -> PurchaseOutcome.Failed(debugMessage)
    }

    private fun Purchase.isPremium(): Boolean =
        products.contains(PRODUCT_ID) && purchaseState == Purchase.PurchaseState.PURCHASED

    private companion object {
        const val PRODUCT_ID = "familytree_premium"
        const val TAG = "PlayPremium"
    }
}
