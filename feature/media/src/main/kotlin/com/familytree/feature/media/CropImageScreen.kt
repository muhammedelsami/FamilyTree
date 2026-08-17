package com.familytree.feature.media

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Rotate90DegreesCcw
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familytree.core.designsystem.component.FullScreenLoading
import com.familytree.core.media.CropRect
import kotlin.math.abs

@Composable
fun CropImageRoute(
    treeId: Long,
    mediaId: Long,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CropImageViewModel = hiltViewModel(),
) {
    LaunchedEffect(treeId, mediaId) { viewModel.load(treeId, mediaId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.saved) { if (uiState.saved) onDone() }

    CropImageScreen(
        uiState = uiState,
        onRotate = viewModel::rotate,
        onCropChange = viewModel::setCrop,
        onSave = viewModel::save,
        onCancel = onDone,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CropImageScreen(
    uiState: CropUiState,
    onRotate: () -> Unit,
    onCropChange: (CropRect) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.crop)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    // A dark bar keeps the eye on the photograph rather than the chrome,
                    // and matches the black backdrop a cropping screen needs.
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cancel),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRotate, enabled = !uiState.saving) {
                        Icon(
                            Icons.Outlined.Rotate90DegreesCcw,
                            contentDescription = stringResource(R.string.rotate),
                        )
                    }
                    IconButton(onClick = onSave, enabled = uiState.preview != null && !uiState.saving) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.done))
                    }
                },
            )
        },
    ) { padding ->
        val preview = uiState.preview
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (preview == null) {
                FullScreenLoading()
            } else {
                CropCanvas(
                    bitmap = preview,
                    rotation = uiState.rotation,
                    crop = uiState.crop,
                    onCropChange = onCropChange,
                )
            }
        }
    }
}

/**
 * The photograph with a draggable selection over it.
 *
 * The corners resize and the middle moves, which is the gesture vocabulary every phone
 * gallery uses; anything else would have to be learnt.
 */
@Composable
private fun CropCanvas(
    bitmap: Bitmap,
    rotation: Int,
    crop: CropRect,
    onCropChange: (CropRect) -> Unit,
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    // A quarter turn swaps which dimension governs the fit.
    val quarterTurned = rotation % 180 != 0
    val ratio = if (quarterTurned) {
        bitmap.height.toFloat() / bitmap.width
    } else {
        bitmap.width.toFloat() / bitmap.height
    }

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val available = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
        // The image is letterboxed, so the crop rectangle must live over the drawn area
        // rather than over the whole viewport.
        val drawnWidth: Float
        val drawnHeight: Float
        if (available.width / available.height > ratio) {
            drawnHeight = available.height
            drawnWidth = drawnHeight * ratio
        } else {
            drawnWidth = available.width
            drawnHeight = drawnWidth / ratio
        }
        val boxWidth = with(density) { drawnWidth.toDp() }
        val boxHeight = with(density) { drawnHeight.toDp() }

        Box(modifier = Modifier.size(boxWidth, boxHeight)) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(rotation.toFloat()),
            )
            CropOverlay(crop, drawnWidth, drawnHeight, onCropChange)
        }
    }
}

