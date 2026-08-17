package com.familytree.feature.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familytree.core.domain.repository.MediaRepository
import com.familytree.core.model.MediaObject
import com.familytree.core.model.OwnerType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class OwnerMediaViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    private val owner = MutableStateFlow<Pair<OwnerType, Long>?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val media: StateFlow<List<MediaObject>> = owner
        .flatMapLatest { current ->
            if (current == null) {
                flowOf(emptyList())
            } else {
                mediaRepository.observeFor(current.first, current.second)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun load(ownerType: OwnerType, ownerId: Long) {
        owner.value = ownerType to ownerId
    }

    /** Chooses which picture stands for this record on cards, lists and the diagram. */
    fun setPortrait(mediaId: Long) = viewModelScope.launch {
        val current = owner.value ?: return@launch
        mediaRepository.setPrimary(mediaId, current.first, current.second)
    }
}
