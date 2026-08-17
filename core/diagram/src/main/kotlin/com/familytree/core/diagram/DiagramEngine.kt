package com.familytree.core.diagram

import com.familytree.core.model.DiagramSettings
import com.familytree.core.model.Sex
import graph.gedcom.CurveLine
import graph.gedcom.DuplicateLine
import graph.gedcom.Graph
import graph.gedcom.Line
import graph.gedcom.Util
import org.folg.gedcom.model.Gedcom
import javax.inject.Inject

/**
 * Wraps Family Gem's `gedcomgraph` layout engine.
 *
 * The engine is pure Java and knows nothing about Android: it takes a GEDCOM model and
 * returns positions in **dp**. Everything here is about feeding it and reading the
 * result — no drawing, so the layout can be unit-tested and reused for export.
 *
 * The engine needs two passes, because it cannot place a card before it knows how big
 * that card is:
 *
 * 1. [start] builds the sub-graph around the fulcrum and lists the cards to measure.
 * 2. [DiagramSession.place] takes the measured sizes back and produces the layout.
 */
class DiagramEngine @Inject constructor() {

    fun start(
        gedcom: Gedcom,
        fulcrumGedcomId: String,
        settings: DiagramSettings,
        leftToRight: Boolean = true,
        whichFamily: Int = 0,
    ): DiagramSession? {
        val fulcrum = gedcom.getPerson(fulcrumGedcomId) ?: return null
        val normalised = settings.normalised()

        val graph = Graph()
            .setGedcom(gedcom)
            .setLayoutDirection(leftToRight)
            .maxAncestors(normalised.ancestors)
            .maxGreatUncles(normalised.greatUncles)
            .maxDescendants(normalised.descendants)
            .maxSiblingsNephews(normalised.siblingsNephews)
            .maxUnclesCousins(normalised.unclesCousins)
            .displaySpouses(normalised.showSpouses)
            .displayNumbers(normalised.showNumbers)
            .displayDuplicateLines(normalised.showDuplicateLines)
            .showFamily(whichFamily)

        graph.startFrom(fulcrum)
        return DiagramSession(graph)
    }
}

/**
 * One diagram being laid out, between measuring and placing.
 *
 * Not reusable: a new one is built whenever the fulcrum or the settings change.
 */
class DiagramSession internal constructor(private val graph: Graph) {

    /** The cards to measure, in the order their sizes must be handed back. */
    val cards: List<DiagramCard> = graph.personNodes.mapIndexed { index, node ->
        DiagramCard(
            index = index,
            personGedcomId = node.person?.id,
            isMini = node.mini,
            // A mini card stands for a whole branch that is not drawn; the number is how
            // many people it hides.
            hiddenCount = node.amount,
            isAcquired = node.acquired,
            isDeceased = node.dead,
            isFulcrum = node.isFulcrumNode,
            isDuplicate = node.duplicate,
            generation = node.generation,
        )
    }

    /**
     * @param sizes card sizes **in dp**, parallel to [cards].
     * @param maxBitmapSize the largest path the canvas can draw, in dp.
     *
     * Note on [maxBitmapSize]: when the engine reports `needMaxBitmapSize()` it will
     * emit **no connector lines at all** until it is told the limit — it is a required
     * handshake, not an optimisation for large trees. Skipping it produces a diagram of
     * cards floating with nothing joining them, which is why this parameter has a
     * default rather than being nullable.
     */
    fun place(sizes: List<DpSize>, maxBitmapSize: Float = DEFAULT_MAX_BITMAP_DP): DiagramLayout {
        require(sizes.size == graph.personNodes.size) {
            "Expected ${graph.personNodes.size} card sizes, received ${sizes.size}"
        }
        graph.personNodes.forEachIndexed { index, node ->
            node.width = sizes[index].width
            node.height = sizes[index].height
        }

        graph.initNodes()
        if (graph.needMaxBitmapSize()) {
            graph.setMaxBitmapSize(maxBitmapSize)
        }
        graph.placeNodes()

        return DiagramLayout(
            width = graph.width,
            height = graph.height,
            cards = graph.personNodes.mapIndexed { index, node ->
                PlacedCard(
                    card = cards[index],
                    x = node.x,
                    y = node.y,
                    width = node.width,
                    height = node.height,
                    familyGedcomId = node.spouseFamily?.id,
                )
            },
            bonds = graph.bonds.map { bond ->
                PlacedBond(
                    x = bond.x,
                    y = bond.y,
                    width = bond.width,
                    height = bond.height,
                    // The engine pads the year for its own drawing; callers want the value.
                    marriageYear = bond.marriageYear()?.trim()?.takeIf { it.isNotEmpty() },
                    familyGedcomId = bond.familyNode?.spouseFamily?.id,
                    isMini = bond.width <= Util.MINI_BOND_WIDTH.toFloat(),
                )
            },
            lines = graph.lines.toSegments(),
            backLines = graph.backLines.toSegments(),
            duplicateLines = graph.duplicateLines.map { it.toSegment() },
            // Only meaningful once the engine has decided it needs to scale.
            biggestPathSize = graph.biggestPathSize,
            maxBitmapSize = graph.maxBitmapSize,
        )
    }

