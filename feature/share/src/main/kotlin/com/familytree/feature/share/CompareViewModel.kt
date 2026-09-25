package com.familytree.feature.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familytree.core.domain.repository.AppliedUpdates
import com.familytree.core.domain.repository.PremiumOffer
import com.familytree.core.domain.repository.PremiumRepository
import com.familytree.core.domain.repository.PurchaseOutcome
import com.familytree.core.domain.repository.ShareRepository
import com.familytree.core.domain.repository.TreeRepository
import com.familytree.core.model.TreeComparison
import com.familytree.core.model.TreeDifference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CompareUiState(
    val loading: Boolean = true,
    val localTitle: String = "",
    val incomingTitle: String = "",
    val comparison: TreeComparison? = null,
    val applying: Boolean = false,
    val applied: AppliedUpdates? = null,
    val failed: Boolean = false,
    /** Applying is the paid part; looking at what would change is not. */
    val premium: Boolean = false,
    /** Null while unknown or when the store cannot be reached. */
    val offer: PremiumOffer? = null,
    val purchasing: Boolean = false,
    val purchaseMessage: PurchaseOutcome? = null,
) {
    val differences: List<TreeDifference> get() = comparison?.differences.orEmpty()
    val acceptedCount: Int get() = differences.count { it.accepted }
}

@HiltViewModel
class CompareViewModel @Inject constructor(
    private val share: ShareRepository,
    private val trees: TreeRepository,
    private val premiumRepository: PremiumRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompareUiState())
    val uiState: StateFlow<CompareUiState> = combine(_uiState, premiumRepository.isPremium) { state, premium ->
        state.copy(premium = premium)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _uiState.value)

    init {
        viewModelScope.launch {
            if (premiumRepository.isPremium.first()) return@launch
            // A reinstall or a second phone: the purchase is on the Play account but not yet
            // in this app's settings, and this is the screen where that would cost them.
            premiumRepository.restorePurchases()
            if (!premiumRepository.isPremium.first()) {
                _uiState.update { it.copy(offer = premiumRepository.offer()) }
            }
        }
    }

    fun load(localTreeId: Long, incomingTreeId: Long) = viewModelScope.launch {
        _uiState.update { it.copy(loading = true) }
        val comparison = share.compare(localTreeId, incomingTreeId).getOrNull()
        _uiState.update {
            it.copy(
                loading = false,
                localTitle = trees.getTree(localTreeId)?.title.orEmpty(),
                incomingTitle = trees.getTree(incomingTreeId)?.title.orEmpty(),
                comparison = comparison,
                failed = comparison == null,
            )
        }
    }

    fun toggle(difference: TreeDifference) = _uiState.update { state ->
        val comparison = state.comparison ?: return@update state
        state.copy(
            comparison = comparison.copy(
                differences = comparison.differences.map {
                    if (it.id == difference.id) it.copy(accepted = !it.accepted) else it
                },
            ),
        )
    }

    fun setAll(accepted: Boolean) = _uiState.update { state ->
        val comparison = state.comparison ?: return@update state
        state.copy(
            comparison = comparison.copy(
                differences = comparison.differences.map { it.copy(accepted = accepted) },
            ),
        )
    }

    fun apply() = viewModelScope.launch {
        if (!premiumRepository.isPremium.first()) return@launch
        val comparison = _uiState.value.comparison ?: return@launch
        _uiState.update { it.copy(applying = true) }
        share.applyUpdates(comparison)
            .onSuccess { result -> _uiState.update { it.copy(applying = false, applied = result) } }
            .onFailure { _uiState.update { it.copy(applying = false, failed = true) } }
    }

    fun purchasePremium(activity: Any) = viewModelScope.launch {
        _uiState.update { it.copy(purchasing = true) }
        val outcome = premiumRepository.purchase(activity)
        _uiState.update { it.copy(purchasing = false, purchaseMessage = outcome) }
    }

    fun onPurchaseMessageShown() = _uiState.update { it.copy(purchaseMessage = null) }
}
