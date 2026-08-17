package com.familytree.core.backup

import androidx.room.withTransaction
import com.familytree.core.common.di.Dispatcher
import com.familytree.core.common.di.FtDispatcher
import com.familytree.core.database.FamilyTreeDatabase
import com.familytree.core.database.entity.FamilyEntity
import com.familytree.core.database.entity.FamilyMemberEntity
import com.familytree.core.database.entity.NoteEntity
import com.familytree.core.database.entity.PersonEntity
import com.familytree.core.model.OwnerType
import com.familytree.core.model.RecordType
import com.familytree.core.model.TreeComparison
import com.familytree.core.model.TreeDifference
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** What a merge actually changed. */
data class MergeResult(val added: Int, val updated: Int, val removed: Int)

/**
 * Applies the differences the user accepted.
 *
 * The hard part is not deciding what to copy — the comparison did that — but that row ids
 * are meaningless across two trees. A person in the returning copy is row 41 there and row
 * 7 here, and an event pointing at "41" would silently attach itself to a stranger. So
 * every reference is translated through the GEDCOM cross-reference id on the way across,
 * and a record whose references cannot be resolved is skipped rather than guessed at.
 */
@Singleton
class TreeMerger @Inject constructor(
    private val database: FamilyTreeDatabase,
    @Dispatcher(FtDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun apply(comparison: TreeComparison): Result<MergeResult> = withContext(ioDispatcher) {
        runCatching {
            val accepted = comparison.differences.filter { it.accepted }
            if (accepted.isEmpty()) return@runCatching MergeResult(0, 0, 0)

            var added = 0
            var updated = 0
            var removed = 0

            database.withTransaction {
                val local = comparison.localTreeId
                val incoming = comparison.incomingTreeId

                // People first: families and events reference them, and a family copied
                // before its members would have nothing to point at.
                accepted.filter { it.recordType == RecordType.PERSON }.forEach { difference ->
                    when (difference.kind) {
                        TreeDifference.Kind.ADDED -> if (copyPerson(difference.gedcomId, incoming, local)) added++
                        TreeDifference.Kind.CHANGED -> if (copyPerson(difference.gedcomId, incoming, local)) updated++
                        TreeDifference.Kind.REMOVED -> if (removePerson(difference.gedcomId, local)) removed++
                    }
                }

                accepted.filter { it.recordType == RecordType.NOTE }.forEach { difference ->
                    when (difference.kind) {
                        TreeDifference.Kind.ADDED -> if (copyNote(difference.gedcomId, incoming, local)) added++
                        TreeDifference.Kind.CHANGED -> if (copyNote(difference.gedcomId, incoming, local)) updated++
                        TreeDifference.Kind.REMOVED ->
                            database.noteDao().getByGedcomId(local, difference.gedcomId)?.let {
                                database.noteDao().delete(it)
                                removed++
                            }
                    }
                }

                accepted.filter { it.recordType == RecordType.FAMILY }.forEach { difference ->
                    when (difference.kind) {
                        TreeDifference.Kind.ADDED -> if (copyFamily(difference.gedcomId, incoming, local)) added++
                        TreeDifference.Kind.CHANGED -> if (copyFamily(difference.gedcomId, incoming, local)) updated++
                        TreeDifference.Kind.REMOVED ->
                            database.familyDao().getByGedcomId(local, difference.gedcomId)?.let {
                                database.familyDao().delete(it)
                                removed++
                            }
                    }
                }
            }

            MergeResult(added, updated, removed)
        }
    }

    /**
     * Copies a person and everything that belongs only to them.
     *
     * Names and events are replaced wholesale rather than merged field by field. Two
     * spellings of the same name are not a conflict a program can settle, and the user has
     * already answered the question the comparison asked: take theirs, or keep mine.
     */
    private suspend fun copyPerson(gedcomId: String, from: Long, to: Long): Boolean {
        val source = database.personDao().getByGedcomId(from, gedcomId) ?: return false
        val existing = database.personDao().getByGedcomId(to, gedcomId)

        val personId = if (existing == null) {
            database.personDao().insert(
                PersonEntity(
                    treeId = to,
                    gedcomId = gedcomId,
                    sex = source.sex,
                    uid = source.uid,
                    uidTag = source.uidTag,
                    referenceNumbers = source.referenceNumbers,
                    rin = source.rin,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        } else {
            database.personDao().update(
                existing.copy(sex = source.sex, updatedAt = System.currentTimeMillis()),
            )
            database.personDao().deleteNamesOf(existing.id)
            database.eventDao().deleteFor(OwnerType.PERSON, existing.id)
            existing.id
        }

        database.personDao().insertNames(
            database.personDao().getNames(source.id).map { it.copy(id = 0L, personId = personId) },
        )
        database.eventDao().insertAll(
            database.eventDao().getFor(OwnerType.PERSON, source.id).map {
                it.copy(id = 0L, treeId = to, ownerId = personId)
            },
        )
        return true
    }

    private suspend fun removePerson(gedcomId: String, from: Long): Boolean {
        val person = database.personDao().getByGedcomId(from, gedcomId) ?: return false
        // Names and memberships cascade; the polymorphic attachments do not, so they go
        // explicitly or they would outlive the person they describe.
        database.eventDao().deleteFor(OwnerType.PERSON, person.id)
        database.personDao().delete(person)
        return true
    }

    private suspend fun copyNote(gedcomId: String, from: Long, to: Long): Boolean {
        val source = database.noteDao().getByGedcomId(from, gedcomId) ?: return false
        val existing = database.noteDao().getByGedcomId(to, gedcomId)
        if (existing == null) {
            database.noteDao().insert(
                NoteEntity(treeId = to, gedcomId = gedcomId, value = source.value, rin = source.rin),
            )
        } else {
            database.noteDao().update(existing.copy(value = source.value))
        }
        return true
    }

    /**
     * Copies a family by rebuilding its membership from cross-reference ids.
     *
     * A member whose person is not here is dropped, not invented: accepting a family
     * without accepting its people would otherwise attach a marriage to whoever happened
     * to hold that row id.
     */
    private suspend fun copyFamily(gedcomId: String, from: Long, to: Long): Boolean {
        val source = database.familyDao().getByGedcomId(from, gedcomId) ?: return false
        val existing = database.familyDao().getByGedcomId(to, gedcomId)

        val familyId = existing?.id ?: database.familyDao().insert(
            FamilyEntity(treeId = to, gedcomId = gedcomId, updatedAt = System.currentTimeMillis()),
        )
        if (existing != null) database.familyDao().deleteMembersOf(familyId)

        val members = database.familyDao().getMembers(source.id).mapNotNull { member ->
            val personGedcomId = database.personDao().get(member.personId)?.gedcomId
                ?: return@mapNotNull null
            val localPersonId = database.personDao().getByGedcomId(to, personGedcomId)?.id
                ?: return@mapNotNull null
            FamilyMemberEntity(
                familyId = familyId,
                personId = localPersonId,
                role = member.role,
                position = member.position,
            )
        }
        database.familyDao().insertMembers(members)

        // Events belong to the family, not to its members, so they are replaced with it.
        database.eventDao().deleteFor(OwnerType.FAMILY, familyId)
        database.eventDao().insertAll(
            database.eventDao().getFor(OwnerType.FAMILY, source.id).map {
                it.copy(id = 0L, treeId = to, ownerId = familyId)
            },
        )
        return true
    }
}
