package com.familytree.core.backup

import com.familytree.core.common.di.Dispatcher
import com.familytree.core.common.di.FtDispatcher
import com.familytree.core.database.dao.PersonDao
import com.familytree.core.database.dao.TreeDao
import com.familytree.core.gedcom.GedcomExporter
import com.familytree.core.gedcom.GedcomImporter
import com.familytree.core.media.MediaResolver
import com.familytree.core.model.TreeGrade
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** What a restored archive turned into. */
data class RestoredTree(val treeId: Long, val manifest: BackupManifest, val mediaRestored: Int)

/**
 * Packs a tree and its photographs into a single file, and unpacks one again.
 *
 * The archive is the app's answer to two different needs at once: a restore point, and
 * something to hand to a relative. Both want the same thing — everything in one file that
 * still means something in five years — so they share one format.
 */
@Singleton
class TreeArchiver @Inject constructor(
    private val treeDao: TreeDao,
    private val personDao: PersonDao,
    private val exporter: GedcomExporter,
    private val importer: GedcomImporter,
    private val resolver: MediaResolver,
    @Dispatcher(FtDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        // Defaults are written out too, so the manifest always states its version and
        // grade. A format marker that only appears when it differs from the default is
        // no marker at all when someone is trying to work out why an archive will not open.
        encodeDefaults = true
    }

    suspend fun write(treeId: Long, output: OutputStream): Result<BackupManifest> =
        withContext(ioDispatcher) {
            runCatching {
                val tree = treeDao.get(treeId) ?: error("No such tree.")
                val manifest = BackupManifest(
                    title = tree.title,
                    createdAt = System.currentTimeMillis(),
                    personCount = tree.personCount,
                    familyCount = tree.familyCount,
                    mediaCount = tree.mediaCount,
                    generationCount = tree.generationCount,
                    grade = tree.grade,
                    // Stored as the GEDCOM id, not the row id: a restore creates fresh
                    // row ids, so the only stable way to name a person is the id the
                    // GEDCOM file itself uses.
                    rootGedcomId = tree.rootPersonId?.let { personDao.get(it)?.gedcomId },
                    shareRootGedcomId = tree.shareRootPersonId?.let { personDao.get(it)?.gedcomId },
                )

                ZipOutputStream(output.buffered()).use { zip ->
                    zip.putNextEntry(ZipEntry(BackupManifest.FILE_NAME))
                    zip.write(json.encodeToString(BackupManifest.serializer(), manifest).toByteArray())
                    zip.closeEntry()

                    zip.putNextEntry(ZipEntry(BackupManifest.GEDCOM_NAME))
                    // Not closed by the exporter: closing here would end the archive after
                    // its first entry.
                    exporter.export(treeId, NonClosingOutputStream(zip))
                    zip.closeEntry()

                    val storage = resolver.treeStorage(treeId)
                    storage.walkTopDown().filter(File::isFile).forEach { file ->
                        zip.putNextEntry(ZipEntry(BackupManifest.MEDIA_PREFIX + file.relativeTo(storage).path))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
                manifest
            }
        }

    /**
     * Unpacks an archive into a brand-new tree.
     *
     * Always new, never in place: restoring over the tree that is already open would
     * destroy whatever has happened since the backup was taken, and by the time someone
     * notices, the backup they wanted is the one they just overwrote.
     */
    suspend fun read(input: InputStream, fallbackTitle: String): Result<RestoredTree> =
        withContext(ioDispatcher) {
            runCatching {
                // Two passes over the archive would need it seekable, which a content
                // stream is not, so entries are staged in a scratch directory first.
                val staging = createTempDir()
                try {
                    var manifest: BackupManifest? = null
                    var gedcom: File? = null
                    // Kept as (staged file, path inside the archive). Deriving the path
                    // back out with relativeTo would be fragile: canonicalising resolves
                    // symlinks, and the resolved path no longer sits under the
                    // unresolved staging root.
                    val media = mutableListOf<Pair<File, String>>()

                    ZipInputStream(input.buffered()).use { zip ->
                        var entry: ZipEntry? = zip.nextEntry
                        while (entry != null) {
                            val name = entry.name
                            when {
                                entry.isDirectory -> Unit

                                name == BackupManifest.FILE_NAME ->
                                    manifest = json.decodeFromString(
                                        BackupManifest.serializer(),
                                        zip.readBytes().decodeToString(),
                                    )

                                name == BackupManifest.GEDCOM_NAME ->
                                    gedcom = File(staging, "tree.ged").also { file ->
                                        file.outputStream().use { zip.copyTo(it) }
                                    }

                                name.startsWith(BackupManifest.MEDIA_PREFIX) -> {
                                    val relative = name.removePrefix(BackupManifest.MEDIA_PREFIX)
                                    // An archive from elsewhere could name an entry
                                    // "../../…" and write outside the destination; the
                                    // path is checked rather than trusted.
                                    val target = File(staging, "media/$relative").canonicalFile
                                    val root = File(staging, "media").canonicalFile
                                    if (target.startsWith(root)) {
                                        target.parentFile?.mkdirs()
                                        target.outputStream().use { zip.copyTo(it) }
                                        media += target to relative
                                    }
                                }
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }

                    val found = gedcom ?: error("This archive holds no family data.")
                    val details = manifest
                    val treeId = importer.importFile(found, details?.title ?: fallbackTitle)

                    // Media goes in after the import, because the destination folder is
                    // named after the tree id and that only exists once the tree does.
                    val storage = resolver.treeStorage(treeId)
                    media.forEach { (file, relative) ->
                        val destination = File(storage, relative)
                        destination.parentFile?.mkdirs()
                        file.copyTo(destination, overwrite = true)
                    }
                    resolver.invalidate(treeId)

                    details?.let { restoreTreeState(treeId, it) }

                    RestoredTree(
                        treeId = treeId,
                        manifest = details ?: BackupManifest(
                            title = fallbackTitle,
                            createdAt = System.currentTimeMillis(),
                        ),
                        mediaRestored = media.size,
                    )
                } finally {
                    staging.deleteRecursively()
                }
            }
        }

    /**
     * Puts back the parts of a tree that GEDCOM has no room for.
     *
     * The sharing grade is the one that matters: a restored copy of a received tree must
     * still count as received, or the state machine would let it become an original again
     * and every later share would be misjudged.
     */
    private suspend fun restoreTreeState(treeId: Long, manifest: BackupManifest) {
        val tree = treeDao.get(treeId) ?: return
        suspend fun rowIdOf(gedcomId: String?): Long? =
            gedcomId?.let { personDao.getByGedcomId(treeId, it)?.id }

        treeDao.update(
            tree.copy(
                grade = TreeGrade.fromValue(manifest.grade).value,
                rootPersonId = rowIdOf(manifest.rootGedcomId) ?: tree.rootPersonId,
                shareRootPersonId = rowIdOf(manifest.shareRootGedcomId),
            ),
        )
    }

    private fun createTempDir(): File =
        File.createTempFile("restore", null).let { file ->
            file.delete()
            file.mkdirs()
            file
        }
}

/**
 * Hands a stream to something that will close it, without letting it.
 *
 * The GEDCOM exporter closes what it is given, which is right everywhere else; inside an
 * archive it would end the file after the first entry.
 */
private class NonClosingOutputStream(private val delegate: OutputStream) : OutputStream() {
    override fun write(b: Int) = delegate.write(b)
    override fun write(b: ByteArray, off: Int, len: Int) = delegate.write(b, off, len)
    override fun flush() = delegate.flush()
    override fun close() = flush()
}
