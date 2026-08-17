package com.familytree.feature.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familytree.core.designsystem.component.EmptyState
import com.familytree.core.designsystem.component.FullScreenLoading
import com.familytree.core.designsystem.theme.FtDimens
import com.familytree.core.model.MediaObject
import com.familytree.core.ui.media.MediaImage

@Composable
fun GalleryRoute(
    treeId: Long,
    onOpenMedia: (Long) -> Unit,
    onOpenFolders: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GalleryViewModel = hiltViewModel(),
) {
    LaunchedEffect(treeId) { viewModel.setTree(treeId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val maintenance by viewModel.maintenance.collectAsStateWithLifecycle()

    GalleryScreen(
        uiState = uiState,
        maintenance = maintenance,
        onOpenMedia = onOpenMedia,
        onOpenFolders = onOpenFolders,
        onShortenLinks = viewModel::shortenLinks,
        onCopyFiles = viewModel::copyFilesIntoApp,
        onMaintenanceShown = viewModel::clearMaintenanceOutcome,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GalleryScreen(
    uiState: GalleryUiState,
    maintenance: MaintenanceState,
    onOpenMedia: (Long) -> Unit,
    onOpenFolders: () -> Unit,
    onShortenLinks: () -> Unit,
    onCopyFiles: () -> Unit,
    onMaintenanceShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }

    val outcomeMessage = maintenance.outcome?.let { outcome ->
        when (outcome) {
            MaintenanceState.Outcome.LINKS_SHORTENED ->
                pluralStringResource(R.plurals.links_shortened, maintenance.done, maintenance.done)
            MaintenanceState.Outcome.FILES_COPIED ->
                pluralStringResource(R.plurals.files_copied, maintenance.done, maintenance.done)
            MaintenanceState.Outcome.NOTHING_TO_DO -> stringResource(R.string.nothing_to_do)
            MaintenanceState.Outcome.FAILED -> stringResource(R.string.maintenance_failed)
        }
    }
    LaunchedEffect(outcomeMessage) {
        outcomeMessage?.let {
            snackbarHostState.showSnackbar(it)
            onMaintenanceShown()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    val count = (uiState as? GalleryUiState.Items)?.media?.size ?: 0
                    Column {
                        Text(stringResource(R.string.gallery))
                        if (count > 0) {
                            Text(
                                text = pluralStringResource(R.plurals.media_count, count, count),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenFolders) {
                        Icon(
                            Icons.Outlined.CreateNewFolder,
                            contentDescription = stringResource(R.string.media_folders),
                        )
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.shorten_links)) },
                            onClick = { menuOpen = false; onShortenLinks() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.copy_files_in)) },
                            onClick = { menuOpen = false; onCopyFiles() },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (maintenance.running) {
                LinearProgressIndicator(
                    progress = {
                        if (maintenance.total == 0) 0f else maintenance.done.toFloat() / maintenance.total
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            when (uiState) {
                GalleryUiState.Loading -> FullScreenLoading()

                GalleryUiState.Empty -> EmptyState(
                    icon = Icons.Outlined.PhotoLibrary,
                    title = stringResource(R.string.gallery_empty),
                    description = stringResource(R.string.gallery_empty_description),
                )

                is GalleryUiState.Items -> MediaGrid(uiState.media, onOpenMedia)
            }
        }
    }
}

@Composable
private fun MediaGrid(media: List<MediaObject>, onOpenMedia: (Long) -> Unit) {
    LazyVerticalGrid(
        // Adaptive rather than a fixed count: two columns on a phone, more on a tablet,
        // without the grid being described twice.
        columns = GridCells.Adaptive(minSize = 140.dp),
        contentPadding = PaddingValues(FtDimens.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(FtDimens.listItemSpacing),
        verticalArrangement = Arrangement.spacedBy(FtDimens.listItemSpacing),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(media, key = { it.id }) { item ->
            MediaTile(item, onClick = { onOpenMedia(item.id) })
        }
    }
}

@Composable
private fun MediaTile(media: MediaObject, onClick: () -> Unit) {
    val label = media.title?.takeIf { it.isNotBlank() } ?: media.file?.substringAfterLast('/')
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // A square keeps the grid even whatever the photographs' proportions are.
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            MediaImage(media = media, modifier = Modifier.fillMaxSize(), contentDescription = label)
        }
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            )
        }
    }
}
