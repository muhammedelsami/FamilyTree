package com.familytree.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.familytree.core.database.FamilyTreeDatabase
import com.familytree.core.database.entity.PersonEntity
import com.familytree.core.database.entity.TreeEntity
import com.familytree.core.model.MediaObject
import com.familytree.core.model.OwnerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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

/**
 * The rules that make media behave like GEDCOM expects.
 *
 * One photo can belong to several people, and the format expresses that with a shared
 * record and a cross-reference id — but only when it really is shared. Getting this wrong
 * either loses the sharing on export or litters the file with ids nobody references.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MediaAttachmentTest {

    private lateinit var database: FamilyTreeDatabase
    private lateinit var media: MediaRepositoryImpl

    private var treeId = 0L

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FamilyTreeDatabase::class.java,
        ).allowMainThreadQueries().build()

        media = MediaRepositoryImpl(
            database = database,
            mediaDao = database.mediaDao(),
            maintenanceDao = database.maintenanceDao(),
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    @After
    fun tearDown() = database.close()

    private suspend fun newPerson(): Long {
        if (treeId == 0L) treeId = database.treeDao().insert(TreeEntity(title = "Test"))
        return database.personDao().insert(PersonEntity(treeId = treeId))
    }

    private suspend fun newMedia(owner: Long, file: String = "nonna.jpg") =
        media.create(MediaObject(treeId = treeId, file = file), OwnerType.PERSON, owner)

    @Test
    fun `a media attached to one owner stays inline`() = runTest {
        val person = newPerson()
        val mediaId = newMedia(person)
        // No cross-reference id: it will be written under the person, not as a record.
        assertNull(media.get(mediaId)?.gedcomId)
        assertEquals(1, media.referenceCount(mediaId))
    }

    @Test
    fun `attaching a second owner promotes it to a shared record`() = runTest {
        val anna = newPerson()
        val carlo = newPerson()
        val mediaId = newMedia(anna)

        media.attach(mediaId, OwnerType.PERSON, carlo)

        assertEquals("M1", media.get(mediaId)?.gedcomId)
        assertEquals(2, media.referenceCount(mediaId))
    }

    @Test
    fun `detaching the last owner removes the record`() = runTest {
        val anna = newPerson()
        val carlo = newPerson()
        val mediaId = newMedia(anna)
        media.attach(mediaId, OwnerType.PERSON, carlo)

        media.detach(mediaId, OwnerType.PERSON, anna)
        // Still attached to Carlo, so it survives.
        assertNotNull(media.get(mediaId))

        media.detach(mediaId, OwnerType.PERSON, carlo)
        // Unattached media is unreachable and would vanish on the next export anyway.
        assertNull(media.get(mediaId))
    }

    @Test
    fun `marking a portrait clears the previous one`() = runTest {
        val person = newPerson()
        val first = newMedia(person, "first.jpg")
        val second = newMedia(person, "second.jpg")

        media.setPrimary(first, OwnerType.PERSON, person)
        media.setPrimary(second, OwnerType.PERSON, person)

        assertEquals(false, media.get(first)?.isPrimary)
        assertEquals(true, media.get(second)?.isPrimary)
        // The portrait query follows the flag, not the insertion order.
        assertEquals(second, media.observePortraitOf(OwnerType.PERSON, person).first()?.id)
    }

    @Test
    fun `without a portrait flag the first attachment is used`() = runTest {
        val person = newPerson()
        val first = newMedia(person, "first.jpg")
        newMedia(person, "second.jpg")
        assertEquals(first, media.observePortraitOf(OwnerType.PERSON, person).first()?.id)
    }

    @Test
    fun `portraits for the whole tree come back keyed by person`() = runTest {
        val anna = newPerson()
        val carlo = newPerson()
        newPerson() // has no media at all
        newMedia(anna, "anna.jpg")
        val carloPortrait = newMedia(carlo, "carlo-second.jpg")
        media.setPrimary(carloPortrait, OwnerType.PERSON, carlo)

        val portraits = media.observePersonPortraits(treeId).first()

        assertEquals(2, portraits.size)
        assertEquals("anna.jpg", portraits[anna]?.file)
        assertEquals(carloPortrait, portraits[carlo]?.id)
    }

    @Test
    fun `shortening a link keeps only the filename`() = runTest {
        val person = newPerson()
        val mediaId = newMedia(person, """C:\Users\anna\Pictures\nonna.jpg""")
        media.shortenLink(mediaId, "nonna.jpg")
        assertEquals("nonna.jpg", media.get(mediaId)?.file)
        // This is what makes the tree portable to another device.
        assertTrue(media.get(mediaId)?.file?.contains('\\') == false)
    }
}
