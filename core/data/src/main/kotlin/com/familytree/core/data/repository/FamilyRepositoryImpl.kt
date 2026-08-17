package com.familytree.core.data.repository

import androidx.room.withTransaction
import com.familytree.core.common.di.Dispatcher
import com.familytree.core.common.di.FtDispatcher
import com.familytree.core.data.mapper.toDomain
import com.familytree.core.data.mapper.toEntity
import com.familytree.core.database.FamilyTreeDatabase
import com.familytree.core.database.dao.EventDao
import com.familytree.core.database.dao.ExtensionDao
import com.familytree.core.database.dao.FamilyDao
import com.familytree.core.database.dao.MaintenanceDao
import com.familytree.core.database.dao.MediaDao
import com.familytree.core.database.dao.NoteDao
import com.familytree.core.database.dao.SourceDao
import com.familytree.core.database.entity.FamilyEntity
import com.familytree.core.database.entity.FamilyMemberEntity
import com.familytree.core.domain.repository.FamilyRepository
import com.familytree.core.model.Event
import com.familytree.core.model.Family
import com.familytree.core.model.FamilyDetails
import com.familytree.core.model.FamilyMember
import com.familytree.core.model.MemberRole
import com.familytree.core.model.RecordType
import com.familytree.core.model.OwnerType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class FamilyRepositoryImpl @Inject constructor(
    private val database: FamilyTreeDatabase,
    private val familyDao: FamilyDao,
    private val eventDao: EventDao,
    private val noteDao: NoteDao,
    private val mediaDao: MediaDao,
    private val sourceDao: SourceDao,
    private val extensionDao: ExtensionDao,
    private val maintenanceDao: MaintenanceDao,
    @Dispatcher(FtDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : FamilyRepository {

    override fun observeFamilies(treeId: Long): Flow<List<Family>> =
        familyDao.observeAll(treeId).map { list -> list.map { it.toDomain() } }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeFamilyDetails(familyId: Long): Flow<FamilyDetails?> =
        familyDao.observeMembers(familyId).flatMapLatest { members ->
            val family = familyDao.get(familyId)
            if (family == null) {
                flowOf(null)
            } else {
                combine(
                    eventDao.observeFor(OwnerType.FAMILY, familyId),
                    noteDao.observeFor(OwnerType.FAMILY, familyId),
                    mediaDao.observeFor(OwnerType.FAMILY, familyId),
                    sourceDao.observeCitationsFor(OwnerType.FAMILY, familyId),
                ) { events, notes, media, citations ->
                    FamilyDetails(
                        family = family.toDomain(),
                        husbands = familyDao.getPersonsInRole(familyId, MemberRole.HUSBAND)
                            .map { it.toDomain() },
                        wives = familyDao.getPersonsInRole(familyId, MemberRole.WIFE)
                            .map { it.toDomain() },
                        children = familyDao.getPersonsInRole(familyId, MemberRole.CHILD)
                            .map { it.toDomain() },
                        events = events.map { it.toDomain() },
                        notes = notes.map { it.toDomain() },
                        media = media.map { it.toDomain() },
                        citations = citations.map { it.toDomain() },
                    )
                }
            }
        }

    override suspend fun getFamily(familyId: Long): Family? = withContext(ioDispatcher) {
        familyDao.get(familyId)?.toDomain()
    }

    override suspend fun getMembers(familyId: Long): List<FamilyMember> =
        withContext(ioDispatcher) { familyDao.getMembers(familyId).map { it.toDomain() } }

    override fun observeAllMemberships(treeId: Long): Flow<List<FamilyMember>> =
        familyDao.observeAllMemberships(treeId).map { list -> list.map { it.toDomain() } }

    override fun observeFamilyEvents(treeId: Long): Flow<List<Event>> =
        eventDao.observeAllForTree(treeId).map { list ->
            list.filter { it.ownerType == OwnerType.FAMILY }.map { it.toDomain() }
        }

    override suspend fun createFamily(treeId: Long): Long = withContext(ioDispatcher) {
        // Assigned now for the same reason people are: the diagram and the GEDCOM writer
        // both address families by their cross-reference id.
        val prefix = RecordType.FAMILY.idPrefix
        val gedcomId = "$prefix${maintenanceDao.nextFamilyNumber(treeId, prefix)}"
        familyDao.insert(
            FamilyEntity(
                treeId = treeId,
                gedcomId = gedcomId,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun deleteFamily(familyId: Long) = withContext(ioDispatcher) {
        database.withTransaction {
            eventDao.deleteFor(OwnerType.FAMILY, familyId)
            noteDao.unlinkAllFrom(OwnerType.FAMILY, familyId)
            mediaDao.unlinkAllFrom(OwnerType.FAMILY, familyId)
            sourceDao.deleteCitationsFor(OwnerType.FAMILY, familyId)
            extensionDao.deleteFor(OwnerType.FAMILY, familyId)
            familyDao.deleteById(familyId)
        }
    }

    override suspend fun addMember(familyId: Long, personId: Long, role: MemberRole): Long =
        withContext(ioDispatcher) {
            val nextPosition = familyDao.getMembers(familyId)
                .filter { it.role == role }
                .maxOfOrNull { it.position }
                ?.plus(1) ?: 0
            familyDao.insertMember(
                FamilyMemberEntity(
                    familyId = familyId,
                    personId = personId,
                    role = role,
                    position = nextPosition,
                ),
            )
        }

    override suspend fun updateMember(member: FamilyMember) = withContext(ioDispatcher) {
        familyDao.updateMember(member.toEntity())
    }

    override suspend fun removeMember(familyId: Long, personId: Long) = withContext(ioDispatcher) {
        familyDao.unlink(familyId, personId)
    }

    override suspend fun getParentFamilies(personId: Long): List<Family> =
        withContext(ioDispatcher) {
            familyDao.getParentFamilies(personId).map { it.toDomain() }
        }

    override suspend fun getSpouseFamilies(personId: Long): List<Family> =
        withContext(ioDispatcher) {
            familyDao.getSpouseFamilies(personId).map { it.toDomain() }
        }

    override suspend fun pruneUnderpopulatedFamilies(treeId: Long): Int =
        withContext(ioDispatcher) {
            val doomed = familyDao.findUnderpopulatedFamilies(treeId)
            doomed.forEach { deleteFamily(it.id) }
            doomed.size
        }
}
