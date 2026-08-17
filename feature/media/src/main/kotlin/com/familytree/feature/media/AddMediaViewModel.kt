package com.familytree.feature.media

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familytree.core.domain.repository.MediaRepository
import com.familytree.core.media.ImportResult
import com.familytree.core.media.MediaKind
import com.familytree.core.media.MediaResolver
import com.familytree.core.media.MediaStorage
import com.familytree.core.model.MediaObject
import com.familytree.core.model.OwnerType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class AddMediaState(
    val importing: Boolean = false,
    /**
     * Set when the chosen file is already in the tree's folder under the same name and
     * size. The user decides whether that is the same photograph or a different one.
     */
    val duplicate: Duplicate? = null,
    /** The media record just created, so the caller can open or crop it. */
    val created: Created? = null,
    val failed: Boolean = false,
) {
    data class Duplicate(val filename: String, val file: File, val kind: MediaKind)
    data class Created(val mediaId: Long, val croppable: Boolean)
}

@HiltViewModel
class AddMediaViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val storage: MediaStorage,
    private val resolver: MediaResolver,
) : ViewModel() {

    private val _state = MutableStateFlow(AddMediaState())
    val state: StateFlow<AddMediaState> = _state.asStateFlow()

    private var treeId = 0L
    private var ownerType = OwnerType.PERSON
    private var ownerId = 0L

    fun configure(treeId: Long, ownerType: OwnerType, ownerId: Long) {
        this.treeId = treeId
        this.ownerType = ownerType
        this.ownerId = ownerId
    }

    fun importFile(uri: Uri) = viewModelScope.launch {
        _state.update { it.copy(importing = true, failed = false) }
        when (val result = storage.importFile(treeId, uri)) {
            is ImportResult.Copied -> attach(result.filename, result.kind)
            is ImportResult.AlreadyPresent -> _state.update {
                it.copy(
                    importing = false,
                    duplicate = AddMediaState.Duplicate(result.filename, result.file, result.kind),
                )
            }
            is ImportResult.Failed -> _state.update { it.copy(importing = false, failed = true) }
        }
    }

    /** A destination inside the tree's folder for the camera app to write into. */
    suspend fun newCameraFile(): File = storage.newCameraFile(treeId)

    /** A photograph the camera has already written into the tree's folder. */
    fun attachCameraFile(file: File) = viewModelScope.launch {
        if (!file.isFile || file.length() == 0L) {
            // The camera was cancelled: it leaves behind the empty file it was given.
            file.delete()
            return@launch
        }
        resolver.invalidate(treeId)
        attach(file.name, MediaKind.IMAGE)
    }

    /** The chosen file is the one already stored: point a new record at it. */
    fun reuseDuplicate() = viewModelScope.launch {
        val duplicate = _state.value.duplicate ?: return@launch
        attach(duplicate.filename, duplicate.kind)
    }

    /** The chosen file is a different picture that happens to match: keep both. */
    fun keepBothCopies() = viewModelScope.launch {
        val duplicate = _state.value.duplicate ?: return@launch
        _state.update { it.copy(importing = true, duplicate = null) }
        when (val result = storage.duplicate(treeId, duplicate.file)) {
            is ImportResult.Copied -> attach(result.filename, result.kind)
            is ImportResult.AlreadyPresent -> attach(result.filename, result.kind)
            is ImportResult.Failed -> _state.update { it.copy(importing = false, failed = true) }
        }
    }

    private suspend fun attach(filename: String, kind: MediaKind) {
        val mediaId = mediaRepository.create(
            media = MediaObject(
                treeId = treeId,
                // Only the filename is stored, never a full path: that is what lets the
                // tree be opened on another device without every photo going missing.
                file = filename,
                format = filename.substringAfterLast('.', "").lowercase().takeIf { it.isNotBlank() },
            ),
            ownerType = ownerType,
            ownerId = ownerId,
        )
        resolver.invalidate(treeId)
        _state.update {
            it.copy(
                importing = false,
                duplicate = null,
                created = AddMediaState.Created(mediaId, croppable = kind.isCroppable),
            )
        }
    }

    fun consumeResult() = _state.update { it.copy(created = null, failed = false) }

    fun dismissDuplicate() = _state.update { it.copy(duplicate = null) }
}
