package com.familytree.feature.media

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familytree.core.domain.repository.MediaRepository
import com.familytree.core.domain.repository.TreeRepository
import com.familytree.core.media.ImportResult
import com.familytree.core.media.MediaResolver
import com.familytree.core.media.MediaSource
import com.familytree.core.media.MediaStorage
import com.familytree.core.model.MediaFolder
import com.familytree.core.model.MediaObject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface GalleryUiState {
    data object Loading : GalleryUiState
    data object Empty : GalleryUiState
    data class Items(val media: List<MediaObject>) : GalleryUiState
}

/** Progress of a bulk maintenance run, shown as a banner while it works. */
data class MaintenanceState(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    /** Set when a run finishes, so the screen can report what changed. */
    val outcome: Outcome? = null,
) {
    enum class Outcome { LINKS_SHORTENED, FILES_COPIED, NOTHING_TO_DO, FAILED }
}

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val treeRepository: TreeRepository,
    private val resolver: MediaResolver,
    private val storage: MediaStorage,
) : ViewModel() {

    private val treeId = MutableStateFlow(0L)

    private val _maintenance = MutableStateFlow(MaintenanceState())
    val maintenance: StateFlow<MaintenanceState> = _maintenance.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<GalleryUiState> = treeId
        .flatMapLatest { id ->
            if (id == 0L) {
                flowOf(GalleryUiState.Loading)
            } else {
                mediaRepository.observeGallery(id).map { media ->
                    if (media.isEmpty()) GalleryUiState.Empty else GalleryUiState.Items(media)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GalleryUiState.Loading)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val folders: StateFlow<List<MediaFolder>> = treeId
        .flatMapLatest { id ->
            if (id == 0L) flowOf(emptyList()) else treeRepository.observeMediaFolders(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setTree(id: Long) {
        treeId.value = id
    }

    /**
     * Rewrites every link that only matched by filename down to that filename.
     *
     * A tree exported from a desktop program carries paths like `C:\Users\anna\Pictures\`
     * that are meaningless here. Once the file has been located anyway, keeping the dead
     * path helps nobody and breaks the tree again on the next device.
     */
    fun shortenLinks() = runMaintenance(MaintenanceState.Outcome.LINKS_SHORTENED) { media ->
        val resolved = resolver.resolve(treeId.value, media.file, folders.value)
        val name = resolved.name
        if (resolved.foundByFilename && !name.isNullOrBlank() && media.file != name) {
            mediaRepository.shortenLink(media.id, name)
            true
        } else {
            false
        }
    }

    /**
     * Copies every file the app does not own into the tree's own folder.
     *
     * This is what makes a tree self-contained: files inside app storage travel with the
     * ZIP backup, survive a granted folder being revoked, and can be renamed or cropped.
     */
    fun copyFilesIntoApp() = runMaintenance(MaintenanceState.Outcome.FILES_COPIED) { media ->
        val resolved = resolver.resolve(treeId.value, media.file, folders.value)
        when (val source = resolved.source) {
            is MediaSource.DocumentUri -> copyIn(media, source)
            is MediaSource.LocalFile -> if (storage.isOwned(source.file)) false else copyIn(media, source)
            else -> false
        }
    }

    private suspend fun copyIn(media: MediaObject, source: MediaSource): Boolean {
        val uri = when (source) {
            is MediaSource.DocumentUri -> source.uri
            is MediaSource.LocalFile -> Uri.fromFile(source.file)
            else -> return false
        }
        return when (val result = storage.importFile(treeId.value, uri)) {
            is ImportResult.Copied -> {
                mediaRepository.shortenLink(media.id, result.filename)
                true
            }
            is ImportResult.AlreadyPresent -> {
                // The file is already in the folder under this name: just point at it.
                mediaRepository.shortenLink(media.id, result.filename)
                true
            }
            is ImportResult.Failed -> false
        }
    }

    private fun runMaintenance(
        outcome: MaintenanceState.Outcome,
        action: suspend (MediaObject) -> Boolean,
    ) = viewModelScope.launch {
        val all = (uiState.value as? GalleryUiState.Items)?.media.orEmpty()
        _maintenance.value = MaintenanceState(running = true, total = all.size)
        var changed = 0
        all.forEachIndexed { index, media ->
            if (runCatching { action(media) }.getOrDefault(false)) changed++
            _maintenance.value = _maintenance.value.copy(done = index + 1)
        }
        resolver.invalidate(treeId.value)
        _maintenance.value = MaintenanceState(
            outcome = if (changed > 0) outcome else MaintenanceState.Outcome.NOTHING_TO_DO,
            done = changed,
        )
    }

    fun clearMaintenanceOutcome() {
        _maintenance.value = MaintenanceState()
    }

    fun delete(mediaId: Long) = viewModelScope.launch {
        mediaRepository.delete(mediaId)
    }
}
