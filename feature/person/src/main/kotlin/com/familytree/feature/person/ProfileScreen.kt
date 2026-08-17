package com.familytree.feature.person

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familytree.core.designsystem.component.EmptyState
import com.familytree.core.designsystem.component.FullScreenLoading
import com.familytree.core.designsystem.theme.FamilyTreeTheme
import com.familytree.core.designsystem.theme.FtDimens
import com.familytree.core.model.Event
import com.familytree.core.model.MediaObject
import com.familytree.core.model.Person
import com.familytree.core.model.PersonDetails
import com.familytree.core.model.PersonName
import com.familytree.core.model.Relation
import com.familytree.core.model.RelativeGroup
import com.familytree.core.model.Sex
import com.familytree.core.ui.PersonAvatar
import com.familytree.core.ui.PersonRow
import com.familytree.core.ui.eventLabel

private enum class ProfileTab(val labelRes: Int) {
    Facts(R.string.tab_facts),
    Relatives(R.string.tab_relatives),
    Media(R.string.tab_media),
}

@Composable
fun ProfileRoute(
    personId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenPerson: (Long) -> Unit,
    onAddRelative: (pivotId: Long, relation: Relation) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
    // Handed in rather than built here: the media screens know about file pickers and
    // cropping, and none of that belongs in the person feature.
    mediaTab: @Composable (personId: Long) -> Unit = {},
) {
    LaunchedEffect(personId) { viewModel.setPerson(personId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ProfileScreen(
        uiState = uiState,
        onBack = onBack,
        onEdit = { onEdit(personId) },
        onDelete = { viewModel.onDelete(onBack) },
        onOpenPerson = onOpenPerson,
        onAddRelative = { relation -> onAddRelative(personId, relation) },
        mediaTab = { mediaTab(personId) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileScreen(
    uiState: ProfileUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenPerson: (Long) -> Unit,
    onAddRelative: (Relation) -> Unit,
    modifier: Modifier = Modifier,
    mediaTab: @Composable () -> Unit = {},
) {
    var tab by rememberSaveable { mutableStateOf(ProfileTab.Facts) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var choosingRelation by remember { mutableStateOf(false) }

    val details = uiState.details
    val name = details?.names?.minByOrNull { it.position }?.display().orEmpty()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(name.ifBlank { stringResource(R.string.person_unnamed_profile) }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_person))
                    }
                    IconButton(onClick = { confirmingDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_person))
                    }
                },
            )
        },
        floatingActionButton = {
            if (tab == ProfileTab.Relatives) {
                ExtendedFloatingActionButton(
                    onClick = { choosingRelation = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.add_relative)) },
                )
            }
        },
    ) { padding ->
        when {
            uiState.loading -> FullScreenLoading(Modifier.padding(padding))
            details == null -> EmptyState(
                icon = Icons.Outlined.PhotoLibrary,
                title = stringResource(R.string.person_gone),
                modifier = Modifier.padding(padding),
            )
            else -> Column(Modifier.padding(padding)) {
                ProfileHeader(
                    person = details.person,
                    name = name,
                    events = details.events,
                    portrait = uiState.portrait,
                )
                TabRow(selectedTabIndex = tab.ordinal) {
                    ProfileTab.entries.forEach { entry ->
                        Tab(
                            selected = entry == tab,
                            onClick = { tab = entry },
                            text = { Text(stringResource(entry.labelRes)) },
                        )
                    }
                }
                when (tab) {
                    ProfileTab.Facts -> FactsTab(details)
                    ProfileTab.Relatives -> RelativesTab(uiState.relatives, onOpenPerson)
                    ProfileTab.Media -> mediaTab()
                }
            }
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.delete_person)) },
            text = { Text(stringResource(R.string.delete_person_message, name)) },
            confirmButton = {
                TextButton(onClick = { confirmingDelete = false; onDelete() }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    if (choosingRelation) {
        RelationChooser(
            onDismiss = { choosingRelation = false },
            onChoose = {
                choosingRelation = false
                onAddRelative(it)
            },
        )
    }
}

@Composable
private fun ProfileHeader(
    person: Person,
    name: String,
    events: List<Event>,
    portrait: MediaObject? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(FtDimens.screenPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FtDimens.screenPadding),
    ) {
        PersonAvatar(
            sex = person.sex,
            size = FtDimens.portraitLarge,
            deceased = events.any { it.tag == "DEAT" },
            portrait = portrait,
        )
        Column {
            Text(
                text = name.ifBlank { stringResource(R.string.person_unnamed_profile) },
                style = MaterialTheme.typography.headlineSmall,
            )
            person.gedcomId?.let { id ->
                Text(
                    text = id,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FactsTab(details: PersonDetails) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = FtDimens.screenPadding * 5),
    ) {
        if (details.names.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.section_names)) }
            items(details.names, key = { "name-${it.id}" }) { NameRow(it) }
        }
        if (details.events.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.section_events)) }
            items(details.events, key = { "event-${it.id}" }) { EventRow(it) }
        }
        if (details.notes.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.section_notes)) }
            items(details.notes, key = { "note-${it.id}" }) { note ->
                Text(
                    text = note.value,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(
                        horizontal = FtDimens.screenPadding,
                        vertical = FtDimens.listItemSpacing,
                    ),
                )
            }
        }
        if (details.names.isEmpty() && details.events.isEmpty() && details.notes.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.no_facts),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(FtDimens.sectionSpacing),
                )
            }
        }
    }
}

