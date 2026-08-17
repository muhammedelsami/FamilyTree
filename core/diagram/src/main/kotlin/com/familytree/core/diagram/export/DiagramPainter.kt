package com.familytree.core.diagram.export

import android.graphics.Paint
import android.graphics.Typeface
import com.familytree.core.diagram.DuplicateSegment
import com.familytree.core.diagram.LineSegment

/**
 * The surface a diagram export draws onto.
 *
 * [DiagramExporter] owns the geometry, the palette and the typography and knows nothing
 * about the file being written; a painter owns the file format and knows nothing about
 * family trees. The split exists because the drawing routine was already duplicated once
 * — Compose on screen, Android canvas for export — and a third format would have made it
 * three.
 *
 * **Every coordinate is in dp**, the unit the layout engine speaks. A raster painter
 * scales on the way out ([CanvasPainter]); a vector one writes the numbers as they arrive
 * ([SvgPainter]). The margin around the diagram is the painter's business too, so callers
 * draw in the engine's own coordinates and never mention it.
 */
internal interface DiagramPainter {

    /** Fills the whole surface, margin included, before anything else is drawn. */
    fun background(colour: Int)

    /** One group of connectors as a single stroked path, exactly as the engine grouped them. */
    fun path(segments: List<LineSegment>, colour: Int, width: Float)

    /** The quadratic joining two appearances of the same person. */
    fun curve(segment: DuplicateSegment, colour: Int, width: Float)

    fun roundRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        fill: Int,
        stroke: Int,
        strokeWidth: Float,
    )

    /** Centred horizontally on [centreX]; [baseline] is a text baseline, not a top edge. */
    fun text(value: String, centreX: Float, baseline: Float, style: TextStyle)

    fun circle(centreX: Float, centreY: Float, radius: Float, colour: Int)

    /** The width [value] would occupy, so the exporter can trim a name to its card. */
    fun measure(value: String, style: TextStyle): Float

    /** Closes whatever the painter opened. Nothing may be drawn afterwards. */
    fun finish()
}

/** @param size in dp — the painter is responsible for turning that into device units. */
internal data class TextStyle(val size: Float, val colour: Int, val bold: Boolean = false)

/**
 * Both painters measure through this, so a name is trimmed to the same point whichever
 * format it is exported to — even though only one of them draws with the result.
 */
internal fun TextStyle.toPaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = colour
    textAlign = Paint.Align.CENTER
    textSize = size
    if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
}
