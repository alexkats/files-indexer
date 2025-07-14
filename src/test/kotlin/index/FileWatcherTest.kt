package index

import helpers.FileHelper
import helpers.wait
import helpers.waitCondition
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import parser.DelimiterWordParser
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class FileWatcherTest {

    private val wordParser = DelimiterWordParser()

    @Test
    fun testBasicDirectoryWatching(@TempDir tempDir: Path) {
        runTest {
            val filesWatcher = FilesWatcherImpl(wordParser, backgroundScope)
            val directory = FileHelper.createDirectory(tempDir, Paths.get("dir"))
            val file = FileHelper.createOrUpdateFile(tempDir, Paths.get("dir", "a"), "aaa")

            assertEquals(setOf(), filesWatcher.getCurrentlyWatchedSnapshot())
            assertEquals(setOf(), filesWatcher.queryIndex("aaa"))

            filesWatcher.startWatching(directory)
            waitCondition(2.seconds) { filesWatcher.queryIndex("aaa").isNotEmpty() }
            assertEquals(setOf(directory), filesWatcher.getCurrentlyWatchedSnapshot())
            assertEquals(setOf(file.toString()), filesWatcher.queryIndex("aaa"))

            filesWatcher.startWatching(directory)
            assertEquals(setOf(directory), filesWatcher.getCurrentlyWatchedSnapshot())
            assertEquals(setOf(file.toString()), filesWatcher.queryIndex("aaa"))

            filesWatcher.stopWatching(directory)
            waitCondition(2.seconds) { filesWatcher.queryIndex("aaa").isEmpty() }
            assertEquals(setOf(), filesWatcher.getCurrentlyWatchedSnapshot())
            assertEquals(setOf(), filesWatcher.queryIndex("aaa"))
        }
    }

    @Test
    fun testWatchingParent(@TempDir tempDir: Path) {
        runTest {
            val filesWatcher = FilesWatcherImpl(wordParser, backgroundScope)
            val child1Directory = FileHelper.createDirectory(tempDir, Paths.get("parent", "child1"))
            val child2Directory = FileHelper.createDirectory(tempDir, Paths.get("parent", "child2"))
            val parentDirectory = tempDir.resolve("parent")
            val fileChild1 = FileHelper.createOrUpdateFile(tempDir, Paths.get("parent", "child1", "b"), "aaa")
            val fileChild2 = FileHelper.createOrUpdateFile(tempDir, Paths.get("parent", "child2", "b"), "aaa")
            val fileParent = FileHelper.createOrUpdateFile(tempDir, Paths.get("parent", "a"), "aaa")

            filesWatcher.startWatching(child1Directory)
            waitCondition(2.seconds) { filesWatcher.queryIndex("aaa").isNotEmpty() }
            assertEquals(setOf(child1Directory), filesWatcher.getCurrentlyWatchedSnapshot())
            assertEquals(setOf(fileChild1.toString()), filesWatcher.queryIndex("aaa"))

            filesWatcher.startWatching(child2Directory)
            waitCondition(2.seconds) { filesWatcher.queryIndex("aaa").size == 2 }
            assertEquals(setOf(child1Directory, child2Directory), filesWatcher.getCurrentlyWatchedSnapshot())
            assertEquals(setOf(fileChild1.toString(), fileChild2.toString()), filesWatcher.queryIndex("aaa"))

            filesWatcher.startWatching(parentDirectory)
            waitCondition(2.seconds) { filesWatcher.queryIndex("aaa").size == 3 }
            assertEquals(setOf(parentDirectory), filesWatcher.getCurrentlyWatchedSnapshot())
            assertEquals(
                setOf(fileChild1.toString(), fileChild2.toString(), fileParent.toString()),
                filesWatcher.queryIndex("aaa")
            )

            filesWatcher.stopWatching(child1Directory)
            filesWatcher.stopWatching(child2Directory)
            wait(1.seconds)
            assertEquals(setOf(parentDirectory), filesWatcher.getCurrentlyWatchedSnapshot())
            assertEquals(
                setOf(fileChild1.toString(), fileChild2.toString(), fileParent.toString()),
                filesWatcher.queryIndex("aaa")
            )

            filesWatcher.stopWatching(parentDirectory)
            waitCondition(2.seconds) { filesWatcher.queryIndex("aaa").isEmpty() }
            assertEquals(setOf(), filesWatcher.getCurrentlyWatchedSnapshot())
            assertEquals(setOf(), filesWatcher.queryIndex("aaa"))
        }
    }

    @Test
    fun testWatchingChild(@TempDir tempDir: Path) {
        runTest {
            val filesWatcher = FilesWatcherImpl(wordParser, backgroundScope)
            val child1Directory = FileHelper.createDirectory(tempDir, Paths.get("parent", "child1"))
            val child2Directory = FileHelper.createDirectory(tempDir, Paths.get("parent", "child2"))
            val parentDirectory = tempDir.resolve("parent")
            val fileChild1 = FileHelper.createOrUpdateFile(tempDir, Paths.get("parent", "child1", "b"), "aaa")
            val fileChild2 = FileHelper.createOrUpdateFile(tempDir, Paths.get("parent", "child2", "b"), "aaa")
            val fileParent = FileHelper.createOrUpdateFile(tempDir, Paths.get("parent", "a"), "aaa")

            filesWatcher.startWatching(parentDirectory)
            waitCondition(2.seconds) { filesWatcher.queryIndex("aaa").isNotEmpty() }
            assertEquals(setOf(parentDirectory), filesWatcher.getCurrentlyWatchedSnapshot())
            assertEquals(
                setOf(fileChild1.toString(), fileChild2.toString(), fileParent.toString()),
                filesWatcher.queryIndex("aaa")
            )

            filesWatcher.startWatching(child1Directory)
            filesWatcher.startWatching(child2Directory)
            wait(1.seconds)
            assertEquals(setOf(parentDirectory), filesWatcher.getCurrentlyWatchedSnapshot())
            assertEquals(
                setOf(fileChild1.toString(), fileChild2.toString(), fileParent.toString()),
                filesWatcher.queryIndex("aaa")
            )

            filesWatcher.stopWatching(parentDirectory)
            waitCondition(2.seconds) { filesWatcher.queryIndex("aaa").isEmpty() }
            assertEquals(setOf(), filesWatcher.getCurrentlyWatchedSnapshot())
            assertEquals(setOf(), filesWatcher.queryIndex("aaa"))
        }
    }

    @Test
    fun testAddAndRemoveWhileWatching(@TempDir tempDir: Path) {
        runTest {
            val filesWatcher = FilesWatcherImpl(wordParser, backgroundScope)
            val watchDirectory = FileHelper.createDirectory(tempDir, Paths.get("watch"))

            filesWatcher.startWatching(watchDirectory)
            assertEquals(setOf(watchDirectory), filesWatcher.getCurrentlyWatchedSnapshot())

            FileHelper.createDirectory(tempDir, Paths.get("watch", "child1"))
            assertEquals(setOf(watchDirectory), filesWatcher.getCurrentlyWatchedSnapshot())
            val file1Child1 = FileHelper.createOrUpdateFile(tempDir, Paths.get("watch", "child1", "a"), "aaa")
            val file2Child1 = FileHelper.createOrUpdateFile(tempDir, Paths.get("watch", "child1", "b"), "bbb")

            waitCondition(2.seconds) { filesWatcher.queryIndex("aaa").isNotEmpty() }
            waitCondition(2.seconds) { filesWatcher.queryIndex("bbb").isNotEmpty() }
            assertEquals(setOf(watchDirectory), filesWatcher.getCurrentlyWatchedSnapshot())
            assertEquals(setOf(file1Child1.toString()), filesWatcher.queryIndex("aaa"))
            assertEquals(setOf(file2Child1.toString()), filesWatcher.queryIndex("bbb"))

            FileHelper.createDirectory(tempDir, Paths.get("watch", "child2"))
            val fileChild2 = FileHelper.createOrUpdateFile(tempDir, Paths.get("watch", "child2", "c"), "aaa")
            val fileParent = FileHelper.createOrUpdateFile(tempDir, Paths.get("watch", "d"), "bbb")

            waitCondition(2.seconds) { filesWatcher.queryIndex("aaa").size == 2 }
            waitCondition(2.seconds) { filesWatcher.queryIndex("bbb").size == 2 }
            assertEquals(setOf(watchDirectory), filesWatcher.getCurrentlyWatchedSnapshot())
            assertEquals(setOf(file1Child1.toString(), fileChild2.toString()), filesWatcher.queryIndex("aaa"))
            assertEquals(setOf(file2Child1.toString(), fileParent.toString()), filesWatcher.queryIndex("bbb"))

            FileHelper.deleteFile(tempDir, Paths.get("watch", "child1", "a"))
            waitCondition(2.seconds) { filesWatcher.queryIndex("aaa").size == 1 }
            assertEquals(setOf(fileChild2.toString()), filesWatcher.queryIndex("aaa"))
            assertEquals(setOf(file2Child1.toString(), fileParent.toString()), filesWatcher.queryIndex("bbb"))

            FileHelper.deleteDirectory(tempDir, Paths.get("watch", "child2"))
            waitCondition(2.seconds) { filesWatcher.queryIndex("aaa").isEmpty() }
            assertEquals(setOf(), filesWatcher.queryIndex("aaa"))
            assertEquals(setOf(file2Child1.toString(), fileParent.toString()), filesWatcher.queryIndex("bbb"))
        }
    }

    @Test
    fun testRename(@TempDir tempDir: Path) {
        runTest {
            val filesWatcher = FilesWatcherImpl(wordParser, backgroundScope)
            val watchDirectory = FileHelper.createDirectory(tempDir, Paths.get("watch"))
            filesWatcher.startWatching(watchDirectory)

            val directory = FileHelper.createDirectory(tempDir, Paths.get("watch", "dir"))
            val file1 = FileHelper.createOrUpdateFile(tempDir, Paths.get("watch", "dir", "a"), "aaa")
            val file2 = FileHelper.createOrUpdateFile(tempDir, Paths.get("watch", "dir", "b"), "bbb")

            waitCondition(2.seconds) { filesWatcher.queryIndex("aaa").isNotEmpty() }
            waitCondition(2.seconds) { filesWatcher.queryIndex("bbb").isNotEmpty() }
            assertEquals(setOf(file1.toString()), filesWatcher.queryIndex("aaa"))
            assertEquals(setOf(file2.toString()), filesWatcher.queryIndex("bbb"))

            val directoryRenamed =
                Files.move(directory, directory.parent.resolve("dirRenamed"), StandardCopyOption.ATOMIC_MOVE)
            val file1Renamed = directoryRenamed.resolve("a").normalize().absolutePathString()
            val file2Renamed = directoryRenamed.resolve("b").normalize().absolutePathString()

            waitCondition(2.seconds) { file1Renamed in filesWatcher.queryIndex("aaa") }
            waitCondition(2.seconds) { file2Renamed in filesWatcher.queryIndex("bbb") }
            assertEquals(setOf(watchDirectory), filesWatcher.getCurrentlyWatchedSnapshot())
            assertEquals(setOf(file1Renamed), filesWatcher.queryIndex("aaa"))
            assertEquals(setOf(file2Renamed), filesWatcher.queryIndex("bbb"))

            filesWatcher.stopWatching(watchDirectory)

            waitCondition(2.seconds) { filesWatcher.queryIndex("aaa").isEmpty() }
            waitCondition(2.seconds) { filesWatcher.queryIndex("bbb").isEmpty() }
            assertEquals(setOf(), filesWatcher.getCurrentlyWatchedSnapshot())
            assertEquals(setOf(), filesWatcher.queryIndex("aaa"))
            assertEquals(setOf(), filesWatcher.queryIndex("bbb"))
        }
    }

    @Test
    fun testWatchingFile(@TempDir tempDir: Path) {
        runTest {
            val filesWatcher = FilesWatcherImpl(wordParser, backgroundScope)
            val file = FileHelper.createOrUpdateFile(tempDir, "a", "aaa")
            filesWatcher.startWatching(file)

            waitCondition(2.seconds) { filesWatcher.queryIndex("aaa").isNotEmpty() }
            assertEquals(setOf(file), filesWatcher.getCurrentlyWatchedSnapshot())
            assertEquals(setOf(file.toString()), filesWatcher.queryIndex("aaa"))

            FileHelper.createOrUpdateFile(tempDir, "a", "bbb")

            waitCondition(2.seconds) { filesWatcher.queryIndex("bbb").isNotEmpty() }
            assertEquals(setOf(file), filesWatcher.getCurrentlyWatchedSnapshot())
            assertEquals(setOf(), filesWatcher.queryIndex("aaa"))
            assertEquals(setOf(file.toString()), filesWatcher.queryIndex("bbb"))

            FileHelper.deleteFile(tempDir, "a")

            waitCondition(2.seconds) { filesWatcher.queryIndex("bbb").isEmpty() }
            assertEquals(setOf(), filesWatcher.getCurrentlyWatchedSnapshot())
            assertEquals(setOf(), filesWatcher.queryIndex("aaa"))
            assertEquals(setOf(), filesWatcher.queryIndex("bbb"))
        }
    }

    @Test
    fun testSymlinks(@TempDir tempDir: Path) {
        runTest {
            val filesWatcher = FilesWatcherImpl(wordParser, backgroundScope)
            val directory = FileHelper.createDirectory(tempDir, Paths.get("dir"))
            val symlinkExistingDirToWatch = Files.createSymbolicLink(tempDir.resolve("dir_symlink"), directory)
            val symlinkNotExistingDirToWatch = Files.createSymbolicLink(
                tempDir.resolve("dir_symlink_ne"), tempDir.resolve("dir_ne")
            )
            val file = FileHelper.createOrUpdateFile(tempDir, "a", "aaa")
            val symlinkExistingFileToWatch = Files.createSymbolicLink(tempDir.resolve("a_symlink"), file)
            val symlinkNotExistingFileToWatch = Files.createSymbolicLink(
                tempDir.resolve("a_symlink_ne"), tempDir.resolve("a_ne")
            )

            filesWatcher.startWatching(symlinkExistingDirToWatch)
            filesWatcher.startWatching(symlinkNotExistingDirToWatch)
            filesWatcher.startWatching(symlinkExistingFileToWatch)
            filesWatcher.startWatching(symlinkNotExistingFileToWatch)

            wait(1.seconds)
            assertEquals(setOf(), filesWatcher.getCurrentlyWatchedSnapshot())
            assertEquals(setOf(), filesWatcher.queryIndex("aaa"))
        }
    }

    @Test
    fun testMultipleAndRecursiveWatches(@TempDir tempDir: Path) {
        runTest {
            val filesWatcher = FilesWatcherImpl(wordParser, backgroundScope)
            val watchDirectory1 = FileHelper.createDirectory(tempDir, Paths.get("watch1"))
            val watchDirectory2 = FileHelper.createDirectory(tempDir, Paths.get("watch2"))
            val watchFile = FileHelper.createOrUpdateFile(tempDir, "a", "aaa")
            val aaaFiles = mutableSetOf<Path>()
            val bbbFiles = mutableSetOf<Path>()

            filesWatcher.startWatching(watchDirectory1)
            filesWatcher.startWatching(watchDirectory2)
            filesWatcher.startWatching(watchFile)
            aaaFiles.add(watchFile)
            assertEquals(setOf(watchDirectory1, watchDirectory2, watchFile), filesWatcher.getCurrentlyWatchedSnapshot())

            FileHelper.createDirectory(tempDir, Paths.get("watch1", "dir1", "dir2"))
            val willUpdateFile = FileHelper.createOrUpdateFile(tempDir, Paths.get("watch1", "dir1", "dir2", "a"), "aaa")
            aaaFiles.add(willUpdateFile)
            aaaFiles.add(FileHelper.createOrUpdateFile(tempDir, Paths.get("watch1", "dir1", "dir2", "b"), "aaa"))
            bbbFiles.add(FileHelper.createOrUpdateFile(tempDir, Paths.get("watch2", "a"), "bbb"))
            val doubleMatchFile = FileHelper.createOrUpdateFile(tempDir, Paths.get("watch1", "dir1", "c"), "aaa bbb")
            aaaFiles.add(doubleMatchFile)
            bbbFiles.add(doubleMatchFile)
            FileHelper.createDirectory(tempDir, Paths.get("watch2", "dir3"))
            aaaFiles.add(FileHelper.createOrUpdateFile(tempDir, Paths.get("watch2", "dir3", "d"), "aaa"))

            waitCondition(2.seconds) { filesWatcher.queryIndex("aaa").size == aaaFiles.size }
            waitCondition(2.seconds) { filesWatcher.queryIndex("bbb").size == bbbFiles.size }
            assertEquals(setOf(watchDirectory1, watchDirectory2, watchFile), filesWatcher.getCurrentlyWatchedSnapshot())
            assertEquals(aaaFiles.asSequence().map { it.toString() }.toSet(), filesWatcher.queryIndex("aaa"))
            assertEquals(bbbFiles.asSequence().map { it.toString() }.toSet(), filesWatcher.queryIndex("bbb"))

            FileHelper.createOrUpdateFile(tempDir, Paths.get("watch1", "dir1", "dir2", "a"), "bbb")
            aaaFiles.remove(willUpdateFile)
            bbbFiles.add(willUpdateFile)
            FileHelper.createOrUpdateFile(tempDir, "a", "bbb")
            aaaFiles.remove(watchFile)
            bbbFiles.add(watchFile)

            waitCondition(2.seconds) { filesWatcher.queryIndex("aaa").size == aaaFiles.size }
            waitCondition(2.seconds) { filesWatcher.queryIndex("bbb").size == bbbFiles.size }
            assertEquals(setOf(watchDirectory1, watchDirectory2, watchFile), filesWatcher.getCurrentlyWatchedSnapshot())
            assertEquals(aaaFiles.asSequence().map { it.toString() }.toSet(), filesWatcher.queryIndex("aaa"))
            assertEquals(bbbFiles.asSequence().map { it.toString() }.toSet(), filesWatcher.queryIndex("bbb"))

            FileHelper.deleteFile(tempDir, "a")
            bbbFiles.remove(watchFile)
            waitCondition(2.seconds) { filesWatcher.queryIndex("bbb").size == bbbFiles.size }
            assertEquals(setOf(watchDirectory1, watchDirectory2), filesWatcher.getCurrentlyWatchedSnapshot())
            assertEquals(bbbFiles.asSequence().map { it.toString() }.toSet(), filesWatcher.queryIndex("bbb"))

            FileHelper.deleteDirectory(tempDir, Paths.get("watch1"))
            waitCondition(2.seconds) { filesWatcher.queryIndex("aaa").size == 1 }
            waitCondition(2.seconds) { filesWatcher.queryIndex("bbb").size == 1 }
            assertEquals(setOf(watchDirectory2), filesWatcher.getCurrentlyWatchedSnapshot())
            assertEquals(
                setOf(watchDirectory2.resolve(Paths.get("dir3", "d")).toString()), filesWatcher.queryIndex("aaa")
            )
            assertEquals(setOf(watchDirectory2.resolve("a").toString()), filesWatcher.queryIndex("bbb"))
        }
    }
}