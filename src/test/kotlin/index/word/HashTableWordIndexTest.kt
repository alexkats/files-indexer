package index.word

import helpers.FileHelper
import org.junit.jupiter.api.io.TempDir
import parser.DelimiterWordParser
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HashTableWordIndexTest {

    private val wordParser = DelimiterWordParser()

    @Test
    fun testBasicIndexing(@TempDir tempDir: Path) {
        val index = HashTableWordIndex(wordParser)
        index.createIndexForRoot(tempDir)

        val file = FileHelper.createOrUpdateFile(tempDir, fileName = "a", content = "aaa bbb")
        val timestamp = file.toFile().lastModified()
        index.indexFile(tempDir, file, timestamp)

        assertTrue(index.isFileIndexed(tempDir, file))
        assertTrue(index.isFileIndexedWithTimestamp(tempDir, file, timestamp))
        index.assertExpected(setOf(file), "aaa")
        index.assertExpected(setOf(file), "bbb")
        index.assertEmpty("aaa bbb")
    }

    @Test
    fun testIndexRemoval(@TempDir tempDir: Path) {
        val index = HashTableWordIndex(wordParser)
        index.createIndexForRoot(tempDir)

        val file = FileHelper.createOrUpdateFile(tempDir, fileName = "a", content = "aaa bbb")
        val timestamp = file.toFile().lastModified()
        index.indexFile(tempDir, file, timestamp)
        index.deleteIndexForFile(tempDir, file)

        assertFalse(index.isFileIndexed(tempDir, file))
        index.assertEmpty("aaa")
        index.assertEmpty("bbb")
        index.assertEmpty("aaa bbb")
    }

    @Test
    fun testRootRemoval(@TempDir tempDir: Path) {
        val index = HashTableWordIndex(wordParser)
        index.createIndexForRoot(tempDir)

        val file = FileHelper.createOrUpdateFile(tempDir, fileName = "a", content = "aaa bbb")
        val timestamp = file.toFile().lastModified()
        index.indexFile(tempDir, file, timestamp)
        index.deleteIndexForRoot(tempDir)

        assertFalse(index.isFileIndexed(tempDir, file))
        index.assertEmpty("aaa")
        index.assertEmpty("bbb")
        index.assertEmpty("aaa bbb")
    }

    @Test
    fun testMultipleFiles(@TempDir tempDir: Path) {
        val index = HashTableWordIndex(wordParser)
        index.createIndexForRoot(tempDir)

        val file1 = FileHelper.createOrUpdateFile(tempDir, fileName = "a", content = "aaa bbb")
        val file2 = FileHelper.createOrUpdateFile(tempDir, fileName = "b", content = "ccc ddd aaa")
        val timestamp1 = file1.toFile().lastModified()
        val timestamp2 = file2.toFile().lastModified()
        index.indexFile(tempDir, file1, timestamp1)
        index.indexFile(tempDir, file2, timestamp2)

        assertTrue(index.isFileIndexed(tempDir, file1))
        assertTrue(index.isFileIndexed(tempDir, file2))

        index.assertExpected(setOf(file1, file2), "aaa")
        index.assertExpected(setOf(file1), "bbb")
        index.assertExpected(setOf(file2), "ccc")
        index.assertExpected(setOf(file2), "ddd")

        index.deleteIndexForFile(tempDir, file1)

        index.assertExpected(setOf(file2), "aaa")
        index.assertEmpty("bbb")

        index.deleteIndexForFile(tempDir, file2)

        index.assertEmpty("ccc")
        index.assertEmpty("ddd")
    }

    @Test
    fun testReindexing(@TempDir tempDir: Path) {
        val index = HashTableWordIndex(wordParser)
        index.createIndexForRoot(tempDir)

        val file = FileHelper.createOrUpdateFile(tempDir, fileName = "a", content = "aaa bbb")
        val timestamp1 = file.toFile().lastModified()
        index.indexFile(tempDir, file, timestamp1)
        FileHelper.createOrUpdateFile(tempDir, fileName = "a", content = "ccc ddd")
        val timestamp2 = file.toFile().lastModified()
        index.indexFile(tempDir, file, timestamp2)

        assertTrue(index.isFileIndexedWithTimestamp(tempDir, file, timestamp2))
        index.assertEmpty("aaa")
        index.assertEmpty("bbb")
        index.assertExpected(setOf(file), "ccc")
        index.assertExpected(setOf(file), "ddd")

        index.deleteIndexForFile(tempDir, file)

        assertFalse(index.isFileIndexed(tempDir, file))
        index.assertEmpty("ccc")
        index.assertEmpty("ddd")

        index.indexFile(tempDir, file, timestamp2)

        assertTrue(index.isFileIndexedWithTimestamp(tempDir, file, timestamp2))
        index.assertExpected(setOf(file), "ccc")
        index.assertExpected(setOf(file), "ddd")
    }

    private fun WordIndex.assertExpected(expected: Set<Path>, word: String) {
        assertEquals(expected, queryIndex(word), "Index for word \"$word\" is unexpected")
    }

    private fun WordIndex.assertEmpty(word: String) {
        assertEquals(setOf(), queryIndex(word), "Index is not empty for word \"$word\"")
    }
}