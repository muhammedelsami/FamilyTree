package com.familytree.feature.person

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familytree.core.designsystem.theme.FamilyTreeTheme
import com.familytree.core.designsystem.theme.FtDimens
import com.familytree.core.model.PersonDraft
import com.familytree.core.model.Relation
import com.familytree.core.model.Sex
import com.familytree.core.ui.DateField

@Composable
fun PersonEditorRoute(
    treeId: Long,
    personId: Long?,
    pivotPersonId: Long?,
    relation: Relation?,
    onSaved: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PersonEditorViewModel = hiltViewModel(),
) {
    LaunchedEffect(treeId, personId) {
        viewModel.initialise(treeId, personId, pivotPersonId, relation)
    }

    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val expert by viewModel.expertMode.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PersonEditorEvent.Saved -> onSaved(event.personId)
                is PersonEditorEvent.Failed -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    PersonEditorScreen(
        draft = draft,
        relation = relation,
        expert = expert,
        saving = saving,
        snackbarHostState = snackbarHostState,
        onDraftChange = { viewModel.update { _ -> it } },
        onSexSelected = viewModel::onSexSelected,
        onSave = { viewModel.onSave() },
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PersonEditorScreen(
    draft: PersonDraft,
    relation: Relation?,
    expert: Boolean,
    saving: Boolean,
    snackbarHostState: SnackbarHostState,
    onDraftChange: (PersonDraft) -> Unit,
    onSexSelected: (Sex) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when {
                                relation != null -> relation.titleRes()
                                draft.personId != null -> R.string.edit_person
                                else -> R.string.add_person
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(android.R.string.cancel),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSave, enabled = !saving) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.save))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(FtDimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(FtDimens.listItemSpacing * 1.5f),
        ) {
            OutlinedTextField(
                value = draft.given,
                onValueChange = { onDraftChange(draft.copy(given = it.filterNot { c -> c == '/' })) },
                label = { Text(stringResource(R.string.given_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.surname,
                // Slashes delimit the surname in the GEDCOM value, so they cannot appear in it.
                onValueChange = { onDraftChange(draft.copy(surname = it.filterNot { c -> c == '/' })) },
                label = { Text(stringResource(R.string.surname)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.sex), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(FtDimens.listItemSpacing)) {
                listOf(Sex.MALE, Sex.FEMALE, Sex.UNDEFINED).forEach { option ->
                    FilterChip(
                        selected = draft.sex == option,
                        onClick = { onSexSelected(option) },
                        label = { Text(stringResource(option.labelRes())) },
                    )
                }
            }

            HorizontalDivider()

            Text(stringResource(R.string.birth), style = MaterialTheme.typography.titleSmall)
            DateField(
                value = draft.birthDate,
                onValueChange = { onDraftChange(draft.copy(birthDate = it)) },
                label = stringResource(R.string.date),
                expert = expert,
            )
            OutlinedTextField(
                value = draft.birthPlace,
                onValueChange = { onDraftChange(draft.copy(birthPlace = it)) },
                label = { Text(stringResource(R.string.place)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.deceased), style = MaterialTheme.typography.titleSmall)
                Switch(
                    checked = draft.deceased,
                    onCheckedChange = { onDraftChange(draft.copy(deceased = it)) },
                )
            }

            // Death details only make sense once the person is marked as deceased.
            if (draft.deceased) {
                DateField(
                    value = draft.deathDate,
                    onValueChange = { onDraftChange(draft.copy(deathDate = it)) },
                    label = stringResource(R.string.date),
                    expert = expert,
                )
                OutlinedTextField(
                    value = draft.deathPlace,
                    onValueChange = { onDraftChange(draft.copy(deathPlace = it)) },
                    label = { Text(stringResource(R.string.place)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun Sex.labelRes(): Int = when (this) {
    Sex.MALE -> R.string.sex_male
    Sex.FEMALE -> R.string.sex_female
    else -> R.string.sex_unknown
}

private fun Relation.titleRes(): Int = when (this) {
    Relation.PARENT -> R.string.add_parent
    Relation.SIBLING -> R.string.add_sibling
    Relation.PARTNER -> R.string.add_partner
    Relation.CHILD -> R.string.add_child
}

@Preview
@Composable
private fun PersonEditorPreview() {
    FamilyTreeTheme {
        PersonEditorScreen(
            draft = PersonDraft(
                treeId = 1,
                given = "Ahmet",
                surname = "Yılmaz",
                sex = Sex.MALE,
                birthDate = "3 FEB 1715",
                birthPlace = "Bursa",
                deceased = true,
                deathDate = "ABT 1790",
            ),
            relation = null,
            expert = false,
            saving = false,
            snackbarHostState = remember { SnackbarHostState() },
            onDraftChange = {},
            onSexSelected = {},
            onSave = {},
            onBack = {},
        )
    }
}
