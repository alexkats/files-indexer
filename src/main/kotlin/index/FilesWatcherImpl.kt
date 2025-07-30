package index

import index.file.EventType
import index.file.FileChangeEvent
import index.file.FileIndexerImpl
import index.filter.AggregateFilter
import index.filter.BinaryFileFilter
import index.filter.DirectoryNameFilter
import index.filter.FileExtensionFilter
import index.filter.IndexingFilter
import index.filter.SymlinkFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import parser.WordParser
import util.ProgressStatus
import util.ProgressTracker
import java.nio.file.Path
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.notExists
import kotlin.io.path.useDirectoryEntries

class FilesWatcherImpl(
    wordParser: WordParser,
    scope: CoroutineScope
) : FilesWatcher {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(FilesWatcherImpl::class.simpleName)

        private val COMMON_FILTERS = arrayOf(SymlinkFilter(), BinaryFileFilter())
        private const val DIRECTORY_SCAN_PARALLELISM_LEVEL = 8
    }

    private val job = SupervisorJob(scope.coroutineContext[Job.Key])
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Default

    private val fileChangeEventsChannel = Channel<FileChangeEvent>(Channel.Factory.UNLIMITED)
    private val watchOperationsChannel = Channel<WatchOperation<*>>(Channel.UNLIMITED)

    private val fileIndexer = FileIndexerImpl(wordParser, fileChangeEventsChannel, this)
    private val directoryWatchers = ConcurrentHashMap<Path, FilteringDirectoryWatcher>()


    init {
        launch {
            for (msg in watchOperationsChannel) {
                when (msg) {
                    is WatchOperation.Start -> startWatchingImpl(msg.data)
                    is WatchOperation.Stop -> stopWatchingImpl(msg.data.root)
                }
            }
        }
    }

    // Won't suspend in practice, because the channel is unlimited
    override suspend fun startWatching(
        path: Path,
        excludePatterns: Set<String>,
        extensions: Set<String>,
        progressTracker: ProgressTracker
    ): WatchOperationResult {
        val (normalizedPath, reason) = path.validatePathAndNormalize()
        if (normalizedPath == null) {
            return WatchOperationResult(false, reason)
        }

        val (shouldAbort, comment) = resolveDependenciesAndShouldAbort(normalizedPath)
        return if (shouldAbort) {
            WatchOperationResult(false, comment)
        } else {
            watchOperationsChannel.send(
                WatchOperation.Start(
                    WatchStartOperationData(
                        normalizedPath,
                        excludePatterns,
                        extensions,
                        progressTracker
                    )
                )
            )
            WatchOperationResult(true, comment)
        }
    }

    private suspend fun startWatchingImpl(data: WatchStartOperationData) {
        val filter = AggregateFilter(
            listOfNotNull(
                *COMMON_FILTERS,
                if (data.excludePatterns.isNotEmpty()) DirectoryNameFilter(data.excludePatterns) else null,
                if (data.extensions.isNotEmpty()) FileExtensionFilter(data.extensions) else null
            )
        )
        LOGGER.info("Starting watching")
        fileChangeEventsChannel.send(FileChangeEvent(data.root, data.root, 0L, EventType.CREATE_ROOT))
        data.progressTracker.status = ProgressStatus.INITIALIZING_WATCHER
        val directoryWatcher = FilteringDirectoryWatcher(
            data.root,
            filter,
            data.progressTracker,
            ::startIndexingFile,
            ::stopIndexingFile
        ) {
            it.status = ProgressStatus.REMOVING
            fileChangeEventsChannel.send(
                FileChangeEvent(
                    data.root,
                    data.root,
                    0L,
                    EventType.DELETE_ROOT
                ) {
                    it.status = ProgressStatus.REMOVED
                }
            )
        }
        directoryWatchers += data.root to directoryWatcher
        directoryWatcher.watchAsync()
        LOGGER.info("Started watching")
        data.progressTracker.status = ProgressStatus.INDEXING

        if (data.root.isDirectory()) {
            scanDirectoriesAndStartIndexing(data, filter)
        } else {
            if (filter.accept(data.root)) {
                data.progressTracker.fileAdded()
                data.progressTracker.allFilesAdded()
                startIndexingFile(data.root, data.root) { data.progressTracker.fileIndexed() }
            } else {
                data.progressTracker.allFilesAdded()
            }
        }
    }

    private suspend fun scanDirectoriesAndStartIndexing(data: WatchStartOperationData, filter: IndexingFilter) {
        val pathsQueue = Channel<Path>(Channel.UNLIMITED)
        pathsQueue.send(data.root)
        val activeDirs = AtomicLong(1L)
        val workers = List(DIRECTORY_SCAN_PARALLELISM_LEVEL) {
            launch(Dispatchers.IO) {
                for (path in pathsQueue) {
                    try {
                        if (path.isDirectory()) {
                            path.useDirectoryEntries {
                                it.forEach { entry ->
                                    if (filter.accept(entry)) {
                                        activeDirs.incrementAndGet()
                                        pathsQueue.send(entry)
                                    }
                                }
                            }
                        } else {
                            data.progressTracker.fileAdded()
                            startIndexingFile(data.root, path) {
                                data.progressTracker.fileIndexed()
                                LOGGER.debug(
                                    "Path {} progress: {}",
                                    data.root,
                                    data.progressTracker.getPrintableStatus()
                                )
                            }
                        }
                    } finally {
                        if (activeDirs.decrementAndGet() == 0L) {
                            pathsQueue.close()
                        }
                    }
                }
            }
        }
        workers.joinAll()
        data.progressTracker.allFilesAdded()
    }

    // Won't suspend in practice, because the channel is unlimited
    override suspend fun stopWatching(path: Path, progressTracker: ProgressTracker?): WatchOperationResult {
        val normalizedPath = path.toAbsolutePath().normalize()
        val result = if (directoryWatchers.containsKey(normalizedPath)) {
            watchOperationsChannel.send(WatchOperation.Stop(WatchStopOperationData(normalizedPath)))
            progressTracker?.status = ProgressStatus.REMOVE_QUEUED
            WatchOperationResult(true, "")
        } else WatchOperationResult(false, "This path wasn't tracked before, ignoring")
        return result
    }

    private suspend fun stopWatchingImpl(root: Path) {
        val watcher = directoryWatchers.remove(root)
        watcher?.close() ?: LOGGER.debug("Didn't watch the path \"{}\" before", root)
    }

    override fun queryIndex(word: String): Set<Path> {
        return if (word.isNotEmpty()) fileIndexer.query(word) else setOf()
    }

    override fun getCurrentlyWatchedLive(): Set<Path> {
        return Collections.unmodifiableSet(directoryWatchers.keys)
    }

    override fun getCurrentWatchesPrintableInfo(): List<String> {
        return directoryWatchers.map { it.value.getPrintableInfo() }.toList()
    }

    override fun close() {
        job.cancel()
        launch {
            directoryWatchers.forEach { (_, watcher) -> watcher.close() }
        }
    }

    private fun Path.validatePathAndNormalize(): Pair<Path?, String> {
        val normalizedPath = toAbsolutePath().normalize()
        return when {
            normalizedPath.notExists() -> {
                LOGGER.debug("No path exists: {}", normalizedPath)
                null to "Path doesn't exist"
            }

            directoryWatchers.containsKey(normalizedPath) -> {
                LOGGER.debug("Already watching this path: {}", normalizedPath)
                null to "Path is already watched, ignoring"
            }

            normalizedPath.isSymbolicLink() -> {
                LOGGER.debug("Symbolic links watching is not supported: {}", normalizedPath)
                null to "Symbolic links can't be watched, ignoring"
            }

            !(normalizedPath.isRegularFile() || normalizedPath.isDirectory()) -> {
                LOGGER.debug("Path is neither a regular file not a directory, won't track: {}", normalizedPath)
                null to "Path is neither a regular file nor a directory, ignoring"
            }

            else -> normalizedPath to ""
        }
    }

    private suspend fun resolveDependenciesAndShouldAbort(path: Path): Pair<Boolean, String> {
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
            "Path(s) ${children.joinToString()} will stop being watched explicitly, " +
                "they will be tracked by watching the provided path"
        } else ""
        children.forEach { stopWatchingImpl(it) }
        return false to comment
    }

    // Won't suspend in practice, because the channel is unlimited
    private suspend fun startIndexingFile(root: Path, path: Path, onFinish: () -> Unit = {}) = coroutineScope {
        fileChangeEventsChannel.send(
            FileChangeEvent(
                root,
                path,
                path.toFile().lastModified(),
                EventType.UPDATE,
                onFinish
            )
        )
    }

    // Won't suspend in practice, because the channel is unlimited
    private suspend fun stopIndexingFile(root: Path, path: Path): Unit = coroutineScope {
        if (root == path) {
            stopWatchingImpl(root)
        } else {
            fileChangeEventsChannel.send(FileChangeEvent(root, path, 0L, EventType.DELETE))
        }
    }
}
