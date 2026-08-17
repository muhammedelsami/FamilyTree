package com.familytree.feature.person

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familytree.core.designsystem.component.EmptyState
import com.familytree.core.designsystem.component.FullScreenLoading
import com.familytree.core.designsystem.theme.FtDimens
import com.familytree.core.ui.PersonRow

/**
 * Picks somebody already in the tree.
 *
 * A screen of its own rather than a dialog, because the choice is made by searching: the
 * trees this matters in have hundreds of people, and a list in a dialog cannot be
 * searched comfortably.
 *
 * @param excludePersonId the person the link starts from — offering to relate somebody to
 *   themselves is never what was meant.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonPickerRoute(
    treeId: Long,
    excludePersonId: Long,
    onBack: () -> Unit,
    onPicked: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PersonListViewModel = hiltViewModel(),
) {
    LaunchedEffect(treeId) { viewModel.setTree(treeId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val portraits by viewModel.portraits.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.choose_person)) },
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
            TextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text(stringResource(R.string.search_people)) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FtDimens.screenPadding),
            )

            when (uiState) {
                PersonListUiState.Loading -> FullScreenLoading()

                is PersonListUiState.NoMatches -> EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = stringResource(R.string.no_matches_title),
                    description = stringResource(
                        R.string.no_matches_description,
                        (uiState as PersonListUiState.NoMatches).query,
                    ),
                )

                PersonListUiState.Empty -> EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = stringResource(R.string.no_people_title),
                )

                is PersonListUiState.People -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = FtDimens.screenPadding),
                ) {
                    val people = (uiState as PersonListUiState.People).people
                        .filterNot { it.person.id == excludePersonId }
                    items(people, key = { it.person.id }) { person ->
                        PersonRow(
                            person = person,
                            onClick = { onPicked(person.person.id) },
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
}
