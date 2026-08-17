package com.familytree.core.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.familytree.core.designsystem.theme.FtDimens
import com.familytree.core.model.MediaObject
import com.familytree.core.model.PersonSummary

/**
 * One person in a list: avatar, name, and the dates that identify them.
 *
 * Two people in a family tree often share a name, so the dates are not decoration —
 * they are how the reader tells a grandfather from his grandson.
 */
@Composable
fun PersonRow(
    person: PersonSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    portrait: MediaObject? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = FtDimens.minTouchTarget)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = FtDimens.screenPadding, vertical = FtDimens.listItemSpacing),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FtDimens.listItemSpacing * 1.5f),
    ) {
        PersonAvatar(
            sex = person.person.sex,
            deceased = person.isDeceased,
            portrait = portrait,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = person.displayName.ifBlank { stringResourceUnnamed() },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            person.lifeSpanLabel()?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            person.placeLabel()?.let { place ->
                Text(
                    text = place,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun stringResourceUnnamed(): String =
    androidx.compose.ui.res.stringResource(R.string.person_unnamed)

/**
 * `1890 – 1954`, or `1890 –` for someone still living.
 *
 * Falls back to the raw GEDCOM date when no year could be read, so an approximate or
 * phrase date still shows the user what they typed rather than nothing.
 */
@Composable
private fun PersonSummary.lifeSpanLabel(): String? {
    val birthYear = birth?.year?.toString() ?: birth?.date
    val deathYear = death?.year?.toString() ?: death?.date
    return when {
        birthYear != null && deathYear != null -> "$birthYear – $deathYear"
        birthYear != null && isDeceased -> "$birthYear – "
        birthYear != null -> birthYear
        deathYear != null -> "– $deathYear"
        else -> null
    }
}

@Composable
private fun PersonSummary.placeLabel(): String? =
    birth?.place?.takeIf { it.isNotBlank() } ?: death?.place?.takeIf { it.isNotBlank() }
