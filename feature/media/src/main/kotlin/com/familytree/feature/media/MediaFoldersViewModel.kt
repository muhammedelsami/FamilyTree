package com.familytree.feature.media

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familytree.core.domain.repository.TreeRepository
import com.familytree.core.media.MediaFolderAccess
import com.familytree.core.media.MediaResolver
import com.familytree.core.model.MediaFolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MediaFoldersViewModel @Inject constructor(
    private val treeRepository: TreeRepository,
    private val access: MediaFolderAccess,
    private val resolver: MediaResolver,
) : ViewModel() {

    private val treeId = MutableStateFlow(0L)

    @OptIn(ExperimentalCoroutinesApi::class)
    val folders: StateFlow<List<FolderRow>> = treeId
        .flatMapLatest { id ->
            if (id == 0L) flowOf(emptyList()) else treeRepository.observeMediaFolders(id)
        }
        .map { list ->
            list.map { folder ->
                FolderRow(
                    id = folder.id,
                    name = access.displayName(folder),
                    available = access.isAvailable(folder),
                    uri = folder.value.takeIf { folder.kind == MediaFolder.Kind.URI }?.let(Uri::parse),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setTree(id: Long) {
        treeId.value = id
    }

    fun addFolder(uri: Uri) = viewModelScope.launch {
        // Without the persisted grant the folder would work until the app is killed and
        // then quietly stop — worse than refusing it now.
        if (!access.persist(uri)) return@launch
        val id = treeId.value
        val existing = treeRepository.observeMediaFolders(id).first()
        if (existing.any { it.value == uri.toString() }) return@launch

        treeRepository.addMediaFolder(
            MediaFolder(treeId = id, kind = MediaFolder.Kind.URI, value = uri.toString()),
        )
        // Files that were unfindable a moment ago may now resolve.
        resolver.invalidate(id)
    }

    fun removeFolder(folderId: Long) = viewModelScope.launch {
        val id = treeId.value
        val folder = treeRepository.observeMediaFolders(id).first().firstOrNull { it.id == folderId }
        folder?.takeIf { it.kind == MediaFolder.Kind.URI }?.let { access.release(Uri.parse(it.value)) }
        treeRepository.removeMediaFolder(folderId)
        resolver.invalidate(id)
    }
}
