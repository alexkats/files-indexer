package index.filter

import java.nio.file.Path
import kotlin.io.path.isSymbolicLink

class SymlinkFilter : IndexingFilter {

    override fun acceptFile(path: Path): Boolean = !path.isSymbolicLink()

    override fun acceptDirectory(path: Path): Boolean = !path.isSymbolicLink()

    override val priority: Int
        get() = 0
}