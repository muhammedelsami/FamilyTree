package com.familytree.feature.person

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familytree.core.domain.repository.MediaRepository
import com.familytree.core.domain.usecase.DeletePersonUseCase
import com.familytree.core.domain.usecase.ObservePersonListUseCase
import com.familytree.core.model.MediaObject
import com.familytree.core.model.PersonSort
import com.familytree.core.model.PersonSorting
import com.familytree.core.model.PersonSummary
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

sealed interface PersonListUiState {
    data object Loading : PersonListUiState
    /** The tree has no people at all — different from a search that found none. */
    data object Empty : PersonListUiState
    data class NoMatches(val query: String) : PersonListUiState
    data class People(val people: List<PersonSummary>) : PersonListUiState
}

@HiltViewModel
class PersonListViewModel @Inject constructor(
    private val observePersonList: ObservePersonListUseCase,
    private val deletePerson: DeletePersonUseCase,
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    private val treeId = MutableStateFlow(0L)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _sorting = MutableStateFlow(PersonSorting())
    val sorting: StateFlow<PersonSorting> = _sorting.asStateFlow()

    /** True while the tree holds people, so an empty result can be told from an empty tree. */
    private val treeHasPeople = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val uiState: StateFlow<PersonListUiState> =
        combine(
            treeId,
            // Re-filtering on every keystroke would rebuild the list for each letter.
            _query.debounce(SEARCH_DEBOUNCE_MS),
            _sorting,
        ) { tree, query, sorting -> Triple(tree, query, sorting) }
            .flatMapLatest { (tree, query, sorting) ->
                if (tree == 0L) {
                    kotlinx.coroutines.flow.flowOf(PersonListUiState.Loading)
                } else {
                    observePersonList(tree, query, sorting).map { people ->
                        when {
                            people.isNotEmpty() -> PersonListUiState.People(people)
                            query.isNotBlank() -> PersonListUiState.NoMatches(query)
                            else -> PersonListUiState.Empty
                        }
                    }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = PersonListUiState.Loading,
            )

    fun setTree(id: Long) {
        treeId.value = id
    }

    /**
     * Portraits for every person in the tree, in one query rather than one per row: a
     * list of two thousand people would otherwise open two thousand flows while scrolling.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val portraits: StateFlow<Map<Long, MediaObject>> = treeId
        .flatMapLatest { id ->
            if (id == 0L) flowOf(emptyMap()) else mediaRepository.observePersonPortraits(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun onSortSelected(sort: PersonSort) {
        _sorting.value = _sorting.value.toggled(sort)
    }

    fun onDelete(personId: Long) = viewModelScope.launch {
        deletePerson(personId)
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 200L
    }
}
