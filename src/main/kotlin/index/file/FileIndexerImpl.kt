package index.file

import index.word.HashTableWordIndex
import index.word.WordIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import parser.WordParser
import util.MutexWithRefCount
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.notExists

class FileIndexerImpl(
    wordParser: WordParser,
    private val fileChangeEventsChannel: ReceiveChannel<FileChangeEvent>,
    scope: CoroutineScope
) : FileIndexer {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(FileIndexerImpl::class.simpleName)
    }

    private val computingFiles: MutableMap<Path, MutexWithRefCount> = ConcurrentHashMap()

    private val wordIndex: WordIndex = HashTableWordIndex(wordParser)

    private fun getLogLine(file: Path, prefix: String) = "$prefix file $file"
    private fun printLogLine(file: Path, prefix: String) = LOGGER.info(getLogLine(file, prefix))

    init {
        scope.launch {
            for (msg in fileChangeEventsChannel) {
                when (msg.eventType) {
                    EventType.UPDATE, EventType.DELETE -> launch(Dispatchers.IO) {
                        msg.file.withFileLock {
                            msg.eventType.opFunc(this@FileIndexerImpl, msg.root, msg.file)
                            msg.onFinish()
                        }
                    }

                    EventType.CREATE_ROOT,
                    EventType.DELETE_ROOT -> {
                        msg.eventType.opFunc(this@FileIndexerImpl, msg.root, msg.file)
                        msg.onFinish()
                    }
                }
            }
        }
    }

    private suspend fun <T> Path.withFileLock(action: suspend () -> T): T {
        val mutex = computingFiles.compute(this) { _, v -> v?.also { it.acquire() } ?: MutexWithRefCount() }!!
        return try {
            mutex.withLock(action)
        } finally {
            computingFiles.compute(this) { _, v -> v?.let { if (it.releaseAndIsLast()) null else it } }
        }
    }

    override suspend fun createOrUpdateFile(root: Path, file: Path) {
        printLogLine(file, "Created or updated")
        val timestamp = file.toFile().lastModified()
        if (file.notExists() || timestamp == 0L) {
            LOGGER.debug("File {} was already deleted", file)
            return
        }

        wordIndex.indexFile(root, file, timestamp)
    }

    override suspend fun deleteFile(root: Path, file: Path) {
        printLogLine(file, "Deleted")
        // If a regular file exists, it means the file was recreated, and we got into a race between create and delete
        // events. To prevent deleting index in such a case, we check if the file exists on the filesystem.
        if (file.exists() && file.isRegularFile()) {
            LOGGER.debug("File {} exists, won't delete index for it", file)
            return
        }
        wordIndex.deleteIndexForFile(root, file)
    }

    override fun createRoot(root: Path) {
        wordIndex.createIndexForRoot(root)
    }

    override fun deleteRoot(root: Path) {
        wordIndex.deleteIndexForRoot(root)
    }

    override fun query(word: String): Set<Path> {
        return wordIndex.queryIndex(word)
    }
}