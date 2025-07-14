package index.word

import index.hash.FileHash

interface WordIndex {

    fun indexFile(fileName: String, hash: FileHash, getUpdatedHash: () -> FileHash)

    fun deleteIndex(hash: FileHash)

    fun isFileHashIndexed(hash: FileHash): Boolean

    fun queryIndex(word: String): Set<FileHash>
}