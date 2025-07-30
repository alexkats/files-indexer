package index.file

import helpers.FileHelper
import index.word.WordIndex
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import parser.DelimiterWordParser
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class FileIndexerTest {

    private val wordParser = DelimiterWordParser()

    @Test
    fun testBasicFileIndexing(@TempDir tempDir: Path) {
        runTest {
            val file = FileHelper.createOrUpdateFile(tempDir, fileName = "a", content = "aaa")
            val channel = Channel<FileChangeEvent>(Channel.Factory.UNLIMITED)
            val fileIndexer = FileIndexerImpl(wordParser, channel, backgroundScope)
            fileIndexer.createRoot(tempDir)
            fileIndexer.createOrUpdateFile(tempDir, file, file.toFile().lastModified())

            assertEquals(setOf(file), fileIndexer.query("aaa"))
        }
    }

    @Test
    fun testIndexDeleted(@TempDir tempDir: Path) {
        runTest {
            val file = FileHelper.createOrUpdateFile(tempDir, fileName = "a", content = "aaa")
            val channel = Channel<FileChangeEvent>(Channel.Factory.UNLIMITED)
            val fileIndexer = FileIndexerImpl(wordParser, channel, backgroundScope)
            fileIndexer.createRoot(tempDir)
            fileIndexer.createOrUpdateFile(tempDir, file, file.toFile().lastModified())

            assertEquals(setOf(file), fileIndexer.query("aaa"))
            FileHelper.deleteFile(tempDir, fileName = "a")
            fileIndexer.deleteFile(tempDir, file)

            assertEquals(setOf(), fileIndexer.query("aaa"))
            val wordIndex = fileIndexer::class.java.getDeclaredField("wordIndex").also { it.isAccessible = true }
                .get(fileIndexer) as WordIndex
            assertFalse(wordIndex.isFileIndexed(tempDir, file))
        }
    }

    @Test
    fun testIndexNotDeletedIfFileIsPresent(@TempDir tempDir: Path) {
        runTest {
            val file = FileHelper.createOrUpdateFile(tempDir, fileName = "a", content = "aaa")
            val channel = Channel<FileChangeEvent>(Channel.Factory.UNLIMITED)
            val fileIndexer = FileIndexerImpl(wordParser, channel, backgroundScope)
            fileIndexer.createRoot(tempDir)
            fileIndexer.createOrUpdateFile(tempDir, file, file.toFile().lastModified())

            assertEquals(setOf(file), fileIndexer.query("aaa"))
            fileIndexer.deleteFile(tempDir, file)

            assertEquals(setOf(file), fileIndexer.query("aaa"))
        }
    }

    @Test
    fun testReindexing(@TempDir tempDir: Path) {
        runTest {
            val file = FileHelper.createOrUpdateFile(tempDir, fileName = "a", content = "aaa")
            val channel = Channel<FileChangeEvent>(Channel.Factory.UNLIMITED)
            val fileIndexer = FileIndexerImpl(wordParser, channel, backgroundScope)
            fileIndexer.createRoot(tempDir)
            fileIndexer.createOrUpdateFile(tempDir, file, file.toFile().lastModified())

            assertEquals(setOf(file), fileIndexer.query("aaa"))
            FileHelper.createOrUpdateFile(tempDir, fileName = "a", content = "bbb")
            fileIndexer.createOrUpdateFile(tempDir, file, file.toFile().lastModified())

            assertEquals(setOf(), fileIndexer.query("aaa"))
            assertEquals(setOf(file), fileIndexer.query("bbb"))
        }
    }

    @Test
    fun testMultipleFilesWithSameContent(@TempDir tempDir: Path) {
        runTest {
            // Create 2 files with the same content
            val file1 = FileHelper.createOrUpdateFile(tempDir, fileName = "a", content = "aaa")
            val file2 = FileHelper.createOrUpdateFile(tempDir, fileName = "b", content = "aaa")
            val channel = Channel<FileChangeEvent>(Channel.Factory.UNLIMITED)
            val fileIndexer = FileIndexerImpl(wordParser, channel, backgroundScope)
            fileIndexer.createRoot(tempDir)

            // Start tracking
            fileIndexer.createOrUpdateFile(tempDir, file1, file1.toFile().lastModified())
            fileIndexer.createOrUpdateFile(tempDir, file2, file2.toFile().lastModified())

            assertEquals(setOf(file1, file2), fileIndexer.query("aaa"))
            FileHelper.deleteFile(tempDir, "a")
            fileIndexer.deleteFile(tempDir, file1)

            // Check that after first file deletion, we still have correct index
            assertEquals(setOf(file2), fileIndexer.query("aaa"))
            FileHelper.deleteFile(tempDir, "b")
            fileIndexer.deleteFile(tempDir, file2)

            // Create another file with the same content
            val file3 = FileHelper.createOrUpdateFile(tempDir, fileName = "c", content = "aaa")
            assertEquals(setOf(), fileIndexer.query("aaa"))

            // Check that we have a result after starting tracking the file
            fileIndexer.createOrUpdateFile(tempDir, file3, file3.toFile().lastModified())
            assertEquals(setOf(file3), fileIndexer.query("aaa"))
        }
    }
}