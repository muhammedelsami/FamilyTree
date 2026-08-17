package com.familytree.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import com.familytree.core.designsystem.icon.FtIcons
import com.familytree.core.designsystem.theme.FtDimens
import com.familytree.core.designsystem.theme.FtTheme
import com.familytree.core.media.MediaKind
import com.familytree.core.model.MediaObject
import com.familytree.core.model.Sex
import com.familytree.core.ui.media.MediaImage
import com.familytree.core.ui.media.rememberResolvedMedia

/**
 * A person's portrait, or a figure standing in for one while no picture is available.
 *
 * The placeholder is a man, a woman or an unmarked person rather than the initials of the
 * name, which the name beside it is already spelling out. Sex is the attribute a
 * genealogist scans a list for, so the space is better spent saying it a second way: the
 * ring is tinted for it too, and having both means the distinction survives for a reader
 * who cannot separate the two colours.
 */
@Composable
fun PersonAvatar(
    sex: Sex,
    modifier: Modifier = Modifier,
    size: Dp = FtDimens.portraitMedium,
    deceased: Boolean = false,
    portrait: MediaObject? = null,
) {
    val colors = FtTheme.genealogyColors
    val ring = when (sex) {
        Sex.MALE -> colors.maleAccent
        Sex.FEMALE -> colors.femaleAccent
        else -> colors.undefinedAccent
    }
    val placeholderTint = MaterialTheme.colorScheme.onSurfaceVariant
        .let { if (deceased) it.copy(alpha = 0.7f) else it }
    val resolved by rememberResolvedMedia(portrait)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(FtDimens.hairline * 2, if (deceased) ring.copy(alpha = 0.5f) else ring, CircleShape)
            // The name is already announced by the row, so the avatar stays silent.
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        // The figure shows while the portrait resolves, and stays if none does — most
        // people in a tree have no photograph, so that is a normal state rather than a
        // failure.
        if (resolved.exists && resolved.kind == MediaKind.IMAGE) {
            MediaImage(resolved = resolved, modifier = Modifier.fillMaxSize())
        } else {
            // Hung from the bottom of the circle at a little under its width. The
            // silhouettes are drawn edge to edge in a square, so filling the circle with
            // them puts the crown of the head outside it and beheads them; pulled in and
            // dropped, the head clears the top and only the shoulders run off, which is
            // where a portrait is cropped anyway.
            when (sex) {
                Sex.MALE, Sex.FEMALE -> Icon(
                    imageVector = if (sex == Sex.MALE) FtIcons.AvatarMan else FtIcons.AvatarWoman,
                    contentDescription = null,
                    modifier = Modifier
                        .size(size * 0.92f)
                        .align(Alignment.BottomCenter),
                    tint = placeholderTint,
                )

                // Unrecorded sex gets a mark rather than a third figure. Any figure drawn
                // here would be read as an answer — a neutral one is still one — and in a
                // tree the difference between "no sex recorded" and "sex recorded as
                // neither" is a real one the avatar should not paper over.
                else -> Icon(
                    imageVector = Icons.Filled.PriorityHigh,
                    contentDescription = null,
                    // Proportional to the circle, since the same avatar is drawn at 40dp
                    // in a list, 44dp on a diagram card and 96dp on a profile.
                    modifier = Modifier.size(size * 0.5f),
                    tint = placeholderTint,
                )
            }
        }
    }
}
