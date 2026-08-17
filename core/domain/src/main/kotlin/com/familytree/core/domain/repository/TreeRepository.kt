package com.familytree.core.domain.repository

import com.familytree.core.model.MediaFolder
import com.familytree.core.model.Tree
import com.familytree.core.model.TreeGrade
import com.familytree.core.model.TreeIssue
import kotlinx.coroutines.flow.Flow

/**
 * Tree-level operations.
 *
 * Deliberately free of Android types — a file to import arrives as an already-opened
 * stream provider, not a `Uri`, so this layer stays unit-testable on the JVM.
 */
interface TreeRepository {

    fun observeTrees(): Flow<List<Tree>>

    fun observeTree(treeId: Long): Flow<Tree?>

    suspend fun getTree(treeId: Long): Tree?

    suspend fun createTree(title: String): Long

    suspend fun renameTree(treeId: Long, title: String)

    suspend fun deleteTree(treeId: Long)

    /** Persists a manual reordering of the tree list. */
    suspend fun reorderTrees(orderedIds: List<Long>)

    suspend fun setRootPerson(treeId: Long, personId: Long?)

    suspend fun setGrade(treeId: Long, grade: TreeGrade)

    /** Recomputes person/family/media counts and the generation span. */
    suspend fun refreshCounters(treeId: Long)

    /**
     * Picks a root when the file did not name one, trying, in order: the `_ROOT`
     * header tag (Family Historian), `_HOME` (Ahnenblatt), the lowest numeric id,
     * then simply the first person.
     */
    suspend fun findRootPersonId(treeId: Long): Long?

    /** Structural problems worth showing the user before anything is changed. */
    suspend fun findIssues(treeId: Long): List<TreeIssue>

    /** Fixes what can be fixed automatically and reports what it did. */
    suspend fun repairIssues(treeId: Long): List<TreeIssue>

    fun observeMediaFolders(treeId: Long): Flow<List<MediaFolder>>

    suspend fun addMediaFolder(folder: MediaFolder): Long

    suspend fun removeMediaFolder(folderId: Long)
}
