package com.familytrees.app.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.familytree.feature.backup.BackupRoute
import com.familytree.feature.settings.SettingsRoute
import com.familytree.feature.share.CompareRoute
import com.familytree.feature.share.ShareLaunchers
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familytree.feature.diagram.DiagramSettingsRoute
import com.familytree.feature.diagram.PendingLink
import com.familytree.feature.family.FamilyDetailRoute
import com.familytree.feature.media.CropImageRoute
import com.familytree.feature.media.MediaDetailRoute
import com.familytree.core.model.OwnerType
import com.familytree.feature.media.MediaFoldersRoute
import com.familytree.feature.media.OwnerMediaTab
import com.familytree.feature.media.ProvideMediaContext
import com.familytree.core.model.Relation
import com.familytree.feature.person.PersonEditorRoute
import com.familytree.feature.person.PersonPickerRoute
import com.familytree.feature.person.ProfileRoute
import com.familytree.feature.trees.NewTreeRoute
import com.familytree.feature.trees.TreesRoute

/** Key the picker uses to hand its choice back to the screen that opened it. */
private const val LINK_RESULT = "link-result"

@Composable
fun FamilyTreeNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Route.Trees,
        modifier = modifier.fillMaxSize(),
    ) {
        composable<Route.Trees> {
            // The share pickers wrap the list rather than sitting on a screen of their
            // own: sharing is one decision followed by the system file chooser, and a
            // page with a single button in front of that would only be in the way.
            var shareTarget by remember { mutableStateOf<Pair<Long, String>?>(null) }
            val target = shareTarget

            ShareLaunchers(
                treeId = target?.first ?: 0L,
                treeTitle = target?.second.orEmpty(),
                onShared = { shareTarget = null },
                onFailed = { shareTarget = null },
                onReceived = { incoming, origin ->
                    shareTarget = null
                    // With no tree here to compare against, the arrival is simply a new
                    // tree and there is nothing to review.
                    if (origin != null) {
                        navController.navigate(Route.Compare(origin, incoming))
                    } else {
                        navController.navigate(Route.Tree(incoming))
                    }
                },
            ) { startShare, startReceive ->
                LaunchedEffect(target) { if (target != null) startShare() }

                TreesRoute(
                    onOpenTree = { treeId -> navController.navigate(Route.Tree(treeId)) },
                    onNewTree = { navController.navigate(Route.NewTree) },
                    onOpenSettings = { navController.navigate(Route.Settings) },
                    onOpenBackups = { treeId -> navController.navigate(Route.Backups(treeId)) },
                    onShareTree = { treeId, title -> shareTarget = treeId to title },
                    onReceiveShare = startReceive,
                )
            }
        }

        composable<Route.NewTree> {
            NewTreeRoute(
                onTreeReady = { treeId ->
                    // Replace this screen so back from the tree returns to the list,
                    // not to the picker the user has already finished with.
                    navController.navigate(Route.Tree(treeId)) {
                        popUpTo(Route.NewTree) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable<Route.Tree> { entry ->
            val route = entry.toRoute<Route.Tree>()
            // The picker hands its answer back through this entry's saved state, which
            // is what survives the trip to another screen and back.
            val linkResult by entry.savedStateHandle
                .getStateFlow<List<Long>?>(LINK_RESULT, null)
                .collectAsStateWithLifecycle()
            val pendingLink = linkResult?.takeIf { it.size == 3 }?.let { (pivot, other, relation) ->
                PendingLink(pivot, other, Relation.entries[relation.toInt()])
            }

            // Portraits are drawn in every section of the shell, so the resolver is
            // provided around the whole thing rather than by each screen that shows one.
            ProvideMediaContext(route.treeId) {
                TreeShell(
                    treeId = route.treeId,
                    onOpenPerson = { personId -> navController.navigate(Route.Profile(personId, route.treeId)) },
                    onAddPerson = { navController.navigate(Route.PersonEditor(route.treeId)) },
                    onOpenFamily = { familyId -> navController.navigate(Route.Family(familyId, route.treeId)) },
                    onOpenDiagramSettings = { navController.navigate(Route.DiagramSettings) },
                    onOpenMedia = { mediaId -> navController.navigate(Route.MediaDetail(mediaId, route.treeId)) },
                    onOpenMediaFolders = { navController.navigate(Route.MediaFolders(route.treeId)) },
                    onEditPerson = { personId ->
                        navController.navigate(Route.PersonEditor(route.treeId, personId = personId))
                    },
                    onAddRelative = { pivotId, relation ->
                        navController.navigate(
                            Route.PersonEditor(
                                treeId = route.treeId,
                                pivotPersonId = pivotId,
                                relation = relation,
                            ),
                        )
                    },
                    onPickPersonToLink = { pivotId, relation ->
                        navController.navigate(
                            Route.PersonPicker(route.treeId, pivotId, relation.name),
                        )
                    },
                    pendingLink = pendingLink,
                    onPendingLinkHandled = { entry.savedStateHandle[LINK_RESULT] = null },
                )
            }
        }

        composable<Route.Profile> { entry ->
            val route = entry.toRoute<Route.Profile>()
            ProvideMediaContext(route.treeId) {
            ProfileRoute(
                personId = route.personId,
                onBack = { navController.popBackStack() },
                onEdit = { personId ->
                    navController.navigate(Route.PersonEditor(treeId = route.treeId, personId = personId))
                },
                onOpenPerson = { personId ->
                    navController.navigate(Route.Profile(personId, route.treeId))
                },
                onAddRelative = { pivotId, relation ->
                    navController.navigate(
                        Route.PersonEditor(
                            treeId = route.treeId,
                            pivotPersonId = pivotId,
                            relation = relation,
                        ),
                    )
                },
                mediaTab = { personId ->
                    OwnerMediaTab(
                        treeId = route.treeId,
                        ownerType = OwnerType.PERSON,
                        ownerId = personId,
                        onOpenMedia = { mediaId ->
                            navController.navigate(Route.MediaDetail(mediaId, route.treeId))
                        },
                        onCropMedia = { mediaId ->
                            navController.navigate(Route.CropImage(mediaId, route.treeId))
                        },
                    )
                },
            )
            }
        }

        composable<Route.PersonEditor> { entry ->
            val route = entry.toRoute<Route.PersonEditor>()
            PersonEditorRoute(
                treeId = route.treeId,
                personId = route.personId,
                pivotPersonId = route.pivotPersonId,
                relation = route.relation,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        composable<Route.Family> { entry ->
            val route = entry.toRoute<Route.Family>()
            FamilyDetailRoute(
                familyId = route.familyId,
                treeId = route.treeId,
                onBack = { navController.popBackStack() },
                onOpenPerson = { personId ->
                    navController.navigate(Route.Profile(personId, route.treeId))
                },
            )
        }

        composable<Route.MediaDetail> { entry ->
            val route = entry.toRoute<Route.MediaDetail>()
            ProvideMediaContext(route.treeId) {
                MediaDetailRoute(
                    treeId = route.treeId,
                    mediaId = route.mediaId,
                    onBack = { navController.popBackStack() },
                    onCrop = { mediaId -> navController.navigate(Route.CropImage(mediaId, route.treeId)) },
                )
            }
        }

        composable<Route.CropImage> { entry ->
            val route = entry.toRoute<Route.CropImage>()
            CropImageRoute(
                treeId = route.treeId,
                mediaId = route.mediaId,
                onDone = { navController.popBackStack() },
            )
        }

        composable<Route.MediaFolders> { entry ->
            val route = entry.toRoute<Route.MediaFolders>()
            MediaFoldersRoute(
                treeId = route.treeId,
                onBack = { navController.popBackStack() },
            )
        }

        composable<Route.DiagramSettings> {
            DiagramSettingsRoute(onBack = { navController.popBackStack() })
        }

        composable<Route.Backups> { entry ->
            val route = entry.toRoute<Route.Backups>()
            BackupRoute(
                treeId = route.treeId,
                onBack = { navController.popBackStack() },
                onOpenTree = { treeId ->
                    navController.navigate(Route.Tree(treeId)) {
                        popUpTo(Route.Trees)
                    }
                },
            )
        }

        composable<Route.PersonPicker> { entry ->
            val route = entry.toRoute<Route.PersonPicker>()
            // The picker lists people, and a list of people shows portraits everywhere
            // else in the application; without this it would be the one place that does not.
            ProvideMediaContext(route.treeId) {
            PersonPickerRoute(
                treeId = route.treeId,
                excludePersonId = route.pivotPersonId,
                onBack = { navController.popBackStack() },
                onPicked = { pickedId ->
                    // Handed back to the screen that asked, rather than linked here: the
                    // navigation graph should not know how two people are joined.
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(
                            LINK_RESULT,
                            listOf(route.pivotPersonId, pickedId, Relation.valueOf(route.relation).ordinal),
                        )
                    navController.popBackStack()
                },
            )
            }
        }

        composable<Route.Compare> { entry ->
            val route = entry.toRoute<Route.Compare>()
            CompareRoute(
                localTreeId = route.localTreeId,
                incomingTreeId = route.incomingTreeId,
                onBack = { navController.popBackStack() },
                onDone = {
                    navController.navigate(Route.Trees) {
                        popUpTo(Route.Trees) { inclusive = true }
                    }
                },
            )
        }

        composable<Route.Settings> {
            SettingsRoute(onBack = { navController.popBackStack() })
        }
    }
}
