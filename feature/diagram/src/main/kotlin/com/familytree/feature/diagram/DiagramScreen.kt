package com.familytree.feature.diagram

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familytree.core.designsystem.component.EmptyState
import com.familytree.core.designsystem.component.FullScreenLoading
import com.familytree.core.diagram.DiagramLayout
import com.familytree.core.diagram.ui.DiagramView
import com.familytree.core.diagram.ui.PannableZoomable
import com.familytree.core.diagram.ui.fulcrumCentre
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import com.familytree.core.model.Relation
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun DiagramRoute(
    treeId: Long,
    onOpenPerson: (Long) -> Unit,
    onOpenFamily: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onEditPerson: (Long) -> Unit,
    onAddRelative: (pivotPersonId: Long, relation: Relation) -> Unit,
    onPickPersonToLink: (pivotPersonId: Long, relation: Relation) -> Unit,
    /** Somebody the picker returned, waiting to be joined to [PendingLink.pivotPersonId]. */
    pendingLink: PendingLink? = null,
    onPendingLinkHandled: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: DiagramViewModel = hiltViewModel(),
) {
    LaunchedEffect(treeId) { viewModel.setTree(treeId) }

    // Handed in as state rather than fetched from the navigation graph, so the link is
    // made exactly once even if the screen recomposes on the way back.
    LaunchedEffect(pendingLink) {
        pendingLink?.let { link ->
            viewModel.linkExisting(link.pivotPersonId, link.otherPersonId, link.relation)
            onPendingLinkHandled()
        }
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Held across the file-picker round trip, so the result knows which format to write.
    var pendingFormat by remember { mutableStateOf(ExportFormat.PNG) }
    val exportedMessage = stringResource(R.string.export_done)
    val exportLauncher = rememberLauncherForActivityResult(
        CreateExportDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.export(uri.toString(), pendingFormat) { result ->
                scope.launch {
                    snackbarHostState.showSnackbar(
                        result.exceptionOrNull()?.message ?: exportedMessage,
                    )
                }
            }
        }
    }

    // The engine speaks GEDCOM ids; the rest of the app addresses records by row id.
    // The mapping is already in the state, so the translation happens here rather than
    // pushing engine identifiers into the navigation graph.
    DiagramScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onExport = { format ->
            pendingFormat = format
            exportLauncher.launch(ExportRequest(format, exportFileName(format)))
        },
        onLayoutReady = viewModel::onLayoutReady,
        onOpenPerson = { gedcomId ->
            uiState.peopleById[gedcomId]?.person?.id?.let(onOpenPerson)
        },
        onOpenFamily = { gedcomId ->
            uiState.familyIdsByGedcomId[gedcomId]?.let(onOpenFamily)
        },
        onRecenter = viewModel::recenterOn,
        onOpenSettings = onOpenSettings,
        onCardMenu = viewModel::openCardMenu,
        onDismissCardMenu = viewModel::dismissCardMenu,
        onUnlink = viewModel::unlinkFromFamilies,
        onDeletePerson = viewModel::deletePerson,
        onCardAction = { action ->
            // Every action closes the sheet first: leaving it open behind a new screen
            // means finding it still there on the way back.
            viewModel.dismissCardMenu()
            when (action) {
                is CardAction.OpenProfile -> onOpenPerson(action.personId)
                is CardAction.Recentre -> viewModel.recenterOn(action.gedcomId)
                is CardAction.OpenFamily -> onOpenFamily(action.familyId)
                is CardAction.Edit -> onEditPerson(action.personId)
                is CardAction.AddRelative -> onAddRelative(action.personId, action.relation)
                is CardAction.LinkExisting -> onPickPersonToLink(action.personId, action.relation)
            }
        },
        modifier = modifier,
    )
}