    companion object {
        /**
         * A conservative stand-in for the canvas limit, used until the real one is known.
         *
         * Devices report between 4096 and 16384 pixels; the smaller figure is safe
         * because overstating it is what makes paths vanish.
         */
        const val DEFAULT_MAX_BITMAP_DP = 4096f
    }
}

/** A card before it has a position: everything needed to render and measure it. */
data class DiagramCard(
    val index: Int,
    val personGedcomId: String?,
    /** A placeholder standing for ancestors or descendants that are not drawn. */
    val isMini: Boolean,
    val hiddenCount: Int,
    /** Married into the family rather than born into it. */
    val isAcquired: Boolean,
    val isDeceased: Boolean,
    val isFulcrum: Boolean,
    /** The same person appears more than once in this layout. */
    val isDuplicate: Boolean,
    val generation: Int,
)

data class DpSize(val width: Float, val height: Float)

data class PlacedCard(
    val card: DiagramCard,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val familyGedcomId: String?,
)

/** The marriage marker drawn between two partners. */
data class PlacedBond(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val marriageYear: String?,
    val familyGedcomId: String?,
    val isMini: Boolean,
)

/**
 * A connector between cards.
 *
 * [curved] segments are drawn as a cubic through the two control points the original
 * used; straight ones as a plain line.
 */
data class LineSegment(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val curved: Boolean,
)

/** Links two appearances of the same person, coloured by their sex. */
data class DuplicateSegment(
    val x1: Float,
    val y1: Float,
    val controlX: Float,
    val controlY: Float,
    val x2: Float,
    val y2: Float,
    val sex: Sex,
)

/**
 * Empty space kept around the whole diagram, in dp.
 *
 * The engine lays a tree out flush to its own bounding box, so without this the outermost
 * cards end at the exact edge of whatever the diagram is drawn into — the screen, or the
 * page — and the tree reads as cut off rather than finished. The original reserved the same
 * margin by padding the layout that held the cards.
 *
 * One number for every surface: the viewport pads its scrollable area by it, and the PNG
 * and PDF exports grow their canvas by twice it and draw offset into the middle. They have
 * to agree, or a diagram will not look on paper the way it looked on screen.
 */
const val DIAGRAM_MARGIN_DP = 48f

data class DiagramLayout(
    val width: Float,
    val height: Float,
    val cards: List<PlacedCard>,
    val bonds: List<PlacedBond>,
    /** Grouped as the engine groups them, so each group can share one path. */
    val lines: List<List<LineSegment>>,
    val backLines: List<List<LineSegment>>,
    val duplicateLines: List<DuplicateSegment>,
    val biggestPathSize: Float,
    val maxBitmapSize: Float,
) {
    /**
     * Whether the drawing has to be scaled down to fit the hardware bitmap limit.
     *
     * Without this, a large tree's connectors are silently dropped by the platform —
     * the tree appears with cards but no lines between them.
     */
    val needsPathScaling: Boolean get() = maxBitmapSize > 0f && biggestPathSize > maxBitmapSize

    val pathScale: Float
        get() = if (needsPathScaling) maxBitmapSize / biggestPathSize else 1f
}

private fun List<Set<Line>>.toSegments(): List<List<LineSegment>> = map { group ->
    group.map { line ->
        LineSegment(
            x1 = line.x1,
            y1 = line.y1,
            x2 = line.x2,
            y2 = line.y2,
            curved = line is CurveLine,
        )
    }
}

private fun DuplicateLine.toSegment() = DuplicateSegment(
    x1 = x1,
    y1 = y1,
    controlX = x3,
    controlY = y3,
    x2 = x2,
    y2 = y2,
    sex = when (gender) {
        Util.Gender.MALE -> Sex.MALE
        Util.Gender.FEMALE -> Sex.FEMALE
        else -> Sex.UNDEFINED
    },
)
