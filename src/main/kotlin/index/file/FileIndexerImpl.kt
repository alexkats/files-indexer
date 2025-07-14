package index.file

import index.hash.DefaultFileHasher
import index.hash.FileHash
import index.word.HashTableWordIndex
import index.word.WordIndex
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import parser.WordParser
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.isDirectory
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.notExists
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark

class FileIndexerImpl(
    wordParser: WordParser,
    private val fileChangeEventsChannel: ReceiveChannel<FileChangeEvent>,
    private val scope: CoroutineScope,
    private val startingTimeMark: TimeMark,
    backgroundWorkerDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
) : FileIndexer {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(FileIndexerImpl::class.simpleName)

        val STALE_HASH_TIMEOUT = 30.minutes
        val FILES_LAST_EVENT_CACHE_TTL = 5.minutes
    }

    private val fileToHash: MutableMap<String, FileHash> = ConcurrentHashMap()
    private val hashToFiles: MutableMap<FileHash, MutableSet<String>> = ConcurrentHashMap()
    private val filesLastEventCache = ConcurrentHashMap<Path, Duration>()
    private val computingHashes = ConcurrentHashMap<FileHash, Boolean>()
    private val staleHashes = ConcurrentHashMap<FileHash, Job>()

    private val wordIndex: WordIndex = HashTableWordIndex(wordParser)
    private val fileHasher = DefaultFileHasher()

    private fun getLogLine(path: Path, prefix: String) = "$prefix ${if (path.isDirectory()) "dir" else "file"} $path"
    private fun printLogLine(path: Path, prefix: String) = LOGGER.info(getLogLine(path, prefix))

    init {
        // Cleanup of last event cache for each file by ttl
        scope.launch(backgroundWorkerDispatcher) {
            while (true) {
                delay(FILES_LAST_EVENT_CACHE_TTL)
                val timestamp = startingTimeMark.elapsedNow().minus(FILES_LAST_EVENT_CACHE_TTL)
                for (path in filesLastEventCache.keys) {
                    filesLastEventCache.compute(path) { _, v ->
                        if (v != null && v <= timestamp) null else v
                    }
                }
            }
        }
        scope.launch {
            for (msg in fileChangeEventsChannel) {
                launch(Dispatchers.IO) {
                    LOGGER.trace("Got event {} for {} at {}", msg.eventType.name, msg.path, msg.timestamp)
                    // Basically we check that the event we will process is the latest so far.
                    // This is to avoid situations like when we created a file and deleted it immediately.
                    // Events from the filesystem came in the correct order, but at this point ordering is
                    // not guaranteed, so we mark all events with timestamps.
                    val computedTimestamp = filesLastEventCache.compute(msg.path) { _, v ->
                        if ((v ?: Duration.Companion.ZERO) > msg.timestamp) v else msg.timestamp
                    }
                    LOGGER.trace("Computed timestamp {}", computedTimestamp)

                    if (computedTimestamp == msg.timestamp) {
                        msg.eventType.opFunc(this@FileIndexerImpl, msg.path)
                    }
                }
            }
        }
    }

    override suspend fun createOrUpdateFile(path: Path) {
        printLogLine(path, "Created or updated")
        val fileName = path.validatePathAndGetFileName() ?: return
        val hash = fileHasher.hash(path)
        if (path.notExists() || hash == FileHash.EMPTY) {
            LOGGER.debug("File {} was already deleted", fileName)
            return
        }
        // We can have a data race between this remove and subsequent add, we'll overwrite the last change
        // correctly anyway, so better just to take an old hash for cleanup purposes.
        val oldHash = fileToHash[fileName] ?: FileHash.EMPTY
        if (oldHash == hash) {
            LOGGER.debug("File {} is already indexed with the latest hash", fileName)
            return
        }
        hashToFiles.computeIfPresent(oldHash) { _, v ->
            v.also { it -= fileName }.ifEmpty { null }
        }
        staleHashes[hash]?.cancelAndJoin()?.also { staleHashes -= hash }
        fileToHash[fileName] = hash
        hashToFiles.compute(hash) { _, v ->
            (v ?: mutableSetOf()).also { it += fileName }
        }
        if (wordIndex.isFileHashIndexed(hash)) {
            return
        }
        if (computingHashes.putIfAbsent(hash, true) == true) {
            return
        }
        // This is possible if hash wasn't in reverse index in the beginning,
        // but then we computed that hash and put it into our reverse index.
        // So we need to check the reverse index again in order to avoid the duplicate work.
        if (wordIndex.isFileHashIndexed(hash)) {
            computingHashes -= hash
            return
        }
        wordIndex.indexFile(fileName, hash) { fileHasher.hash(path) }
        computingHashes -= hash
    }

    override suspend fun deleteFile(path: Path) {
        printLogLine(path, "Deleted")
        val fileName = path.validatePathAndGetFileName() ?: return
        val hash = fileToHash.remove(fileName) ?: FileHash.EMPTY
        hashToFiles.compute(hash) { _, v ->
            v?.also { it -= fileName }?.ifEmpty { null }
        }
        if (!hashToFiles.containsKey(hash)) {
            val job = scope.launch(start = CoroutineStart.LAZY) {
                // This is to avoid the following race condition:
                // no files for hash -> start creating job -> createFile() -> put stale hash -> job start
                if (hashToFiles.containsKey(hash)) {
                    staleHashes -= hash
                    return@launch
                }

                delay(STALE_HASH_TIMEOUT)
                withContext(NonCancellable) {
                    wordIndex.deleteIndex(hash)
                    staleHashes -= hash
                }
            }
            // In case we have a race between two deletions at the same time (duplicate events for example)
            staleHashes.put(hash, job)?.cancelAndJoin()
            job.start()
        }
    }

    override fun query(word: String): Set<String> {
        val index = wordIndex.queryIndex(word)
        return index.asSequence().map { hashToFiles[it] ?: emptySet() }.flatten().toSet()
    }

    private fun Path.validatePathAndGetFileName(): String? {
        val reason = when {
            isSymbolicLink() -> "symlink"
            isDirectory() -> "directory"
            else -> null
        }
        return if (reason != null) {
            LOGGER.debug("Path \"{}\" is a {}, ignoring", this, reason)
            null
        } else toString()
    }
}