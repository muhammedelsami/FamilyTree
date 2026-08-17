package com.familytree.core.model

/**
 * The seven GEDCOM top-level record types, each with its conventional ID prefix.
 *
 * FamilyGem used the same prefixes (`U.newID`), so trees exported by either app
 * stay recognisable to the other.
 */
enum class RecordType(val gedcomTag: String, val idPrefix: String) {
    PERSON("INDI", "I"),
    FAMILY("FAM", "F"),
    NOTE("NOTE", "T"),
    MEDIA("OBJE", "M"),
    SOURCE("SOUR", "S"),
    REPOSITORY("REPO", "R"),
    SUBMITTER("SUBM", "U"),
}

/**
 * Identifies what a polymorphic link (a note, a media object, a source citation,
 * an event, an extension…) is attached to.
 *
 * GEDCOM is a graph, not a tree: a note can hang off a source citation that hangs
 * off a name that hangs off a person. Rather than one nullable FK column per possible
 * parent, links carry an [OwnerType] plus the owner's row id.
 */
enum class OwnerType {
    PERSON,
    FAMILY,
    NAME,
    EVENT,
    NOTE,
    MEDIA,
    SOURCE,
    SOURCE_CITATION,
    REPOSITORY,
    REPOSITORY_REF,
    SUBMITTER,
    HEADER,
    ADDRESS,
}
