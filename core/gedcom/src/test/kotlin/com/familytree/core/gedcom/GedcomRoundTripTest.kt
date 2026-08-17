package com.familytree.core.gedcom

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.familytree.core.database.FamilyTreeDatabase
import com.familytree.core.model.MemberRole
import com.familytree.core.model.OwnerType
import com.familytree.core.model.Sex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * The safety net for the "lossless" claim.
 *
 * A GEDCOM file goes through Room and back out again, and every tag the original
 * contained must still be there. Without this, a dropped `_MILT` or a lost `QUAY` would
 * only surface when a user re-imported their tree somewhere else and found data missing.
 */
// Pinned because the module compiles against a newer SDK than Robolectric ships images
// for; nothing here is version-sensitive, it only needs a working SQLite and Context.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GedcomRoundTripTest {

    private lateinit var database: FamilyTreeDatabase
    private lateinit var importer: GedcomImporter
    private lateinit var exporter: GedcomExporter

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FamilyTreeDatabase::class.java,
        ).allowMainThreadQueries().build()
        importer = GedcomImporter(database, Dispatchers.Unconfined)
        exporter = GedcomExporter(GedcomProjector(database, Dispatchers.Unconfined), Dispatchers.Unconfined)
    }

    @After
    fun tearDown() = database.close()

    private fun fixture(name: String = "roundtrip.ged"): File {
        val text = checkNotNull(javaClass.classLoader?.getResourceAsStream(name))
            .bufferedReader().readText()
        return File.createTempFile("fixture", ".ged").apply { writeText(text) }
    }

    /**
     * FamilyGem's own test file, kept as a second fixture because it was written to
     * exercise awkward real-world cases: several inline media objects, two different
     * paths ending in the same filename, and an accented filename.
     */
    @Test
    fun `inline media with awkward paths survive`() = runTest {
        val original = fixture("media.ged")
        val treeId = importer.importFile(original, "Media")

        val media = database.mediaDao().getAll(treeId)
        assertEquals(7, media.size)
        // All of them are inline, so none may claim a shared record id.
        assertTrue("Inline media must not get ids", media.all { it.gedcomId == null })
        assertTrue("second/homonym.txt missing", media.any { it.file == "second/homonym.txt" })
        assertTrue("accented filename missing", media.any { it.file == "È Carmelo.pdf" })

        val exported = ByteArrayOutputStream()
        exporter.export(treeId, exported)
        val text = exported.toString(Charsets.UTF_8.name())

        val before = original.readText().tagCounts()
        val missing = before.filterKeys { (text.tagCounts()[it] ?: 0) < 1 }.keys
        assertTrue("Lost: $missing", missing.isEmpty())
    }

    @Test
    fun `every tag survives the round trip`() = runTest {
        val original = fixture()
        val treeId = importer.importFile(original, "Round trip")

        val exported = ByteArrayOutputStream()
        exporter.export(treeId, exported)
        val exportedText = exported.toString(Charsets.UTF_8.name())

        val before = original.readText().tagCounts()
        val after = exportedText.tagCounts()

        val missing = before.mapNotNull { (line, count) ->
            val exportedCount = after[line] ?: 0
            if (exportedCount < count) "$line (expected $count, found $exportedCount)" else null
        }
        assertTrue(
            "These ${missing.size} tag/value pairs were lost:\n" + missing.joinToString("\n"),
            missing.isEmpty(),
        )
    }

    @Test
    fun `records and relationships are stored correctly`() = runTest {
        val treeId = importer.importFile(fixture(), "Round trip")

        assertEquals(3, database.personDao().getAll(treeId).size)
        assertEquals(1, database.familyDao().getAll(treeId).size)
        assertEquals(1, database.sourceDao().getAll(treeId).size)
        assertEquals(1, database.repositoryDao().getAll(treeId).size)
        assertEquals(1, database.submitterDao().getAll(treeId).size)

        // Three notes: the shared one, the inline one on Ahmet, the inline one on the source.
        assertEquals(3, database.noteDao().getAll(treeId).size)
        assertEquals(1, database.noteDao().getAll(treeId).count { it.gedcomId != null })

        val ahmet = checkNotNull(database.personDao().getByGedcomId(treeId, "I1"))
        assertEquals(Sex.MALE, ahmet.sex)

        // SEX is a column, not an event, so it must not also appear as an event row.
        val events = database.eventDao().getFor(OwnerType.PERSON, ahmet.id)
        assertEquals(listOf("BIRT", "DEAT", "OCCU", "_MILT"), events.map { it.tag })

        val family = checkNotNull(database.familyDao().getByGedcomId(treeId, "F1"))
        val members = database.familyDao().getMembers(family.id)
        assertEquals(1, members.count { it.role == MemberRole.HUSBAND })
        assertEquals(1, members.count { it.role == MemberRole.WIFE })
        assertEquals(1, members.count { it.role == MemberRole.CHILD })
    }

    /**
     * The parser lifts some non-standard tags onto typed fields and leaves others as
     * raw tags, so preservation has to be checked on both paths.
     */
    @Test
    fun `unmapped vendor tags are preserved with their children`() = runTest {
        val treeId = importer.importFile(fixture(), "Round trip")

        // `_UID` becomes a typed field, so it must land in a column, not the extensions table.
        val ahmet = checkNotNull(database.personDao().getByGedcomId(treeId, "I1"))
        assertEquals("4F2A9C1E5B3D", ahmet.uid)
        assertEquals("_UID", ahmet.uidTag)

        // `_APID` has no typed home, so it is kept verbatim.
        val ayse = checkNotNull(database.personDao().getByGedcomId(treeId, "I2"))
        val ayseTags = database.extensionDao().getFor(OwnerType.PERSON, ayse.id)
        assertEquals(listOf("_APID"), ayseTags.map { it.tag })
        assertEquals("1,1234::5678", ayseTags.first().value)

        // `_MILT` is read as an event, and its vendor-specific `_RANK` child hangs off it.
        val events = database.eventDao().getFor(OwnerType.PERSON, ahmet.id)
        val milt = checkNotNull(events.firstOrNull { it.tag == "_MILT" }) { "events were ${events.map { e -> e.tag }}" }
        assertEquals("1735", milt.date)
        assertEquals("Edirne", milt.place)
        val rank = database.extensionDao().getFor(OwnerType.EVENT, milt.id)
        assertEquals(listOf("_RANK"), rank.map { it.tag })
        assertEquals("Cavus", rank.first().value)
    }

    /**
     * Exporting and importing again must be a fixed point. A one-way check can be
     * fooled by an export that merely looks plausible; re-reading it proves the file is
     * genuinely valid GEDCOM that carries the same tree.
     */
    @Test
    fun `re-importing the export reproduces the same tree`() = runTest {
        val firstId = importer.importFile(fixture(), "First")

        val exported = File.createTempFile("exported", ".ged")
        exporter.export(firstId, exported)
        assertTrue("Export was empty", exported.length() > 0)

        val secondId = importer.importFile(exported, "Second")

        assertEquals(
            database.personDao().getAll(firstId).map { it.gedcomId },
            database.personDao().getAll(secondId).map { it.gedcomId },
        )
        assertEquals(
            database.familyDao().getAll(firstId).size,
            database.familyDao().getAll(secondId).size,
        )
        assertEquals(
            database.noteDao().getAll(firstId).size,
            database.noteDao().getAll(secondId).size,
        )
        assertEquals(
            database.sourceDao().getAllCitations(firstId).size,
            database.sourceDao().getAllCitations(secondId).size,
        )
        // The vendor tags must still be there after the second trip, not just the first.
        assertEquals(
            database.extensionDao().getAll(firstId).map { it.tag }.sorted(),
            database.extensionDao().getAll(secondId).map { it.tag }.sorted(),
        )

        val ahmetAgain = checkNotNull(database.personDao().getByGedcomId(secondId, "I1"))
        assertEquals("4F2A9C1E5B3D", ahmetAgain.uid)
        assertEquals(Sex.MALE, ahmetAgain.sex)
        assertEquals(
            listOf("BIRT", "DEAT", "OCCU", "_MILT"),
            database.eventDao().getFor(OwnerType.PERSON, ahmetAgain.id).map { it.tag },
        )
    }

    @Test
    fun `a person the family did not list is still linked`() = runTest {
        // Ahmet's FAMS points at F1 and F1 lists him back, so both sides agree. This
        // checks the merge does not duplicate the membership when they do.
        val treeId = importer.importFile(fixture(), "Round trip")
        val ahmet = checkNotNull(database.personDao().getByGedcomId(treeId, "I1"))
        val memberships = database.familyDao().getMembershipsOf(ahmet.id)
        assertEquals(1, memberships.size)
        assertEquals(MemberRole.HUSBAND, memberships.first().role)
    }
}

