package com.familytree.feature.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familytree.core.domain.repository.TreeRepository
import com.familytree.core.media.MediaResolver
import com.familytree.core.media.PdfPreviewer
import com.familytree.core.model.MediaFolder
import com.familytree.core.ui.media.LocalMediaContext
import com.familytree.core.ui.media.MediaContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Makes photographs resolvable for everything drawn inside it.
 *
 * Wrapped around the tree's screens rather than around the gallery alone, because
 * portraits appear in the person list, on diagram cards and in the profile header — all
 * of which would otherwise each need the resolver and the tree's granted folders passed
 * down to them.
 */
@Composable
fun ProvideMediaContext(
    treeId: Long,
    content: @Composable () -> Unit,
) {
    val viewModel: MediaContextViewModel = hiltViewModel()
    LaunchedEffect(treeId) { viewModel.setTree(treeId) }
    val folders by viewModel.folders.collectAsStateWithLifecycle()

    val context = remember(treeId, folders) {
        MediaContext(
            resolver = viewModel.resolver,
            pdfPreviewer = viewModel.pdfPreviewer,
            treeId = treeId,
            folders = folders,
        )
    }
    CompositionLocalProvider(LocalMediaContext provides context, content = content)
}

@HiltViewModel
class MediaContextViewModel @Inject constructor(
    val resolver: MediaResolver,
    val pdfPreviewer: PdfPreviewer,
    treeRepository: TreeRepository,
) : ViewModel() {

    private val treeId = MutableStateFlow(0L)

    @OptIn(ExperimentalCoroutinesApi::class)
    val folders: StateFlow<List<MediaFolder>> = treeId
        .flatMapLatest { id ->
            if (id == 0L) flowOf(emptyList()) else treeRepository.observeMediaFolders(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setTree(id: Long) {
        if (treeId.value != id) {
            treeId.value = id
            // Answers cached for another tree's folders would be wrong for this one.
            resolver.invalidate()
        }
    }
}
