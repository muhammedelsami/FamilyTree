package com.familytree.feature.media

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familytree.core.designsystem.component.EmptyState
import com.familytree.core.designsystem.theme.FtDimens

@Composable
fun MediaFoldersRoute(
    treeId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MediaFoldersViewModel = hiltViewModel(),
) {
    LaunchedEffect(treeId) { viewModel.setTree(treeId) }
    val folders by viewModel.folders.collectAsStateWithLifecycle()

    // OpenDocumentTree is the only way to read a folder the app does not own; the grant
    // it returns has to be persisted straight away or it dies with the process.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::addFolder)
    }

    MediaFoldersScreen(
        folders = folders,
        onBack = onBack,
        onAdd = { picker.launch(null) },
        onRemove = viewModel::removeFolder,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaFoldersScreen(
    folders: List<FolderRow>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onRemove: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.media_folders)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_folder)) },
            )
        },
    ) { padding ->
        if (folders.isEmpty()) {
            EmptyState(
                modifier = Modifier.padding(padding),
                icon = Icons.Outlined.Folder,
                title = stringResource(R.string.no_media_folders),
                description = stringResource(R.string.no_media_folders_description),
                actionLabel = stringResource(R.string.add_folder),
                onAction = onAdd,
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(FtDimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(FtDimens.listItemSpacing),
        ) {
            item {
                Text(
                    text = stringResource(R.string.media_folders_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = FtDimens.listItemSpacing),
                )
            }
            items(folders, key = { it.id }) { folder ->
                FolderCard(folder, onRemove = { onRemove(folder.id) })
            }
        }
    }
}

@Composable
private fun FolderCard(folder: FolderRow, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(FtDimens.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FtDimens.listItemSpacing),
        ) {
            Icon(
                imageVector = if (folder.available) Icons.Outlined.Folder else Icons.Outlined.FolderOff,
                contentDescription = null,
                tint = if (folder.available) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    // A revoked grant or a removed card looks exactly like a missing
                    // photo, so the cause is named here rather than left to guesswork.
                    text = if (folder.available) {
                        stringResource(R.string.folder_available)
                    } else {
                        stringResource(R.string.folder_unavailable)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (folder.available) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.remove))
            }
        }
    }
}

/** A media folder plus whether it can still be read. */
data class FolderRow(
    val id: Long,
    val name: String,
    val available: Boolean,
    val uri: Uri?,
)
