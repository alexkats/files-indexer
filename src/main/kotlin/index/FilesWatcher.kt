package index

import kotlinx.coroutines.CoroutineScope
import util.ProgressTracker
import java.nio.file.Path

interface FilesWatcher : CoroutineScope, AutoCloseable {

    suspend fun startWatching(
        path: Path,
        excludePatterns: Set<String>,
        extensions: Set<String>,
        progressTracker: ProgressTracker,
    ): WatchOperationResult

    suspend fun stopWatching(path: Path, progressTracker: ProgressTracker?): WatchOperationResult

    fun queryIndex(word: String): Set<Path>

    fun getCurrentlyWatchedLive(): Set<Path>

    fun getCurrentWatchesPrintableInfo(): List<String>
}