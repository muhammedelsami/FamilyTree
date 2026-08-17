package com.familytree.feature.family

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
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Diversity3
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familytree.core.designsystem.component.EmptyState
import com.familytree.core.designsystem.component.FullScreenLoading
import com.familytree.core.designsystem.theme.FamilyTreeTheme
import com.familytree.core.designsystem.theme.FtDimens
import com.familytree.core.model.Family
import com.familytree.core.model.FamilySort
import com.familytree.core.model.FamilySorting
import com.familytree.core.model.FamilySummary
import com.familytree.core.model.LifeEvent
import com.familytree.core.model.Person
import com.familytree.core.model.PersonSummary
import com.familytree.core.model.Sex

@Composable
fun FamilyListRoute(
    treeId: Long,
    onOpenFamily: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FamilyListViewModel = hiltViewModel(),
) {
    LaunchedEffect(treeId) { viewModel.setTree(treeId) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val sorting by viewModel.sorting.collectAsStateWithLifecycle()

    FamilyListScreen(
        uiState = uiState,
        query = query,
        sorting = sorting,
        onQueryChange = viewModel::onQueryChange,
        onSortSelected = viewModel::onSortSelected,
        onOpenFamily = onOpenFamily,
        onCreateFamily = { viewModel.onCreateFamily(onOpenFamily) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FamilyListScreen(
    uiState: FamilyListUiState,
    query: String,
    sorting: FamilySorting,
    onQueryChange: (String) -> Unit,
    onSortSelected: (FamilySort) -> Unit,
    onOpenFamily: (Long) -> Unit,
    onCreateFamily: () -> Unit,
    modifier: Modifier = Modifier,
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
                            placeholder = { Text(stringResource(R.string.search_families)) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            ),
                        )
                    } else {
                        Text(stringResource(R.string.families_title))
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
                                if (searching) R.string.close_search else R.string.search_families,
                            ),
                        )
                    }
                    SortMenu(sorting = sorting, onSortSelected = onSortSelected)
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateFamily) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_family))
            }
        },
    ) { padding ->
        when (uiState) {
            FamilyListUiState.Loading -> FullScreenLoading(Modifier.padding(padding))

            FamilyListUiState.Empty -> EmptyState(
                icon = Icons.Outlined.Diversity3,
                title = stringResource(R.string.no_families_title),
                description = stringResource(R.string.no_families_description),
                modifier = Modifier.padding(padding),
            )

            is FamilyListUiState.NoMatches -> EmptyState(
                icon = Icons.Outlined.SearchOff,
                title = stringResource(R.string.no_matches_title),
                description = stringResource(R.string.no_family_matches, uiState.query),
                modifier = Modifier.padding(padding),
            )

            is FamilyListUiState.Families -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = FtDimens.screenPadding,
                    end = FtDimens.screenPadding,
                    top = padding.calculateTopPadding() + FtDimens.listItemSpacing,
                    bottom = padding.calculateBottomPadding() + FtDimens.screenPadding * 5,
                ),
                verticalArrangement = Arrangement.spacedBy(FtDimens.listItemSpacing),
            ) {
                items(uiState.families, key = { it.family.id }) { family ->
                    FamilyCard(family = family, onClick = { onOpenFamily(family.family.id) })
                }
            }
        }
    }
}

@Composable
private fun FamilyCard(family: FamilySummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(FtDimens.cardPadding)) {
            // Partners are the family's identity; without them the row reads as unnamed.
            Text(
                text = family.partners.joinToString(" & ") { it.displayName }
                    .ifBlank { stringResource(R.string.family_without_partners) },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            family.marriage?.let { marriage ->
                val detail = listOfNotNull(
                    marriage.year?.toString() ?: marriage.date,
                    marriage.place,
                ).joinToString(" · ")
                if (detail.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.married_prefix, detail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (family.children.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.children_count, family.children.size) +
                        ": " + family.children.joinToString(", ") { it.displayName },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SortMenu(sorting: FamilySorting, onSortSelected: (FamilySort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                Icons.AutoMirrored.Filled.Sort,
                contentDescription = stringResource(R.string.sort_families),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            FamilySort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(stringResource(sort.labelRes())) },
                    trailingIcon = {
                        if (sort == sorting.sort) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = null,
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

private fun FamilySort.labelRes(): Int = when (this) {
    FamilySort.ID -> R.string.family_sort_id
    FamilySort.SURNAME -> R.string.family_sort_surname
    FamilySort.MEMBERS -> R.string.family_sort_members
}

@Preview
@Composable
private fun FamilyListPreview() {
    fun person(id: Long, name: String, sex: Sex) = PersonSummary(
        person = Person(id = id, treeId = 1, gedcomId = "I$id", sex = sex),
        displayName = name,
        searchText = name.lowercase(),
        birth = null,
        death = null,
        isDeceased = false,
        birthSortKey = Int.MAX_VALUE,
        ageInYears = null,
        daysToNextBirthday = null,
        relativeCount = 0,
        portraitMediaId = null,
    )
    FamilyTreeTheme {
        FamilyListScreen(
            uiState = FamilyListUiState.Families(
                listOf(
                    FamilySummary(
                        family = Family(id = 1, treeId = 1, gedcomId = "F1"),
                        partners = listOf(
                            person(1, "Ahmet Yılmaz", Sex.MALE),
                            person(2, "Ayşe Yılmaz", Sex.FEMALE),
                        ),
                        children = listOf(person(3, "Mehmet Yılmaz", Sex.MALE)),
                        marriage = LifeEvent("8 JUN 1740", "Bursa", 1740),
                        searchText = "",
                    ),
                ),
            ),
            query = "",
            sorting = FamilySorting(),
            onQueryChange = {},
            onSortSelected = {},
            onOpenFamily = {},
            onCreateFamily = {},
        )
    }
}
