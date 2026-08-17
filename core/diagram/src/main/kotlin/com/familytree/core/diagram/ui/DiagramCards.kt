package com.familytree.core.diagram.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.familytree.core.designsystem.theme.FtTheme
import com.familytree.core.diagram.DiagramCard
import com.familytree.core.model.MediaObject
import com.familytree.core.model.PersonSummary
import com.familytree.core.ui.PersonAvatar
import com.familytree.core.model.Sex

/**
 * One person in the diagram.
 *
 * Deliberately compact: a diagram is read by scanning shapes and dates, so the card
 * carries a name, a lifespan and a sex-tinted border, and nothing else. Everything
 * further lives one tap away on the profile.
 */
@Composable
fun DiagramPersonCard(
    card: DiagramCard,
    person: PersonSummary?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    portrait: MediaObject? = null,
) {
    val colors = FtTheme.genealogyColors
    val sex = person?.person?.sex ?: Sex.NONE
    val border = when (sex) {
        Sex.MALE -> colors.maleAccent
        Sex.FEMALE -> colors.femaleAccent
        else -> colors.undefinedAccent
    }
    // The fulcrum is the person the diagram is drawn around; it has to be findable at a
    // glance in a screen full of similar cards.
    val background = when {
        card.isFulcrum -> MaterialTheme.colorScheme.primaryContainer
        card.isAcquired -> colors.acquiredCard
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    Column(
        modifier = modifier
            .widthIn(min = 120.dp, max = 220.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(
                width = if (card.isFulcrum) 3.dp else 2.dp,
                color = border,
                shape = RoundedCornerShape(12.dp),
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Only drawn when there is a picture: an empty circle on every card would be
        // noise, and most people in a tree have no photograph.
        if (portrait != null) {
            PersonAvatar(
                sex = sex,
                size = 44.dp,
                deceased = person?.isDeceased == true,
                portrait = portrait,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Text(
            text = person?.displayName?.takeIf { it.isNotBlank() } ?: "—",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (card.isFulcrum) FontWeight.Bold else FontWeight.Medium,
            color = if (card.isFulcrum) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        person?.lifespan()?.let { span ->
            Text(
                text = span,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (card.isDuplicate) {
            // The same person can appear more than once when families interlink; saying
            // so prevents the reader thinking they are looking at two relatives.
            Text(
                text = "↔",
                style = MaterialTheme.typography.labelSmall,
                color = colors.undefinedAccent,
            )
        }
    }
}

/**
 * A placeholder for a branch that is not drawn, showing how many people it hides.
 *
 * Tapping it re-centres the diagram there, which is how a large tree stays navigable
 * without ever drawing all of it.
 */
@Composable
fun DiagramMiniCard(
    card: DiagramCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .combinedClickable(onClick = onClick, onLongClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            // Anything above ninety-nine is "lots"; the exact figure stops being useful.
            text = if (card.hiddenCount > 99) "99+" else card.hiddenCount.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/**
 * The marriage marker between two partners: a year when known, a heart when not.
 */
@Composable
fun DiagramBondMarker(
    marriageYear: String?,
    isMini: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FtTheme.genealogyColors
    if (marriageYear.isNullOrBlank()) {
        Box(
            modifier = modifier
                .size(if (isMini) 8.dp else 12.dp)
                .clip(CircleShape)
                .background(colors.diagramLine)
                .combinedClickable(onClick = onClick, onLongClick = {}),
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .combinedClickable(onClick = onClick, onLongClick = {})
                .padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = marriageYear,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
            )
        }
    }
}

/** `1890–1954`, or a single year when only one is known. */
private fun PersonSummary.lifespan(): String? {
    val birth = birth?.year?.toString()
    val death = death?.year?.toString()
    return when {
        birth != null && death != null -> "$birth–$death"
        birth != null -> birth
        death != null -> "–$death"
        else -> null
    }
}