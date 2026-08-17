package com.familytree.feature.trees

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familytree.core.designsystem.component.EmptyState
import com.familytree.core.designsystem.component.FullScreenLoading
import com.familytree.core.designsystem.theme.FamilyTreeTheme
import com.familytree.core.designsystem.theme.FtDimens
import com.familytree.core.designsystem.theme.FtTheme
import com.familytree.core.model.Tree
import com.familytree.core.model.TreeGrade
import com.familytree.core.model.TreeIssue

@Composable
fun TreesRoute(
    onOpenTree: (Long) -> Unit,
    onNewTree: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBackups: (Long) -> Unit,
    onShareTree: (Long, String) -> Unit,
    onReceiveShare: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TreesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val exportedMessage = stringResource(R.string.export_succeeded)
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TreesEvent.Exported ->
                    snackbarHostState.showSnackbar(exportedMessage.format(event.treeTitle))

                is TreesEvent.Failed -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    val issueReport by viewModel.issues.collectAsStateWithLifecycle()

    TreesScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onOpenTree = onOpenTree,
        onNewTree = onNewTree,
        onOpenSettings = onOpenSettings,
        onOpenBackups = onOpenBackups,
        onShareTree = onShareTree,
        onReceiveShare = onReceiveShare,
        onRename = viewModel::onRename,
        onDelete = viewModel::onDelete,
        onExport = viewModel::onExport,
        onCheck = viewModel::onCheckTree,
        modifier = modifier,
    )

    issueReport?.let { report ->
        IssuesDialog(
            report = report,
            onRepair = { viewModel.onRepair(report.tree) },
            onDismiss = viewModel::onDismissIssues,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TreesScreen(
    uiState: TreesUiState,
    snackbarHostState: SnackbarHostState,
    onOpenTree: (Long) -> Unit,
    onNewTree: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBackups: (Long) -> Unit,
    onShareTree: (Long, String) -> Unit,
    onReceiveShare: () -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onExport: (Tree, String) -> Unit,
    onCheck: (Tree) -> Unit,
    modifier: Modifier = Modifier,
) {
    var renaming by remember { mutableStateOf<Tree?>(null) }
    var deleting by remember { mutableStateOf<Tree?>(null) }
    // Held across the file-picker round trip so the result knows which tree it belongs to.
    var exporting by remember { mutableStateOf<Tree?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val tree = exporting
        exporting = null
        if (uri != null && tree != null) onExport(tree, uri.toString())
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trees_title)) },
                actions = {
                    IconButton(onClick = onReceiveShare) {
                        Icon(
                            Icons.Outlined.MoveToInbox,
                            contentDescription = stringResource(R.string.receive_share),
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.settings),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewTree) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_tree))
            }
        },
    ) { padding ->
        val trees = (uiState as? TreesUiState.Trees)?.trees.orEmpty()

        when {
            uiState is TreesUiState.Loading -> FullScreenLoading(Modifier.padding(padding))

            trees.isEmpty() -> EmptyState(
                icon = Icons.Outlined.AccountTree,
                title = stringResource(R.string.no_trees_title),
                description = stringResource(R.string.no_trees_description),
                actionLabel = stringResource(R.string.new_tree),
                onAction = onNewTree,
                modifier = Modifier.padding(padding),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = FtDimens.screenPadding,
                    end = FtDimens.screenPadding,
                    top = padding.calculateTopPadding() + FtDimens.listItemSpacing,
                    // Clears the floating action button so the last card stays reachable.
                    bottom = padding.calculateBottomPadding() + FtDimens.screenPadding * 5,
                ),
                verticalArrangement = Arrangement.spacedBy(FtDimens.listItemSpacing),
            ) {
                items(trees, key = { it.id }) { tree ->
                    TreeCard(
                        tree = tree,
                        onClick = { onOpenTree(tree.id) },
                        onRename = { renaming = tree },
                        onDelete = { deleting = tree },
                        onExport = {
                            exporting = tree
                            exportLauncher.launch("${tree.title}.ged")
                        },
                        onCheck = { onCheck(tree) },
                        onBackups = { onOpenBackups(tree.id) },
                        onShare = { onShareTree(tree.id, tree.title) },
                    )
                }
            }
        }
    }

    renaming?.let { tree ->
        RenameDialog(
            initialTitle = tree.title,
            onDismiss = { renaming = null },
            onConfirm = { title ->
                onRename(tree.id, title)
                renaming = null
            },
        )
    }

    deleting?.let { tree ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_tree_title)) },
            text = { Text(stringResource(R.string.delete_tree_message, tree.title)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(tree.id)
                    deleting = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun RenameDialog(
    initialTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_tree)) },
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
                onClick = { onConfirm(title) },
                // A blank title would be rejected by the use case anyway.
                enabled = title.isNotBlank(),
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun TreeCard(
    tree: Tree,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onBackups: () -> Unit,
    onShare: () -> Unit,
    onCheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val exhausted = tree.grade == TreeGrade.EXHAUSTED

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            // A consumed tree is visually retired without being hidden, so it can still
            // be opened before the user decides to delete it.
            containerColor = if (exhausted) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(
                start = FtDimens.cardPadding,
                top = FtDimens.cardPadding,
                bottom = FtDimens.cardPadding,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = tree.title.ifBlank { stringResource(R.string.untitled_tree) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.tree_summary,
                        tree.personCount,
                        tree.generationCount,
                        tree.mediaCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (tree.grade == TreeGrade.DERIVED || exhausted) {
                    Text(
                        text = stringResource(
                            if (exhausted) R.string.tree_state_exhausted else R.string.tree_state_derived,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (exhausted) {
                            FtTheme.genealogyColors.treeExhausted
                        } else {
                            FtTheme.genealogyColors.treeDerived
                        },
                    )
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.tree_actions),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rename_tree)) },
                        onClick = { menuOpen = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.export_gedcom)) },
                        onClick = { menuOpen = false; onExport() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.check_tree)) },
                        onClick = { menuOpen = false; onCheck() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.backups)) },
                        onClick = { menuOpen = false; onBackups() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share_tree)) },
                        onClick = { menuOpen = false; onShare() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

/**
 * Shows what was found before anything is changed, so the user decides whether the app
 * may touch their data.
 */
@Composable
private fun IssuesDialog(
    report: IssueReport,
    onRepair: () -> Unit,
    onDismiss: () -> Unit,
) {
    val repairable = report.issues.any { it.kind.repairable }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    when {
                        report.issues.isEmpty() && report.repaired -> R.string.repair_done
                        report.issues.isEmpty() -> R.string.check_clean_title
                        report.repaired -> R.string.repair_done
                        else -> R.string.check_found_title
                    },
                ),
            )
        },
        text = {
            Column {
                if (report.issues.isEmpty()) {
                    Text(stringResource(R.string.check_clean_description))
                } else {
                    report.issues.forEach { issue ->
                        Text(
                            text = stringResource(issue.kind.labelRes(), issue.count),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = FtDimens.listItemSpacing / 2),
                        )
                    }
                    if (!report.repaired && !repairable) {
                        Text(
                            text = stringResource(R.string.check_nothing_automatic),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!report.repaired && repairable) {
                TextButton(onClick = onRepair) { Text(stringResource(R.string.repair)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) }
            }
        },
        dismissButton = {
            if (!report.repaired && repairable) {
                TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
            }
        },
    )
}