@Composable
private fun NameRow(name: PersonName) {
    Column(
        Modifier.padding(horizontal = FtDimens.screenPadding, vertical = FtDimens.listItemSpacing),
    ) {
        Text(name.display(), style = MaterialTheme.typography.bodyLarge)
        name.type?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EventRow(event: Event) {
    Column(
        Modifier.padding(horizontal = FtDimens.screenPadding, vertical = FtDimens.listItemSpacing),
    ) {
        Text(eventLabel(event.tag), style = MaterialTheme.typography.labelLarge)
        val detail = listOfNotNull(
            event.date?.takeIf { it.isNotBlank() },
            event.place?.takeIf { it.isNotBlank() },
            // `Y` only records that the event happened; showing it would confuse.
            event.value?.takeIf { it.isNotBlank() && it != "Y" },
        ).joinToString(" · ")
        if (detail.isNotBlank()) {
            Text(detail, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun RelativesTab(groups: List<RelativeGroup>, onOpenPerson: (Long) -> Unit) {
    if (groups.all { it.isEmpty }) {
        EmptyState(
            icon = Icons.Outlined.PhotoLibrary,
            title = stringResource(R.string.no_relatives_title),
            description = stringResource(R.string.no_relatives_description),
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = FtDimens.screenPadding * 6),
    ) {
        groups.filterNot { it.isEmpty }.forEach { group ->
            val origin = group.kind == RelativeGroup.Kind.ORIGIN
            if (group.partners.isNotEmpty()) {
                item(key = "partners-${group.family.id}") {
                    SectionHeader(
                        stringResource(if (origin) R.string.group_parents else R.string.group_partners),
                    )
                }
                items(group.partners, key = { "p-${group.family.id}-${it.person.id}" }) { person ->
                    PersonRow(person = person, onClick = { onOpenPerson(person.person.id) })
                }
            }
            if (group.children.isNotEmpty()) {
                item(key = "children-${group.family.id}") {
                    SectionHeader(
                        stringResource(if (origin) R.string.group_siblings else R.string.group_children),
                    )
                }
                items(group.children, key = { "c-${group.family.id}-${it.person.id}" }) { person ->
                    PersonRow(person = person, onClick = { onOpenPerson(person.person.id) })
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(
                horizontal = FtDimens.screenPadding,
                vertical = FtDimens.listItemSpacing,
            ),
        )
    }
}

@Composable
private fun RelationChooser(onDismiss: () -> Unit, onChoose: (Relation) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_relative)) },
        text = {
            Column {
                Relation.entries.forEach { relation ->
                    Card(
                        onClick = { onChoose(relation) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = FtDimens.listItemSpacing / 2),
                    ) {
                        Text(
                            text = stringResource(relation.labelRes()),
                            modifier = Modifier.padding(FtDimens.cardPadding),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

private fun Relation.labelRes(): Int = when (this) {
    Relation.PARENT -> R.string.relation_parent
    Relation.SIBLING -> R.string.relation_sibling
    Relation.PARTNER -> R.string.relation_partner
    Relation.CHILD -> R.string.relation_child
}

@Preview
@Composable
private fun ProfilePreview() {
    FamilyTreeTheme {
        ProfileScreen(
            uiState = ProfileUiState(
                details = PersonDetails(
                    person = Person(id = 1, treeId = 1, gedcomId = "I1", sex = Sex.MALE),
                    names = listOf(PersonName(personId = 1, value = "Ahmet /Yılmaz/")),
                    events = listOf(
                        Event(treeId = 1, ownerType = com.familytree.core.model.OwnerType.PERSON, ownerId = 1, tag = "BIRT", date = "3 FEB 1715", place = "Bursa"),
                        Event(treeId = 1, ownerType = com.familytree.core.model.OwnerType.PERSON, ownerId = 1, tag = "DEAT", date = "ABT 1790"),
                    ),
                ),
                loading = false,
            ),
            onBack = {},
            onEdit = {},
            onDelete = {},
            onOpenPerson = {},
            onAddRelative = {},
        )
    }
}
