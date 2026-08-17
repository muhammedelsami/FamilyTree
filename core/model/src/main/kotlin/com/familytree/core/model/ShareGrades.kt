package com.familytree.core.model

/**
 * How a tree's sharing grade moves as it is sent out and comes back.
 *
 * The grade is the app's memory of where a tree has been. It matters because two copies of
 * the same family look identical, and without this the app could not tell "the tree I sent
 * my cousin, now returned with her additions" from "a tree I have never seen".
 *
 * ```
 *   ORIGINAL ──send──▶ SHARED ──all submitters marked──▶ ORIGINAL
 *      │
 *      └──received elsewhere──▶ RECEIVED ──proved derived──▶ DERIVED ──updates taken──▶ EXHAUSTED
 * ```
 *
 * Two rules are worth stating because they are easy to get backwards, and getting them
 * backwards silently misjudges every later share:
 *
 * * A [RECEIVED] tree can never become [ORIGINAL] again. It was somebody else's work
 *   first, and pretending otherwise loses that.
 * * Only a [RECEIVED] tree can become [DERIVED]. A tree that arrives without a matching
 *   origin here is simply new.
 */
object ShareGrades {

    /** The grade after a tree has been sent out for sharing. */
    fun afterSharing(current: TreeGrade): TreeGrade = when (current) {
        // An original stays an original once its submitters are marked; the intermediate
        // state exists only to remember that marking is still owed.
        TreeGrade.ORIGINAL -> TreeGrade.SHARED
        else -> current
    }

    /** Sending is finished and every submitter has been marked as passed. */
    fun afterSubmittersMarked(current: TreeGrade): TreeGrade =
        if (current == TreeGrade.SHARED) TreeGrade.ORIGINAL else current

    /**
     * The grade for a tree arriving from someone else.
     *
     * @param matchesExistingTree whether a tree here shares its origin, which is what
     *   makes the arrival an update rather than a stranger.
     */
    fun onArrival(matchesExistingTree: Boolean): TreeGrade =
        if (matchesExistingTree) TreeGrade.DERIVED else TreeGrade.RECEIVED

    /**
     * Where a returning copy stands once it has been compared with its origin.
     *
     * A copy with nothing new in it is [EXHAUSTED] straight away — it has already given
     * everything it had, and saying so is what lets the user delete it without wondering.
     */
    fun afterComparison(current: TreeGrade, hasUpdates: Boolean): TreeGrade = when {
        !hasUpdates -> TreeGrade.EXHAUSTED
        current == TreeGrade.RECEIVED -> TreeGrade.DERIVED
        else -> current
    }

    /** Every update has been taken out of a returning copy. */
    fun afterUpdatesApplied(current: TreeGrade): TreeGrade = when (current) {
        TreeGrade.DERIVED, TreeGrade.RECEIVED -> TreeGrade.EXHAUSTED
        else -> current
    }

    /** True when a tree holds nothing the user still needs, so deleting it is safe. */
    fun isDisposable(grade: TreeGrade): Boolean = grade == TreeGrade.EXHAUSTED
}
