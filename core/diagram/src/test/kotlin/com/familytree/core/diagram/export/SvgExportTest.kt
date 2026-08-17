package com.familytree.core.diagram.export

import com.familytree.core.diagram.DIAGRAM_MARGIN_DP
import com.familytree.core.diagram.DiagramCard
import com.familytree.core.diagram.DiagramLayout
import com.familytree.core.diagram.LineSegment
import com.familytree.core.diagram.PlacedBond
import com.familytree.core.diagram.PlacedCard
import com.familytree.core.model.LifeEvent
import com.familytree.core.model.Person
import com.familytree.core.model.PersonSummary
import com.familytree.core.model.Sex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.w3c.dom.Document
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The SVG export is the only one whose output can be read back, so it is also the only
 * place the shared drawing routine can be checked at all: PNG and PDF come out as opaque
 * bytes. What is asserted here about margins and card counts holds for those two as well,
 * because all three go through the same [DiagramPainter] calls.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SvgExportTest {

    private val exporter = DiagramExporter(Dispatchers.Unconfined)
    private val defaultLocale = Locale.getDefault()

    @After
    fun tearDown() = Locale.setDefault(defaultLocale)

    @Test
    fun `wraps the diagram in the same margin as the other formats`() = runTest {
        val document = export(layout(width = 300f, height = 120f))

        val expectedWidth = 300f + DIAGRAM_MARGIN_DP * 2
        val expectedHeight = 120f + DIAGRAM_MARGIN_DP * 2
        assertEquals("svg", document.documentElement.tagName)
        assertEquals(
            "0 0 ${expectedWidth.toInt()} ${expectedHeight.toInt()}",
            document.documentElement.getAttribute("viewBox"),
        )
        // The cards are drawn in the engine's own coordinates, offset by the group.
        assertEquals(
            "translate(${DIAGRAM_MARGIN_DP.toInt()} ${DIAGRAM_MARGIN_DP.toInt()})",
            document.getElementsByTagName("g").item(0).attributes.getNamedItem("transform").nodeValue,
        )
    }

    /**
     * A tree wide enough to be refused as a PDF page, and far too wide to hold as a
     * bitmap, is written out at its true size. This is the whole reason the format exists,
     * so it is worth a test that fails loudly if a size limit ever creeps in.
     */
    @Test
    fun `writes a diagram past the PDF page limit at full size`() = runTest {
        val document = export(layout(width = 40_000f, height = 900f))

        assertTrue(
            document.documentElement.getAttribute("viewBox"),
            document.documentElement.getAttribute("viewBox").endsWith("40096 996"),
        )
    }

    /**
     * Turkish and Arabic write `1,5` for one and a half, and a locale-formatted coordinate
     * is read by an SVG renderer as two numbers — enough to corrupt every path in the file.
     * The app is exported from whichever locale the user chose, so this is not theoretical.
     */
    @Test
    fun `writes numbers with a decimal point in any locale`() = runTest {
        Locale.setDefault(Locale.forLanguageTag("tr"))

        val text = exportText(layout(width = 300f, height = 120f, cardX = 10.5f))

        assertTrue(text.take(600), "stroke-width=\"1.5\"" in text)
        assertTrue(text.take(600), "x=\"10.5\"" in text)
        assertFalse("1,5" in text)
    }

    /** An `&` in a name is ordinary; an unescaped one makes the file unopenable. */
    @Test
    fun `escapes names that are not valid XML on their own`() = runTest {
        val name = "Ali & Ayşe <Yılmaz>"

        val document = export(layout(width = 300f, height = 120f), person("I1", name))

        val texts = document.getElementsByTagName("text")
        assertEquals(name, texts.item(0).textContent)
    }

    @Test
    fun `draws one card per placed card, over a single sheet of paper`() = runTest {
        val document = export(
            layout(width = 300f, height = 120f, cards = 3),
            person("I1", "Bir"),
            person("I2", "İki"),
            person("I3", "Üç"),
        )

        // Three cards plus the background, which is a rect too.
        assertEquals(4, document.getElementsByTagName("rect").length)
        assertEquals(1, document.getElementsByTagName("path").length)
        assertEquals(1, document.getElementsByTagName("circle").length)
    }

    private suspend fun export(layout: DiagramLayout, vararg people: PersonSummary): Document {
        val text = exportText(layout, *people)
        return DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(ByteArrayInputStream(text.toByteArray()))
    }

    private suspend fun exportText(layout: DiagramLayout, vararg people: PersonSummary): String {
        val output = ByteArrayOutputStream()
        exporter.exportSvg(layout, people.associateBy { it.person.gedcomId!! }, output)
        return output.toString(Charsets.UTF_8)
    }

    private fun person(gedcomId: String, name: String) = PersonSummary(
        person = Person(id = 1L, treeId = 1L, gedcomId = gedcomId, sex = Sex.MALE),
        displayName = name,
        searchText = name.lowercase(),
        birth = LifeEvent(date = "1900", place = null, year = 1900),
        death = null,
        isDeceased = false,
        birthSortKey = 19000000,
        ageInYears = null,
        daysToNextBirthday = null,
        relativeCount = 0,
        portraitMediaId = null,
    )

    private fun layout(
        width: Float,
        height: Float,
        cards: Int = 1,
        cardX: Float = 0f,
    ) = DiagramLayout(
        width = width,
        height = height,
        cards = List(cards) { index ->
            PlacedCard(
                card = DiagramCard(
                    index = index,
                    personGedcomId = "I${index + 1}",
                    isMini = false,
                    hiddenCount = 0,
                    isAcquired = false,
                    isDeceased = false,
                    isFulcrum = index == 0,
                    isDuplicate = false,
                    generation = 0,
                ),
                x = cardX + index * 120f,
                y = 0f,
                width = 100f,
                height = 60f,
                familyGedcomId = null,
            )
        },
        bonds = listOf(
            PlacedBond(
                x = 100f,
                y = 20f,
                width = 20f,
                height = 20f,
                marriageYear = null,
                familyGedcomId = "F1",
                isMini = true,
            ),
        ),
        lines = listOf(listOf(LineSegment(0f, 0f, 10f, 10f, curved = false))),
        backLines = emptyList(),
        duplicateLines = emptyList(),
        biggestPathSize = 0f,
        maxBitmapSize = 0f,
    )
}
