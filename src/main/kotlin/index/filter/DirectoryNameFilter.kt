package index.filter

import java.nio.file.Path
import kotlin.io.path.name

class DirectoryNameFilter(private val exclusions: Set<String>) : IndexingFilter {

    override fun acceptFile(path: Path): Boolean {
        return path.asSequence().take(path.nameCount - 1).none { it.name in exclusions }
    }

    override fun acceptDirectory(path: Path): Boolean {
        return path.none { it.name in exclusions }
    }

    override val priority: Int
        get() = 0

    override fun getPrintableInfo(): String {
        return "excluded dirs - [${exclusions.joinToString(",")}]"
    }
}