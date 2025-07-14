package index.file

import java.nio.file.Path

interface FileIndexer {

    suspend fun createOrUpdateFile(path: Path)

    suspend fun deleteFile(path: Path)

    fun query(word: String): Set<String>
}