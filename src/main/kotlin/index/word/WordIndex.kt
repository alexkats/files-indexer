package index.word

import java.nio.file.Path

interface WordIndex {

    fun indexFile(root: Path, file: Path, timestamp: Long)

    fun deleteIndexForFile(root: Path, file: Path)

    fun createIndexForRoot(root: Path)

    fun deleteIndexForRoot(root: Path)

    fun isFileIndexed(root: Path, file: Path): Boolean

    fun isFileIndexedWithTimestamp(root: Path, file: Path, timestamp: Long): Boolean

    fun queryIndex(word: String): Set<Path>
}