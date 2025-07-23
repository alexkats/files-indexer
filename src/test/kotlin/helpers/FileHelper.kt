package helpers

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.writeText

object FileHelper {

    private fun validateAndGetRealPath(rootPath: Path, path: Path): Path {
        if (path.isAbsolute) {
            error("FileHelper shouldn't use absolute paths")
        }
        return rootPath.resolve(path).normalize().toAbsolutePath()
    }

    fun createOrUpdateFile(rootPath: Path, fileName: String, content: String) =
        createOrUpdateFile(rootPath, Paths.get(fileName), content)

    fun deleteFile(rootPath: Path, fileName: String) = deleteFile(rootPath, Paths.get(fileName))

    fun createOrUpdateFile(rootPath: Path, path: Path, content: String): Path {
        val realPath = validateAndGetRealPath(rootPath, path)
        // This is to make sure that we'll have different modification timestamps for all files
        Thread.sleep(1)
        if (!realPath.exists()) {
            Files.createFile(realPath)
        }
        realPath.writeText(content)
        return realPath
    }

    fun deleteFile(rootPath: Path, path: Path) {
        val realPath = validateAndGetRealPath(rootPath, path)
        Files.deleteIfExists(realPath)
    }

    fun createDirectory(rootPath: Path, path: Path): Path {
        val realPath = validateAndGetRealPath(rootPath, path)
        Files.createDirectories(realPath)
        return realPath
    }

    fun deleteDirectory(rootPath: Path, path: Path) {
        val realPath = validateAndGetRealPath(rootPath, path)
        realPath.toFile().walkBottomUp().forEach { it.delete() }
    }
}