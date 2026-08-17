package com.familytree.feature.backup

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familytree.core.designsystem.component.EmptyState
import com.familytree.core.designsystem.theme.FtDimens
import com.familytree.core.domain.repository.BackupFile

@Composable
fun BackupRoute(
    treeId: Long,
    onBack: () -> Unit,
    onOpenTree: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    LaunchedEffect(treeId) { viewModel.load(treeId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val saveTo = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let { viewModel.exportTo(it.toString()) } }

    val openFrom = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.restoreFrom(it.toString(), context.getString(R.string.restored_tree)) } }

    BackupScreen(
        uiState = uiState,
        onBack = onBack,
        onBackUpNow = viewModel::backUpNow,
        onExport = { saveTo.launch("${uiState.treeTitle.ifBlank { "family" }}.zip") },
        onRestoreFile = { openFrom.launch(arrayOf("application/zip", "application/octet-stream")) },
        onRestore = viewModel::restore,
        onDelete = viewModel::delete,
        onAutomaticChange = viewModel::setAutomatic,
        onMessageShown = viewModel::onMessageShown,
        onOpenRestored = { id ->
            viewModel.onRestoreHandled()
            onOpenTree(id)
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackupScreen(
    uiState: BackupUiState,
    onBack: () -> Unit,
    onBackUpNow: () -> Unit,
    onExport: () -> Unit,
    onRestoreFile: () -> Unit,
    onRestore: (BackupFile) -> Unit,
    onDelete: (BackupFile) -> Unit,
    onAutomaticChange: (Boolean) -> Unit,
    onMessageShown: () -> Unit,
    onOpenRestored: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var confirming by remember { mutableStateOf<BackupFile?>(null) }

    val message = uiState.message?.let {
        stringResource(
            when (it) {
                BackupUiState.Message.SAVED -> R.string.backup_saved
                BackupUiState.Message.RESTORED -> R.string.backup_restored
                BackupUiState.Message.EXPORTED -> R.string.backup_exported
                BackupUiState.Message.DELETED -> R.string.backup_deleted
                BackupUiState.Message.FAILED -> R.string.backup_failed
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
                title = { Text(stringResource(R.string.backups)) },
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
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (uiState.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(FtDimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(FtDimens.listItemSpacing),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(FtDimens.listItemSpacing)) {
                        Button(
                            onClick = onBackUpNow,
                            enabled = !uiState.busy,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.Backup, contentDescription = null)
                            Text(
                                text = stringResource(R.string.back_up_now),
                                modifier = Modifier.padding(start = 8.dp()),
                            )
                        }
                        OutlinedButton(
                            onClick = onExport,
                            enabled = !uiState.busy,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.Save, contentDescription = null)
                            Text(
                                text = stringResource(R.string.save_elsewhere),
                                modifier = Modifier.padding(start = 8.dp()),
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = FtDimens.listItemSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.automatic_backup),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = pluralStringResource(
                                    R.plurals.automatic_backup_description,
                                    uiState.keptPerTree,
                                    uiState.keptPerTree,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = uiState.automatic, onCheckedChange = onAutomaticChange)
                    }
                }

                item {
                    OutlinedButton(onClick = onRestoreFile, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.FileOpen, contentDescription = null)
                        Text(
                            text = stringResource(R.string.restore_from_file),
                            modifier = Modifier.padding(start = 8.dp()),
                        )
                    }
                }

                item { HorizontalDivider(Modifier.padding(vertical = FtDimens.listItemSpacing)) }

                if (uiState.backups.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.Backup,
                            title = stringResource(R.string.no_backups),
                            description = stringResource(R.string.no_backups_description),
                        )
                    }
                } else {
                    items(uiState.backups, key = { it.id }) { backup ->
                        BackupCard(
                            backup = backup,
                            onRestore = { confirming = backup },
                            onDelete = { onDelete(backup) },
                        )
                    }
                }
            }
        }
    }

    confirming?.let { backup ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(stringResource(R.string.restore_backup)) },
            // Restoring adds a tree rather than replacing one, and saying so removes the
            // fear that stops people using backups at all.
            text = { Text(stringResource(R.string.restore_backup_explanation)) },
            confirmButton = {
                TextButton(onClick = { confirming = null; onRestore(backup) }) {
                    Text(stringResource(R.string.restore))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    uiState.restoredTreeId?.let { id ->
        AlertDialog(
            onDismissRequest = { onOpenRestored(id) },
            title = { Text(stringResource(R.string.backup_restored)) },
            text = { Text(stringResource(R.string.restored_as_new_tree)) },
            confirmButton = {
                TextButton(onClick = { onOpenRestored(id) }) { Text(stringResource(R.string.open)) }
            },
        )
    }
}

@Composable
private fun BackupCard(backup: BackupFile, onRestore: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(FtDimens.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FtDimens.listItemSpacing),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    // "Yesterday, 21:04" reads better than a timestamp for deciding which
                    // backup to go back to.
                    text = DateUtils.getRelativeDateTimeString(
                        context,
                        backup.createdAt,
                        DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.WEEK_IN_MILLIS,
                        0,
                    ).toString(),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(
                        R.string.backup_summary,
                        backup.personCount,
                        Formatter.formatShortFileSize(context, backup.sizeBytes),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRestore) {
                Icon(Icons.Outlined.Restore, contentDescription = stringResource(R.string.restore))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete))
            }
        }
    }
}

private fun Int.dp() = androidx.compose.ui.unit.Dp(toFloat())
