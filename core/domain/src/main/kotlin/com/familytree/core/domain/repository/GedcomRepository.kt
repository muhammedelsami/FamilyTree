package com.familytree.core.domain.repository

/**
 * Reading and writing GEDCOM files.
 *
 * Locations are passed as opaque strings so this layer stays free of Android types;
 * the implementation resolves them through the content resolver.
 */
interface GedcomRepository {

    /** Reads a `.ged` file into a new tree and returns its id. */
    suspend fun importFrom(uri: String, title: String): Long

    /** Writes a tree out as a `.ged` file. */
    suspend fun exportTo(treeId: Long, uri: String)

    /** A sensible tree title taken from the file name, e.g. `family.ged` -> `family`. */
    fun suggestTitle(uri: String): String?
}
