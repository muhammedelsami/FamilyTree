package com.familytree.core.diagram.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.familytree.core.designsystem.theme.FtTheme
import com.familytree.core.diagram.DiagramLayout
import com.familytree.core.diagram.DiagramSession
import com.familytree.core.diagram.DpSize
import com.familytree.core.diagram.DuplicateSegment
import com.familytree.core.diagram.LineSegment
import com.familytree.core.model.MediaObject
import com.familytree.core.model.PersonSummary
import com.familytree.core.model.Sex

/**
 * Draws a laid-out family diagram.
 *
 * The engine cannot place a card until it knows how big that card is, and a card's width
 * depends on the name inside it — so this runs in two passes: an invisible pass that
 * measures the cards, then the real one that draws them where the engine put them.
 *
 * The original solved the same problem by measuring Android views and then polling every
 * 100 ms until the portrait images had loaded. Here the cards have no images to wait for,
 * so one subcomposition replaces the polling loop entirely.
 */
@Composable
fun DiagramView(
    session: DiagramSession,
    /** How much the viewport has zoomed out; applied here rather than to a wrapper layer. */
    scale: Float,
    /** Where the viewport has panned to, in pixels. */
    offset: Offset,
    peopleById: Map<String, PersonSummary>,
    portraits: Map<String, MediaObject> = emptyMap(),
    onOpenPerson: (String) -> Unit,
    onCardMenu: (String) -> Unit,
    onOpenFamily: (String) -> Unit,
    onRecenter: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Reports the placed layout, so a viewport can size and centre itself on it. */
    onLayoutReady: (DiagramLayout) -> Unit = {},
) {
    val density = LocalDensity.current
    // Devices report a maximum drawable bitmap between 4096 and 16384 pixels; the smaller
    // figure is the safe assumption, since overstating it is what makes paths vanish.
    val maxBitmapDp = remember(density) { (4096f / density.density) - 10f }

    // Computed once per session: the engine mutates its own state as it places nodes, so
    // it must not be re-run on every layout pass.
    var layout by remember(session) { mutableStateOf<DiagramLayout?>(null) }

    val placed = layout
    if (placed == null) {
        CardMeasurer(session, peopleById, portraits) { sizes ->
            layout = session.place(sizes, maxBitmapDp).also(onLayoutReady)
        }
    } else {
        DiagramContent(
            layout = placed,
            scale = scale,
            offset = offset,
            peopleById = peopleById,
            portraits = portraits,
            onOpenPerson = onOpenPerson,
            onCardMenu = onCardMenu,
            onOpenFamily = onOpenFamily,
            onRecenter = onRecenter,
            modifier = modifier,
        )
    }
}

/**
 * Measures the cards without showing them, then reports their sizes in dp.
 *
 * Occupies no space, so nothing flickers between the two passes.
 */
@Composable
private fun CardMeasurer(
    session: DiagramSession,
    peopleById: Map<String, PersonSummary>,
    portraits: Map<String, MediaObject> = emptyMap(),
    onMeasured: (List<DpSize>) -> Unit,
) {
    SubcomposeLayout { _ ->
        val measurables = subcompose(Unit) {
            session.cards.forEach { card ->
                if (card.isMini) {
                    DiagramMiniCard(card = card, onClick = {})
                } else {
                    DiagramPersonCard(
                        card = card,
                        person = card.personGedcomId?.let(peopleById::get),
                        // The portrait has to be here too: a card measured without its
                        // picture would be reported shorter than it draws, and the engine
                        // would place the rows overlapping.
                        portrait = card.personGedcomId?.let(portraits::get),
                        onClick = {},
                        onLongClick = {},
                    )
                }
            }
        }
        // Unbounded, so each card reports the size its content actually wants.
        val sizes = measurables.map { measurable ->
            val placeable = measurable.measure(Constraints())
            DpSize(
                width = with(this) { placeable.width.toDp().value },
                height = with(this) { placeable.height.toDp().value },
            )
        }
        onMeasured(sizes)
        layout(0, 0) {}
    }
}

