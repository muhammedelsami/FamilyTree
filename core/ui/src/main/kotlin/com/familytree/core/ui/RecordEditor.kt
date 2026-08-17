package com.familytree.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.familytree.core.designsystem.theme.FtDimens

/**
 * Edits any record from a list of [FieldSpec]s.
 *
 * One screen serves every record type, as the original's `DetailActivity` did, but the
 * fields are described by typed lambdas instead of reflected method names — so a
 * renamed model property is a compile error rather than a crash at runtime.
 *
 * @param knownPlaces places already used in this tree, offered for [FieldKind.PLACE].
 *   This replaces the online place lookup the original depended on, and works offline.
 */
@Composable
fun <T> RecordEditor(
    record: T,
    fields: List<FieldSpec<T>>,
    onRecordChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    expert: Boolean = false,
    knownPlaces: List<String> = emptyList(),
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FtDimens.listItemSpacing * 1.5f),
    ) {
        fields
            .filter { expert || !it.expertOnly }
            .forEach { field ->
                val value = field.get(record).orEmpty()
                when (field.kind) {
                    FieldKind.DATE -> DateField(
                        value = value,
                        onValueChange = { onRecordChange(field.set(record, it)) },
                        label = stringResource(field.labelRes),
                        expert = expert,
                    )

                    FieldKind.PLACE -> PlaceField(
                        value = value,
                        onValueChange = { onRecordChange(field.set(record, it)) },
                        label = stringResource(field.labelRes),
                        suggestions = knownPlaces,
                    )

                    FieldKind.MULTILINE -> OutlinedTextField(
                        value = value,
                        onValueChange = { onRecordChange(field.set(record, it)) },
                        label = { Text(stringResource(field.labelRes)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                    )

                    FieldKind.TEXT -> OutlinedTextField(
                        value = value,
                        onValueChange = { onRecordChange(field.set(record, it)) },
                        label = { Text(stringResource(field.labelRes)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
    }
}

/**
 * A place with suggestions drawn from the tree itself.
 *
 * Genealogists enter the same handful of places over and over, and the spelling has to
 * match for the diagram and searches to group them — so offering what is already in the
 * tree is more useful here than a global gazetteer would be.
 */
@Composable
private fun PlaceField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suggestions: List<String>,
) {
    var focused by remember { mutableStateOf(false) }
    val matches = remember(value, suggestions) {
        if (value.isBlank()) {
            emptyList()
        } else {
            suggestions.filter { it.contains(value, ignoreCase = true) && it != value }.take(5)
        }
    }

    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
        )
        DropdownMenu(
            expanded = focused && matches.isNotEmpty(),
            onDismissRequest = { focused = false },
            // Never steal focus from the field the user is typing in.
            properties = PopupProperties(focusable = false),
        ) {
            matches.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = {
                        onValueChange(suggestion)
                        focused = false
                    },
                )
            }
        }
    }
}
