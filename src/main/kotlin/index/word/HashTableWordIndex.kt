package index.word

import org.slf4j.LoggerFactory
import parser.WordParser
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

class HashTableWordIndex(private val wordParser: WordParser) : WordIndex {

    private val index: MutableMap<String, MutableSet<Path>> = ConcurrentHashMap()
    private val reverseIndex: MutableMap<Path, RootIndexData> = ConcurrentHashMap()

    companion object {
        private val LOGGER = LoggerFactory.getLogger(HashTableWordIndex::class.simpleName)
    }

    override fun indexFile(root: Path, file: Path, timestamp: Long) {
        if (shouldTryIndex(root, file, timestamp)) {
            // If we have an index for later timestamp, we don't need to index it
            val (computedTimestamp, oldWords) = reverseIndex[root]?.value?.compute(file) { _, v ->
                val oldTimestamp = v?.timestamp ?: 0L
                if (oldTimestamp >= timestamp) v else TimestampAndWords(timestamp, v?.words ?: setOf())
            } ?: TimestampAndWords(
                0L,
                setOf()
            ) // If the value is null, it means we deleted our root, don't need to index the file

            if (computedTimestamp != timestamp) {
                LOGGER.debug(
                    "File \"{}\" was already indexed with timestamp {}, won't update index",
                    file,
                    computedTimestamp
                )
                return
            }

            tryPerformFileIndexing(root, file, timestamp)?.also { words ->
                deleteWordsFromIndex(oldWords, file)
                reverseIndex.compute(root) { _, v ->
                    v?.also { rootReverseIndex ->
                        // We need to do it inside compute to avoid a race between indexing and deleting root.
                        // Otherwise, it would be possible to have hanging index entries in the map.
                        words.forEach { word ->
                            index.compute(word) { _, v -> (v ?: ConcurrentHashMap.newKeySet()).also { it.add(file) } }
                        }
                        rootReverseIndex.value.put(file, TimestampAndWords(timestamp, words))
                        LOGGER.debug("File \"{}\" was indexed with timestamp \"{}\"", file, timestamp)
                    }
                }
            }
        }
    }

    private fun shouldTryIndex(root: Path, file: Path, timestamp: Long) = when {
        root !in reverseIndex -> {
            LOGGER.debug("Root is not indexed")
            false
        }

        isFileIndexedWithTimestamp(root, file, timestamp) -> {
            LOGGER.debug(
                "File \"{}\" was already indexed with requested timestamp {}, won't update index",
                file,
                timestamp
            )
            false
        }

        else -> true
    }

    private fun tryPerformFileIndexing(root: Path, file: Path, timestamp: Long): Set<String>? = try {
        LOGGER.debug("Indexing file \"{}\" with timestamp {}", file, timestamp)
        val words = wordParser.splitAll(file)

        // To make it really safe from external changes, I need to check the timestamp after calculating the index
        if (file.toFile().lastModified() != timestamp) {
            LOGGER.debug("While indexing the file \"{}\", it was modified, will be re-indexed", file)
            null
        } else {
            words
        }
    } catch (e: IOException) {
        LOGGER.debug("Unable to index the file $file", e)
        deleteIndexForFile(root, file)
        null
    }

    override fun deleteIndexForFile(root: Path, file: Path) {
        LOGGER.debug("Deleting index for file \"{}\"", file)
        reverseIndex.compute(root) { _, v ->
            v?.also { rootReverseIndex ->
                val words = rootReverseIndex.value[file]?.words ?: setOf()
                deleteWordsFromIndex(words, file)
                rootReverseIndex.value.remove(file)
            }
        }
    }

    override fun createIndexForRoot(root: Path) {
        reverseIndex.putIfAbsent(root, RootIndexData(ConcurrentHashMap()))
    }

    override fun deleteIndexForRoot(root: Path) {
        reverseIndex.compute(root) { _, v ->
            v?.value?.forEach { file, value ->
                deleteWordsFromIndex(value.words, file)
            }
            null
        }
    }

    private fun deleteWordsFromIndex(words: Set<String>, file: Path) {
        words.forEach { word ->
            index.compute(word) { _, v -> v?.also { it.remove(file) }?.ifEmpty { null } }
        }
    }

    override fun isFileIndexed(root: Path, file: Path) = reverseIndex[root]?.value?.containsKey(file) ?: false

    override fun isFileIndexedWithTimestamp(root: Path, file: Path, timestamp: Long) =
        reverseIndex[root]?.value?.get(file)?.timestamp == timestamp

    override fun queryIndex(word: String): Set<Path> {
        LOGGER.trace("Querying index for word \"{}\"", word)
        return index[word]?.toSet() ?: setOf()
    }

    @JvmInline
    private value class RootIndexData(
        val value: MutableMap<Path, TimestampAndWords>
    )

    private data class TimestampAndWords(
        val timestamp: Long,
        val words: Set<String>
    )
}