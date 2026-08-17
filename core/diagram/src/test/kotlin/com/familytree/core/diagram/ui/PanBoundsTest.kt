package com.familytree.core.diagram.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Records where a dragged diagram is allowed to end up, which is the original's rule and not
 * the one that sounds right. There is no UI test anywhere in this project, so a gesture that
 * regresses regresses in silence — this is the only thing standing in the way.
 */
class PanBoundsTest {

    private val viewport = 1080f

    /** Bigger than the screen: the diagram covers it, no edge wanders inside. */
    @Test
    fun `content larger than the viewport comes to rest against its own edges`() {
        val scaled = 6300f

        assertEquals(viewport - scaled, clampAxis(-9999f, viewport, scaled), 0.01f)
        assertEquals(0f, clampAxis(9999f, viewport, scaled), 0.01f)
    }

    /** Smaller than the screen: one resting position, the middle. */
    @Test
    fun `content smaller than the viewport comes to rest centred`() {
        val scaled = 400f
        val centred = (viewport - scaled) / 2f

        assertEquals(centred, clampAxis(-9999f, viewport, scaled), 0.01f)
        assertEquals(centred, clampAxis(9999f, viewport, scaled), 0.01f)
    }

    /**
     * The complaint that started this: a small diagram ignored the finger completely. It has
     * nowhere to go and still has to move, so the drag overruns its resting position and the
     * release brings it back.
     */
    @Test
    fun `a diagram with nowhere to go still follows the finger`() {
        val scaled = 400f
        val centred = (viewport - scaled) / 2f
        val overrun = viewport / 4f

        assertEquals(centred - overrun, clampAxis(-9999f, viewport, scaled, overrun), 0.01f)
        assertEquals(centred + overrun, clampAxis(9999f, viewport, scaled, overrun), 0.01f)
    }

    /** Zoomed to fit, the diagram matches the viewport exactly — the same dead end, sideways. */
    @Test
    fun `content exactly filling the viewport overruns in both directions`() {
        val overrun = viewport / 4f

        assertTrue(clampAxis(-9999f, viewport, viewport, overrun) < 0f)
        assertTrue(clampAxis(9999f, viewport, viewport, overrun) > 0f)
    }

    /** The overrun is a loan, not a position: settling passes zero and the give disappears. */
    @Test
    fun `settling ignores the overrun`() {
        listOf(400f, 1080f, 6300f).forEach { scaled ->
            val dragged = clampAxis(9999f, viewport, scaled, overrun = viewport / 4f)
            val settled = clampAxis(dragged, viewport, scaled)

            assertTrue("content $scaled settled to $settled", settled < dragged)
            assertEquals(clampAxis(9999f, viewport, scaled), settled, 0.01f)
        }
    }
}
