package com.familytree.core.backup

import android.content.Context
import android.net.Uri
import com.familytree.core.common.di.Dispatcher
import com.familytree.core.common.di.FtDispatcher
import com.familytree.core.database.FamilyTreeDatabase
import com.familytree.core.database.dao.TreeDao
import com.familytree.core.domain.repository.AppliedUpdates
import com.familytree.core.domain.repository.ReceivedShare
import com.familytree.core.domain.repository.ShareRepository
import com.familytree.core.model.ShareGrades
import com.familytree.core.model.TreeComparison
import com.familytree.core.model.TreeGrade
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShareRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: FamilyTreeDatabase,
    private val treeDao: TreeDao,
    private val archiver: TreeArchiver,
    private val comparator: TreeComparator,
    private val merger: TreeMerger,
    @Dispatcher(FtDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : ShareRepository {

    override suspend fun share(treeId: Long, uri: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val stream = context.contentResolver.openOutputStream(Uri.parse(uri))
                ?: error("That location could not be opened for writing.")
            stream.use { archiver.write(treeId, it).getOrThrow() }

            treeDao.get(treeId)?.let { tree ->
                treeDao.update(
                    tree.copy(grade = ShareGrades.afterSharing(TreeGrade.fromValue(tree.grade)).value),
                )
            }
            Unit
        }
    }

    override suspend fun receive(uri: String, fallbackTitle: String): Result<ReceivedShare> =
        withContext(ioDispatcher) {
            runCatching {
                val stream = context.contentResolver.openInputStream(Uri.parse(uri))
                    ?: error("That file could not be opened.")
                val restored = stream.use { archiver.read(it, fallbackTitle).getOrThrow() }

                val origin = candidateOrigins(restored.treeId).getOrDefault(emptyList()).firstOrNull()
                treeDao.get(restored.treeId)?.let { tree ->
                    treeDao.update(
                        tree.copy(grade = ShareGrades.onArrival(origin != null).value),
                    )
                }

                ReceivedShare(
                    treeId = restored.treeId,
                    title = restored.manifest.title,
                    originTreeId = origin,
                )
            }
        }

    override suspend fun compare(localTreeId: Long, incomingTreeId: Long): Result<TreeComparison> =
        withContext(ioDispatcher) {
            runCatching {
                val comparison = comparator.compare(localTreeId, incomingTreeId)
                treeDao.get(incomingTreeId)?.let { tree ->
                    treeDao.update(
                        tree.copy(
                            grade = ShareGrades.afterComparison(
                                TreeGrade.fromValue(tree.grade),
                                comparison.hasUpdates,
                            ).value,
                        ),
                    )
                }
                comparison
            }
        }

    override suspend fun applyUpdates(comparison: TreeComparison): Result<AppliedUpdates> =
        withContext(ioDispatcher) {
            runCatching {
                val result = merger.apply(comparison).getOrThrow()
                treeDao.get(comparison.incomingTreeId)?.let { tree ->
                    treeDao.update(
                        tree.copy(
                            grade = ShareGrades.afterUpdatesApplied(TreeGrade.fromValue(tree.grade)).value,
                        ),
                    )
                }
                AppliedUpdates(result.added, result.updated, result.removed)
            }
        }

    /**
     * Finds trees the received copy could have come from.
     *
     * Matched on shared cross-reference ids rather than on the title, which people rename
     * freely. A copy that shares most of its person ids with a tree here is a copy of it;
     * one that shares none is a different family altogether.
     */
    override suspend fun candidateOrigins(incomingTreeId: Long): Result<List<Long>> =
        withContext(ioDispatcher) {
            runCatching {
                val incomingIds = database.personDao().getAll(incomingTreeId)
                    .mapNotNull { it.gedcomId }
                    .toSet()
                if (incomingIds.isEmpty()) return@runCatching emptyList()

                treeDao.getAll()
                    .filter { it.id != incomingTreeId }
                    .mapNotNull { tree ->
                        val shared = database.personDao().getAll(tree.id)
                            .count { it.gedcomId in incomingIds }
                        val overlap = shared.toFloat() / incomingIds.size
                        if (overlap >= MATCH_THRESHOLD) tree.id to overlap else null
                    }
                    .sortedByDescending { it.second }
                    .map { it.first }
            }
        }

    private companion object {
        /**
         * How much of the incoming tree must already be here to call it a copy.
         *
         * Half is deliberately generous: a relative who added a whole branch still shares
         * most of the original ids, and treating their work as a stranger would make the
         * user merge it by hand.
         */
        const val MATCH_THRESHOLD = 0.5f
    }
}
