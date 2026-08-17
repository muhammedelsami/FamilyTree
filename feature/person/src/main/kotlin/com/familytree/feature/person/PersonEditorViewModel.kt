package com.familytree.feature.person

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familytree.core.domain.repository.PersonRepository
import com.familytree.core.domain.repository.SettingsRepository
import com.familytree.core.domain.usecase.AddRelativeUseCase
import com.familytree.core.domain.usecase.SavePersonUseCase
import com.familytree.core.model.EventCatalog
import com.familytree.core.model.PersonDraft
import com.familytree.core.model.Relation
import com.familytree.core.model.RelativeTarget
import com.familytree.core.model.Sex
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PersonEditorEvent {
    data class Saved(val personId: Long) : PersonEditorEvent
    data class Failed(val message: String) : PersonEditorEvent
}

@HiltViewModel
class PersonEditorViewModel @Inject constructor(
    private val personRepository: PersonRepository,
    private val savePerson: SavePersonUseCase,
    private val addRelative: AddRelativeUseCase,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _draft = MutableStateFlow(PersonDraft(treeId = 0L))
    val draft: StateFlow<PersonDraft> = _draft.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    /** Expert mode unlocks the full date grammar in the date picker. */
    val expertMode: StateFlow<Boolean> = settingsRepository.settings
        .map { it.expertMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _events = Channel<PersonEditorEvent>(Channel.BUFFERED)
    val events: Flow<PersonEditorEvent> = _events.receiveAsFlow()

    private var pivotPersonId: Long? = null
    private var relation: Relation? = null
    private var loaded = false

    /** Called once from the screen; re-entry after a rotation must not wipe the form. */
    fun initialise(treeId: Long, personId: Long?, pivotPersonId: Long?, relation: Relation?) {
        if (loaded) return
        loaded = true
        this.pivotPersonId = pivotPersonId
        this.relation = relation
        _draft.value = PersonDraft(treeId = treeId, personId = personId)
        if (personId != null) load(personId, treeId)
    }

    private fun load(personId: Long, treeId: Long) = viewModelScope.launch {
        val person = personRepository.getPerson(personId) ?: return@launch
        val name = personRepository.getNames(personId).minByOrNull { it.position }
        val events = personRepository.getEvents(personId)
        val birth = EventCatalog.BIRTH_TAGS.firstNotNullOfOrNull { tag ->
            events.firstOrNull { it.tag == tag }
        }
        val death = EventCatalog.DEATH_TAGS.firstNotNullOfOrNull { tag ->
            events.firstOrNull { it.tag == tag }
        }
        _draft.value = PersonDraft(
            treeId = treeId,
            personId = personId,
            given = name?.given.orEmpty(),
            surname = name?.surname.orEmpty(),
            sex = person.sex,
            birthDate = birth?.date.orEmpty(),
            birthPlace = birth?.place.orEmpty(),
            deceased = death != null,
            deathDate = death?.date.orEmpty(),
            deathPlace = death?.place.orEmpty(),
        )
    }

    fun update(transform: (PersonDraft) -> PersonDraft) {
        _draft.value = transform(_draft.value)
    }

    fun onSave() = viewModelScope.launch {
        _saving.value = true
        runCatching {
            val personId = savePerson(_draft.value)
            // Linking happens after the person exists, because the family needs their id.
            val pivot = pivotPersonId
            val how = relation
            if (pivot != null && how != null) {
                addRelative(
                    treeId = _draft.value.treeId,
                    pivotPersonId = pivot,
                    newPersonId = personId,
                    target = RelativeTarget(how),
                )
            }
            personId
        }
            .onSuccess { _events.send(PersonEditorEvent.Saved(it)) }
            .onFailure { _events.send(PersonEditorEvent.Failed(it.message.orEmpty())) }
        _saving.value = false
    }

    fun onSexSelected(sex: Sex) = update {
        // Tapping the selected option again clears it, since "unknown" is a real answer.
        it.copy(sex = if (it.sex == sex) Sex.NONE else sex)
    }
}
