package com.familytree.feature.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familytree.core.domain.usecase.CreateFamilyUseCase
import com.familytree.core.domain.usecase.DeleteFamilyUseCase
import com.familytree.core.domain.usecase.ObserveFamilyListUseCase
import com.familytree.core.model.FamilySort
import com.familytree.core.model.FamilySorting
import com.familytree.core.model.FamilySummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface FamilyListUiState {
    data object Loading : FamilyListUiState
    data object Empty : FamilyListUiState
    data class NoMatches(val query: String) : FamilyListUiState
    data class Families(val families: List<FamilySummary>) : FamilyListUiState
}

@HiltViewModel
class FamilyListViewModel @Inject constructor(
    private val observeFamilyList: ObserveFamilyListUseCase,
    private val createFamily: CreateFamilyUseCase,
    private val deleteFamily: DeleteFamilyUseCase,
) : ViewModel() {

    private val treeId = MutableStateFlow(0L)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _sorting = MutableStateFlow(FamilySorting())
    val sorting: StateFlow<FamilySorting> = _sorting.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val uiState: StateFlow<FamilyListUiState> =
        combine(treeId, _query.debounce(SEARCH_DEBOUNCE_MS), _sorting) { tree, query, sorting ->
            Triple(tree, query, sorting)
        }
            .flatMapLatest { (tree, query, sorting) ->
                if (tree == 0L) {
                    flowOf(FamilyListUiState.Loading)
                } else {
                    observeFamilyList(tree, query, sorting).map { families ->
                        when {
                            families.isNotEmpty() -> FamilyListUiState.Families(families)
                            query.isNotBlank() -> FamilyListUiState.NoMatches(query)
                            else -> FamilyListUiState.Empty
                        }
                    }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = FamilyListUiState.Loading,
            )

    fun setTree(id: Long) {
        treeId.value = id
    }

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun onSortSelected(sort: FamilySort) {
        _sorting.value = _sorting.value.toggled(sort)
    }

    fun onCreateFamily(onCreated: (Long) -> Unit) = viewModelScope.launch {
        onCreated(createFamily(treeId.value))
    }

    fun onDelete(familyId: Long) = viewModelScope.launch {
        deleteFamily(familyId)
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 200L
    }
}
