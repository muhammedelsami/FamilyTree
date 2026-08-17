package com.familytree.feature.family

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Diversity3
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familytree.core.designsystem.component.EmptyState
import com.familytree.core.designsystem.component.FullScreenLoading
import com.familytree.core.designsystem.theme.FtDimens
import com.familytree.core.model.Event
import com.familytree.core.model.PersonSummary
import com.familytree.core.ui.PersonRow
import com.familytree.core.ui.RecordEditor
import com.familytree.core.ui.RecordFields
import com.familytree.core.ui.eventLabel

@Composable
fun FamilyDetailRoute(
    familyId: Long,
    treeId: Long,
    onBack: () -> Unit,
    onOpenPerson: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FamilyDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(familyId, treeId) { viewModel.setFamily(familyId, treeId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    FamilyDetailScreen(
        uiState = uiState,
        addableTags = viewModel.addableTags,
        onBack = onBack,
        onOpenPerson = onOpenPerson,
        onEventChange = viewModel::onEventChange,
        onAddEvent = viewModel::onAddEvent,
        onDeleteEvent = viewModel::onDeleteEvent,
        onRemoveMember = viewModel::onRemoveMember,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FamilyDetailScreen(
    uiState: FamilyDetailUiState,
    addableTags: List<String>,
    onBack: () -> Unit,
    onOpenPerson: (Long) -> Unit,
    onEventChange: (Event) -> Unit,
    onAddEvent: (String) -> Unit,
    onDeleteEvent: (Long) -> Unit,
    onRemoveMember: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var addingEvent by remember { mutableStateOf(false) }
    val details = uiState.details

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.family_detail_title)) },
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
            Box {
                FloatingActionButton(onClick = { addingEvent = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_event))
                }
                DropdownMenu(expanded = addingEvent, onDismissRequest = { addingEvent = false }) {
                    addableTags.forEach { tag ->
                        DropdownMenuItem(
                            text = { Text(eventLabel(tag)) },
                            onClick = {
                                addingEvent = false
                                onAddEvent(tag)
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        when {
            uiState.loading -> FullScreenLoading(Modifier.padding(padding))
            details == null -> EmptyState(
                icon = Icons.Outlined.Diversity3,
                title = stringResource(R.string.family_gone),
                modifier = Modifier.padding(padding),
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = FtDimens.screenPadding * 5),
            ) {
                MemberSection(
                    titleRes = R.string.group_partners,
                    people = uiState.partners,
                    onOpenPerson = onOpenPerson,
                    onRemove = onRemoveMember,
                )
                MemberSection(
                    titleRes = R.string.group_children,
                    people = uiState.children,
                    onOpenPerson = onOpenPerson,
                    onRemove = onRemoveMember,
                )

                if (details.events.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_family_events),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(FtDimens.screenPadding),
                    )
                }
                details.events.forEach { event ->
                    EventCard(
                        event = event,
                        knownPlaces = uiState.knownPlaces,
                        expert = uiState.expertMode,
                        onChange = onEventChange,
                        onDelete = { onDeleteEvent(event.id) },
                    )
                }
            }
        }
    }
}

/**
 * One event, edited through the shared [RecordEditor].
 *
 * Every record type in the app reaches its fields the same way, which is what the
 * [RecordFields] table buys: adding a new type means describing its fields, not writing
 * another screen.
 */
@Composable
private fun EventCard(
    event: Event,
    knownPlaces: List<String>,
    expert: Boolean,
    onChange: (Event) -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = FtDimens.screenPadding, vertical = FtDimens.listItemSpacing),
    ) {
        Column(Modifier.padding(FtDimens.cardPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(eventLabel(event.tag), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete_event),
                    )
                }
            }
            RecordEditor(
                record = event,
                fields = RecordFields.event,
                onRecordChange = onChange,
                expert = expert,
                knownPlaces = knownPlaces,
            )
        }
    }
}

@Composable
private fun MemberSection(
    titleRes: Int,
    people: List<PersonSummary>,
    onOpenPerson: (Long) -> Unit,
    onRemove: (Long) -> Unit,
) {
    if (people.isEmpty()) return
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(
                horizontal = FtDimens.screenPadding,
                vertical = FtDimens.listItemSpacing,
            ),
        )
        people.forEach { person ->
            // The same row the person list uses, so a member reads identically wherever
            // they appear — and unlinking is a deliberate second action, not a tap away.
            PersonRow(
                person = person,
                onClick = { onOpenPerson(person.person.id) },
                trailing = {
                    IconButton(onClick = { onRemove(person.person.id) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.remove_member),
                        )
                    }
                },
            )
        }
    }
}
