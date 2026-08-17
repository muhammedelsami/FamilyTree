package com.familytree.core.domain.repository

import kotlinx.coroutines.flow.Flow

/** What the premium purchase unlocks and what it currently costs. */
data class PremiumOffer(
    val productId: String,
    val formattedPrice: String,
    val title: String,
    val description: String,
)

sealed interface PurchaseOutcome {
    data object Purchased : PurchaseOutcome
    data object AlreadyOwned : PurchaseOutcome
    data object Cancelled : PurchaseOutcome
    data class Failed(val message: String?) : PurchaseOutcome
    /** The store is not available — no Play Services, or a build installed by hand. */
    data object Unavailable : PurchaseOutcome
}

/**
 * The one paid feature.
 *
 * Deliberately narrow: everything a genealogist needs to record and keep a family tree is
 * free, and payment buys only the automatic merging of two trees — the part that saves
 * hours of manual work and costs real effort to get right.
 */
interface PremiumRepository {

    /** Whether the user has paid. Also true while offline, once known. */
    val isPremium: Flow<Boolean>

    /** Null when the store cannot be reached, so the screen can say so honestly. */
    suspend fun offer(): PremiumOffer?

    /** Starts the purchase flow. [activity] must be the current Activity. */
    suspend fun purchase(activity: Any): PurchaseOutcome

    /** Re-reads purchases from the store, for a new device or a reinstall. */
    suspend fun restorePurchases(): Result<Boolean>
}
