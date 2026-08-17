package com.familytree.core.media

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Numbering copies is small but easy to get wrong, and it renames the user's files. */
class FileNamingTest {

    @Test
    fun `a numbered suffix goes before the extension`() {
        assertEquals("scan (1).jpg", incrementName("scan.jpg"))
        assertEquals("scan (2).jpg", incrementName("scan (1).jpg"))
        assertEquals("scan (11).jpg", incrementName("scan (10).jpg"))
    }

    @Test
    fun `a name without an extension is still numbered`() {
        assertEquals("README (1)", incrementName("README"))
        assertEquals("README (2)", incrementName("README (1)"))
    }

    @Test
    fun `a leading dot is part of the name, not an extension`() {
        // ".profile (1)" is right; ".profile (1)" split at the first dot would give " (1).profile".
        assertEquals(".profile (1)", incrementName(".profile"))
    }

    @Test
    fun `only a trailing parenthesised number counts as a counter`() {
        assertEquals("photo 2019 (1).png", incrementName("photo 2019.png"))
        assertEquals("photo (2019) (2).png", incrementName("photo (2019) (1).png"))
        // A year in brackets at the end is indistinguishable from a counter, so it gets
        // incremented — the same behaviour as the original, and harmless: the result is
        // only used when that name is already taken.
        assertEquals("photo (2020).png", incrementName("photo (2019).png"))
    }

    @Test
    fun `an occupied name is skipped until a free one is found`() {
        val folder = Files.createTempDirectory("naming").toFile()
        File(folder, "scan.jpg").writeText("a")
        File(folder, "scan (1).jpg").writeText("b")

        assertEquals("scan (2).jpg", nextAvailableFile(folder, "scan.jpg").name)
        assertEquals("free.jpg", nextAvailableFile(folder, "free.jpg").name)

        folder.deleteRecursively()
    }
}
