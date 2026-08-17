package com.familytree.feature.media

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familytree.core.designsystem.theme.FtDimens
import com.familytree.core.model.OwnerType
import kotlinx.coroutines.launch
import java.io.File

/**
 * Where a new photograph or document comes from.
 *
 * Three sources, because they answer different needs: the photo picker for pictures
 * already on the phone, the file picker for the PDFs and scans a genealogist actually
 * collects, and the camera for photographing a document that only exists on paper.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMediaSheet(
    treeId: Long,
    ownerType: OwnerType,
    ownerId: Long,
    onDismiss: () -> Unit,
    onAdded: (mediaId: Long, croppable: Boolean) -> Unit,
    viewModel: AddMediaViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(treeId, ownerType, ownerId) {
        viewModel.configure(treeId, ownerType, ownerId)
    }
    state.created?.let { created ->
        LaunchedEffect(created) {
            onAdded(created.mediaId, created.croppable)
            viewModel.consumeResult()
            onDismiss()
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(viewModel::importFile) ?: onDismiss() }

    val filePicker = rememberLauncherForActivityResult(
        // Any type: a family archive holds PDFs, audio interviews and spreadsheets as
        // often as it holds photographs.
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importFile) ?: onDismiss() }

    var cameraTarget by remember { mutableStateOf<File?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val target = cameraTarget
        if (taken && target != null) viewModel.attachCameraFile(target) else onDismiss()
        cameraTarget = null
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                text = stringResource(R.string.add_media),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(
                    start = FtDimens.screenPadding,
                    end = FtDimens.screenPadding,
                    bottom = FtDimens.listItemSpacing,
                ),
            )
            SourceRow(Icons.Outlined.PhotoLibrary, stringResource(R.string.from_photos)) {
                photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                )
            }
            SourceRow(Icons.Outlined.Description, stringResource(R.string.from_files)) {
                filePicker.launch(arrayOf("*/*"))
            }
            SourceRow(Icons.Outlined.PhotoCamera, stringResource(R.string.take_photo)) {
                scope.launch {
                    val target = viewModel.newCameraFile()
                    cameraTarget = target
                    camera.launch(context.shareableUri(target))
                }
            }
        }
    }

    state.duplicate?.let { duplicate ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDuplicate,
            title = { Text(duplicate.filename) },
            text = { Text(stringResource(R.string.file_exists_reuse)) },
            confirmButton = {
                TextButton(onClick = viewModel::reuseDuplicate) {
                    Text(stringResource(R.string.use_existing))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::keepBothCopies) {
                    Text(stringResource(R.string.make_copy))
                }
            },
        )
    }
}

@Composable
private fun SourceRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = FtDimens.screenPadding, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FtDimens.screenPadding),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * A URI the camera app is allowed to write to.
 *
 * The tree's folder is private to this app, so the destination has to be handed over as a
 * FileProvider grant rather than as a path.
 */
private fun Context.shareableUri(file: File) =
    FileProvider.getUriForFile(this, "$packageName.provider", file)
