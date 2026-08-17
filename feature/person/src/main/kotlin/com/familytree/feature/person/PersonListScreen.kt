package com.familytree.feature.person

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.SearchOff
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familytree.core.designsystem.component.EmptyState
import com.familytree.core.designsystem.component.FullScreenLoading
import com.familytree.core.designsystem.theme.FamilyTreeTheme
import com.familytree.core.designsystem.theme.FtDimens
import com.familytree.core.model.Person
import com.familytree.core.model.MediaObject
import com.familytree.core.model.PersonSort
import com.familytree.core.model.PersonSorting
import com.familytree.core.model.PersonSummary
import com.familytree.core.model.Sex
import com.familytree.core.ui.PersonRow

@Composable
fun PersonListRoute(
    treeId: Long,
    onOpenPerson: (Long) -> Unit,
    onAddPerson: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PersonListViewModel = hiltViewModel(),
) {
    LaunchedEffect(treeId) { viewModel.setTree(treeId) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val sorting by viewModel.sorting.collectAsStateWithLifecycle()
    val portraits by viewModel.portraits.collectAsStateWithLifecycle()

    PersonListScreen(
        uiState = uiState,
        query = query,
        sorting = sorting,
        portraits = portraits,
        onQueryChange = viewModel::onQueryChange,
        onSortSelected = viewModel::onSortSelected,
        onOpenPerson = onOpenPerson,
        onAddPerson = onAddPerson,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PersonListScreen(
    uiState: PersonListUiState,
    query: String,
    sorting: PersonSorting,
    onQueryChange: (String) -> Unit,
    onSortSelected: (PersonSort) -> Unit,
    onOpenPerson: (Long) -> Unit,
    onAddPerson: () -> Unit,
    modifier: Modifier = Modifier,
    portraits: Map<Long, MediaObject> = emptyMap(),
) {
    var searching by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        TextField(
                            value = query,
                            onValueChange = onQueryChange,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.search_people)) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            ),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                imeAction = ImeAction.Search,
                            ),
                        )
                    } else {
                        Text(stringResource(R.string.people_title))
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            searching = !searching
                            if (!searching) onQueryChange("")
                        },
                    ) {
                        Icon(
                            imageVector = if (searching) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = stringResource(
                                if (searching) R.string.close_search else R.string.search_people,
                            ),
                        )
                    }
                    SortMenu(sorting = sorting, onSortSelected = onSortSelected)
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddPerson) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_person))
            }
        },
    ) { padding ->
        when (uiState) {
            PersonListUiState.Loading -> FullScreenLoading(Modifier.padding(padding))

            PersonListUiState.Empty -> EmptyState(
                icon = Icons.Outlined.Group,
                title = stringResource(R.string.no_people_title),
                description = stringResource(R.string.no_people_description),
                actionLabel = stringResource(R.string.add_person),
                onAction = onAddPerson,
                modifier = Modifier.padding(padding),
            )

            is PersonListUiState.NoMatches -> EmptyState(
                icon = Icons.Outlined.SearchOff,
                title = stringResource(R.string.no_matches_title),
                description = stringResource(R.string.no_matches_description, uiState.query),
                modifier = Modifier.padding(padding),
            )

            is PersonListUiState.People -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + FtDimens.screenPadding * 5,
                ),
            ) {
                items(uiState.people, key = { it.person.id }) { person ->
                    PersonRow(
                        person = person,
                        onClick = { onOpenPerson(person.person.id) },
                        portrait = portraits[person.person.id],
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = FtDimens.screenPadding * 5),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

/** The current criterion is marked with an arrow whose direction shows the order. */
@Composable
private fun SortMenu(
    sorting: PersonSorting,
    onSortSelected: (PersonSort) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                Icons.AutoMirrored.Filled.Sort,
                contentDescription = stringResource(R.string.sort_people),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            PersonSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(stringResource(sort.labelRes())) },
                    trailingIcon = {
                        if (sort == sorting.sort) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = stringResource(
                                    if (sorting.ascending) R.string.ascending else R.string.descending,
                                ),
                                modifier = Modifier.rotate(if (sorting.ascending) 0f else 180f),
                            )
                        }
                    },
                    onClick = {
                        onSortSelected(sort)
                        open = false
                    },
                )
            }
        }
    }
}

private fun PersonSort.labelRes(): Int = when (this) {
    PersonSort.ID -> R.string.sort_id
    PersonSort.SURNAME -> R.string.sort_surname
    PersonSort.DATE -> R.string.sort_date
    PersonSort.AGE -> R.string.sort_age
    PersonSort.BIRTHDAY -> R.string.sort_birthday
    PersonSort.RELATIVES -> R.string.sort_relatives
}

@Preview
@Composable
private fun PersonListPreview() {
    fun person(id: Long, name: String, sex: Sex, birth: Int?, death: Int?) = PersonSummary(
        person = Person(id = id, treeId = 1, gedcomId = "I$id", sex = sex),
        displayName = name,
        searchText = name.lowercase(),
        birth = birth?.let { com.familytree.core.model.LifeEvent("$it", "Bursa", it) },
        death = death?.let { com.familytree.core.model.LifeEvent("$it", null, it) },
        isDeceased = death != null,
        birthSortKey = birth ?: Int.MAX_VALUE,
        ageInYears = null,
        daysToNextBirthday = null,
        relativeCount = 3,
        portraitMediaId = null,
    )
    FamilyTreeTheme {
        PersonListScreen(
            uiState = PersonListUiState.People(
                listOf(
                    person(1, "Ahmet Yilmaz", Sex.MALE, 1890, 1954),
                    person(2, "Ayşe Yilmaz", Sex.FEMALE, 1895, null),
                    person(3, "Mehmet Yilmaz", Sex.MALE, null, null),
                ),
            ),
            query = "",
            sorting = PersonSorting(),
            onQueryChange = {},
            onSortSelected = {},
            onOpenPerson = {},
            onAddPerson = {},
        )
    }
}
