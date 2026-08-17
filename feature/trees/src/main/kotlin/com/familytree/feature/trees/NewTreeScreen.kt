package com.familytree.feature.trees

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familytree.core.designsystem.component.FullScreenLoading
import com.familytree.core.designsystem.theme.FamilyTreeTheme
import com.familytree.core.designsystem.theme.FtDimens

@Composable
fun NewTreeRoute(
    onTreeReady: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NewTreeViewModel = hiltViewModel(),
) {
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is NewTreeEvent.Created -> onTreeReady(event.treeId)
                is NewTreeEvent.Failed -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    NewTreeScreen(
        busy = busy,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onCreateEmpty = viewModel::onCreateEmpty,
        onImportGedcom = viewModel::onImportGedcom,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewTreeScreen(
    busy: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onCreateEmpty: (String) -> Unit,
    onImportGedcom: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var askingTitle by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        // GEDCOM has no registered MIME type and providers disagree on what to report,
        // so every file is offered and the parser decides.
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { onImportGedcom(it.toString()) } }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.new_tree)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(android.R.string.cancel),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            if (busy) {
                FullScreenLoading(contentDescription = stringResource(R.string.importing))
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(FtDimens.screenPadding),
                    verticalArrangement = Arrangement.spacedBy(FtDimens.listItemSpacing),
                ) {
                    OptionCard(
                        icon = Icons.Outlined.NoteAdd,
                        title = stringResource(R.string.option_empty_title),
                        description = stringResource(R.string.option_empty_description),
                        onClick = { askingTitle = true },
                    )
                    OptionCard(
                        icon = Icons.Outlined.FileOpen,
                        title = stringResource(R.string.option_import_title),
                        description = stringResource(R.string.option_import_description),
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                    )
                    OptionCard(
                        icon = Icons.Outlined.Backup,
                        title = stringResource(R.string.option_restore_title),
                        description = stringResource(R.string.option_restore_description),
                        enabled = false,
                        onClick = {},
                    )
                }
            }
        }
    }

    if (askingTitle) {
        val defaultTitle = stringResource(R.string.default_tree_title)
        var title by remember { mutableStateOf(defaultTitle) }
        AlertDialog(
            onDismissRequest = { askingTitle = false },
            title = { Text(stringResource(R.string.option_empty_title)) },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.tree_title_label)) },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        askingTitle = false
                        onCreateEmpty(title)
                    },
                    enabled = title.isNotBlank(),
                ) { Text(stringResource(R.string.create)) }
            },
            dismissButton = {
                TextButton(onClick = { askingTitle = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun OptionCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Card(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(FtDimens.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FtDimens.screenPadding),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Column(Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview
@Composable
private fun NewTreeScreenPreview() {
    FamilyTreeTheme {
        NewTreeScreen(
            busy = false,
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onCreateEmpty = {},
            onImportGedcom = {},
        )
    }
}
