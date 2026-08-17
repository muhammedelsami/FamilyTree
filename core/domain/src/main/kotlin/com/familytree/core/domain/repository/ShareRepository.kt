package com.familytree.core.domain.repository

import com.familytree.core.model.TreeComparison

/**
 * Sending a tree to somebody and taking back what they added.
 *
 * The flow is deliberately three steps — receive, compare, confirm — rather than one
 * "import updates" button. Someone else's changes to your family's records are not
 * something to apply unseen: the review step is the feature.
 */
interface ShareRepository {

    /** Writes a shareable archive and marks the tree as sent. */
    suspend fun share(treeId: Long, uri: String): Result<Unit>

    /**
     * Takes in a received archive as a tree of its own.
     *
     * Kept separate rather than merged on arrival, so the comparison has two real trees to
     * work with and the user can walk away without having changed anything.
     */
    suspend fun receive(uri: String, fallbackTitle: String): Result<ReceivedShare>

    /** Works out what the received copy has that the tree it came from does not. */
    suspend fun compare(localTreeId: Long, incomingTreeId: Long): Result<TreeComparison>

    /** Applies the accepted differences and marks the received copy as spent. */
    suspend fun applyUpdates(comparison: TreeComparison): Result<AppliedUpdates>

    /** Trees this one could have come from, matched by shared origin. */
    suspend fun candidateOrigins(incomingTreeId: Long): Result<List<Long>>
}

data class ReceivedShare(
    val treeId: Long,
    val title: String,
    /** The tree here it appears to be a copy of, when there is one. */
    val originTreeId: Long?,
)

data class AppliedUpdates(val added: Int, val updated: Int, val removed: Int) {
    val total: Int get() = added + updated + removed
}
