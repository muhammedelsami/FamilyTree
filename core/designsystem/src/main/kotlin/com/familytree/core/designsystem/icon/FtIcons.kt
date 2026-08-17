package com.familytree.core.designsystem.icon

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.familytree.core.designsystem.R

/**
 * Drawings this app needs that Material's icon set does not carry.
 *
 * Read the same way as `Icons.Filled.*`, so a caller does not have to know which of the
 * two it is reaching for. Only what is genuinely missing belongs here; anything Material
 * already draws should come from Material, so the interface keeps one hand.
 */
object FtIcons {

    /** A man's head and shoulders, standing in for a portrait that does not exist. */
    val AvatarMan: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_avatar_man)

    /** A woman's head and shoulders, standing in for a portrait that does not exist. */
    val AvatarWoman: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_avatar_woman)
}
