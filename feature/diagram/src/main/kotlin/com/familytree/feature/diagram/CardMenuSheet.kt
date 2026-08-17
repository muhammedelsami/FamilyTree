package com.familytree.feature.diagram

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Diversity3
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.familytree.core.designsystem.theme.FtDimens
import com.familytree.core.model.Relation

/**
 * What can be done with one card, on long press.
 *
 * A bottom sheet rather than the original's context menu: the actions are not all one
 * line long — a family entry names the people in it — and several of them lead somewhere
 * else, which a sheet makes obvious in a way a floating menu does not.
 *
 * Entries that cannot apply are absent rather than disabled. A person with no family
 * cannot be unlinked from one, and offering the option greyed out only invites the user
 * to work out why.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CardMenuSheet(
    target: CardMenuTarget,
    onDismiss: () -> Unit,
    onAction: (CardAction) -> Unit,
    onUnlink: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var choosingRelation by remember { mutableStateOf<RelationPurpose?>(null) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var confirmingUnlink by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                text = target.name.ifBlank { stringResource(R.string.unnamed_person) },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(
                    start = FtDimens.screenPadding,
                    end = FtDimens.screenPadding,
                    bottom = FtDimens.listItemSpacing,
                ),
            )

            MenuRow(Icons.Outlined.Person, stringResource(R.string.open_profile)) {
                onAction(CardAction.OpenProfile(target.personId))
            }

            // Centring on the person already at the centre would do nothing.
            if (!target.isFulcrum) {
                MenuRow(Icons.Outlined.CenterFocusStrong, stringResource(R.string.centre_here)) {
                    onAction(CardAction.Recentre(target.gedcomId))
                }
            }

            target.parentFamilies.forEach { family ->
                MenuRow(
                    icon = Icons.Outlined.Diversity3,
                    label = stringResource(R.string.family_as_child),
                    detail = family.label,
                ) {
                    onAction(CardAction.OpenFamily(family.familyId))
                }
            }
            target.spouseFamilies.forEach { family ->
                MenuRow(
                    icon = Icons.Outlined.Diversity3,
                    label = stringResource(R.string.family_as_spouse),
                    detail = family.label,
                ) {
                    onAction(CardAction.OpenFamily(family.familyId))
                }
            }

            HorizontalDivider(Modifier.padding(vertical = FtDimens.listItemSpacing / 2))

            MenuRow(Icons.Outlined.PersonAdd, stringResource(R.string.add_relative)) {
                choosingRelation = RelationPurpose.CREATE
            }
            if (target.canLinkExisting) {
                MenuRow(Icons.Outlined.PersonSearch, stringResource(R.string.link_existing_person)) {
                    choosingRelation = RelationPurpose.LINK
                }
            }
            MenuRow(Icons.Outlined.Edit, stringResource(R.string.edit_person)) {
                onAction(CardAction.Edit(target.personId))
            }

            HorizontalDivider(Modifier.padding(vertical = FtDimens.listItemSpacing / 2))

            if (target.hasFamilies) {
                MenuRow(Icons.Outlined.LinkOff, stringResource(R.string.unlink_from_families)) {
                    confirmingUnlink = true
                }
            }
            MenuRow(
                icon = Icons.Outlined.Delete,
                label = stringResource(R.string.delete_person),
                tint = MaterialTheme.colorScheme.error,
            ) {
                confirmingDelete = true
            }
        }
    }

    choosingRelation?.let { purpose ->
        RelationChooser(
            onDismiss = { choosingRelation = null },
            onChoose = { relation ->
                choosingRelation = null
                onAction(
                    when (purpose) {
                        RelationPurpose.CREATE -> CardAction.AddRelative(target.personId, relation)
                        RelationPurpose.LINK -> CardAction.LinkExisting(target.personId, relation)
                    },
                )
            },
        )
    }

    if (confirmingUnlink) {
        AlertDialog(
            onDismissRequest = { confirmingUnlink = false },
            title = { Text(stringResource(R.string.unlink_from_families)) },
            // Says what survives, because the two destructive actions sit next to each
            // other and the difference between them is the whole point.
            text = { Text(stringResource(R.string.unlink_explanation, target.name)) },
            confirmButton = {
                TextButton(onClick = { confirmingUnlink = false; onUnlink(target.personId) }) {
                    Text(stringResource(R.string.unlink))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingUnlink = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.delete_person)) },
            text = { Text(stringResource(R.string.delete_person_explanation, target.name)) },
            confirmButton = {
                TextButton(onClick = { confirmingDelete = false; onDelete(target.personId) }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

private enum class RelationPurpose { CREATE, LINK }

/**
 * The four relationships GEDCOM can record directly.
 *
 * Everything else — an uncle, a grandchild — is a chain of these, and offering them here
 * would be promising a link the format cannot store.
 */
@Composable
private fun RelationChooser(onDismiss: () -> Unit, onChoose: (Relation) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.which_relation)) },
        text = {
            Column {
                Relation.entries.forEach { relation ->
                    Text(
                        text = stringResource(relation.labelRes()),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChoose(relation) }
                            .padding(vertical = FtDimens.listItemSpacing * 1.5f),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    detail: String? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            // A comfortable target: this sheet is reached by long-pressing a small card,
            // often with the other hand already holding the phone.
            .heightIn(min = FtDimens.minTouchTarget)
            .padding(horizontal = FtDimens.screenPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FtDimens.screenPadding),
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (!detail.isNullOrBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun Relation.labelRes(): Int = when (this) {
    Relation.PARENT -> R.string.relation_parent
    Relation.SIBLING -> R.string.relation_sibling
    Relation.PARTNER -> R.string.relation_partner
    Relation.CHILD -> R.string.relation_child
}
