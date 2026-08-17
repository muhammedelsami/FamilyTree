package com.familytree.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The grade decides whether a returning tree counts as an update or as a stranger, and
 * nothing on screen shows it. A wrong transition is therefore invisible until someone's
 * additions are silently treated as a whole new tree, or their own work is treated as
 * somebody else's.
 */
class ShareGradesTest {

    @Test
    fun `sending an original marks it as awaiting its submitters`() {
        assertEquals(TreeGrade.SHARED, ShareGrades.afterSharing(TreeGrade.ORIGINAL))
        assertEquals(TreeGrade.ORIGINAL, ShareGrades.afterSubmittersMarked(TreeGrade.SHARED))
    }

    @Test
    fun `sending a received tree does not change what it is`() {
        // Passing somebody else's tree along does not make it yours.
        assertEquals(TreeGrade.RECEIVED, ShareGrades.afterSharing(TreeGrade.RECEIVED))
        assertEquals(TreeGrade.DERIVED, ShareGrades.afterSharing(TreeGrade.DERIVED))
    }

    @Test
    fun `an arrival is derived only when it matches a tree already here`() {
        assertEquals(TreeGrade.DERIVED, ShareGrades.onArrival(matchesExistingTree = true))
        assertEquals(TreeGrade.RECEIVED, ShareGrades.onArrival(matchesExistingTree = false))
    }

    @Test
    fun `a returning copy with nothing new is exhausted straight away`() {
        // Saying so is what lets the user delete it without wondering what they might lose.
        assertEquals(TreeGrade.EXHAUSTED, ShareGrades.afterComparison(TreeGrade.RECEIVED, hasUpdates = false))
        assertEquals(TreeGrade.EXHAUSTED, ShareGrades.afterComparison(TreeGrade.DERIVED, hasUpdates = false))
    }

    @Test
    fun `a returning copy with updates becomes derived`() {
        assertEquals(TreeGrade.DERIVED, ShareGrades.afterComparison(TreeGrade.RECEIVED, hasUpdates = true))
    }

    @Test
    fun `taking the updates out exhausts the copy`() {
        assertEquals(TreeGrade.EXHAUSTED, ShareGrades.afterUpdatesApplied(TreeGrade.DERIVED))
        assertEquals(TreeGrade.EXHAUSTED, ShareGrades.afterUpdatesApplied(TreeGrade.RECEIVED))
    }

    @Test
    fun `an original never becomes exhausted`() {
        // Exhausted means "safe to delete". Applying that to the user's own tree would
        // invite them to delete their life's work.
        assertEquals(TreeGrade.ORIGINAL, ShareGrades.afterUpdatesApplied(TreeGrade.ORIGINAL))
        assertEquals(TreeGrade.ORIGINAL, ShareGrades.afterComparison(TreeGrade.ORIGINAL, hasUpdates = true))
    }

    @Test
    fun `only an exhausted tree is offered for deletion`() {
        assertTrue(ShareGrades.isDisposable(TreeGrade.EXHAUSTED))
        listOf(TreeGrade.ORIGINAL, TreeGrade.SHARED, TreeGrade.RECEIVED, TreeGrade.DERIVED)
            .forEach { assertFalse(it.name, ShareGrades.isDisposable(it)) }
    }

    @Test
    fun `the numeric values match the original application`() {
        // Archives are interchangeable with Family Gem, so these numbers are a wire
        // format and not an implementation detail.
        assertEquals(0, TreeGrade.ORIGINAL.value)
        assertEquals(9, TreeGrade.SHARED.value)
        assertEquals(10, TreeGrade.RECEIVED.value)
        assertEquals(20, TreeGrade.DERIVED.value)
        assertEquals(30, TreeGrade.EXHAUSTED.value)
        assertEquals(TreeGrade.DERIVED, TreeGrade.fromValue(20))
        // An unknown number is treated as an original rather than refused.
        assertEquals(TreeGrade.ORIGINAL, TreeGrade.fromValue(99))
    }
}
