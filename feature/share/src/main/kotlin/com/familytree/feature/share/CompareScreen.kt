package com.familytree.feature.share

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Difference
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familytree.core.designsystem.component.EmptyState
import com.familytree.core.designsystem.component.FullScreenLoading
import com.familytree.core.designsystem.theme.FtDimens
import com.familytree.core.model.RecordType
import com.familytree.core.model.TreeDifference

@Composable
fun CompareRoute(
    localTreeId: Long,
    incomingTreeId: Long,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CompareViewModel = hiltViewModel(),
) {
    LaunchedEffect(localTreeId, incomingTreeId) { viewModel.load(localTreeId, incomingTreeId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CompareScreen(
        uiState = uiState,
        onBack = onBack,
        onToggle = viewModel::toggle,
        onSetAll = viewModel::setAll,
        onApply = viewModel::apply,
        onDone = onDone,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CompareScreen(
    uiState: CompareUiState,
    onBack: () -> Unit,
    onToggle: (TreeDifference) -> Unit,
    onSetAll: (Boolean) -> Unit,
    onApply: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.review_updates))
                        if (uiState.incomingTitle.isNotBlank()) {
                            Text(
                                text = stringResource(R.string.from_tree, uiState.incomingTitle),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    if (uiState.differences.isNotEmpty()) {
                        TextButton(onClick = { onSetAll(uiState.acceptedCount == 0) }) {
                            Text(
                                stringResource(
                                    if (uiState.acceptedCount == 0) R.string.select_all else R.string.select_none,
                                ),
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (uiState.differences.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onApply,
                    icon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null) },
                    text = {
                        Text(
                            pluralStringResource(
                                R.plurals.apply_updates,
                                uiState.acceptedCount,
                                uiState.acceptedCount,
                            ),
                        )
                    },
                )
            }
        },
    ) { padding ->
        when {
            uiState.loading -> FullScreenLoading(Modifier.padding(padding))

            uiState.differences.isEmpty() -> EmptyState(
                modifier = Modifier.padding(padding),
                icon = Icons.Outlined.Difference,
                title = stringResource(R.string.no_updates),
                // Saying it plainly is the point: the user can now delete the copy without
                // wondering what they might be throwing away.
                description = stringResource(R.string.no_updates_description),
            )

            else -> Column(Modifier.padding(padding)) {
                if (uiState.applying) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = FtDimens.screenPadding,
                        end = FtDimens.screenPadding,
                        bottom = FtDimens.screenPadding * 5,
                    ),
                    verticalArrangement = Arrangement.spacedBy(FtDimens.listItemSpacing),
                ) {
                    item { Summary(uiState) }
                    items(uiState.differences, key = { it.id }) { difference ->
                        DifferenceRow(difference, onClick = { onToggle(difference) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }

    uiState.applied?.let { applied ->
        AlertDialog(
            onDismissRequest = onDone,
            title = { Text(stringResource(R.string.updates_applied)) },
            text = {
                Text(
                    stringResource(
                        R.string.updates_applied_summary,
                        applied.added,
                        applied.updated,
                        applied.removed,
                    ),
                )
            },
            confirmButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.done)) } },
        )
    }
}

@Composable
private fun Summary(uiState: CompareUiState) {
    val comparison = uiState.comparison ?: return
    Row(
        modifier = Modifier.padding(vertical = FtDimens.listItemSpacing),
        horizontalArrangement = Arrangement.spacedBy(FtDimens.listItemSpacing),
    ) {
        if (comparison.added > 0) {
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(stringResource(R.string.count_added, comparison.added)) },
            )
        }
        if (comparison.changed > 0) {
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(stringResource(R.string.count_changed, comparison.changed)) },
            )
        }
        if (comparison.removed > 0) {
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(stringResource(R.string.count_removed, comparison.removed)) },
            )
        }
    }
}

@Composable
private fun DifferenceRow(difference: TreeDifference, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = FtDimens.listItemSpacing),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FtDimens.listItemSpacing),
    ) {
        Checkbox(checked = difference.accepted, onCheckedChange = { onClick() })

        // A colour bar rather than only a word: the three kinds are scanned far more often
        // than they are read.
        Box(
            Modifier
                .size(4.dp, 36.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(difference.kind.tint()),
        )

        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(difference.recordType.labelRes()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (difference.kind) {
                TreeDifference.Kind.ADDED -> Text(
                    text = difference.incomingSummary.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                )

                TreeDifference.Kind.REMOVED -> Text(
                    text = difference.localSummary.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    // Struck through, because this row means "this goes away".
                    textDecoration = TextDecoration.LineThrough,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                TreeDifference.Kind.CHANGED -> {
                    Text(
                        text = difference.localSummary.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        textDecoration = TextDecoration.LineThrough,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = difference.incomingSummary.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun TreeDifference.Kind.tint(): Color = when (this) {
    TreeDifference.Kind.ADDED -> MaterialTheme.colorScheme.primary
    TreeDifference.Kind.CHANGED -> MaterialTheme.colorScheme.tertiary
    TreeDifference.Kind.REMOVED -> MaterialTheme.colorScheme.error
}

private fun RecordType.labelRes(): Int = when (this) {
    RecordType.PERSON -> R.string.record_person
    RecordType.FAMILY -> R.string.record_family
    RecordType.NOTE -> R.string.record_note
    RecordType.SOURCE -> R.string.record_source
    RecordType.MEDIA -> R.string.record_media
    RecordType.REPOSITORY -> R.string.record_repository
    RecordType.SUBMITTER -> R.string.record_submitter
}
