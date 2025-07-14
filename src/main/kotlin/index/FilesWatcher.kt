package index

import kotlinx.coroutines.CoroutineScope
import java.nio.file.Path

interface FilesWatcher : CoroutineScope, AutoCloseable {

    suspend fun startWatching(path: Path): WatchOperationResult

    suspend fun stopWatching(path: Path): WatchOperationResult

    fun queryIndex(word: String): Set<String>

    fun getCurrentlyWatchedLive(): Set<Path>

    fun getCurrentlyWatchedSnapshot(): Set<Path>
}