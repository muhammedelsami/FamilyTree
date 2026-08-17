package com.familytree.core.diagram.export

import android.graphics.Paint
import com.familytree.core.diagram.DuplicateSegment
import com.familytree.core.diagram.LineSegment
import kotlin.math.roundToLong

/**
 * Writes the diagram as SVG.
 *
 * The reason is not sharpness — the PDF export is vector too — but size. A PDF page is
 * capped at 14400 points by the format, and a raster export needs the whole bitmap in
 * memory at once, which is why [DiagramExporter.exportPng] has to shrink and retry. This
 * painter streams text straight to the file, so what it costs does not depend on how
 * large the tree is and it has no dimension limit at all. That the result opens in
 * Inkscape and in a browser is a bonus.
 *
 * **Text stays text**, so it can be selected, searched and restyled — but that also means
 * the renderer picks the font, and its metrics will not be exactly the ones [measure]
 * reports here. A name trimmed to fit its card can therefore sit a little wide or narrow
 * in another program. Drawing the glyphs as paths would fix that and cost the very thing
 * SVG is being added for.
 *
 * @param margin dp of empty space around the diagram, matching the other exports.
 */
internal class SvgPainter(
    private val out: Appendable,
    width: Float,
    height: Float,
    private val margin: Float,
) : DiagramPainter {

    private val fullWidth = width + margin * 2
    private val fullHeight = height + margin * 2
    private val textPaints = mutableMapOf<TextStyle, Paint>()

    init {
        out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        out.append("<svg")
            .attribute("xmlns", "http://www.w3.org/2000/svg")
            .attribute("width", fullWidth)
            .attribute("height", fullHeight)
        out.append(" viewBox=\"0 0 ").number(fullWidth).append(' ').number(fullHeight).append("\">\n")
        // One group carries the margin, so everything below is written in the engine's own
        // coordinates — the same numbers the canvas painter is handed.
        out.append("<g transform=\"translate(").number(margin).append(' ').number(margin)
        out.append(")\">\n")
    }

    override fun background(colour: Int) {
        out.append("<rect")
            .attribute("x", -margin)
            .attribute("y", -margin)
            .attribute("width", fullWidth)
            .attribute("height", fullHeight)
            .colourAttribute("fill", colour)
            .append("/>\n")
    }

    override fun path(segments: List<LineSegment>, colour: Int, width: Float) {
        if (segments.isEmpty()) return
        out.append("<path d=\"")
        segments.forEach { segment ->
            out.append('M').number(segment.x1).append(' ').number(segment.y1)
            if (segment.curved) {
                // The same two control points the canvas painter hands to `cubicTo`.
                out.append('C').number(segment.x1).append(' ').number(segment.y2)
                out.append(' ').number(segment.x2).append(' ').number(segment.y1)
                out.append(' ').number(segment.x2).append(' ').number(segment.y2)
            } else {
                out.append('L').number(segment.x2).append(' ').number(segment.y2)
            }
        }
        out.append('"').strokeOnly(colour, width).append("/>\n")
    }

    override fun curve(segment: DuplicateSegment, colour: Int, width: Float) {
        out.append("<path d=\"M").number(segment.x1).append(' ').number(segment.y1)
        out.append('Q').number(segment.controlX).append(' ').number(segment.controlY)
        out.append(' ').number(segment.x2).append(' ').number(segment.y2)
        out.append('"').strokeOnly(colour, width).append("/>\n")
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
        out.append("<rect")
            .attribute("x", x)
            .attribute("y", y)
            .attribute("width", width)
            .attribute("height", height)
            .attribute("rx", radius)
            .colourAttribute("fill", fill)
            .colourAttribute("stroke", stroke)
            .attribute("stroke-width", strokeWidth)
            .append("/>\n")
    }

    override fun text(value: String, centreX: Float, baseline: Float, style: TextStyle) {
        out.append("<text")
            .attribute("x", centreX)
            .attribute("y", baseline)
            .attribute("text-anchor", "middle")
            .attribute("font-family", "sans-serif")
            .attribute("font-size", style.size)
        if (style.bold) out.attribute("font-weight", "bold")
        out.colourAttribute("fill", style.colour).append('>')
        out.escaped(value)
        out.append("</text>\n")
    }

    override fun circle(centreX: Float, centreY: Float, radius: Float, colour: Int) {
        out.append("<circle")
            .attribute("cx", centreX)
            .attribute("cy", centreY)
            .attribute("r", radius)
            .colourAttribute("fill", colour)
            .append("/>\n")
    }

    /**
     * Measured with the platform's own text engine rather than anything SVG-specific, so
     * a name is trimmed at the same character it would be for a PNG or a PDF of the same
     * tree.
     */
    override fun measure(value: String, style: TextStyle): Float =
        textPaints.getOrPut(style) { style.toPaint() }.measureText(value)

    override fun finish() {
        out.append("</g>\n</svg>\n")
    }

    // No `stroke-linecap`: SVG's default is the butt cap, which is what an Android `Paint`
    // and the on-screen Compose renderer both use. Rounding the ends here would make this
    // one format quietly disagree with the other two.
    private fun Appendable.strokeOnly(colour: Int, width: Float): Appendable =
        attribute("fill", "none")
            .colourAttribute("stroke", colour)
            .attribute("stroke-width", width)
}

private fun Appendable.attribute(name: String, value: Float): Appendable {
    append(' ').append(name).append("=\"").number(value).append('"')
    return this
}

private fun Appendable.attribute(name: String, value: String): Appendable {
    append(' ').append(name).append("=\"").append(value).append('"')
    return this
}

private fun Appendable.colourAttribute(name: String, value: Int): Appendable {
    append(' ').append(name).append("=\"").colour(value).append('"')
    return this
}

/**
 * Two decimals at most, and never a locale's decimal separator.
 *
 * This app runs in Turkish and Arabic, where the default-locale `String.format` writes
 * `1,5` — in SVG that is two numbers, and a single one of them corrupts the path it
 * appears in. Hand-formatting sidesteps the question entirely, and dropping trailing
 * zeroes keeps a large tree's file appreciably smaller.
 */
private fun Appendable.number(value: Float): Appendable {
    var hundredths = (value * 100f).roundToLong()
    if (hundredths < 0) {
        append('-')
        hundredths = -hundredths
    }
    append((hundredths / 100).toString())
    val fraction = (hundredths % 100).toInt()
    if (fraction != 0) {
        append('.')
        append('0' + fraction / 10)
        if (fraction % 10 != 0) append('0' + fraction % 10)
    }
    return this
}

/** `#rrggbb`; the export palette is fully opaque, so the alpha byte is dropped. */
private fun Appendable.colour(value: Int): Appendable {
    append('#')
    for (shift in intArrayOf(20, 16, 12, 8, 4, 0)) append(HEX[(value shr shift) and 0xF])
    return this
}

private const val HEX = "0123456789abcdef"

/**
 * XML-escapes a person's name.
 *
 * Control characters are dropped rather than escaped: XML 1.0 has no way to represent
 * them, and a GEDCOM file carrying one in a name would otherwise produce a document no
 * renderer will open at all.
 */
private fun Appendable.escaped(value: String): Appendable {
    value.forEach { character ->
        when {
            character == '&' -> append("&amp;")
            character == '<' -> append("&lt;")
            character == '>' -> append("&gt;")
            character == '"' -> append("&quot;")
            character < ' ' && character != '\t' -> Unit
            else -> append(character)
        }
    }
    return this
}