/** The picker's answer, on its way back to the diagram. */
data class PendingLink(
    val pivotPersonId: Long,
    val otherPersonId: Long,
    val relation: Relation,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiagramScreen(
    uiState: DiagramUiState,
    snackbarHostState: SnackbarHostState,
    onExport: (ExportFormat) -> Unit,
    onLayoutReady: (DiagramLayout) -> Unit,
    onOpenPerson: (String) -> Unit,
    onOpenFamily: (String) -> Unit,
    onRecenter: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onCardMenu: (String) -> Unit,
    onDismissCardMenu: () -> Unit,
    onCardAction: (CardAction) -> Unit,
    onUnlink: (Long) -> Unit,
    onDeletePerson: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var exportMenu by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diagram_title)) },
                actions = {
                    Box {
                        IconButton(
                            onClick = { exportMenu = true },
                            enabled = uiState.session != null,
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = stringResource(R.string.export_diagram),
                            )
                        }
                        DropdownMenu(
                            expanded = exportMenu,
                            onDismissRequest = { exportMenu = false },
                        ) {
                            ExportFormat.entries.forEach { format ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(format.labelRes())) },
                                    onClick = {
                                        exportMenu = false
                                        onExport(format)
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.diagram_settings),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when {
                uiState.loading -> FullScreenLoading()

                uiState.error != null -> EmptyState(
                    icon = Icons.Outlined.ErrorOutline,
                    title = stringResource(R.string.diagram_failed),
                    description = uiState.error,
                )

                uiState.isEmpty || uiState.session == null -> EmptyState(
                    icon = Icons.Outlined.AccountTree,
                    title = stringResource(R.string.diagram_empty_title),
                    description = stringResource(R.string.diagram_empty_description),
                )

                else -> DiagramCanvas(
                    uiState = uiState,
                    onOpenPerson = onOpenPerson,
                    onOpenFamily = onOpenFamily,
                    onRecenter = onRecenter,
                    onCardMenu = onCardMenu,
                    onLayoutReady = onLayoutReady,
                )
            }

            uiState.cardMenu?.let { target ->
                CardMenuSheet(
                    target = target,
                    onDismiss = onDismissCardMenu,
                    onAction = onCardAction,
                    onUnlink = onUnlink,
                    onDelete = onDeletePerson,
                )
            }
        }
    }
}

@Composable
private fun DiagramCanvas(
    uiState: DiagramUiState,
    onOpenPerson: (String) -> Unit,
    onOpenFamily: (String) -> Unit,
    onRecenter: (String) -> Unit,
    onCardMenu: (String) -> Unit,
    onLayoutReady: (DiagramLayout) -> Unit,
) {
    val session = uiState.session ?: return
    val density = LocalDensity.current

    // The drawn size is only known after the engine has placed everything, so the
    // viewport is told about it as the diagram reports back.
    var layout by remember(session) { mutableStateOf<DiagramLayout?>(null) }

    val contentSize = layout?.let {
        with(density) { IntSize(it.width.dp.roundToPx(), it.height.dp.roundToPx()) }
    } ?: IntSize.Zero

    val focus = layout?.fulcrumCentre()?.let { centre ->
        with(density) { Offset(centre.x.dp.toPx(), centre.y.dp.toPx()) }
    }

    PannableZoomable(contentSize = contentSize, focusOn = focus) { scale, offset ->
        DiagramView(
            session = session,
            scale = scale,
            offset = offset,
            peopleById = uiState.peopleById,
            portraits = uiState.portraits,
            onOpenPerson = onOpenPerson,
            onCardMenu = onCardMenu,
            onOpenFamily = onOpenFamily,
            onRecenter = onRecenter,
            onLayoutReady = {
                layout = it
                onLayoutReady(it)
            },
        )
    }
}

private fun ExportFormat.labelRes(): Int = when (this) {
    ExportFormat.PNG -> R.string.export_png
    ExportFormat.PDF -> R.string.export_pdf
    ExportFormat.SVG -> R.string.export_svg
}

/** `yyyyMMdd-HHmmss`; no colons, because they are not legal in a file name everywhere. */
private val ExportStamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

/**
 * `family-diagram-20260816-180542.png`.
 *
 * Stamped with the time so that no two exports ever ask for a name that is already taken.
 * When one is, the Storage Access Framework has to make the name unique itself, and where
 * it puts the counter is not something the app gets a say in — the file arrives as
 * `family-diagram.png (1)`, with the suffix past the extension, and then nothing will open
 * it because nothing can tell what it is any more. A name that is unique to begin with
 * never reaches that code, and it sorts the exports by date into the bargain.
 */
private fun exportFileName(format: ExportFormat): String =
    "family-diagram-${LocalDateTime.now().format(ExportStamp)}.${format.extension}"

private data class ExportRequest(val format: ExportFormat, val fileName: String)

/**
 * `ACTION_CREATE_DOCUMENT` with the MIME type carried in the request rather than fixed
 * when the launcher is registered.
 *
 * AndroidX's `CreateDocument` takes its type once, at registration, which left a screen
 * that writes both a PNG and a PDF declaring the wildcard type for both. That is not a
 * cosmetic inaccuracy: the picker uses the type to reconcile the name it was given with
 * the file it creates, and given a wildcard it treats `.png` as part of the name rather
 * than as an extension. The document is then written with no type anything recognises, so
 * it does not appear among a gallery's images or a file browser's PDFs even when it is
 * sitting in the folder.
 */
private class CreateExportDocument : ActivityResultContract<ExportRequest, Uri?>() {

    override fun createIntent(context: Context, input: ExportRequest): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.format.mimeType)
            .putExtra(Intent.EXTRA_TITLE, input.fileName)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent.takeIf { resultCode == Activity.RESULT_OK }?.data
}
