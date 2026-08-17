package com.familytree.core.diagram

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.familytree.core.database.FamilyTreeDatabase
import com.familytree.core.gedcom.GedcomImporter
import com.familytree.core.gedcom.GedcomProjector
import com.familytree.core.model.DiagramSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Proves the whole chain before any drawing exists: a GEDCOM file goes into Room, comes
 * back out as a model, and the layout engine turns it into coordinates.
 *
 * Worth testing on its own because the engine is a binary with no source — if a future
 * version changes behaviour, this is where it shows up rather than in a blank screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DiagramEngineTest {

    private lateinit var database: FamilyTreeDatabase
    private lateinit var projector: GedcomProjector
    private lateinit var importer: GedcomImporter
    private val engine = DiagramEngine()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FamilyTreeDatabase::class.java,
        ).allowMainThreadQueries().build()
        importer = GedcomImporter(database, Dispatchers.Unconfined)
        projector = GedcomProjector(database, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() = database.close()

    private fun fixture(name: String = "roundtrip.ged"): File {
        val text = checkNotNull(javaClass.classLoader?.getResourceAsStream(name))
            .bufferedReader().readText()
        return File.createTempFile("diagram", ".ged").apply { writeText(text) }
    }

    private suspend fun session(
        settings: DiagramSettings = DiagramSettings(),
        file: String = "roundtrip.ged",
        fulcrum: String = "I1",
    ): DiagramSession {
        val treeId = importer.importFile(fixture(file), "Diagram")
        val gedcom = projector.project(treeId)
        return checkNotNull(engine.start(gedcom, fulcrum, settings)) { "Engine returned no session" }
    }

    /** A common phone density, for turning dp into the pixels a texture is measured in. */
    private val TYPICAL_DENSITY = 2.625f

    /** The smallest maximum texture size in wide use. */
    private val SAFE_TEXTURE_PX = 4096f

    /** Sizes stand in for measured cards; the engine only needs numbers, not real views. */
    private fun sizes(session: DiagramSession) =
        session.cards.map { if (it.isMini) DpSize(40f, 40f) else DpSize(200f, 80f) }

    /**
     * Everything drawn has to sit inside the size the layout reports.
     *
     * The viewport scales and clamps by that size, so anything outside it cannot be
     * reached: it is drawn beyond the edge and no amount of panning brings it back. A
     * three-person tree never showed this, because there is nowhere for the engine to
     * put a card except in front of you.
     */
    @Test
    fun `every card sits inside the reported bounds`() = runTest {
        val session = session(file = "generations.ged", fulcrum = "I20")
        val layout = session.place(sizes(session))

        val leftmost = layout.cards.minOf { it.x }
        val topmost = layout.cards.minOf { it.y }
        val rightmost = layout.cards.maxOf { it.x + it.width }
        val bottommost = layout.cards.maxOf { it.y + it.height }

        assertTrue("Cards start left of the origin at $leftmost", leftmost >= 0f)
        assertTrue("Cards start above the origin at $topmost", topmost >= 0f)
        assertTrue("Cards reach $rightmost beyond the width ${layout.width}", rightmost <= layout.width + 1f)
        assertTrue("Cards reach $bottommost beyond the height ${layout.height}", bottommost <= layout.height + 1f)
    }

    /**
     * A real tree is wider than a graphics layer is allowed to be.
     *
     * This is a canary, not a rule about the engine: it records the measurement behind the
     * way the viewport is built. A five-generation family lays out around 6000 px across,
     * and a render node past roughly 4096 px draws *nothing at all* — silently, with no
     * error. That is why the pan and zoom are applied to each card and to the connector
     * canvas separately instead of to one layer wrapped around the whole diagram.
     *
     * If this ever stops holding, the per-piece transform is still correct; but anyone
     * tempted to simplify it back to a single layer should read this first.
     */
    @Test
    fun `a real tree is wider than one graphics layer may be`() = runTest {
        val session = session(file = "generations.ged", fulcrum = "I1")
        val layout = session.place(sizes(session))

        val widthPx = layout.width * TYPICAL_DENSITY
        assertTrue(
            "Layout is ${layout.width}dp = ${widthPx}px, which no longer exceeds the texture limit",
            widthPx > SAFE_TEXTURE_PX,
        )
    }

    @Test
    fun `the fulcrum and their family become cards`() = runTest {
        val session = session()

        assertTrue("No cards produced", session.cards.isNotEmpty())
        val fulcrum = session.cards.filter { it.isFulcrum }
        assertEquals("Exactly one card is the fulcrum", 1, fulcrum.size)
        assertEquals("I1", fulcrum.single().personGedcomId)

        // Ahmet's wife and son must both appear with the default settings.
        val ids = session.cards.mapNotNull { it.personGedcomId }.toSet()
        assertTrue("Wife missing from $ids", "I2" in ids)
        assertTrue("Child missing from $ids", "I3" in ids)
    }

    @Test
    fun `placing produces a sized layout with positioned cards`() = runTest {
        val session = session()
        val layout = session.place(sizes(session))

        assertTrue("Layout has no width", layout.width > 0f)
        assertTrue("Layout has no height", layout.height > 0f)
        assertEquals(session.cards.size, layout.cards.size)

        // Every card must sit inside the reported bounds, or panning would lose it.
        layout.cards.forEach { placed ->
            assertTrue("${placed.card.personGedcomId} is left of the canvas", placed.x >= -1f)
            assertTrue("${placed.card.personGedcomId} is above the canvas", placed.y >= -1f)
            assertTrue(
                "${placed.card.personGedcomId} overflows the width",
                placed.x + placed.width <= layout.width + 1f,
            )
        }
    }

    @Test
    fun `a married couple gets a bond and connecting lines`() = runTest {
        val session = session()
        val layout = session.place(sizes(session))

        assertTrue("No marriage bond drawn", layout.bonds.isNotEmpty())
        assertEquals("1740", layout.bonds.first().marriageYear)
        assertTrue("No connectors between cards", layout.lines.flatten().isNotEmpty())
    }

    @Test
    fun `narrowing the settings draws fewer people`() = runTest {
        val full = session().let { it.place(sizes(it)) }

        // Ancestors 0 also forces siblings and cousins to 0, so only the fulcrum's own
        // family should remain.
        val narrowed = session(
            DiagramSettings(ancestors = 0, descendants = 0, showSpouses = false),
        ).let { it.place(sizes(it)) }

        assertTrue(
            "Expected fewer cards than ${full.cards.size}, got ${narrowed.cards.size}",
            narrowed.cards.size < full.cards.size,
        )
    }

    @Test
    fun `an unknown fulcrum yields no session rather than crashing`() = runTest {
        val treeId = importer.importFile(fixture(), "Diagram")
        val gedcom = projector.project(treeId)
        assertNull(engine.start(gedcom, "I999", DiagramSettings()))
    }

    /**
     * Guards the handshake the engine requires: without a bitmap limit it silently
     * returns no connectors, which looks like a rendering bug rather than a missing call.
     */
    @Test
    fun `connectors depend on the bitmap limit being declared`() = runTest {
        val session = session()
        val layout = session.place(sizes(session), maxBitmapSize = 4096f)
        assertTrue("Declaring the limit should produce connectors", layout.lines.flatten().isNotEmpty())
    }

    @Test
    fun `card sizes are handed back to the engine`() = runTest {
        val session = session()
        val given = session.cards.map { DpSize(160f, 90f) }
        val layout = session.place(given)

        // The engine lays out around the sizes it was told, so they must survive intact.
        assertNotNull(layout.cards.firstOrNull())
        layout.cards.forEach { placed ->
            assertEquals(160f, placed.width, 0.01f)
            assertEquals(90f, placed.height, 0.01f)
        }
    }
}
