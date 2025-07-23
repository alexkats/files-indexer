package index.filter

import java.nio.file.Path

class AcceptAllFilter : IndexingFilter {

    override fun acceptFile(path: Path): Boolean = true

    override fun acceptDirectory(path: Path): Boolean = true

    override val priority: Int
        get() = 1000
}