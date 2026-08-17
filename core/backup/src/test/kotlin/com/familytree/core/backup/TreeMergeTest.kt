package com.familytree.core.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.familytree.core.database.FamilyTreeDatabase
import com.familytree.core.gedcom.GedcomImporter
import com.familytree.core.model.RecordType
import com.familytree.core.model.TreeDifference
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
import java.io.ByteArrayInputStream

/**
 * The merge is where a bug costs somebody their research.
 *
 * The original application shipped this feature with no tests at all, and it is the one
 * place that rewrites records the user did not touch. Every rule it relies on is pinned
 * here — above all that records are matched by cross-reference id, never by row id, which
 * is the mistake that would silently graft one family's events onto another.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TreeMergeTest {

    private lateinit var database: FamilyTreeDatabase
    private lateinit var comparator: TreeComparator
    private lateinit var merger: TreeMerger
    private lateinit var importer: GedcomImporter

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FamilyTreeDatabase::class.java,
        ).allowMainThreadQueries().build()
        comparator = TreeComparator(database, Dispatchers.Unconfined)
        merger = TreeMerger(database, Dispatchers.Unconfined)
        importer = GedcomImporter(database, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() = database.close()

    private fun gedcom(vararg records: String) = """
        0 HEAD
        1 GEDC
        2 VERS 5.5.1
        2 FORM LINEAGE-LINKED
        1 CHAR UTF-8
        ${records.joinToString("\n")}
        0 TRLR
    """.trimIndent()

    private suspend fun import(content: String, title: String): Long =
        importer.import(ByteArrayInputStream(content.toByteArray()), title)

    private val ahmet = """
        0 @I1@ INDI
        1 NAME Ahmet /Yılmaz/
        1 SEX M
        1 BIRT
        2 DATE 12 MAR 1890
    """.trimIndent()

    private val ayse = """
        0 @I2@ INDI
        1 NAME Ayşe /Yılmaz/
        1 SEX F
    """.trimIndent()

    @Test
    fun `a person only in the returning copy is reported as added`() = runTest {
        val local = import(gedcom(ahmet), "Mine")
        val incoming = import(gedcom(ahmet, ayse), "Returned")

        val comparison = comparator.compare(local, incoming)

        val difference = comparison.differences.single { it.recordType == RecordType.PERSON }
        assertEquals(TreeDifference.Kind.ADDED, difference.kind)
        assertEquals("I2", difference.gedcomId)
        assertEquals("Ayşe Yılmaz", difference.incomingSummary)
    }

    @Test
    fun `an unchanged person produces no difference at all`() = runTest {
        val local = import(gedcom(ahmet, ayse), "Mine")
        val incoming = import(gedcom(ahmet, ayse), "Returned")

        // Two independent imports give the same people entirely different row ids. If the
        // comparison looked at those, every record would read as changed.
        assertTrue(
            "Identical trees should compare equal: ${comparator.compare(local, incoming).differences}",
            comparator.compare(local, incoming).differences.isEmpty(),
        )
    }

    @Test
    fun `an edited person is reported as changed with both readings`() = runTest {
        val local = import(gedcom(ahmet), "Mine")
        val edited = """
            0 @I1@ INDI
            1 NAME Ahmet Kemal /Yılmaz/
            1 SEX M
            1 BIRT
            2 DATE 12 MAR 1890
        """.trimIndent()
        val incoming = import(gedcom(edited), "Returned")

        val difference = comparator.compare(local, incoming).differences.single()
        assertEquals(TreeDifference.Kind.CHANGED, difference.kind)
        assertEquals("Ahmet Yılmaz", difference.localSummary)
        assertEquals("Ahmet Kemal Yılmaz", difference.incomingSummary)
    }

    @Test
    fun `a person missing from the returning copy is reported as removed`() = runTest {
        val local = import(gedcom(ahmet, ayse), "Mine")
        val incoming = import(gedcom(ahmet), "Returned")

        val difference = comparator.compare(local, incoming).differences.single()
        assertEquals(TreeDifference.Kind.REMOVED, difference.kind)
        assertEquals("I2", difference.gedcomId)
    }

    @Test
    fun `accepting an addition brings the person across with their events`() = runTest {
        val local = import(gedcom(ahmet), "Mine")
        val newcomer = """
            0 @I2@ INDI
            1 NAME Ayşe /Yılmaz/
            1 SEX F
            1 BIRT
            2 DATE 3 FEB 1895
            2 PLAC Bursa
        """.trimIndent()
        val incoming = import(gedcom(ahmet, newcomer), "Returned")

        val result = merger.apply(comparator.compare(local, incoming)).getOrThrow()

        assertEquals(1, result.added)
        val added = database.personDao().getByGedcomId(local, "I2")
        assertNotNull(added)
        val names = database.personDao().getNames(added!!.id)
        assertEquals("Ayşe /Yılmaz/", names.single().value)
        val birth = database.eventDao().getFor(com.familytree.core.model.OwnerType.PERSON, added.id).single()
        assertEquals("3 FEB 1895", birth.date)
        assertEquals("Bursa", birth.place)
    }

    @Test
    fun `a rejected difference is left alone`() = runTest {
        val local = import(gedcom(ahmet), "Mine")
        val incoming = import(gedcom(ahmet, ayse), "Returned")

        val comparison = comparator.compare(local, incoming)
        val declined = comparison.copy(
            differences = comparison.differences.map { it.copy(accepted = false) },
        )
        val result = merger.apply(declined).getOrThrow()

        assertEquals(MergeResult(0, 0, 0), result)
        assertNull(database.personDao().getByGedcomId(local, "I2"))
    }

    @Test
    fun `a family is rebuilt from cross-reference ids, not row ids`() = runTest {
        val family = """
            0 @F1@ FAM
            1 HUSB @I1@
            1 WIFE @I2@
            1 MARR
            2 DATE 1918
        """.trimIndent()
        // Deliberately different row ids on the two sides: the local tree holds an extra
        // person, so every id in the returning copy is off by one.
        val filler = "0 @I9@ INDI\n1 NAME Someone /Else/"
        val local = import(gedcom(filler, ahmet, ayse), "Mine")
        val incoming = import(gedcom(ahmet, ayse, family), "Returned")

        merger.apply(comparator.compare(local, incoming)).getOrThrow()

        val merged = database.familyDao().getByGedcomId(local, "F1")
        assertNotNull(merged)
        val members = database.familyDao().getMembers(merged!!.id)
        assertEquals(2, members.size)
        // The membership must name Ahmet and Ayşe, not whoever happens to hold those row
        // ids in the other tree.
        val gedcomIds = members.mapNotNull { database.personDao().get(it.personId)?.gedcomId }.sorted()
        assertEquals(listOf("I1", "I2"), gedcomIds)
    }

    @Test
    fun `a family whose members were declined is not grafted onto strangers`() = runTest {
        val family = """
            0 @F1@ FAM
            1 HUSB @I1@
            1 WIFE @I2@
        """.trimIndent()
        val local = import(gedcom(ahmet), "Mine")
        val incoming = import(gedcom(ahmet, ayse, family), "Returned")

        val comparison = comparator.compare(local, incoming)
        // Take the family but refuse the person it needs — the case that would otherwise
        // attach a marriage to an unrelated row.
        val partial = comparison.copy(
            differences = comparison.differences.map {
                if (it.recordType == RecordType.PERSON) it.copy(accepted = false) else it
            },
        )
        merger.apply(partial).getOrThrow()

        val merged = database.familyDao().getByGedcomId(local, "F1")
        assertNotNull(merged)
        val members = database.familyDao().getMembers(merged!!.id)
        assertEquals(1, members.size)
        assertEquals("I1", database.personDao().get(members.single().personId)?.gedcomId)
    }

    @Test
    fun `accepting a change replaces names rather than accumulating them`() = runTest {
        val local = import(gedcom(ahmet), "Mine")
        val renamed = "0 @I1@ INDI\n1 NAME Ahmet Kemal /Yılmaz/\n1 SEX M"
        val incoming = import(gedcom(renamed), "Returned")

        merger.apply(comparator.compare(local, incoming)).getOrThrow()

        val person = database.personDao().getByGedcomId(local, "I1")!!
        // One name, the new one. Merging field by field would leave both spellings and no
        // way to tell which is meant.
        assertEquals(1, database.personDao().getNames(person.id).size)
        assertEquals("Ahmet Kemal /Yılmaz/", database.personDao().getNames(person.id).single().value)
    }

    @Test
    fun `accepting a removal deletes the person here`() = runTest {
        val local = import(gedcom(ahmet, ayse), "Mine")
        val incoming = import(gedcom(ahmet), "Returned")

        val result = merger.apply(comparator.compare(local, incoming)).getOrThrow()

        assertEquals(1, result.removed)
        assertNull(database.personDao().getByGedcomId(local, "I2"))
        // And the tree it came from is untouched.
        assertNotNull(database.personDao().getByGedcomId(incoming, "I1"))
    }

    @Test
    fun `notes are compared by their own identifier`() = runTest {
        val note = "0 @T1@ NOTE A note about the family"
        val editedNote = "0 @T1@ NOTE A note about the family, corrected"
        val local = import(gedcom(ahmet, note), "Mine")
        val incoming = import(gedcom(ahmet, editedNote), "Returned")

        val difference = comparator.compare(local, incoming).differences.single()
        assertEquals(RecordType.NOTE, difference.recordType)
        assertEquals(TreeDifference.Kind.CHANGED, difference.kind)

        merger.apply(comparator.compare(local, incoming)).getOrThrow()
        assertEquals(
            "A note about the family, corrected",
            database.noteDao().getByGedcomId(local, "T1")?.value,
        )
    }
}
