package com.familytree.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.familytree.core.designsystem.theme.FtDimens
import com.familytree.core.model.date.DateKind
import com.familytree.core.model.date.DateParts
import com.familytree.core.model.date.GedcomDate
import com.familytree.core.model.date.buildGedcomDate
import java.text.DateFormatSymbols
import java.util.Locale

/**
 * A GEDCOM date, editable either as raw text or through a structured picker.
 *
 * Both routes matter: a genealogist transcribing a record wants to type `ABT 1850`
 * directly, while someone entering their grandmother's birthday wants a picker. The
 * text stays the source of truth so nothing is lost in translation between the two.
 *
 * @param expert exposes the twelve GEDCOM date kinds, the B.C. era and double years.
 *   Off, the picker offers a single "approximate" option, which is all most trees need.
 */
@Composable
fun DateField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    expert: Boolean = false,
) {
    var editing by remember { mutableStateOf(false) }
    val parsed = remember(value) { GedcomDate(value) }
    val valid = remember(value) { parsed.isValid(value) }

    Column(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            isError = !valid,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                TextButton(onClick = { editing = true }) {
                    Text(stringResource(R.string.date_pick))
                }
            },
        )
        if (!valid) {
            // A warning, not a block: GEDCOM tolerates free-text dates, and refusing to
            // save what a record actually says would lose information.
            Text(
                text = stringResource(R.string.date_not_standard),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = FtDimens.cardPadding, top = 2.dp),
            )
        }
    }

    if (editing) {
        DateEditorDialog(
            initial = value,
            expert = expert,
            onDismiss = { editing = false },
            onConfirm = {
                onValueChange(it)
                editing = false
            },
        )
    }
}

@Composable
private fun DateEditorDialog(
    initial: String,
    expert: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val parsed = remember(initial) { GedcomDate(initial) }
    var kind by remember { mutableStateOf(parsed.kind ?: DateKind.EXACT) }
    var first by remember { mutableStateOf(DateParts.from(parsed.firstDate)) }
    var second by remember { mutableStateOf(DateParts.from(parsed.secondDate)) }
    var phrase by remember { mutableStateOf(parsed.phrase.orEmpty()) }

    val isRange = kind == DateKind.BETWEEN_AND || kind == DateKind.FROM_TO

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.date_editor_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(FtDimens.listItemSpacing),
            ) {
                if (expert) {
                    KindPicker(kind = kind, onKindChange = { kind = it })
                } else {
                    LabelledCheckbox(
                        label = stringResource(R.string.date_approximate),
                        checked = kind == DateKind.APPROXIMATE,
                        onCheckedChange = { kind = if (it) DateKind.APPROXIMATE else DateKind.EXACT },
                    )
                }

                if (kind != DateKind.PHRASE) {
                    PartsEditor(
                        parts = first,
                        onPartsChange = { first = it },
                        expert = expert,
                    )
                    if (isRange) {
                        Text(
                            text = stringResource(
                                if (kind == DateKind.BETWEEN_AND) R.string.date_and else R.string.date_to,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        PartsEditor(
                            parts = second,
                            onPartsChange = { second = it },
                            expert = expert,
                        )
                    }
                }

                if (kind == DateKind.PHRASE || kind == DateKind.INTERPRETED) {
                    OutlinedTextField(
                        value = phrase,
                        onValueChange = { phrase = it },
                        label = { Text(stringResource(R.string.date_phrase)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        buildGedcomDate(
                            kind = kind,
                            first = first,
                            second = if (isRange) second else null,
                            phrase = phrase,
                        ),
                    )
                },
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun PartsEditor(
    parts: DateParts,
    onPartsChange: (DateParts) -> Unit,
    expert: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(FtDimens.listItemSpacing)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(FtDimens.listItemSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NumberBox(
                value = parts.day,
                onValueChange = { onPartsChange(parts.copy(day = it?.coerceIn(1, 31))) },
                label = stringResource(R.string.date_day),
                modifier = Modifier.width(80.dp),
            )
            MonthPicker(
                month = parts.month,
                onMonthChange = { onPartsChange(parts.copy(month = it)) },
                modifier = Modifier.weight(1f),
            )
            NumberBox(
                value = parts.year,
                onValueChange = { onPartsChange(parts.copy(year = it)) },
                label = stringResource(R.string.date_year),
                modifier = Modifier.width(96.dp),
            )
        }
        if (expert) {
            Row(horizontalArrangement = Arrangement.spacedBy(FtDimens.screenPadding)) {
                LabelledCheckbox(
                    label = stringResource(R.string.date_bc),
                    checked = parts.negative,
                    onCheckedChange = { onPartsChange(parts.copy(negative = it)) },
                )
                LabelledCheckbox(
                    label = stringResource(R.string.date_dual_year),
                    checked = parts.dual,
                    onCheckedChange = { onPartsChange(parts.copy(dual = it)) },
                )
            }
        }
    }
}

@Composable
private fun NumberBox(
    value: Int?,
    onValueChange: (Int?) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value?.toString().orEmpty(),
        onValueChange = { text ->
            // Clearing the box means "this part is unknown", which GEDCOM allows.
            onValueChange(text.filter(Char::isDigit).takeIf { it.isNotEmpty() }?.toIntOrNull())
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthPicker(
    month: Int?,
    onMonthChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    // Month names in the device language; the file always gets the English abbreviation.
    val names = remember(Locale.getDefault()) {
        DateFormatSymbols.getInstance(Locale.getDefault()).months.take(12)
    }
    val none = stringResource(R.string.date_month_none)

    ExposedDropdownMenuBox(
        expanded = open,
        onExpandedChange = { open = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = month?.let { names.getOrNull(it - 1) } ?: none,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.date_month)) },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(none) },
                onClick = { onMonthChange(null); open = false },
            )
            names.forEachIndexed { index, name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { onMonthChange(index + 1); open = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KindPicker(kind: DateKind, onKindChange: (DateKind) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = open,
        onExpandedChange = { open = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = stringResource(kind.labelRes()),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.date_kind)) },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DateKind.entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(stringResource(entry.labelRes())) },
                    onClick = { onKindChange(entry); open = false },
                )
            }
        }
    }
}

@Composable
private fun LabelledCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun DateKind.labelRes(): Int = when (this) {
    DateKind.EXACT -> R.string.date_kind_exact
    DateKind.APPROXIMATE -> R.string.date_kind_approximate
    DateKind.CALCULATED -> R.string.date_kind_calculated
    DateKind.ESTIMATED -> R.string.date_kind_estimated
    DateKind.AFTER -> R.string.date_kind_after
    DateKind.BEFORE -> R.string.date_kind_before
    DateKind.BETWEEN_AND -> R.string.date_kind_between
    DateKind.FROM -> R.string.date_kind_from
    DateKind.TO -> R.string.date_kind_to
    DateKind.FROM_TO -> R.string.date_kind_from_to
    DateKind.INTERPRETED -> R.string.date_kind_interpreted
    DateKind.PHRASE -> R.string.date_kind_phrase
}
