package com.familytree.feature.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familytree.core.domain.repository.AppliedUpdates
import com.familytree.core.domain.repository.ShareRepository
import com.familytree.core.domain.repository.TreeRepository
import com.familytree.core.model.TreeComparison
import com.familytree.core.model.TreeDifference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) {
    val differences: List<TreeDifference> get() = comparison?.differences.orEmpty()
    val acceptedCount: Int get() = differences.count { it.accepted }
}

@HiltViewModel
class CompareViewModel @Inject constructor(
    private val share: ShareRepository,
    private val trees: TreeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompareUiState())
    val uiState: StateFlow<CompareUiState> = _uiState.asStateFlow()

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
        val comparison = _uiState.value.comparison ?: return@launch
        _uiState.update { it.copy(applying = true) }
        share.applyUpdates(comparison)
            .onSuccess { result -> _uiState.update { it.copy(applying = false, applied = result) } }
            .onFailure { _uiState.update { it.copy(applying = false, failed = true) } }
    }
}
