package com.familytree.core.data.repository

import androidx.room.withTransaction
import com.familytree.core.common.di.Dispatcher
import com.familytree.core.common.di.FtDispatcher
import com.familytree.core.data.mapper.toDomain
import com.familytree.core.data.mapper.toEntity
import com.familytree.core.database.FamilyTreeDatabase
import com.familytree.core.database.dao.ExtensionDao
import com.familytree.core.database.dao.FamilyDao
import com.familytree.core.database.dao.MaintenanceDao
import com.familytree.core.database.dao.PersonDao
import com.familytree.core.database.dao.TreeDao
import com.familytree.core.database.entity.TreeEntity
import com.familytree.core.domain.repository.TreeRepository
import com.familytree.core.model.MediaFolder
import com.familytree.core.model.MemberRole
import com.familytree.core.model.RecordType
import com.familytree.core.model.Tree
import com.familytree.core.model.TreeGrade
import com.familytree.core.model.TreeIssue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class TreeRepositoryImpl @Inject constructor(
    private val database: FamilyTreeDatabase,
    private val treeDao: TreeDao,
    private val personDao: PersonDao,
    private val familyDao: FamilyDao,
    private val extensionDao: ExtensionDao,
    private val maintenanceDao: MaintenanceDao,
    @Dispatcher(FtDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : TreeRepository {

    override fun observeTrees(): Flow<List<Tree>> =
        treeDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeTree(treeId: Long): Flow<Tree?> =
        treeDao.observe(treeId).map { it?.toDomain() }

    override suspend fun getTree(treeId: Long): Tree? = withContext(ioDispatcher) {
        treeDao.get(treeId)?.toDomain()
    }

    override suspend fun createTree(title: String): Long = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        treeDao.insert(
            TreeEntity(
                title = title,
                createdAt = now,
                updatedAt = now,
                sortOrder = Int.MAX_VALUE,
            ),
        )
    }

    override suspend fun renameTree(treeId: Long, title: String) = withContext(ioDispatcher) {
        treeDao.rename(treeId, title, System.currentTimeMillis())
    }

    override suspend fun deleteTree(treeId: Long) = withContext(ioDispatcher) {
        // Every tree-scoped table declares ON DELETE CASCADE, so this one statement
        // removes the whole graph.
        treeDao.deleteById(treeId)
    }

    override suspend fun reorderTrees(orderedIds: List<Long>) = withContext(ioDispatcher) {
        database.withTransaction {
            orderedIds.forEachIndexed { index, id -> treeDao.setSortOrder(id, index) }
        }
    }

    override suspend fun setRootPerson(treeId: Long, personId: Long?) = withContext(ioDispatcher) {
        treeDao.setRootPerson(treeId, personId)
    }

    override suspend fun setGrade(treeId: Long, grade: TreeGrade) = withContext(ioDispatcher) {
        treeDao.setGrade(treeId, grade.value)
    }

    override suspend fun refreshCounters(treeId: Long) = withContext(ioDispatcher) {
        val generations = countGenerations(treeId)
        treeDao.refreshCounters(treeId, generations, System.currentTimeMillis())
    }

    override suspend fun findRootPersonId(treeId: Long): Long? = withContext(ioDispatcher) {
        // Other genealogy programs record the root in their own header tags; honouring
        // them means an imported file opens on the person its author intended.
        for (tag in listOf("_ROOT", "_HOME")) {
            val ref = extensionDao.findHeaderTag(treeId, tag)?.ref ?: continue
            personDao.getByGedcomId(treeId, ref)?.let { return@withContext it.id }
        }
        personDao.findLowestNumberedPerson(treeId)?.let { return@withContext it.id }
        personDao.getAll(treeId).firstOrNull()?.id
    }

    override suspend fun findIssues(treeId: Long): List<TreeIssue> = withContext(ioDispatcher) {
        buildList {
            fun add(kind: TreeIssue.Kind, count: Int) {
                if (count > 0) add(TreeIssue(kind, count))
            }
            add(TreeIssue.Kind.MISSING_ROOT, maintenanceDao.countMissingRoot(treeId))
            add(TreeIssue.Kind.MISSING_IDS, maintenanceDao.countRecordsWithoutId(treeId))
            add(TreeIssue.Kind.UNDERPOPULATED_FAMILIES, maintenanceDao.countUnderpopulatedFamilies(treeId))
            add(TreeIssue.Kind.MEDIA_WITHOUT_FILE, maintenanceDao.countMediaWithoutFile(treeId))
            add(TreeIssue.Kind.ORPHANED_LINKS, maintenanceDao.countOrphanedLinks(treeId))
            add(TreeIssue.Kind.PEOPLE_WITHOUT_NAME, maintenanceDao.countPeopleWithoutName(treeId))
        }
    }

    override suspend fun repairIssues(treeId: Long): List<TreeIssue> = withContext(ioDispatcher) {
        val repaired = mutableListOf<TreeIssue>()
        database.withTransaction {
            if (maintenanceDao.countMissingRoot(treeId) > 0) {
                findRootPersonId(treeId)?.let { rootId ->
                    treeDao.setRootPerson(treeId, rootId)
                    repaired += TreeIssue(TreeIssue.Kind.MISSING_ROOT, 1)
                }
            }

            val mintedIds = assignMissingIds(treeId)
            if (mintedIds > 0) repaired += TreeIssue(TreeIssue.Kind.MISSING_IDS, mintedIds)

            val orphans = maintenanceDao.deleteOrphanedNoteLinks(treeId) +
                maintenanceDao.deleteOrphanedMediaLinks(treeId) +
                maintenanceDao.deleteOrphanedEvents(treeId)
            if (orphans > 0) repaired += TreeIssue(TreeIssue.Kind.ORPHANED_LINKS, orphans)

            // Done last: removing orphans can leave a family below the threshold.
            val families = maintenanceDao.deleteUnderpopulatedFamilies(treeId)
            if (families > 0) repaired += TreeIssue(TreeIssue.Kind.UNDERPOPULATED_FAMILIES, families)
        }
        refreshCounters(treeId)
        repaired
    }

    private suspend fun assignMissingIds(treeId: Long): Int {
        var minted = 0
        var next = maintenanceDao.nextPersonNumber(treeId, RecordType.PERSON.idPrefix)
        maintenanceDao.personsWithoutId(treeId).forEach { personId ->
            maintenanceDao.setPersonGedcomId(personId, "${RecordType.PERSON.idPrefix}${next++}")
            minted++
        }
        next = maintenanceDao.nextFamilyNumber(treeId, RecordType.FAMILY.idPrefix)
        maintenanceDao.familiesWithoutId(treeId).forEach { familyId ->
            maintenanceDao.setFamilyGedcomId(familyId, "${RecordType.FAMILY.idPrefix}${next++}")
            minted++
        }
        return minted
    }

    override fun observeMediaFolders(treeId: Long): Flow<List<MediaFolder>> =
        treeDao.observeMediaFolders(treeId).map { list -> list.map { it.toDomain() } }

    override suspend fun addMediaFolder(folder: MediaFolder): Long = withContext(ioDispatcher) {
        treeDao.insertMediaFolder(folder.toEntity())
    }

    override suspend fun removeMediaFolder(folderId: Long) = withContext(ioDispatcher) {
        treeDao.deleteMediaFolderById(folderId)
    }

    /**
     * The generation span of the tree, measured from the root person outwards.
     *
     * Walks up and down the kinship graph iteratively rather than recursively: real
     * trees contain cycles (cousin marriages, mis-entered data), and a visited set plus
     * a work list makes that safe where recursion would either stack-overflow or need
     * the temporary marker extension the original used.
     */
    private suspend fun countGenerations(treeId: Long): Int {
        val rootId = treeDao.get(treeId)?.rootPersonId ?: return 0
        val memberships = familyDao.getAllMemberships(treeId)
        val byFamily = memberships.groupBy { it.familyId }
        val byPerson = memberships.groupBy { it.personId }

        val generation = mutableMapOf(rootId to 0)
        val queue = ArrayDeque<Long>().apply { add(rootId) }

        while (queue.isNotEmpty()) {
            val personId = queue.removeFirst()
            val level = generation.getValue(personId)
            for (membership in byPerson[personId].orEmpty()) {
                val siblings = byFamily[membership.familyId].orEmpty()
                for (other in siblings) {
                    if (other.personId == personId) continue
                    val otherLevel = when {
                        // Person is a child here, so the spouses are one generation up.
                        membership.role == MemberRole.CHILD && other.role != MemberRole.CHILD ->
                            level - 1
                        // Person is a spouse here, so the children are one generation down.
                        membership.role != MemberRole.CHILD && other.role == MemberRole.CHILD ->
                            level + 1
                        // Siblings, or spouses of each other: same generation.
                        else -> level
                    }
                    if (generation.putIfAbsent(other.personId, otherLevel) == null) {
                        queue.add(other.personId)
                    }
                }
            }
        }

        val levels = generation.values
        return if (levels.isEmpty()) 0 else levels.max() - levels.min() + 1
    }
}
