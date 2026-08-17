package com.familytrees.app.navigation

import com.familytree.core.model.Relation
import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes.
 *
 * Declaring destinations as serializable types rather than string patterns means the
 * compiler checks every argument — which matters here, where almost every screen is
 * keyed by a record id and a mistyped argument would silently open the wrong person.
 */
sealed interface Route {

    @Serializable
    data object Trees : Route

    @Serializable
    data object NewTree : Route

    @Serializable
    data class Tree(val treeId: Long) : Route

    /** Carries the tree id so editing and adding relatives stay in the same tree. */
    @Serializable
    data class Profile(val personId: Long, val treeId: Long) : Route

    /**
     * @param pivotPersonId set when the new person is being added as someone's relative;
     *   the link is made only after the person exists.
     */
    @Serializable
    data class PersonEditor(
        val treeId: Long,
        val personId: Long? = null,
        val pivotPersonId: Long? = null,
        val relation: Relation? = null,
    ) : Route

    @Serializable
    data class Family(val familyId: Long, val treeId: Long) : Route

    @Serializable
    data class MediaDetail(val mediaId: Long, val treeId: Long) : Route

    @Serializable
    data class CropImage(val mediaId: Long, val treeId: Long) : Route

    @Serializable
    data class MediaFolders(val treeId: Long) : Route

    @Serializable
    data object DiagramSettings : Route

    @Serializable
    data class Backups(val treeId: Long) : Route

    @Serializable
    data class Compare(val localTreeId: Long, val incomingTreeId: Long) : Route

    /**
     * Choosing somebody already in the tree, to link them to [pivotPersonId].
     *
     * The relation travels with the route rather than being remembered elsewhere: the
     * picker can be left and returned to, and a half-remembered intention is worse than
     * none.
     */
    @Serializable
    data class PersonPicker(
        val treeId: Long,
        val pivotPersonId: Long,
        val relation: String,
    ) : Route

    @Serializable
    data object Settings : Route
}
