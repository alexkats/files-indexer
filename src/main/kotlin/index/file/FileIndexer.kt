package index.file

import java.nio.file.Path

interface FileIndexer {

    suspend fun createOrUpdateFile(root: Path, file: Path, timestamp: Long)

    suspend fun deleteFile(root: Path, file: Path)

    fun createRoot(root: Path)

    fun deleteRoot(root: Path)

    fun query(word: String): Set<Path>
}