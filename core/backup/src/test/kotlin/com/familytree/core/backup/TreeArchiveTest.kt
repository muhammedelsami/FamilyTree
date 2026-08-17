package com.familytree.core.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.familytree.core.database.FamilyTreeDatabase
import com.familytree.core.gedcom.GedcomExporter
import com.familytree.core.gedcom.GedcomImporter
import com.familytree.core.gedcom.GedcomProjector
import com.familytree.core.media.MediaResolver
import com.familytree.core.model.TreeGrade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * A backup nobody has restored is not a backup.
 *
 * These tests take the archive all the way back out again, because the failure mode that
 * matters is silent: an archive that writes cleanly, opens cleanly, and is missing the
 * photographs or the sharing state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TreeArchiveTest {

    private lateinit var database: FamilyTreeDatabase
    private lateinit var archiver: TreeArchiver
    private lateinit var resolver: MediaResolver

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, FamilyTreeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        resolver = MediaResolver(context, Dispatchers.Unconfined)

        val importer = GedcomImporter(database, Dispatchers.Unconfined)
        val exporter = GedcomExporter(GedcomProjector(database, Dispatchers.Unconfined), Dispatchers.Unconfined)
        archiver = TreeArchiver(
            treeDao = database.treeDao(),
            personDao = database.personDao(),
            exporter = exporter,
            importer = importer,
            resolver = resolver,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    @After
    fun tearDown() = database.close()

    private val sampleGedcom = """
        0 HEAD
        1 GEDC
        2 VERS 5.5.1
        2 FORM LINEAGE-LINKED
        1 CHAR UTF-8
        0 @I1@ INDI
        1 NAME Ahmet /Yılmaz/
        1 SEX M
        1 BIRT
        2 DATE 12 MAR 1890
        1 OBJE
        2 FILE nonna.jpg
        2 TITL A photograph
        1 FAMS @F1@
        0 @I2@ INDI
        1 NAME Ayşe /Yılmaz/
        1 SEX F
        1 FAMS @F1@
        0 @I3@ INDI
        1 NAME Mehmet /Yılmaz/
        1 FAMC @F1@
        0 @F1@ FAM
        1 HUSB @I1@
        1 WIFE @I2@
        1 CHIL @I3@
        1 MARR
        2 DATE 1918
        0 TRLR
    """.trimIndent()

    private suspend fun importSample(title: String = "Yılmaz"): Long =
        GedcomImporter(database, Dispatchers.Unconfined)
            .import(ByteArrayInputStream(sampleGedcom.toByteArray()), title)

    private fun placeMedia(treeId: Long, name: String, content: String): File =
        File(resolver.treeStorage(treeId), name).apply {
            parentFile?.mkdirs()
            writeText(content)
        }

    @Test
    fun `an archive restores the whole family`() = runTest {
        val treeId = importSample()
        val out = ByteArrayOutputStream()
        archiver.write(treeId, out).getOrThrow()

        val restored = archiver.read(ByteArrayInputStream(out.toByteArray()), "fallback").getOrThrow()

        assertEquals("Yılmaz", restored.manifest.title)
        val people = database.personDao().getAll(restored.treeId)
        assertEquals(3, people.size)
        assertEquals(1, database.familyDao().getAll(restored.treeId).size)
        // The relationships have to survive, not just the people.
        assertEquals(3, database.familyDao().getAllMemberships(restored.treeId).size)
    }

    @Test
    fun `photographs travel inside the archive`() = runTest {
        val treeId = importSample()
        placeMedia(treeId, "nonna.jpg", "pretend this is a photograph")

        val out = ByteArrayOutputStream()
        archiver.write(treeId, out).getOrThrow()
        val restored = archiver.read(ByteArrayInputStream(out.toByteArray()), "fallback").getOrThrow()

        assertEquals(1, restored.mediaRestored)
        val file = File(resolver.treeStorage(restored.treeId), "nonna.jpg")
        assertTrue("The photograph did not come back", file.isFile)
        assertEquals("pretend this is a photograph", file.readText())
    }

    @Test
    fun `the sharing grade survives a restore`() = runTest {
        val treeId = importSample()
        database.treeDao().update(
            database.treeDao().get(treeId)!!.copy(grade = TreeGrade.RECEIVED.value),
        )

        val out = ByteArrayOutputStream()
        archiver.write(treeId, out).getOrThrow()
        val restored = archiver.read(ByteArrayInputStream(out.toByteArray()), "fallback").getOrThrow()

        // A restored copy of a received tree is still a received tree. Were it to come
        // back as an original, every later share would be judged wrongly.
        assertEquals(TreeGrade.RECEIVED.value, database.treeDao().get(restored.treeId)?.grade)
    }

    @Test
    fun `the root person is found again by its gedcom id`() = runTest {
        val treeId = importSample()
        val root = database.personDao().getByGedcomId(treeId, "I2")!!
        database.treeDao().update(database.treeDao().get(treeId)!!.copy(rootPersonId = root.id))

        val out = ByteArrayOutputStream()
        archiver.write(treeId, out).getOrThrow()
        val restored = archiver.read(ByteArrayInputStream(out.toByteArray()), "fallback").getOrThrow()

        // Row ids are reassigned on restore, so the root can only be named by the id the
        // GEDCOM file itself uses.
        val restoredRoot = database.treeDao().get(restored.treeId)?.rootPersonId
        assertNotNull(restoredRoot)
        assertEquals("I2", database.personDao().get(restoredRoot!!)?.gedcomId)
    }

    @Test
    fun `restoring never touches the tree it came from`() = runTest {
        val treeId = importSample()
        val out = ByteArrayOutputStream()
        archiver.write(treeId, out).getOrThrow()

        val restored = archiver.read(ByteArrayInputStream(out.toByteArray()), "fallback").getOrThrow()

        // Restoring in place would destroy whatever happened since the backup was taken,
        // and the backup someone wanted would be the one they just overwrote.
        assertTrue(restored.treeId != treeId)
        assertEquals(2, database.treeDao().getAll().size)
        assertEquals(3, database.personDao().getAll(treeId).size)
    }

    @Test
    fun `an archive without family data is refused`() = runTest {
        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("readme.txt"))
            zip.write("not a backup".toByteArray())
            zip.closeEntry()
        }
        assertTrue(archiver.read(ByteArrayInputStream(out.toByteArray()), "fallback").isFailure)
    }

    @Test
    fun `an entry that points outside the archive is ignored`() = runTest {
        val treeId = importSample()
        val out = ByteArrayOutputStream()
        archiver.write(treeId, out).getOrThrow()

        // Rebuild the archive with a traversing media entry added.
        val tampered = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(tampered).use { zip ->
            java.util.zip.ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { source ->
                var entry = source.nextEntry
                while (entry != null) {
                    zip.putNextEntry(java.util.zip.ZipEntry(entry.name))
                    source.copyTo(zip)
                    zip.closeEntry()
                    entry = source.nextEntry
                }
            }
            zip.putNextEntry(java.util.zip.ZipEntry("media/../../../escaped.txt"))
            zip.write("should not be written".toByteArray())
            zip.closeEntry()
        }

        val restored = archiver.read(ByteArrayInputStream(tampered.toByteArray()), "fallback").getOrThrow()

        // An archive can come from anyone; a crafted entry name must not write outside
        // the destination folder.
        assertEquals(0, restored.mediaRestored)
    }
}
