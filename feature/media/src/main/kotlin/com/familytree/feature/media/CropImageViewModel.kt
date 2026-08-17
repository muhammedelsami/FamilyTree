package com.familytree.feature.media

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familytree.core.domain.repository.MediaRepository
import com.familytree.core.domain.repository.TreeRepository
import com.familytree.core.media.CropRect
import com.familytree.core.media.ImageCropper
import com.familytree.core.media.MediaResolver
import com.familytree.core.media.MediaSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class CropUiState(
    val preview: Bitmap? = null,
    val crop: CropRect = CropRect(),
    val rotation: Int = 0,
    val saving: Boolean = false,
    val saved: Boolean = false,
)

@HiltViewModel
class CropImageViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val treeRepository: TreeRepository,
    private val resolver: MediaResolver,
    private val cropper: ImageCropper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CropUiState())
    val uiState: StateFlow<CropUiState> = _uiState.asStateFlow()

    private var treeId = 0L
    private var file: File? = null

    fun load(treeId: Long, mediaId: Long) = viewModelScope.launch {
        this@CropImageViewModel.treeId = treeId
        val media = mediaRepository.get(mediaId) ?: return@launch
        val folders = treeRepository.observeMediaFolders(treeId).first()
        val resolved = resolver.resolve(treeId, media.file, folders)
        val local = (resolved.source as? MediaSource.LocalFile)?.file ?: return@launch
        file = local
        _uiState.update {
            it.copy(
                preview = cropper.loadPreview(local),
                // Opens a little inside the edges, so the handles are visible and
                // obviously draggable rather than flush against the frame.
                crop = CropRect(0.07f, 0.07f, 0.93f, 0.93f),
            )
        }
    }

    fun rotate() = _uiState.update {
        // Rotating resets the selection: a rectangle drawn on the old orientation means
        // something different once the image turns.
        it.copy(rotation = (it.rotation + 90) % 360, crop = CropRect(0.07f, 0.07f, 0.93f, 0.93f))
    }

    fun setCrop(crop: CropRect) = _uiState.update { it.copy(crop = crop) }

    fun save() = viewModelScope.launch {
        val target = file ?: return@launch
        _uiState.update { it.copy(saving = true) }
        val state = _uiState.value
        cropper.cropInPlace(target, state.crop, state.rotation)
        // The file changed underneath every cached decode of it.
        resolver.invalidate(treeId)
        _uiState.update { it.copy(saving = false, saved = true) }
    }
}
