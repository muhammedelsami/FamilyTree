package com.familytree.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.familytree.core.database.FamilyTreeDatabase
import com.familytree.core.database.entity.EventEntity
import com.familytree.core.database.entity.FamilyEntity
import com.familytree.core.database.entity.FamilyMemberEntity
import com.familytree.core.database.entity.MediaEntity
import com.familytree.core.database.entity.NoteEntity
import com.familytree.core.database.entity.NoteLinkEntity
import com.familytree.core.database.entity.PersonEntity
import com.familytree.core.database.entity.PersonNameEntity
import com.familytree.core.database.entity.TreeEntity
import com.familytree.core.model.MemberRole
import com.familytree.core.model.OwnerType
import com.familytree.core.model.TreeIssue
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
 * A clean tree reporting "no problems" proves nothing on its own, so each check is
 * exercised against a tree deliberately broken in exactly that way.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TreeMaintenanceTest {

    private lateinit var database: FamilyTreeDatabase
    private lateinit var repository: TreeRepositoryImpl

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FamilyTreeDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = TreeRepositoryImpl(
            database = database,
            treeDao = database.treeDao(),
            personDao = database.personDao(),
            familyDao = database.familyDao(),
            extensionDao = database.extensionDao(),
            maintenanceDao = database.maintenanceDao(),
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    @After
    fun tearDown() = database.close()

    private suspend fun newTree(): Long =
        database.treeDao().insert(TreeEntity(title = "Test"))

    private suspend fun addPerson(treeId: Long, gedcomId: String?, named: Boolean = true): Long {
        val id = database.personDao().insert(PersonEntity(treeId = treeId, gedcomId = gedcomId))
        if (named) {
            database.personDao().insertNames(
                listOf(PersonNameEntity(personId = id, value = "Someone /Somebody/")),
            )
        }
        return id
    }

    @Test
    fun `a healthy tree reports nothing`() = runTest {
        val treeId = newTree()
        val a = addPerson(treeId, "I1")
        val b = addPerson(treeId, "I2")
        val familyId = database.familyDao().insert(FamilyEntity(treeId = treeId, gedcomId = "F1"))
        database.familyDao().insertMembers(
            listOf(
                FamilyMemberEntity(familyId = familyId, personId = a, role = MemberRole.HUSBAND),
                FamilyMemberEntity(familyId = familyId, personId = b, role = MemberRole.WIFE),
            ),
        )
        database.treeDao().setRootPerson(treeId, a)

        assertEquals(emptyList<TreeIssue>(), repository.findIssues(treeId))
    }

    @Test
    fun `a root pointing at nobody is found and repaired`() = runTest {
        val treeId = newTree()
        addPerson(treeId, "I5")
        database.treeDao().setRootPerson(treeId, 9999L) // never existed

        val issues = repository.findIssues(treeId)
        assertTrue("$issues", issues.any { it.kind == TreeIssue.Kind.MISSING_ROOT })

        val repaired = repository.repairIssues(treeId)
        assertTrue("$repaired", repaired.any { it.kind == TreeIssue.Kind.MISSING_ROOT })
        assertNotNull(database.treeDao().get(treeId)?.rootPersonId)
        assertEquals(emptyList<TreeIssue>(), repository.findIssues(treeId).filter { it.kind == TreeIssue.Kind.MISSING_ROOT })
    }

    @Test
    fun `records without an id are found and given one`() = runTest {
        val treeId = newTree()
        addPerson(treeId, "I3")
        addPerson(treeId, null)
        addPerson(treeId, null)
        database.familyDao().insert(FamilyEntity(treeId = treeId, gedcomId = null))

        val issues = repository.findIssues(treeId)
        assertEquals(3, issues.first { it.kind == TreeIssue.Kind.MISSING_IDS }.count)

        repository.repairIssues(treeId)

        val ids = database.personDao().getAll(treeId).map { it.gedcomId }
        assertTrue("Some person still has no id: $ids", ids.none { it == null })
        // The generated ids must not collide with the one already in use.
        assertEquals(ids.size, ids.toSet().size)
        assertTrue("Existing id was changed", "I3" in ids)
        assertTrue(repository.findIssues(treeId).none { it.kind == TreeIssue.Kind.MISSING_IDS })
    }

    @Test
    fun `families with fewer than two members are found and removed`() = runTest {
        val treeId = newTree()
        val person = addPerson(treeId, "I1")
        val lonely = database.familyDao().insert(FamilyEntity(treeId = treeId, gedcomId = "F1"))
        database.familyDao().insertMembers(
            listOf(FamilyMemberEntity(familyId = lonely, personId = person, role = MemberRole.HUSBAND)),
        )
        database.familyDao().insert(FamilyEntity(treeId = treeId, gedcomId = "F2")) // no members at all

        assertEquals(
            2,
            repository.findIssues(treeId).first { it.kind == TreeIssue.Kind.UNDERPOPULATED_FAMILIES }.count,
        )

        repository.repairIssues(treeId)
        assertEquals(emptyList<FamilyEntity>(), database.familyDao().getAll(treeId))
    }

    @Test
    fun `attachments left behind by a deleted owner are found and cleared`() = runTest {
        val treeId = newTree()
        val person = addPerson(treeId, "I1")
        val noteId = database.noteDao().insert(NoteEntity(treeId = treeId, value = "orphan"))
        database.noteDao().insertLink(
            NoteLinkEntity(treeId = treeId, noteId = noteId, ownerType = OwnerType.PERSON, ownerId = person),
        )
        database.eventDao().insert(
            EventEntity(treeId = treeId, ownerType = OwnerType.PERSON, ownerId = person, tag = "BIRT"),
        )

        // Remove the person the way a buggy delete path would: row gone, links left.
        database.personDao().deleteById(person)

        assertEquals(2, repository.findIssues(treeId).first { it.kind == TreeIssue.Kind.ORPHANED_LINKS }.count)

        repository.repairIssues(treeId)
        assertTrue(repository.findIssues(treeId).none { it.kind == TreeIssue.Kind.ORPHANED_LINKS })
        // The note record itself survives — only the dangling link was removed.
        assertEquals(1, database.noteDao().getAll(treeId).size)
    }

    @Test
    fun `problems that need a human are reported but not touched`() = runTest {
        val treeId = newTree()
        addPerson(treeId, "I1", named = false)
        database.mediaDao().insert(MediaEntity(treeId = treeId, gedcomId = "M1", file = null))
        database.treeDao().setRootPerson(treeId, database.personDao().getAll(treeId).first().id)

        val issues = repository.findIssues(treeId)
        assertEquals(1, issues.first { it.kind == TreeIssue.Kind.PEOPLE_WITHOUT_NAME }.count)
        assertEquals(1, issues.first { it.kind == TreeIssue.Kind.MEDIA_WITHOUT_FILE }.count)

        repository.repairIssues(treeId)

        // Still there: inventing a name or a file path would be fabricating data.
        val after = repository.findIssues(treeId)
        assertTrue(after.any { it.kind == TreeIssue.Kind.PEOPLE_WITHOUT_NAME })
        assertTrue(after.any { it.kind == TreeIssue.Kind.MEDIA_WITHOUT_FILE })
    }
}
