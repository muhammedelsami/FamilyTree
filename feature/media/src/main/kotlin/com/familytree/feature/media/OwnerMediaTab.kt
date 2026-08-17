package com.familytree.feature.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familytree.core.designsystem.component.EmptyState
import com.familytree.core.designsystem.theme.FtDimens
import com.familytree.core.model.MediaObject
import com.familytree.core.model.OwnerType
import com.familytree.core.ui.media.MediaImage

/**
 * The media attached to one record, as a profile tab.
 *
 * Lives here rather than in `feature:person` so the person screens do not have to know
 * about file pickers, cropping or resolution — they hand this in as a slot.
 */
@Composable
fun OwnerMediaTab(
    treeId: Long,
    ownerType: OwnerType,
    ownerId: Long,
    onOpenMedia: (Long) -> Unit,
    onCropMedia: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OwnerMediaViewModel = hiltViewModel(),
) {
    LaunchedEffect(ownerType, ownerId) { viewModel.load(ownerType, ownerId) }
    val media by viewModel.media.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_media)) },
            )
        },
    ) { padding ->
        if (media.isEmpty()) {
            EmptyState(
                modifier = Modifier.padding(padding),
                icon = Icons.Outlined.PhotoLibrary,
                title = stringResource(R.string.no_media_here),
                description = stringResource(R.string.no_media_here_description),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                contentPadding = PaddingValues(FtDimens.screenPadding),
                horizontalArrangement = Arrangement.spacedBy(FtDimens.listItemSpacing),
                verticalArrangement = Arrangement.spacedBy(FtDimens.listItemSpacing),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(media, key = { it.id }) { item ->
                    OwnerMediaTile(
                        media = item,
                        onClick = { onOpenMedia(item.id) },
                        onSetPortrait = { viewModel.setPortrait(item.id) },
                    )
                }
            }
        }
    }

    if (adding) {
        AddMediaSheet(
            treeId = treeId,
            ownerType = ownerType,
            ownerId = ownerId,
            onDismiss = { adding = false },
            onAdded = { mediaId, croppable ->
                // Straight into cropping for a photograph, because a freshly taken
                // picture of a document almost always needs trimming; anything else
                // opens its details so a title can be given.
                if (croppable) onCropMedia(mediaId) else onOpenMedia(mediaId)
            },
        )
    }
}

@Composable
private fun OwnerMediaTile(
    media: MediaObject,
    onClick: () -> Unit,
    onSetPortrait: () -> Unit,
) {
    val label = media.title?.takeIf { it.isNotBlank() } ?: media.file?.substringAfterLast('/')
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onClick),
        ) {
            MediaImage(media = media, modifier = Modifier.fillMaxSize(), contentDescription = label)
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = stringResource(R.string.use_as_portrait),
                tint = if (media.isPrimary) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .clickable(onClick = onSetPortrait),
            )
        }
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
    }
}
