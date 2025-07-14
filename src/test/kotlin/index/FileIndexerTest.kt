package index

import helpers.FileHelper
import helpers.wait
import helpers.waitCondition
import index.file.EventType
import index.file.FileChangeEvent
import index.file.FileIndexerImpl
import index.hash.DefaultFileHasher
import index.word.WordIndex
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import parser.DelimiterWordParser
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import kotlin.time.TimeSource

@OptIn(ExperimentalCoroutinesApi::class)
class FileIndexerTest {

    private val wordParser = DelimiterWordParser()
    private val fileHasher = DefaultFileHasher()

    @Test
    fun testBasicFileIndexing(@TempDir tempDir: Path) {
        runTest {
            val file = FileHelper.createOrUpdateFile(tempDir, fileName = "a", content = "aaa")
            val channel = Channel<FileChangeEvent>(Channel.UNLIMITED)
            val fileIndexer = FileIndexerImpl(wordParser, channel, backgroundScope, TimeSource.Monotonic.markNow())
            fileIndexer.createOrUpdateFile(file)

            assertEquals(setOf(file.toString()), fileIndexer.query("aaa"))
        }
    }

    @Test
    fun testIndexDeletedAfterTtl(@TempDir tempDir: Path) {
        runTest {
            val file = FileHelper.createOrUpdateFile(tempDir, fileName = "a", content = "aaa")
            val hash = fileHasher.hash(file)
            val channel = Channel<FileChangeEvent>(Channel.UNLIMITED)
            val fileIndexer = FileIndexerImpl(wordParser, channel, backgroundScope, TimeSource.Monotonic.markNow())
            fileIndexer.createOrUpdateFile(file)
            advanceTimeBy(FileIndexerImpl.STALE_HASH_TIMEOUT)
            runCurrent()

            assertEquals(setOf(file.toString()), fileIndexer.query("aaa"))
            fileIndexer.deleteFile(file)

            assertEquals(setOf(), fileIndexer.query("aaa"))
            val wordIndex = fileIndexer::class.java.getDeclaredField("wordIndex").also { it.isAccessible = true }
                .get(fileIndexer) as WordIndex
            assertTrue(wordIndex.isFileHashIndexed(hash))
            advanceTimeBy(FileIndexerImpl.STALE_HASH_TIMEOUT)
            runCurrent()
            assertFalse(wordIndex.isFileHashIndexed(hash))
        }
    }

    @Test
    fun testIndexNotDeletedBeforeTtl(@TempDir tempDir: Path) {
        runTest {
            // Create a file with some content
            val file = FileHelper.createOrUpdateFile(tempDir, fileName = "a", content = "aaa")
            val hash = fileHasher.hash(file)
            val channel = Channel<FileChangeEvent>(Channel.UNLIMITED)
            val fileIndexer = FileIndexerImpl(wordParser, channel, backgroundScope, TimeSource.Monotonic.markNow())
            val wordIndex = fileIndexer::class.java.getDeclaredField("wordIndex").also { it.isAccessible = true }
                .get(fileIndexer) as WordIndex

            // Start tracking the file, then delete it. Check that the hash is still physically indexed
            fileIndexer.createOrUpdateFile(file)
            fileIndexer.deleteFile(file)
            assertTrue(wordIndex.isFileHashIndexed(hash))

            // Check that the hash is indexed if timeout hasn't passed yet
            advanceTimeBy(FileIndexerImpl.STALE_HASH_TIMEOUT - 5.seconds)
            runCurrent()
            assertTrue(wordIndex.isFileHashIndexed(hash))

            // Recreate a file, also check that even after timeout hash is not considered stale and not removed
            fileIndexer.createOrUpdateFile(file)
            assertEquals(setOf(file.toString()), fileIndexer.query("aaa"))
            advanceTimeBy(FileIndexerImpl.STALE_HASH_TIMEOUT)
            runCurrent()
            assertEquals(setOf(file.toString()), fileIndexer.query("aaa"))
        }
    }