private fun TreeIssue.Kind.labelRes(): Int = when (this) {
    TreeIssue.Kind.MISSING_ROOT -> R.string.issue_missing_root
    TreeIssue.Kind.MISSING_IDS -> R.string.issue_missing_ids
    TreeIssue.Kind.UNDERPOPULATED_FAMILIES -> R.string.issue_underpopulated_families
    TreeIssue.Kind.MEDIA_WITHOUT_FILE -> R.string.issue_media_without_file
    TreeIssue.Kind.ORPHANED_LINKS -> R.string.issue_orphaned_links
    TreeIssue.Kind.PEOPLE_WITHOUT_NAME -> R.string.issue_people_without_name
}

@Preview
@Composable
private fun TreesScreenPreview() {
    FamilyTreeTheme {
        TreesScreen(
            uiState = TreesUiState.Trees(
                trees = listOf(
                    Tree(id = 1, title = "Elşami Ailesi", personCount = 42, generationCount = 5, mediaCount = 8),
                    Tree(id = 2, title = "Anne Tarafı", personCount = 17, generationCount = 3),
                    Tree(id = 3, title = "Tüketilmiş", personCount = 5, grade = TreeGrade.EXHAUSTED),
                ),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onOpenTree = {},
            onNewTree = {},
            onOpenSettings = {},
            onOpenBackups = {},
            onShareTree = { _, _ -> },
            onReceiveShare = {},
            onRename = { _, _ -> },
            onDelete = {},
            onExport = { _, _ -> },
            onCheck = {},
        )
    }
}
