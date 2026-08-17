package com.familytrees.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Diversity3
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.familytrees.app.R
import com.familytree.core.model.Relation
import com.familytree.feature.diagram.DiagramRoute
import com.familytree.feature.diagram.PendingLink
import com.familytree.feature.family.FamilyListRoute
import com.familytree.feature.media.GalleryRoute
import com.familytree.feature.person.PersonListRoute

/** The sections of an open tree. */
private enum class TreeSection(val labelRes: Int, val icon: ImageVector) {
    Diagram(R.string.section_diagram, Icons.Outlined.AccountTree),
    People(R.string.section_people, Icons.Outlined.Group),
    Families(R.string.section_families, Icons.Outlined.Diversity3),
    Media(R.string.section_media, Icons.Outlined.PhotoLibrary),
}

/**
 * The shell around an open tree.
 *
 * [NavigationSuiteScaffold] picks the navigation form for the window itself — a bottom
 * bar on a phone, a rail on a tablet, a drawer when there is room — so the sections do
 * not have to be laid out twice.
 */
@Composable
fun TreeShell(
    treeId: Long,
    onOpenPerson: (Long) -> Unit,
    onAddPerson: () -> Unit,
    onOpenFamily: (Long) -> Unit,
    onOpenDiagramSettings: () -> Unit,
    onOpenMedia: (Long) -> Unit,
    onOpenMediaFolders: () -> Unit,
    onEditPerson: (Long) -> Unit,
    onAddRelative: (pivotPersonId: Long, relation: Relation) -> Unit,
    onPickPersonToLink: (pivotPersonId: Long, relation: Relation) -> Unit,
    pendingLink: PendingLink? = null,
    onPendingLinkHandled: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // The diagram is what an open tree is for, so it is what opens; saved so rotation
    // does not send the user back to it after they moved on.
    var section by rememberSaveable { mutableStateOf(TreeSection.Diagram) }

    NavigationSuiteScaffold(
        modifier = modifier,
        navigationSuiteItems = {
            TreeSection.entries.forEach { entry ->
                item(
                    selected = entry == section,
                    onClick = { section = entry },
                    icon = { Icon(entry.icon, contentDescription = null) },
                    label = { Text(stringResource(entry.labelRes)) },
                )
            }
        },
    ) {
        when (section) {
            TreeSection.People -> PersonListRoute(
                treeId = treeId,
                onOpenPerson = onOpenPerson,
                onAddPerson = onAddPerson,
            )

            TreeSection.Diagram -> DiagramRoute(
                treeId = treeId,
                onOpenPerson = onOpenPerson,
                onOpenFamily = onOpenFamily,
                onOpenSettings = onOpenDiagramSettings,
                onEditPerson = onEditPerson,
                onAddRelative = onAddRelative,
                onPickPersonToLink = onPickPersonToLink,
                pendingLink = pendingLink,
                onPendingLinkHandled = onPendingLinkHandled,
            )

            TreeSection.Families -> FamilyListRoute(treeId = treeId, onOpenFamily = onOpenFamily)

            TreeSection.Media -> GalleryRoute(
                treeId = treeId,
                onOpenMedia = onOpenMedia,
                onOpenFolders = onOpenMediaFolders,
            )
        }
    }
}
