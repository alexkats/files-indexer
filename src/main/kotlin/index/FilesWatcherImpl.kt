package index

import index.file.EventType
import index.file.FileChangeEvent
import index.file.FileIndexerImpl
import io.methvin.watcher.DirectoryChangeEvent
import io.methvin.watcher.DirectoryWatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import parser.WordParser
import java.nio.file.Path
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.notExists
import kotlin.io.path.walk
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class FilesWatcherImpl(
    private val wordParser: WordParser,
    scope: CoroutineScope,
    private val startingTimeMark: TimeMark = TimeSource.Monotonic.markNow()
) : FilesWatcher {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(FilesWatcherImpl::class.simpleName)
    }

    private val job = SupervisorJob(scope.coroutineContext[Job.Key])
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Default

    private val fileChangeEventsChannel = Channel<FileChangeEvent>(Channel.Factory.UNLIMITED)
    private val fileIndexer = FileIndexerImpl(wordParser, fileChangeEventsChannel, this, startingTimeMark)
    private val directoryWatchers = ConcurrentHashMap<Path, DirectoryWatcher>()

    private val directoryWatchOperationMutex = Mutex()

    override suspend fun startWatching(path: Path): WatchOperationResult = directoryWatchOperationMutex.withLock {
        val (normalizedPath, reason) = path.validatePathAndNormalize()
        if (normalizedPath == null) {
            return WatchOperationResult(false, reason)
        }
        val (shouldAbort, comment) = resolveDependenciesAndShouldAbort(normalizedPath)
        if (shouldAbort) {
            return WatchOperationResult(false, comment)
        }
        val directoryWatcher = createDirectoryWatcher(normalizedPath)
        // We use an earlier timestamp for walking existing files on purpose.
        // If we have some events coming, and then for some reason some files will be processed later, they shouldn't
        // have precedence over events being watched, as that state was more accurate of we had an event on such files.
        val timestamp = startingTimeMark.elapsedNow()
        directoryWatcher.watchAsync()
        normalizedPath.walk().forEach {
            launch {
                startIndexingFile(it, timestamp)
            }
        }
        WatchOperationResult(true, comment)
    }

    override suspend fun stopWatching(path: Path): WatchOperationResult = directoryWatchOperationMutex.withLock {
        stopWatchingUnlocked(path)
    }

    override fun queryIndex(word: String): Set<String> {
        val trimmedWord = wordParser.trim(word)
        return if (trimmedWord.isNotEmpty()) fileIndexer.query(word) else setOf()
    }

    override fun getCurrentlyWatchedLive(): Set<Path> {
        return Collections.unmodifiableSet(directoryWatchers.keys)
    }

    override fun getCurrentlyWatchedSnapshot(): Set<Path> {
        return directoryWatchers.keys.toSet()
    }

    override fun close() {
        job.cancel()
        directoryWatchers.forEach { _, watcher -> watcher.close() }
    }

    private fun Path.validatePathAndNormalize(): Pair<Path?, String> {
        val normalizedPath = toAbsolutePath().normalize()
        if (normalizedPath.notExists()) {
            LOGGER.debug("No path exists: {}", normalizedPath)
            return null to "Path doesn't exist"
        }
        if (directoryWatchers.containsKey(normalizedPath)) {
            LOGGER.debug("Already watching this path: {}", normalizedPath)
            return null to "Path is already watched, ignoring"
        }
        if (normalizedPath.isSymbolicLink()) {
            LOGGER.debug("Symbolic links watching is not supported: {}", normalizedPath)
            return null to "Symbolic links can't be watched, ignoring"
        }
        if (!(normalizedPath.isRegularFile() || normalizedPath.isDirectory())) {
            LOGGER.debug("Path is neither a regular file not a directory, won't track: {}", normalizedPath)
            return null to "Path is neither a regular file not a directory, ignoring"
        }
        return normalizedPath to ""
    }

    private fun resolveDependenciesAndShouldAbort(path: Path): Pair<Boolean, String> {
        val possibleParent = directoryWatchers.keys.firstOrNull { path.startsWith(it) }
        if (possibleParent != null) {
            LOGGER.debug(
                "Path \"{}\" is already being tracked by watching path \"{}\"",
                path,
                possibleParent
            )
            return true to "Provided path is already tracked by watching path $possibleParent, ignoring"
        }
        val children = directoryWatchers.keys.filter { it.startsWith(path) }
        val comment = if (children.isNotEmpty()) {
            LOGGER.debug(
                "Path(s) \"{}\"  will stop being watched explicitly, they will be tracked by watching path \"{}\"",
                children.joinToString(),
                path
            )
            "Path(s) ${children.joinToString()} will stop being watched explicitly, they will be tracked by watching the provided path"
        } else ""
        children.forEach { stopWatchingUnlocked(it) }
        return false to comment
    }

    private fun createDirectoryWatcher(path: Path): DirectoryWatcher {
        val watcher = DirectoryWatcher.builder()
            .path(path)
            .listener { event ->
                val timestamp = startingTimeMark.elapsedNow()
                when (event.eventType()) {
                    DirectoryChangeEvent.EventType.CREATE, DirectoryChangeEvent.EventType.MODIFY -> launch {
                        startIndexingFile(event.path(), timestamp)
                    }

                    DirectoryChangeEvent.EventType.DELETE -> launch {
                        stopIndexingFile(path, event.path(), true, timestamp)
                    }

                    DirectoryChangeEvent.EventType.OVERFLOW -> LOGGER.error("Overflow with ${event.path()} for watching $path")
                }
            }
            // comment why
            .fileHashing(false)
            .build()
        directoryWatchers += path to watcher
        return watcher
    }

    private fun stopWatchingUnlocked(path: Path): WatchOperationResult {
        val normalizedPath = path.toAbsolutePath().normalize()
        return directoryWatchers.remove(normalizedPath)?.close()?.let {
            // can have a data race here if some files (???)
            normalizedPath.walk().forEach {
                launch {
                    stopIndexingFile(normalizedPath, it, false, startingTimeMark.elapsedNow())
                }
            }
            WatchOperationResult(true, "")
        } ?: WatchOperationResult(false, "This path wasn't tracked before, ignoring").also {
            LOGGER.debug("Didn't watch the path \"{}\" before", normalizedPath)
        }
    }

    // Won't suspend in practice, because my channel is unlimited
    private suspend fun startIndexingFile(path: Path, timestamp: Duration) = coroutineScope {
        fileChangeEventsChannel.send(
            FileChangeEvent(
                path,
                EventType.UPDATE,
                timestamp,
            )
        )
    }

    // Won't suspend in practice, because my channel is unlimited
    private suspend fun stopIndexingFile(
        trackPath: Path,
        path: Path,
        wasDeleted: Boolean,
        timestamp: Duration
    ): Unit = coroutineScope {
        fileChangeEventsChannel.send(FileChangeEvent(path, EventType.DELETE, timestamp))
        if (wasDeleted && trackPath == path) {
            stopWatching(trackPath)
        }
    }
}