    @Test
    fun testMultipleFilesWithSameHash(@TempDir tempDir: Path) {
        runTest {
            // Create 2 files with the same content
            val file1 = FileHelper.createOrUpdateFile(tempDir, fileName = "a", content = "aaa")
            val file2 = FileHelper.createOrUpdateFile(tempDir, fileName = "b", content = "aaa")
            val channel = Channel<FileChangeEvent>(Channel.UNLIMITED)
            val fileIndexer = FileIndexerImpl(wordParser, channel, backgroundScope, TimeSource.Monotonic.markNow())
            val wordIndex = fileIndexer::class.java.getDeclaredField("wordIndex").also { it.isAccessible = true }
                .get(fileIndexer) as WordIndex

            // Start tracking
            fileIndexer.createOrUpdateFile(file1)
            fileIndexer.createOrUpdateFile(file2)

            assertEquals(setOf(file1.toString(), file2.toString()), fileIndexer.query("aaa"))

            fileIndexer.deleteFile(file1)
            advanceTimeBy(FileIndexerImpl.STALE_HASH_TIMEOUT)
            runCurrent()

            // Check that after first file deletion and timeout, we still have correct index
            assertEquals(setOf(file2.toString()), fileIndexer.query("aaa"))
            fileIndexer.deleteFile(file2)
            advanceTimeBy(FileIndexerImpl.STALE_HASH_TIMEOUT - 5.seconds)
            runCurrent()

            // Create another file with the same content
            val file3 = FileHelper.createOrUpdateFile(tempDir, fileName = "c", content = "aaa")
            val hash = fileHasher.hash(file3)

            // Check that the hash is still physically indexed, but not result since we're not tracking the file
            assertEquals(setOf(), fileIndexer.query("aaa"))
            assertTrue(wordIndex.isFileHashIndexed(hash))

            // Check that we have a result after starting tracking the file and even after timeout
            fileIndexer.createOrUpdateFile(file3)
            advanceTimeBy(FileIndexerImpl.STALE_HASH_TIMEOUT)
            runCurrent()
            assertTrue(wordIndex.isFileHashIndexed(hash))
            assertEquals(setOf(file3.toString()), fileIndexer.query("aaa"))
        }
    }

    @Test
    fun testTimestamps(@TempDir tempDir: Path) {
        runTest {
            // Basically, we want to check two things:
            // 1. We won't process events with earlier timestamps than already processed
            // 2. Events are properly cleaned up after ttl has passed
            val channel = Channel<FileChangeEvent>(Channel.UNLIMITED)
            val testTimeSource = TestTimeSource()
            val fileIndexer = FileIndexerImpl(
                wordParser,
                channel,
                backgroundScope,
                testTimeSource.markNow(),
                StandardTestDispatcher(testScheduler)
            )
            val file = FileHelper.createOrUpdateFile(tempDir, fileName = "a", content = "aaa")

            // Check part 1
            channel.send(FileChangeEvent(file, EventType.UPDATE, 0.seconds))

            waitCondition(2.seconds) { fileIndexer.query("aaa").isNotEmpty() }
            assertEquals(setOf(file.toString()), fileIndexer.query("aaa"))

            FileHelper.createOrUpdateFile(tempDir, fileName = "a", content = "bbb")
            channel.send(FileChangeEvent(file, EventType.UPDATE, 2.seconds))

            waitCondition(2.seconds) { fileIndexer.query("bbb").isNotEmpty() }
            assertEquals(setOf(), fileIndexer.query("aaa"))
            assertEquals(setOf(file.toString()), fileIndexer.query("bbb"))

            FileHelper.createOrUpdateFile(tempDir, fileName = "a", content = "ccc")
            channel.send(FileChangeEvent(file, EventType.UPDATE, 1.seconds))

            wait(1.seconds)
            assertEquals(setOf(), fileIndexer.query("aaa"))
            assertEquals(setOf(file.toString()), fileIndexer.query("bbb"))
            assertEquals(setOf(), fileIndexer.query("ccc"))


            // Check part 2
            testTimeSource += FileIndexerImpl.FILES_LAST_EVENT_CACHE_TTL + 2.seconds
            channel.send(FileChangeEvent(file, EventType.UPDATE, 1.seconds))

            waitCondition(2.seconds) { fileIndexer.query("ccc").isNotEmpty() }
            assertEquals(setOf(), fileIndexer.query("aaa"))
            assertEquals(setOf(), fileIndexer.query("bbb"))
            assertEquals(setOf(file.toString()), fileIndexer.query("ccc"))
        }
    }
}