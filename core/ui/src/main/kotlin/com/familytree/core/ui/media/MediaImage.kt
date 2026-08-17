package com.familytree.core.ui.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.familytree.core.media.MediaKind
import com.familytree.core.media.ResolvedMedia
import com.familytree.core.model.MediaObject

/**
 * A media record shown as an image.
 *
 * Everything a genealogist attaches is not a picture: scanned certificates arrive as PDFs,
 * interviews as audio, and a tree copied from a computer often points at files that never
 * made the trip. Each of those gets an honest, typed placeholder instead of a broken
 * image, because "this is a PDF I cannot find" and "this is a photo that failed to load"
 * are different problems for the user to act on.
 */
@Composable
fun MediaImage(
    media: MediaObject?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val resolved by rememberResolvedMedia(media)
    MediaImage(
        resolved = resolved,
        format = media?.format,
        modifier = modifier,
        contentDescription = contentDescription ?: media?.title,
        contentScale = contentScale,
    )
}

@Composable
fun MediaImage(
    resolved: ResolvedMedia,
    modifier: Modifier = Modifier,
    format: String? = null,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val model = resolved.loadable
    when {
        resolved.kind == MediaKind.PDF && resolved.exists -> PdfPreview(resolved, modifier, contentDescription)

        model != null && (resolved.kind == MediaKind.IMAGE || resolved.kind == MediaKind.VIDEO) -> {
            Box(modifier, contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = model,
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize(),
                    // A file that resolved but will not decode is still a real file —
                    // an unsupported raw format, say — so it falls back to its type.
                    error = null,
                )
                if (resolved.kind == MediaKind.VIDEO) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
        }

        else -> MediaPlaceholder(
            kind = resolved.kind,
            label = format?.uppercase() ?: resolved.extension?.uppercase(),
            missing = !resolved.exists,
            modifier = modifier,
            contentDescription = contentDescription,
        )
    }
}

/**
 * A tile standing in for something that is not a picture.
 *
 * It carries the file's format as text, so a folder of scans stays distinguishable at a
 * glance rather than becoming a wall of identical icons.
 */
@Composable
fun MediaPlaceholder(
    kind: MediaKind,
    modifier: Modifier = Modifier,
    label: String? = null,
    missing: Boolean = false,
    contentDescription: String? = null,
) {
    val icon: ImageVector = when {
        missing -> Icons.Default.ImageNotSupported
        kind == MediaKind.PDF -> Icons.Default.PictureAsPdf
        kind == MediaKind.VIDEO -> Icons.Default.PlayCircle
        else -> Icons.Default.Description
    }
    val tint = if (missing) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    BoxWithConstraints(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        // Below about a list-row avatar there is no room for text, and a cramped label
        // reads as noise, so small tiles show the icon alone.
        val roomForLabel = maxWidth >= 64.dp && maxHeight >= 64.dp
        val iconSize = if (roomForLabel) 32.dp else maxWidth * 0.5f
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(iconSize))
            if (roomForLabel && !label.isNullOrBlank()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = tint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

/** The first page of a PDF, rendered as a picture. */
@Composable
private fun PdfPreview(
    resolved: ResolvedMedia,
    modifier: Modifier,
    contentDescription: String?,
) {
    val bitmap by rememberPdfPreview(resolved)
    val page = bitmap
    if (page == null) {
        MediaPlaceholder(MediaKind.PDF, modifier, label = "PDF", contentDescription = contentDescription)
    } else {
        Image(
            bitmap = page.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier.background(MaterialTheme.colorScheme.surface),
        )
    }
}
