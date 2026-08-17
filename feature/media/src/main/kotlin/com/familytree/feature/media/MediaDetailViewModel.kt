package com.familytree.feature.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familytree.core.domain.repository.MediaRepository
import com.familytree.core.domain.repository.TreeRepository
import com.familytree.core.media.MediaResolver
import com.familytree.core.media.MediaSource
import com.familytree.core.media.MediaStorage
import com.familytree.core.media.ResolvedMedia
import com.familytree.core.model.MediaObject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MediaDetailUiState(
    val loading: Boolean = true,
    val media: MediaObject? = null,
    val resolved: ResolvedMedia = ResolvedMedia.Missing,
    /** How many records point at this file — deleting it affects all of them. */
    val referenceCount: Int = 0,
    /** True when the file lives in the app's own folder and can be renamed or cropped. */
    val editableFile: Boolean = false,
    val deleted: Boolean = false,
    val message: Message? = null,
) {
    enum class Message { RENAMED, RENAME_FAILED, FILE_DELETED, FILE_DELETE_FAILED }
}

@HiltViewModel
class MediaDetailViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val treeRepository: TreeRepository,
    private val resolver: MediaResolver,
    private val storage: MediaStorage,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MediaDetailUiState())
    val uiState: StateFlow<MediaDetailUiState> = _uiState.asStateFlow()

    private var treeId = 0L
    private var mediaId = 0L

    fun load(treeId: Long, mediaId: Long) {
        this.treeId = treeId
        this.mediaId = mediaId
        refresh()
    }

    private fun refresh() = viewModelScope.launch {
        val media = mediaRepository.get(mediaId)
        if (media == null) {
            _uiState.value = MediaDetailUiState(loading = false, deleted = true)
            return@launch
        }
        val folders = treeRepository.observeMediaFolders(treeId).first()
        val resolved = resolver.resolve(treeId, media.file, folders)
        _uiState.value = MediaDetailUiState(
            loading = false,
            media = media,
            resolved = resolved,
            referenceCount = mediaRepository.referenceCount(mediaId),
            editableFile = (resolved.source as? MediaSource.LocalFile)?.let { storage.isOwned(it.file) } == true,
        )
    }

    fun setTitle(title: String) = update { it.copy(title = title.takeIf(String::isNotBlank)) }

    fun setMediaType(type: String) = update { it.copy(mediaType = type.takeIf(String::isNotBlank)) }

    private fun update(transform: (MediaObject) -> MediaObject) = viewModelScope.launch {
        val current = _uiState.value.media ?: return@launch
        val updated = transform(current)
        _uiState.update { it.copy(media = updated) }
        mediaRepository.update(updated)
    }

    /**
     * Renames the file on disk and repoints the record at the new name.
     *
     * Both halves matter: renaming the file alone would break the link, and changing the
     * link alone would point at nothing.
     */
    fun renameFile(newName: String) = viewModelScope.launch {
        val state = _uiState.value
        val media = state.media ?: return@launch
        if (newName.isBlank() || !state.editableFile) return@launch

        if (storage.rename(treeId, state.resolved.source, newName)) {
            mediaRepository.shortenLink(media.id, newName)
            refresh()
            _uiState.update { it.copy(message = MediaDetailUiState.Message.RENAMED) }
        } else {
            _uiState.update { it.copy(message = MediaDetailUiState.Message.RENAME_FAILED) }
        }
    }

    /** Removes the record. The file itself is only deleted when explicitly asked for. */
    fun deleteRecord() = viewModelScope.launch {
        mediaRepository.delete(mediaId)
        _uiState.update { it.copy(deleted = true) }
    }

    fun deleteFile() = viewModelScope.launch {
        val state = _uiState.value
        val deleted = storage.delete(treeId, state.resolved.source)
        if (deleted) {
            mediaRepository.delete(mediaId)
            _uiState.update {
                it.copy(deleted = true, message = MediaDetailUiState.Message.FILE_DELETED)
            }
        } else {
            _uiState.update { it.copy(message = MediaDetailUiState.Message.FILE_DELETE_FAILED) }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    /** Re-reads everything after the image was cropped outside this screen. */
    fun reload() {
        resolver.invalidate(treeId)
        refresh()
    }
}
