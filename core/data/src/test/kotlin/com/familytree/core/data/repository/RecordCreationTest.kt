package com.familytree.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.familytree.core.database.FamilyTreeDatabase
import com.familytree.core.database.entity.PersonEntity
import com.familytree.core.database.entity.TreeEntity
import com.familytree.core.model.Person
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

/**
 * Records created in the app must carry a GEDCOM cross-reference id straight away.
 *
 * This is not housekeeping: the diagram engine and every GEDCOM writer address records
 * by that id, so a person created without one is invisible to them. It showed up as a
 * newly added child appearing on the diagram as a blank card.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecordCreationTest {

    private lateinit var database: FamilyTreeDatabase
    private lateinit var people: PersonRepositoryImpl
    private lateinit var families: FamilyRepositoryImpl

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FamilyTreeDatabase::class.java,
        ).allowMainThreadQueries().build()

        people = PersonRepositoryImpl(
            database = database,
            personDao = database.personDao(),
            eventDao = database.eventDao(),
            familyDao = database.familyDao(),
            treeDao = database.treeDao(),
            noteDao = database.noteDao(),
            mediaDao = database.mediaDao(),
            sourceDao = database.sourceDao(),
            extensionDao = database.extensionDao(),
            maintenanceDao = database.maintenanceDao(),
            ioDispatcher = Dispatchers.Unconfined,
        )
        families = FamilyRepositoryImpl(
            database = database,
            familyDao = database.familyDao(),
            eventDao = database.eventDao(),
            noteDao = database.noteDao(),
            mediaDao = database.mediaDao(),
            sourceDao = database.sourceDao(),
            extensionDao = database.extensionDao(),
            maintenanceDao = database.maintenanceDao(),
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    @After
    fun tearDown() = database.close()

    private suspend fun newTree(): Long = database.treeDao().insert(TreeEntity(title = "Test"))

    @Test
    fun `a new person is given an identifier`() = runTest {
        val treeId = newTree()
        val personId = people.createPerson(Person(treeId = treeId))
        assertNotNull(database.personDao().get(personId)?.gedcomId)
        assertEquals("I1", database.personDao().get(personId)?.gedcomId)
    }

    @Test
    fun `identifiers do not collide with ones already in the tree`() = runTest {
        val treeId = newTree()
        // Stands in for an imported file that already uses I1 and I7.
        database.personDao().insert(PersonEntity(treeId = treeId, gedcomId = "I1"))
        database.personDao().insert(PersonEntity(treeId = treeId, gedcomId = "I7"))

        val first = people.createPerson(Person(treeId = treeId))
        val second = people.createPerson(Person(treeId = treeId))

        val ids = database.personDao().getAll(treeId).map { it.gedcomId }
        assertEquals("Every id must be distinct: $ids", ids.size, ids.toSet().size)
        // Continues after the highest existing number rather than reusing a gap.
        assertEquals("I8", database.personDao().get(first)?.gedcomId)
        assertEquals("I9", database.personDao().get(second)?.gedcomId)
    }

    @Test
    fun `an explicitly supplied identifier is respected`() = runTest {
        val treeId = newTree()
        val personId = people.createPerson(Person(treeId = treeId, gedcomId = "I42"))
        assertEquals("I42", database.personDao().get(personId)?.gedcomId)
    }

    @Test
    fun `a new family is given an identifier`() = runTest {
        val treeId = newTree()
        val familyId = families.createFamily(treeId)
        assertEquals("F1", database.familyDao().get(familyId)?.gedcomId)

        val second = families.createFamily(treeId)
        assertEquals("F2", database.familyDao().get(second)?.gedcomId)
    }

    @Test
    fun `identifiers are numbered per tree`() = runTest {
        val first = newTree()
        val second = newTree()
        people.createPerson(Person(treeId = first))
        val other = people.createPerson(Person(treeId = second))
        // Trees number independently; both starting at I1 is correct, not a collision.
        assertEquals("I1", database.personDao().get(other)?.gedcomId)
        assertTrue(database.personDao().getAll(first).size == 1)
    }
}
