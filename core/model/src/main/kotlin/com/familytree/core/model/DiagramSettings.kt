package com.familytree.core.model

/**
 * How much of the tree the diagram draws around the fulcrum person.
 *
 * These map one-to-one onto the `graph.gedcom.Graph` builder calls, and the defaults
 * match FamilyGem's so an existing user sees the same diagram they are used to.
 */
data class DiagramSettings(
    /** Generations of direct ancestors. */
    val ancestors: Int = 3,
    /** Generations of great-uncles/aunts. Never exceeds [ancestors]. */
    val greatUncles: Int = 2,
    /** Generations of descendants. */
    val descendants: Int = 3,
    /** Generations of siblings and their descendants. */
    val siblingsNephews: Int = 2,
    /** Generations of uncles/aunts and cousins. */
    val unclesCousins: Int = 1,
    /** Draw spouses next to each person. */
    val showSpouses: Boolean = true,
    /** Show the small counters on ancestry/progeny mini-cards. */
    val showNumbers: Boolean = true,
    /** Draw connectors between repeated appearances of the same person. */
    val showDuplicateLines: Boolean = false,
) {
    companion object {
        /**
         * The slider positions map onto these values rather than 0..9 directly —
         * users need fine control at the low end and coarse jumps at the high end.
         */
        val STEPS = intArrayOf(0, 1, 2, 3, 4, 5, 10, 20, 50, 100)

        /** Slider position (0..9) for a stored value. */
        fun toSliderPosition(value: Int): Int =
            STEPS.indexOfFirst { it >= value }.takeIf { it >= 0 } ?: STEPS.lastIndex

        /** Stored value for a slider position (0..9). */
        fun fromSliderPosition(position: Int): Int =
            STEPS[position.coerceIn(0, STEPS.lastIndex)]
    }

    /**
     * Applies the interdependencies the original enforced through slider listeners,
     * so an inconsistent combination can never be persisted.
     */
    fun normalised(): DiagramSettings {
        var result = copy(greatUncles = minOf(greatUncles, ancestors))
        if (result.ancestors == 0) {
            result = result.copy(siblingsNephews = 0, unclesCousins = 0)
        }
        if (result.unclesCousins > 0 && result.greatUncles == 0) {
            result = result.copy(greatUncles = 1)
        }
        return result
    }
}
