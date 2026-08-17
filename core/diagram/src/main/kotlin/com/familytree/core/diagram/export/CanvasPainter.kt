package com.familytree.core.diagram.export

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.familytree.core.diagram.DuplicateSegment
import com.familytree.core.diagram.LineSegment

/**
 * Draws onto an Android canvas — which is what both a bitmap and a PDF page are, so this
 * one painter serves the PNG and the PDF export alike. The only difference between them
 * is [scale].
 *
 * @param scale device units per dp. The canvas matrix carries it, so stroke widths and
 *   text sizes scale with the geometry and nothing below has to multiply anything.
 * @param margin dp of empty space around the diagram. Applied here as a translation, so
 *   the caller draws in the engine's coordinates; the surface must already have been made
 *   twice this much larger in each direction.
 */
internal class CanvasPainter(
    private val canvas: Canvas,
    scale: Float,
    margin: Float,
) : DiagramPainter {

    init {
        canvas.scale(scale, scale)
        canvas.translate(margin, margin)
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaints = mutableMapOf<TextStyle, Paint>()

    // `drawColor` ignores the matrix, so this covers the margin the translation above
    // moved the origin past.
    override fun background(colour: Int) = canvas.drawColor(colour)

    override fun path(segments: List<LineSegment>, colour: Int, width: Float) {
        val path = Path()
        segments.forEach { segment ->
            path.moveTo(segment.x1, segment.y1)
            if (segment.curved) {
                path.cubicTo(segment.x1, segment.y2, segment.x2, segment.y1, segment.x2, segment.y2)
            } else {
                path.lineTo(segment.x2, segment.y2)
            }
        }
        canvas.drawPath(path, strokeWith(colour, width))
    }

    override fun curve(segment: DuplicateSegment, colour: Int, width: Float) {
        val path = Path().apply {
            moveTo(segment.x1, segment.y1)
            quadTo(segment.controlX, segment.controlY, segment.x2, segment.y2)
        }
        canvas.drawPath(path, strokeWith(colour, width))
    }

    override fun roundRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        fill: Int,
        stroke: Int,
        strokeWidth: Float,
    ) {
        fillPaint.color = fill
        canvas.drawRoundRect(x, y, x + width, y + height, radius, radius, fillPaint)
        canvas.drawRoundRect(x, y, x + width, y + height, radius, radius, strokeWith(stroke, strokeWidth))
    }

    override fun text(value: String, centreX: Float, baseline: Float, style: TextStyle) {
        canvas.drawText(value, centreX, baseline, paintFor(style))
    }

    override fun circle(centreX: Float, centreY: Float, radius: Float, colour: Int) {
        fillPaint.color = colour
        canvas.drawCircle(centreX, centreY, radius, fillPaint)
    }

    override fun measure(value: String, style: TextStyle): Float = paintFor(style).measureText(value)

    override fun finish() = Unit

    private fun strokeWith(colour: Int, width: Float): Paint = strokePaint.apply {
        color = colour
        strokeWidth = width
    }

    private fun paintFor(style: TextStyle): Paint = textPaints.getOrPut(style) { style.toPaint() }
}
