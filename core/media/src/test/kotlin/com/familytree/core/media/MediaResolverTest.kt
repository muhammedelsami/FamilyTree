package com.familytree.core.media

import androidx.test.core.app.ApplicationProvider
import com.familytree.core.model.MediaFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The resolution order is the whole point of this class, so it is pinned here.
 *
 * These cases are taken from what real GEDCOM files contain: exports from desktop programs
 * carry absolute Windows paths, exports from phones carry bare filenames, and a tree that
 * has travelled between the two carries both.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MediaResolverTest {

    private lateinit var resolver: MediaResolver
    private lateinit var storage: File

    private val treeId = 7L

    @Before
    fun setUp() {
        resolver = MediaResolver(
            context = ApplicationProvider.getApplicationContext(),
            ioDispatcher = Dispatchers.Unconfined,
        )
        storage = resolver.treeStorage(treeId)
        storage.deleteRecursively()
        storage.mkdirs()
    }

    private fun place(name: String, into: File = storage): File =
        File(into, name).apply { parentFile?.mkdirs(); writeText("content of $name") }

    @Test
    fun `a blank link resolves to nothing`() = runTest {
        assertFalse(resolver.resolve(treeId, null).exists)
        assertFalse(resolver.resolve(treeId, "  ").exists)
        assertEquals(MediaKind.NONE, resolver.resolve(treeId, null).kind)
    }

    @Test
    fun `a bare filename is found in the tree storage`() = runTest {
        place("nonna.jpg")
        val resolved = resolver.resolve(treeId, "nonna.jpg")
        assertTrue(resolved.source is MediaSource.LocalFile)
        assertEquals("nonna.jpg", resolved.name)
        assertEquals(MediaKind.IMAGE, resolved.kind)
        // The link matched exactly, so there is nothing to shorten.
        assertFalse(resolved.foundByFilename)
    }

    @Test
    fun `a windows path falls back to its filename in the tree storage`() = runTest {
        place("nonna.jpg")
        val resolved = resolver.resolve(treeId, """C:\Users\anna\Pictures\nonna.jpg""")
        assertTrue(resolved.source is MediaSource.LocalFile)
        // This is what the gallery's "shorten links" action acts on.
        assertTrue(resolved.foundByFilename)
    }

    @Test
    fun `an absolute path is used as given when it is readable`() = runTest {
        val outside = File(storage.parentFile, "outside").apply { mkdirs() }
        val file = place("scan.png", outside)
        val resolved = resolver.resolve(treeId, file.absolutePath)
        assertEquals(file, (resolved.source as MediaSource.LocalFile).file)
        assertFalse(resolved.foundByFilename)
        outside.deleteRecursively()
    }

    @Test
    fun `a path media folder is searched with the full link and then the filename`() = runTest {
        val folderDir = File(storage.parentFile, "album").apply { mkdirs() }
        place("holiday/1998/anna.jpg", folderDir)
        place("carlo.jpg", folderDir)
        val folders = listOf(MediaFolder(treeId = treeId, kind = MediaFolder.Kind.PATH, value = folderDir.absolutePath))

        // The link's own path, taken as relative to the granted folder.
        assertTrue(resolver.resolve(treeId, "holiday/1998/anna.jpg", folders).exists)
        // A foreign leading path, with the file sitting directly in the folder.
        val byName = resolver.resolve(treeId, "D:/old/pictures/carlo.jpg", folders)
        assertTrue(byName.exists)
        assertTrue(byName.foundByFilename)
        // But the filename alone is not hunted through subfolders: a path folder is
        // checked at two depths only, never scanned recursively.
        assertFalse(resolver.resolve(treeId, "D:/old/anna.jpg", folders).exists)

        folderDir.deleteRecursively()
    }

    @Test
    fun `a url is reported as a web source`() = runTest {
        val resolved = resolver.resolve(treeId, "https://example.org/photos/anna.jpg")
        assertEquals(MediaSource.Web("https://example.org/photos/anna.jpg"), resolved.source)
        assertEquals(MediaKind.IMAGE, resolved.kind)
        assertTrue(resolved.exists)
    }

    @Test
    fun `a missing file still reports its name and kind`() = runTest {
        // The tree came from another device and the file did not travel with it. The
        // gallery shows a typed placeholder, which is why this is not simply Missing.
        val resolved = resolver.resolve(treeId, """C:\docs\certificate.pdf""")
        assertFalse(resolved.exists)
        assertEquals("certificate.pdf", resolved.name)
        assertEquals(MediaKind.PDF, resolved.kind)
    }

    @Test
    fun `answers are cached until invalidated`() = runTest {
        assertFalse(resolver.resolve(treeId, "late.jpg").exists)
        place("late.jpg")
        // Still the cached miss: filesystem changes have to be announced.
        assertFalse(resolver.resolve(treeId, "late.jpg").exists)
        resolver.invalidate(treeId)
        assertTrue(resolver.resolve(treeId, "late.jpg").exists)
    }

    @Test
    fun `an answer found without folders does not shadow one found with them`() = runTest {
        val folderDir = File(storage.parentFile, "granted").apply { mkdirs() }
        place("late.png", folderDir)
        val folders = listOf(
            MediaFolder(id = 1L, treeId = treeId, kind = MediaFolder.Kind.PATH, value = folderDir.absolutePath),
        )

        // A screen asks before the tree's folders have loaded, which is the normal order
        // of events, and gets a miss.
        assertFalse(resolver.resolve(treeId, "late.png", emptyList()).exists)
        // The folders arrive a moment later. That earlier miss must not be reused, or the
        // photograph would stay invisible until the app was restarted.
        assertTrue(resolver.resolve(treeId, "late.png", folders).exists)

        folderDir.deleteRecursively()
    }

    @Test
    fun `caches are kept apart per tree`() = runTest {
        place("shared.jpg")
        assertTrue(resolver.resolve(treeId, "shared.jpg").exists)
        // Another tree has its own folder, so the same link resolves differently.
        assertFalse(resolver.resolve(99L, "shared.jpg").exists)
    }

    @Test
    fun `file kinds are read from the extension`() {
        assertEquals(MediaKind.IMAGE, kindOf(extensionOf("a.JPEG")))
        assertEquals(MediaKind.VIDEO, kindOf(extensionOf("clip.mov")))
        assertEquals(MediaKind.PDF, kindOf(extensionOf("deed.pdf")))
        assertEquals(MediaKind.DOCUMENT, kindOf(extensionOf("notes.txt")))
        assertEquals(MediaKind.NONE, kindOf(extensionOf("no-extension")))
        // A trailing dot is not an extension.
        assertEquals(null, extensionOf("odd."))
    }
}
