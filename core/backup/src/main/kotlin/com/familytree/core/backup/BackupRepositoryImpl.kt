package com.familytree.core.backup

import android.content.Context
import com.familytree.core.common.di.Dispatcher
import com.familytree.core.common.di.FtDispatcher
import com.familytree.core.database.dao.TreeDao
import com.familytree.core.domain.repository.BackupFile
import com.familytree.core.domain.repository.BackupRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps a handful of recent archives per tree in the app's own storage.
 *
 * Only a handful: a family tree with photographs is not small, and an unbounded backup
 * folder eventually fills the phone and gets the whole app uninstalled. Three is enough
 * to recover from the mistake someone noticed today, yesterday, or last week.
 */
@Singleton
class BackupRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val archiver: TreeArchiver,
    private val treeDao: TreeDao,
    @Dispatcher(FtDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : BackupRepository {

    override val keptPerTree: Int = KEPT

    private val json = Json { ignoreUnknownKeys = true }

    private val root: File get() = File(context.filesDir, "backups").apply { mkdirs() }

    private fun folderFor(treeId: Long) = File(root, treeId.toString()).apply { mkdirs() }

    override fun observeBackups(treeId: Long): Flow<List<BackupFile>> = flow {
        emit(listBackups(treeId))
    }.flowOn(ioDispatcher)

    private fun listBackups(treeId: Long): List<BackupFile> =
        folderFor(treeId).listFiles { file -> file.extension == EXTENSION }
            .orEmpty()
            .mapNotNull { it.describe(treeId) }
            .sortedByDescending { it.createdAt }

    /**
     * Reads the manifest back out rather than trusting the file name.
     *
     * A file copied in by hand, or written by an older version, still has to be listable —
     * and the name alone cannot say how many people are inside.
     */
    private fun File.describe(treeId: Long): BackupFile? = runCatching {
        val manifest = ZipFile(this).use { zip ->
            zip.getEntry(BackupManifest.FILE_NAME)?.let { entry ->
                zip.getInputStream(entry).use {
                    json.decodeFromString(BackupManifest.serializer(), it.readBytes().decodeToString())
                }
            }
        }
        BackupFile(
            id = "$treeId/$name",
            treeId = treeId,
            title = manifest?.title ?: nameWithoutExtension,
            createdAt = manifest?.createdAt ?: lastModified(),
            sizeBytes = length(),
            personCount = manifest?.personCount ?: 0,
            mediaCount = manifest?.mediaCount ?: 0,
        )
    }.getOrNull()

    override suspend fun backUp(treeId: Long): Result<BackupFile> = withContext(ioDispatcher) {
        runCatching {
            val folder = folderFor(treeId)
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(System.currentTimeMillis())
            val file = File(folder, "backup-$stamp.$EXTENSION")

            file.outputStream().use { archiver.write(treeId, it).getOrThrow() }
            prune(folder)

            file.describe(treeId) ?: error("The archive could not be read back.")
        }
    }

    /** Drops the oldest archives beyond [KEPT], by file time rather than by name. */
    private fun prune(folder: File) {
        folder.listFiles { file -> file.extension == EXTENSION }
            .orEmpty()
            .sortedByDescending { it.lastModified() }
            .drop(KEPT)
            .forEach { it.delete() }
    }

    override suspend fun exportTo(treeId: Long, uri: String): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                val stream = context.contentResolver.openOutputStream(android.net.Uri.parse(uri))
                    ?: error("That location could not be opened for writing.")
                stream.use { archiver.write(treeId, it).getOrThrow() }
                Unit
            }
        }

    override suspend fun restore(backupId: String): Result<Long> = withContext(ioDispatcher) {
        runCatching {
            val (treeId, name) = backupId.split('/', limit = 2)
                .takeIf { it.size == 2 } ?: error("Unknown backup.")
            val file = File(folderFor(treeId.toLong()), name)
            if (!file.isFile) error("That backup is no longer on this device.")
            file.inputStream().use { archiver.read(it, file.nameWithoutExtension).getOrThrow() }.treeId
        }
    }

    override suspend fun restoreFrom(uri: String, fallbackTitle: String): Result<Long> =
        withContext(ioDispatcher) {
            runCatching {
                val stream = context.contentResolver.openInputStream(android.net.Uri.parse(uri))
                    ?: error("That file could not be opened.")
                stream.use { archiver.read(it, fallbackTitle).getOrThrow() }.treeId
            }
        }

    override suspend fun delete(backupId: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val (treeId, name) = backupId.split('/', limit = 2)
                .takeIf { it.size == 2 } ?: error("Unknown backup.")
            File(folderFor(treeId.toLong()), name).delete()
            Unit
        }
    }

    override suspend fun backUpEnabledTrees(): Result<Int> = withContext(ioDispatcher) {
        runCatching {
            treeDao.getAll().filter { it.backupEnabled }.count { tree ->
                // One tree's failure must not stop the rest: a tree with an unreadable
                // media file should not cost every other tree its backup.
                backUp(tree.id).isSuccess
            }
        }
    }

    override suspend fun setAutomaticBackup(treeId: Long, enabled: Boolean) =
        withContext(ioDispatcher) {
            treeDao.get(treeId)?.let { treeDao.update(it.copy(backupEnabled = enabled)) } ?: Unit
        }

    private companion object {
        const val KEPT = 3
        const val EXTENSION = "zip"
    }
}
