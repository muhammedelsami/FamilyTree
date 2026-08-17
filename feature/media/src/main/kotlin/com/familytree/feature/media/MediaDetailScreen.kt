package com.familytree.feature.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familytree.core.designsystem.component.FullScreenLoading
import com.familytree.core.designsystem.theme.FtDimens
import com.familytree.core.media.MediaSource
import com.familytree.core.ui.media.MediaImage

@Composable
fun MediaDetailRoute(
    treeId: Long,
    mediaId: Long,
    onBack: () -> Unit,
    onCrop: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MediaDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(treeId, mediaId) { viewModel.load(treeId, mediaId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // The record is gone: there is nothing left to show, so the screen closes itself.
    LaunchedEffect(uiState.deleted) { if (uiState.deleted) onBack() }

    MediaDetailScreen(
        uiState = uiState,
        onBack = onBack,
        onTitleChange = viewModel::setTitle,
        onTypeChange = viewModel::setMediaType,
        onRename = viewModel::renameFile,
        onDeleteRecord = viewModel::deleteRecord,
        onDeleteFile = viewModel::deleteFile,
        onCrop = { onCrop(mediaId) },
        onMessageShown = viewModel::onMessageShown,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaDetailScreen(
    uiState: MediaDetailUiState,
    onBack: () -> Unit,
    onTitleChange: (String) -> Unit,
    onTypeChange: (String) -> Unit,
    onRename: (String) -> Unit,
    onDeleteRecord: () -> Unit,
    onDeleteFile: () -> Unit,
    onCrop: () -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    val message = uiState.message?.let {
        stringResource(
            when (it) {
                MediaDetailUiState.Message.RENAMED -> R.string.file_renamed
                MediaDetailUiState.Message.RENAME_FAILED -> R.string.file_rename_failed
                MediaDetailUiState.Message.FILE_DELETED -> R.string.file_deleted
                MediaDetailUiState.Message.FILE_DELETE_FAILED -> R.string.file_delete_failed
            },
        )
    }
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            onMessageShown()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.media)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    if (uiState.editableFile && uiState.resolved.kind.isCroppable) {
                        IconButton(onClick = onCrop) {
                            Icon(Icons.Outlined.Crop, contentDescription = stringResource(R.string.crop))
                        }
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (uiState.editableFile) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.rename_file)) },
                                onClick = { menuOpen = false; renaming = true },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.remove_media)) },
                            onClick = { menuOpen = false; onDeleteRecord() },
                        )
                        if (uiState.editableFile) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete_file)) },
                                onClick = { menuOpen = false; confirmingDelete = true },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (uiState.loading) {
            FullScreenLoading(Modifier.padding(padding))
            return@Scaffold
        }
        val media = uiState.media ?: return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                MediaImage(
                    resolved = uiState.resolved,
                    format = media.format,
                    // Fit rather than crop: on a detail screen the whole document has to
                    // be visible, even at an awkward aspect ratio.
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                    contentDescription = media.title,
                )
            }

            Column(
                modifier = Modifier.padding(FtDimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(FtDimens.listItemSpacing),
            ) {
                OutlinedTextField(
                    value = media.title.orEmpty(),
                    onValueChange = onTitleChange,
                    label = { Text(stringResource(R.string.title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = media.mediaType.orEmpty(),
                    onValueChange = onTypeChange,
                    label = { Text(stringResource(R.string.media_type)) },
                    supportingText = { Text(stringResource(R.string.media_type_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                HorizontalDivider(Modifier.padding(vertical = FtDimens.listItemSpacing))

                FileFacts(uiState)
            }
        }
    }

    if (renaming) {
        RenameDialog(
            current = uiState.resolved.name.orEmpty(),
            onDismiss = { renaming = false },
            onConfirm = { renaming = false; onRename(it) },
        )
    }
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.delete_file)) },
            text = { Text(stringResource(R.string.delete_file_warning)) },
            confirmButton = {
                TextButton(onClick = { confirmingDelete = false; onDeleteFile() }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/**
 * Where the file actually is, in plain words.
 *
 * This is the screen a user reaches when a photograph will not appear, so it says what
 * the app looked for and what it found rather than only showing a broken tile.
 */
@Composable
private fun FileFacts(uiState: MediaDetailUiState) {
    val media = uiState.media ?: return
    Column(verticalArrangement = Arrangement.spacedBy(FtDimens.listItemSpacing)) {
        Fact(stringResource(R.string.file_link), media.file.orEmpty())

        val location = when (val source = uiState.resolved.source) {
            is MediaSource.LocalFile ->
                if (uiState.editableFile) {
                    stringResource(R.string.stored_in_app)
                } else {
                    source.file.parent.orEmpty()
                }
            is MediaSource.DocumentUri -> stringResource(R.string.stored_in_granted_folder)
            is MediaSource.Web -> stringResource(R.string.stored_online)
            MediaSource.Missing -> stringResource(R.string.file_not_found)
        }
        Fact(stringResource(R.string.location), location)

        if (uiState.referenceCount > 1) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                    label = {
                        Text(
                            pluralStringResource(
                                R.plurals.shared_by_records,
                                uiState.referenceCount,
                                uiState.referenceCount,
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value.ifBlank { stringResource(R.string.not_set) },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_file)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.file_name)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank() && name != current) {
                Text(stringResource(R.string.rename))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