/**
 * Reduces a GEDCOM file to a multiset of `TAG value` lines.
 *
 * Level numbers and record ids are dropped because they may legitimately be renumbered;
 * what must not change is which tags carry which values. `CONC`/`CONT` continuations are
 * folded into their parent so a differently wrapped long note does not read as loss.
 */
private fun String.tagCounts(): Map<String, Int> {
    val counts = HashMap<String, Int>()
    var current: String? = null

    fun flush() {
        current?.let { counts[it] = (counts[it] ?: 0) + 1 }
        current = null
    }

    lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        if (line.isEmpty()) return@forEach
        val withoutLevel = line.substringAfter(' ', "")
        // Drop the xref id that introduces a record: "@I1@ INDI" -> "INDI".
        val body = if (withoutLevel.startsWith("@")) withoutLevel.substringAfter("@ ", "") else withoutLevel
        val tag = body.substringBefore(' ')
        val value = body.substringAfter(' ', "")

        when (tag) {
            "CONC" -> current = current + value
            "CONT" -> current = current + "\n" + value
            // The header records which program wrote the file, so it changes by design.
            "HEAD", "SOUR", "NAME", "VERS", "DEST", "DATE", "CHAR", "GEDC", "FORM", "LANG", "TRLR" -> {
                flush()
                current = "$tag $value".trim()
            }
            else -> {
                flush()
                current = "$tag $value".trim()
            }
        }
    }
    flush()
    return counts
}
