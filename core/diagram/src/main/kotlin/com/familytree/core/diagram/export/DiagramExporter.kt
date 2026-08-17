package com.familytree.core.diagram.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import com.familytree.core.common.di.Dispatcher
import com.familytree.core.common.di.FtDispatcher
import com.familytree.core.diagram.DIAGRAM_MARGIN_DP
import com.familytree.core.diagram.DiagramLayout
import com.familytree.core.model.PersonSummary
import com.familytree.core.model.Sex
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.OutputStream
import javax.inject.Inject
import kotlin.math.max

/**
 * Renders a diagram to a picture file.
 *
 * Drawn straight from the layout data rather than by capturing the on-screen composables,
 * for three reasons: one drawing routine can then serve every format; the export needs its
 * own palette because dark-theme cards are unreadable on paper; and a full-size tree is far
 * larger than the screen, so there is nothing on screen to capture in the first place.
 *
 * The geometry, the palette and the typography live here; where the marks actually go is a
 * [DiagramPainter]'s business. That is what keeps three output formats down to one copy of
 * the drawing — and card drawing already exists twice, here and in Compose, so this
 * deliberately stays simple: a rounded card, a name, a lifespan.
 *
 * **Everything below is in dp**, the units the layout engine speaks. Each painter turns
 * that into the units its format wants, and each adds the [DIAGRAM_MARGIN_DP] itself.
 */
