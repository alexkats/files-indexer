package index.filter

import java.nio.file.Path
import kotlin.io.path.extension

class FileExtensionFilter(private val extensions: Set<String>) : IndexingFilter {

    override fun acceptFile(path: Path): Boolean {
        return path.extension in extensions
    }

    override fun acceptDirectory(path: Path): Boolean = true

    override val priority: Int
        get() = 0

    override fun getPrintableInfo(): String {
        return "file extensions - [${extensions.joinToString(",")}]"
    }
}