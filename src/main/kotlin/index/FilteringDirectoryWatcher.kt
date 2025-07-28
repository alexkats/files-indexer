package index

import index.filter.IndexingFilter
import io.methvin.watcher.DirectoryChangeEvent
import io.methvin.watcher.DirectoryWatcher
import io.methvin.watcher.hashing.FileHasher
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import util.ProgressTracker
import java.nio.file.Path

class FilteringDirectoryWatcher(
    private val root: Path,
    private val filter: IndexingFilter,
    private val progressTracker: ProgressTracker,
    private val startIndexingFile: suspend (Path, Path) -> Unit,
    private val stopIndexingFile: suspend (Path, Path) -> Unit,
    private val onClose: suspend (ProgressTracker) -> Unit
) {

    private val watcher = createDirectoryWatcher()

    companion object {
        private val LOGGER = LoggerFactory.getLogger(FilteringDirectoryWatcher::class.simpleName)
    }

    fun watchAsync() {
        watcher.watchAsync()
    }

    suspend fun close() {
        watcher.close()
        onClose(progressTracker)
    }

    fun getPrintableInfo(): String {
        val filtersInfo = filter.getPrintableInfo()
        val filtersMessage = if (filtersInfo.isBlank()) "" else " ($filtersInfo)"
        return "$root$filtersMessage"
    }

    private fun createDirectoryWatcher(): DirectoryWatcher {
        val watcher = DirectoryWatcher.builder()
            .path(root)
            .listener { event ->
                val eventPath: Path = event.path()

                when (event.eventType()) {
                    DirectoryChangeEvent.EventType.CREATE, DirectoryChangeEvent.EventType.MODIFY -> runBlocking {
                        if (!filter.accept(eventPath)) {
                            LOGGER.trace("Some filter failed for {}: {}", root, eventPath)
                        } else {
                            startIndexingFile(root, event.path())
                        }
                    }

                    DirectoryChangeEvent.EventType.DELETE -> runBlocking {
                        stopIndexingFile(root, event.path())
                    }

                    DirectoryChangeEvent.EventType.OVERFLOW -> {
                        LOGGER.error("Overflow with $eventPath for watching $root")
                    }
                }
            }
            .fileHasher(FileHasher.LAST_MODIFIED_TIME)
            .build()

        return watcher
    }
}