@Composable
private fun DiagramContent(
    layout: DiagramLayout,
    scale: Float,
    offset: Offset,
    peopleById: Map<String, PersonSummary>,
    portraits: Map<String, MediaObject> = emptyMap(),
    onOpenPerson: (String) -> Unit,
    onCardMenu: (String) -> Unit,
    onOpenFamily: (String) -> Unit,
    onRecenter: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Reports the placed layout, so a viewport can size and centre itself on it. */
    onLayoutReady: (DiagramLayout) -> Unit = {},
) {
    val density = LocalDensity.current

    Layout(
        modifier = modifier.fillMaxSize(),
        content = {
            DiagramConnectors(layout, scale, offset, Modifier.fillMaxSize())
            // Each card carries its own small layer for the zoom. Small is the point:
            // one layer per card stays far below the texture limit that a single layer
            // over the whole diagram would blow past.
            layout.cards.forEach { placed ->
                val id = placed.card.personGedcomId
                Box(Modifier.zoomed(scale)) {
                    if (placed.card.isMini) {
                        DiagramMiniCard(
                            card = placed.card,
                            onClick = { id?.let(onRecenter) },
                        )
                    } else {
                        DiagramPersonCard(
                            card = placed.card,
                            person = id?.let(peopleById::get),
                            portrait = id?.let(portraits::get),
                            onClick = { id?.let(onOpenPerson) },
                            onLongClick = { id?.let(onCardMenu) },
                        )
                    }
                }
            }
            layout.bonds.forEach { bond ->
                Box(Modifier.zoomed(scale)) {
                    DiagramBondMarker(
                        marriageYear = bond.marriageYear,
                        isMini = bond.isMini,
                        onClick = { bond.familyGedcomId?.let(onOpenFamily) },
                    )
                }
            }
        },
    ) { measurables, constraints ->
        // The node is the size of the viewport, not of the diagram: the zoom is applied
        // to each piece as it is placed.
        val viewportWidth = constraints.maxWidth
        val viewportHeight = constraints.maxHeight

        val connectors = measurables.first()
            .measure(Constraints.fixed(viewportWidth, viewportHeight))

        val cardStart = 1
        val bondStart = cardStart + layout.cards.size

        // Cards are pinned to the exact size the engine was told about, so the drawing
        // and the connectors cannot drift apart.
        val cards = layout.cards.mapIndexed { index, placed ->
            measurables[cardStart + index].measure(
                Constraints.fixed(
                    width = with(density) { placed.width.dp.roundToPx() },
                    height = with(density) { placed.height.dp.roundToPx() },
                ),
            )
        }
        val bonds = layout.bonds.indices.map { index ->
            measurables[bondStart + index].measure(Constraints())
        }

        layout(viewportWidth, viewportHeight) {
            connectors.place(0, 0)
            cards.forEachIndexed { index, placeable ->
                val card = layout.cards[index]
                placeable.place(
                    x = (with(density) { card.x.dp.toPx() } * scale + offset.x).roundToInt(),
                    y = (with(density) { card.y.dp.toPx() } * scale + offset.y).roundToInt(),
                )
            }
            bonds.forEachIndexed { index, placeable ->
                val bond = layout.bonds[index]
                // Centred on the engine's slot: the marker's natural size rarely matches
                // the space reserved for it, and the lines meet at the centre.
                val centreX = with(density) { (bond.x + bond.width / 2f).dp.toPx() } * scale + offset.x
                val centreY = with(density) { (bond.y + bond.height / 2f).dp.toPx() } * scale + offset.y
                placeable.place(
                    x = centreX.roundToInt() - placeable.width / 2,
                    y = centreY.roundToInt() - placeable.height / 2,
                )
            }
        }
    }
}

/**
 * All the connectors in one canvas.
 *
 * Drawn as paths per engine group rather than per segment: the engine already groups
 * lines that belong to the same branch, and one path per group is far cheaper than one
 * per line on a tree with thousands of connectors.
 */
@Composable
private fun DiagramConnectors(
    layout: DiagramLayout,
    scale: Float,
    offset: Offset,
    modifier: Modifier = Modifier,
) {
    val colors = FtTheme.genealogyColors
    val density = LocalDensity.current

    Canvas(modifier) {
        // Strokes are drawn at their true width and then scaled with everything else, so
        // a line does not fatten as the diagram is zoomed out.
        val stroke = with(density) { 2.dp.toPx() } / scale
        val dash = with(density) { 4.dp.toPx() } / scale

        // The whole drawing is transformed here rather than by a wrapping layer: a layer
        // covering the entire diagram would exceed the maximum texture size and render
        // nothing. This canvas is only ever the size of the screen.
        withTransform({
            translate(offset.x, offset.y)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            // Back lines run behind the cards to reach a repeated ancestor; dashing them
            // tells the reader they are a reference, not a direct descent.
            layout.backLines.forEach { group ->
                drawPath(
                    path = group.toPath(density),
                    color = colors.diagramBackLine,
                    style = Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
                    ),
                )
            }
            layout.lines.forEach { group ->
                drawPath(
                    path = group.toPath(density),
                    color = colors.diagramLine,
                    style = Stroke(width = stroke),
                )
            }
            layout.duplicateLines.forEach { segment ->
                drawPath(
                    path = segment.toPath(density),
                    color = when (segment.sex) {
                        Sex.MALE -> colors.duplicateLineMale
                        Sex.FEMALE -> colors.duplicateLineFemale
                        else -> colors.duplicateLineUndefined
                    },
                    style = Stroke(width = stroke),
                )
            }
        }
    }
}

private fun List<LineSegment>.toPath(density: Density): Path = Path().also { path ->
    forEach { segment ->
        with(density) {
            val x1 = segment.x1.dp.toPx()
            val y1 = segment.y1.dp.toPx()
            val x2 = segment.x2.dp.toPx()
            val y2 = segment.y2.dp.toPx()
            path.moveTo(x1, y1)
            if (segment.curved) {
                // The S-curve the original used: control points swap the y values, so the
                // line leaves its origin vertically and arrives vertically.
                path.cubicTo(x1, y2, x2, y1, x2, y2)
            } else {
                path.lineTo(x2, y2)
            }
        }
    }
}

private fun DuplicateSegment.toPath(density: Density): Path = Path().also { path ->
    with(density) {
        path.moveTo(x1.dp.toPx(), y1.dp.toPx())
        path.quadraticTo(
            controlX.dp.toPx(),
            controlY.dp.toPx(),
            x2.dp.toPx(),
            y2.dp.toPx(),
        )
    }
}

/** Where the fulcrum sits, so the viewport can open centred on it. */
fun DiagramLayout.fulcrumCentre(): Offset? {
    val card = cards.firstOrNull { it.card.isFulcrum } ?: return null
    return Offset(card.x + card.width / 2f, card.y + card.height / 2f)
}


/**
 * Scales one card about its top-left corner.
 *
 * Per card rather than once for the whole diagram, because a render node bigger than the
 * GPU's maximum texture draws nothing at all — and a wide family tree is several times
 * that size before it is zoomed to fit.
 */
private fun Modifier.zoomed(scale: Float) = graphicsLayer {
    scaleX = scale
    scaleY = scale
    transformOrigin = TransformOrigin(0f, 0f)
}