class DiagramExporter @Inject constructor(
    @Dispatcher(FtDispatcher.Default) private val dispatcher: CoroutineDispatcher,
) {

    suspend fun exportPng(
        layout: DiagramLayout,
        people: Map<String, PersonSummary>,
        density: Float,
        output: OutputStream,
    ): Unit = withContext(dispatcher) {
        var scale = density
        // A very large tree can exceed what the heap will hold. Shrinking a little and
        // retrying is far better than failing outright, and the original did the same.
        // (The SVG export has no such ceiling, which is half the reason it exists.)
        while (true) {
            val bitmap = runCatching { createBitmap(layout, scale) }.getOrNull()
            if (bitmap == null) {
                scale *= SHRINK_STEP
                check(scale >= density * MIN_SCALE_FRACTION) { "This tree is too large to export as an image." }
                continue
            }
            draw(CanvasPainter(Canvas(bitmap), scale, DIAGRAM_MARGIN_DP), layout, people)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            return@withContext
        }
    }

    suspend fun exportPdf(
        layout: DiagramLayout,
        people: Map<String, PersonSummary>,
        output: OutputStream,
    ): Unit = withContext(dispatcher) {
        val document = PdfDocument()
        try {
            // A PDF is vector, so it is written at 1 dp = 1 point: no rasterising, and the
            // result stays sharp at any zoom. The format does cap a page at 14400 points,
            // which a tree of enough generations will pass — that is what SVG is for.
            val width = max(1, (layout.width + DIAGRAM_MARGIN_DP * 2).toInt())
            val height = max(1, (layout.height + DIAGRAM_MARGIN_DP * 2).toInt())
            val page = document.startPage(PdfDocument.PageInfo.Builder(width, height, 1).create())
            draw(CanvasPainter(page.canvas, scale = 1f, margin = DIAGRAM_MARGIN_DP), layout, people)
            document.finishPage(page)
            document.writeTo(output)
        } finally {
            document.close()
        }
    }

    /**
     * No density, and no size limit: SVG carries the layout's own dp figures as user units
     * and the renderer decides how large a pixel is. Written straight through to [output],
     * so a tree too big for the other two formats still exports.
     */
    suspend fun exportSvg(
        layout: DiagramLayout,
        people: Map<String, PersonSummary>,
        output: OutputStream,
    ): Unit = withContext(dispatcher) {
        // Deliberately not `use`: the caller owns the stream, and closing it here would
        // shut a document the Storage Access Framework is still writing through.
        val writer = output.bufferedWriter()
        draw(SvgPainter(writer, layout.width, layout.height, DIAGRAM_MARGIN_DP), layout, people)
        writer.flush()
    }

    private fun createBitmap(layout: DiagramLayout, scale: Float): Bitmap =
        Bitmap.createBitmap(
            max(1, ((layout.width + DIAGRAM_MARGIN_DP * 2) * scale).toInt()),
            max(1, ((layout.height + DIAGRAM_MARGIN_DP * 2) * scale).toInt()),
            Bitmap.Config.ARGB_8888,
        )

    private fun draw(
        painter: DiagramPainter,
        layout: DiagramLayout,
        people: Map<String, PersonSummary>,
    ) {
        // Paper first, over the whole surface including the margin. Everything after it is
        // drawn in the engine's coordinates; the painter is already offset by one margin,
        // so card, line and bond geometry never has to know about it.
        painter.background(PAPER)

        layout.backLines.forEach { group -> painter.path(group, INK_FAINT, LINE_WIDTH) }
        layout.lines.forEach { group -> painter.path(group, INK_LINE, LINE_WIDTH) }
        layout.duplicateLines.forEach { segment ->
            painter.curve(segment, segment.sex.exportColour(), LINE_WIDTH)
        }

        layout.cards.forEach { placed ->
            val person = placed.card.personGedcomId?.let(people::get)
            painter.roundRect(
                x = placed.x,
                y = placed.y,
                width = placed.width,
                height = placed.height,
                radius = CARD_RADIUS,
                fill = if (placed.card.isFulcrum) FULCRUM_FILL else CARD_FILL,
                stroke = (person?.person?.sex ?: Sex.NONE).exportColour(),
                strokeWidth = LINE_WIDTH,
            )

            val centreX = placed.x + placed.width / 2f
            val centreY = placed.y + placed.height / 2f

            if (placed.card.isMini) {
                painter.text(placed.card.hiddenCount.toString(), centreX, centreY + NAME.size / 3f, NAME)
                return@forEach
            }

            val name = person?.displayName?.takeIf { it.isNotBlank() } ?: "—"
            val lifespan = person?.exportLifespan()
            // Two lines when there are dates, one when there are not, both centred.
            val baseline = if (lifespan == null) centreY + NAME.size / 3f else centreY - 2f
            painter.text(painter.ellipsise(name, NAME, placed.width - CARD_PADDING), centreX, baseline, NAME)
            if (lifespan != null) {
                painter.text(lifespan, centreX, baseline + DATE.size + 2f, DATE)
            }
        }

        layout.bonds.forEach { bond ->
            val centreX = bond.x + bond.width / 2f
            val centreY = bond.y + bond.height / 2f
            val year = bond.marriageYear
            if (year.isNullOrBlank()) {
                painter.circle(centreX, centreY, BOND_RADIUS, INK_LINE)
            } else {
                painter.text(year, centreX, centreY + BOND.size / 3f, BOND)
            }
        }

        painter.finish()
    }

    private companion object {
        // An export is read on paper or a white background, so it gets its own palette
        // rather than inheriting whatever theme the app happens to be in.
        const val PAPER = Color.WHITE
        const val CARD_FILL = 0xFFF7F7F4.toInt()
        const val FULCRUM_FILL = 0xFFE3F1E7.toInt()
        const val INK_LINE = 0xFF6B7A70.toInt()
        const val INK_FAINT = 0xFFB4BDB6.toInt()
        const val INK_TEXT = 0xFF1B1E1C.toInt()
        const val INK_FAINT_TEXT = 0xFF5C635E.toInt()
        const val MALE_INK = 0xFF3B6EA5.toInt()
        const val FEMALE_INK = 0xFFA53B6E.toInt()
        const val NEUTRAL_INK = 0xFF7A7A7A.toInt()

        const val LINE_WIDTH = 1.5f
        const val CARD_RADIUS = 6f
        const val BOND_RADIUS = 3f

        /** Total breathing room either side of a name, before it has to be trimmed. */
        const val CARD_PADDING = 8f

        val NAME = TextStyle(size = 12f, colour = INK_TEXT, bold = true)
        val DATE = TextStyle(size = 10f, colour = INK_FAINT_TEXT)
        val BOND = TextStyle(size = 9f, colour = INK_TEXT)

        const val SHRINK_STEP = 0.9f
        const val MIN_SCALE_FRACTION = 0.2f
    }

    private fun Sex.exportColour(): Int = when (this) {
        Sex.MALE -> MALE_INK
        Sex.FEMALE -> FEMALE_INK
        else -> NEUTRAL_INK
    }
}

private fun PersonSummary.exportLifespan(): String? {
    val birth = birth?.year?.toString()
    val death = death?.year?.toString()
    return when {
        birth != null && death != null -> "$birth–$death"
        birth != null -> birth
        death != null -> "–$death"
        else -> null
    }
}

/** Trims a name to the card width, since no export can reflow text. */
private fun DiagramPainter.ellipsise(value: String, style: TextStyle, maxWidth: Float): String {
    if (measure(value, style) <= maxWidth) return value
    var end = value.length
    while (end > 1 && measure(value.substring(0, end) + "…", style) > maxWidth) end--
    return value.substring(0, end) + "…"
}
