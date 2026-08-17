package com.familytree.feature.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familytree.core.domain.repository.EventRepository
import com.familytree.core.domain.repository.FamilyRepository
import com.familytree.core.domain.repository.PersonRepository
import com.familytree.core.domain.repository.SettingsRepository
import com.familytree.core.model.Event
import com.familytree.core.model.EventCatalog
import com.familytree.core.model.FamilyDetails
import com.familytree.core.model.OwnerType
import com.familytree.core.model.PersonSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FamilyDetailUiState(
    val details: FamilyDetails? = null,
    /** Members as list rows — a family screen has to show names, not identifiers. */
    val partners: List<PersonSummary> = emptyList(),
    val children: List<PersonSummary> = emptyList(),
    val knownPlaces: List<String> = emptyList(),
    val expertMode: Boolean = false,
    val loading: Boolean = true,
)

@HiltViewModel
class FamilyDetailViewModel @Inject constructor(
    private val familyRepository: FamilyRepository,
    private val personRepository: PersonRepository,
    private val eventRepository: EventRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val familyId = MutableStateFlow(0L)
    private val treeId = MutableStateFlow(0L)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<FamilyDetailUiState> = familyId
        .flatMapLatest { id ->
            if (id == 0L) {
                flowOf(FamilyDetailUiState())
            } else {
                combine(
                    familyRepository.observeFamilyDetails(id),
                    personRepository.observePersonSummaries(treeId.value),
                    eventRepository.observeKnownPlaces(treeId.value),
                    settingsRepository.settings.map { it.expertMode },
                ) { details, summaries, places, expert ->
                    val byId = summaries.associateBy { it.person.id }
                    FamilyDetailUiState(
                        details = details,
                        partners = (details?.husbands.orEmpty() + details?.wives.orEmpty())
                            .mapNotNull { byId[it.id] },
                        children = details?.children.orEmpty().mapNotNull { byId[it.id] },
                        knownPlaces = places,
                        expertMode = expert,
                        loading = false,
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FamilyDetailUiState(),
        )

    fun setFamily(id: Long, tree: Long) {
        treeId.value = tree
        familyId.value = id
    }

    fun onEventChange(event: Event) = viewModelScope.launch {
        eventRepository.upsert(event)
    }

    fun onAddEvent(tag: String) = viewModelScope.launch {
        eventRepository.upsert(
            Event(
                treeId = treeId.value,
                ownerType = OwnerType.FAMILY,
                ownerId = familyId.value,
                tag = tag,
                // GEDCOM's way of saying the event happened without further detail.
                value = "Y",
            ),
        )
    }

    fun onDeleteEvent(eventId: Long) = viewModelScope.launch {
        eventRepository.delete(eventId)
    }

    fun onRemoveMember(personId: Long) = viewModelScope.launch {
        familyRepository.removeMember(familyId.value, personId)
    }

    /** The tags worth offering for a family, in the order the editor shows them. */
    val addableTags: List<String> = EventCatalog.ALL_FAMILY_TAGS
}
