package com.familytree.feature.person

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familytree.core.domain.repository.MediaRepository
import com.familytree.core.domain.repository.PersonRepository
import com.familytree.core.domain.repository.SettingsRepository
import com.familytree.core.domain.usecase.DeletePersonUseCase
import com.familytree.core.domain.usecase.ObservePersonRelativesUseCase
import com.familytree.core.model.MediaObject
import com.familytree.core.model.OwnerType
import com.familytree.core.model.PersonDetails
import com.familytree.core.model.RelativeGroup
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

data class ProfileUiState(
    val details: PersonDetails? = null,
    val relatives: List<RelativeGroup> = emptyList(),
    val portrait: MediaObject? = null,
    val expertMode: Boolean = false,
    val loading: Boolean = true,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val personRepository: PersonRepository,
    private val mediaRepository: MediaRepository,
    private val observeRelatives: ObservePersonRelativesUseCase,
    private val deletePerson: DeletePersonUseCase,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val personId = MutableStateFlow(0L)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ProfileUiState> = personId
        .flatMapLatest { id ->
            if (id == 0L) {
                flowOf(ProfileUiState())
            } else {
                personRepository.observePersonDetails(id).flatMapLatest { details ->
                    if (details == null) {
                        flowOf(ProfileUiState(loading = false))
                    } else {
                        combine(
                            observeRelatives(details.person.treeId, id),
                            settingsRepository.settings.map { it.expertMode },
                            mediaRepository.observePortraitOf(OwnerType.PERSON, id),
                        ) { relatives, expert, portrait ->
                            ProfileUiState(
                                details = details,
                                relatives = relatives,
                                portrait = portrait,
                                expertMode = expert,
                                loading = false,
                            )
                        }
                    }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProfileUiState(),
        )

    fun setPerson(id: Long) {
        personId.value = id
    }

    fun onDelete(onDeleted: () -> Unit) = viewModelScope.launch {
        val id = personId.value
        if (id != 0L) {
            deletePerson(id)
            onDeleted()
        }
    }
}
