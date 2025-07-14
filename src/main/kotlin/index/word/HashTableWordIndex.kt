package index.word

import index.hash.FileHash
import org.slf4j.LoggerFactory
import parser.WordParser
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class HashTableWordIndex(private val wordParser: WordParser) : WordIndex {
    private val index: MutableMap<String, MutableSet<FileHash>> = ConcurrentHashMap()
    private val reverseIndex: MutableMap<FileHash, Set<String>> = ConcurrentHashMap()

    companion object {
        private val LOGGER = LoggerFactory.getLogger(HashTableWordIndex::class.simpleName)
    }

    override fun indexFile(fileName: String, hash: FileHash, getUpdatedHash: () -> FileHash) {
        LOGGER.debug("Indexing file \"{}\" with hash \"{}\"", fileName, hash)
        val words = try {
            wordParser.splitAll(fileName)
        } catch (e: IOException) {
            LOGGER.debug("Unable to index the file $fileName", e)
            return
        }
        // To make it really safe from external changes, I need to check the hash after calculating the index
        if (getUpdatedHash() != hash) {
            LOGGER.debug("While indexing the file \"{}\", it was modified, will be re-indexed", fileName)
            return
        }
        words.forEach { word ->
            index.compute(word) { _, v -> (v ?: mutableSetOf()).also { it += hash } }
        }
        reverseIndex[hash] = words
        LOGGER.debug("File \"{}\" was indexed with hash \"{}\"", fileName, hash)
    }

    override fun deleteIndex(hash: FileHash) {
        LOGGER.debug("Deleting index for hash \"{}\"", hash)
        val words = reverseIndex[hash] ?: setOf()
        words.forEach { word ->
            index.compute(word) { _, v -> v?.also { it -= hash }?.ifEmpty { null } }
        }
        reverseIndex -= hash
    }

    override fun isFileHashIndexed(hash: FileHash) = hash in reverseIndex

    override fun queryIndex(word: String): Set<FileHash> {
        LOGGER.trace("Querying index for word \"{}\"", word)
        return index[word]?.toSet() ?: setOf()
    }
}