@Composable
private fun CropOverlay(
    crop: CropRect,
    width: Float,
    height: Float,
    onCropChange: (CropRect) -> Unit,
) {
    // The gesture block is not restarted when the rectangle changes, so it would
    // otherwise keep seeing the rectangle as it was when the block was launched and every
    // drag would be applied to that stale value — the selection would twitch and snap
    // back instead of following the finger.
    val currentCrop by rememberUpdatedState(crop)
    val handleTouchPx = with(LocalDensity.current) { 32.dp.toPx() }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(width, height) {
                // The rectangle as it was when this drag began, plus the total travel
                // since: deltas accumulate rather than each one being applied alone.
                var anchor = CropRect()
                var handle = Handle.NONE
                var travelX = 0f
                var travelY = 0f
                detectDragGestures(
                    onDragStart = { offset ->
                        anchor = currentCrop
                        handle = handleAt(
                            x = offset.x / width,
                            y = offset.y / height,
                            crop = anchor,
                            toleranceX = handleTouchPx / width,
                            toleranceY = handleTouchPx / height,
                        )
                        travelX = 0f
                        travelY = 0f
                    },
                    onDragEnd = { handle = Handle.NONE },
                ) { change, drag ->
                    change.consume()
                    travelX += drag.x / width
                    travelY += drag.y / height
                    onCropChange(anchor.moved(handle, travelX, travelY).coerced())
                }
            },
    ) {
        val left = crop.left * size.width
        val top = crop.top * size.height
        val right = crop.right * size.width
        val bottom = crop.bottom * size.height

        // Everything outside the selection is dimmed, in four bands rather than a
        // clipped path — cheaper to draw and identical on screen.
        val veil = Color.Black.copy(alpha = 0.55f)
        drawRect(veil, size = Size(size.width, top))
        drawRect(
            veil,
            topLeft = Offset(0f, bottom),
            size = Size(size.width, size.height - bottom),
        )
        drawRect(
            veil,
            topLeft = Offset(0f, top),
            size = Size(left, bottom - top),
        )
        drawRect(
            veil,
            topLeft = Offset(right, top),
            size = Size(size.width - right, bottom - top),
        )

        drawRect(
            color = Color.White,
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
            style = Stroke(width = 2.dp.toPx()),
        )
        val corner = 20.dp.toPx()
        val thickness = 4.dp.toPx()
        listOf(
            Offset(left, top) to (1f to 1f),
            Offset(right, top) to (-1f to 1f),
            Offset(left, bottom) to (1f to -1f),
            Offset(right, bottom) to (-1f to -1f),
        ).forEach { (point, direction) ->
            val (sx, sy) = direction
            drawLine(
                Color.White,
                start = point,
                end = Offset(point.x + corner * sx, point.y),
                strokeWidth = thickness,
            )
            drawLine(
                Color.White,
                start = point,
                end = Offset(point.x, point.y + corner * sy),
                strokeWidth = thickness,
            )
        }
    }
}

private enum class Handle { NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, MOVE }

private fun handleAt(
    x: Float,
    y: Float,
    crop: CropRect,
    toleranceX: Float,
    toleranceY: Float,
): Handle = when {
    // Separate tolerances per axis: the same 32dp target is a different fraction of the
    // width than of the height whenever the picture is not square.
    near(x, crop.left, toleranceX) && near(y, crop.top, toleranceY) -> Handle.TOP_LEFT
    near(x, crop.right, toleranceX) && near(y, crop.top, toleranceY) -> Handle.TOP_RIGHT
    near(x, crop.left, toleranceX) && near(y, crop.bottom, toleranceY) -> Handle.BOTTOM_LEFT
    near(x, crop.right, toleranceX) && near(y, crop.bottom, toleranceY) -> Handle.BOTTOM_RIGHT
    x in crop.left..crop.right && y in crop.top..crop.bottom -> Handle.MOVE
    else -> Handle.NONE
}

private fun near(value: Float, target: Float, tolerance: Float) = abs(value - target) <= tolerance

private fun CropRect.moved(handle: Handle, dx: Float, dy: Float): CropRect = when (handle) {
    Handle.TOP_LEFT -> copy(left = left + dx, top = top + dy)
    Handle.TOP_RIGHT -> copy(right = right + dx, top = top + dy)
    Handle.BOTTOM_LEFT -> copy(left = left + dx, bottom = bottom + dy)
    Handle.BOTTOM_RIGHT -> copy(right = right + dx, bottom = bottom + dy)
    Handle.MOVE -> {
        // Moving keeps the size: the whole rectangle stops at the edge rather than
        // being squashed against it.
        val shiftX = dx.coerceIn(-left, 1f - right)
        val shiftY = dy.coerceIn(-top, 1f - bottom)
        CropRect(left + shiftX, top + shiftY, right + shiftX, bottom + shiftY)
    }
    Handle.NONE -> this
}
