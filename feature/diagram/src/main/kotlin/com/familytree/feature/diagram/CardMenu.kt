package com.familytree.feature.diagram

import com.familytree.core.model.Relation

/** One family a card's person belongs to, ready to be listed in the menu. */
data class CardFamily(
    val familyId: Long,
    /** The other people in it, so the entry reads "with Ayşe" rather than "family F3". */
    val label: String,
)

/**
 * Everything the long-press menu needs about one card.
 *
 * Assembled when the menu opens rather than held for every card on screen: a diagram can
 * show hundreds of people, and their families are three queries each.
 */
data class CardMenuTarget(
    val personId: Long,
    val gedcomId: String,
    val name: String,
    val isFulcrum: Boolean,
    /** Families this person is a child in. */
    val parentFamilies: List<CardFamily> = emptyList(),
    /** Families this person is a spouse in. */
    val spouseFamilies: List<CardFamily> = emptyList(),
    /** False in a tree of one person: there is nobody to link to. */
    val canLinkExisting: Boolean = false,
) {
    val hasFamilies: Boolean get() = parentFamilies.isNotEmpty() || spouseFamilies.isNotEmpty()
}

/**
 * What the user asked for, handed up to the screen.
 *
 * The menu itself does no navigating: the diagram lives inside a shell that owns the
 * navigation graph, and an action that needs another screen has to travel up to it.
 */
sealed interface CardAction {
    data class OpenProfile(val personId: Long) : CardAction
    data class Recentre(val gedcomId: String) : CardAction
    data class OpenFamily(val familyId: Long) : CardAction
    data class Edit(val personId: Long) : CardAction
    data class AddRelative(val personId: Long, val relation: Relation) : CardAction
    data class LinkExisting(val personId: Long, val relation: Relation) : CardAction
}
