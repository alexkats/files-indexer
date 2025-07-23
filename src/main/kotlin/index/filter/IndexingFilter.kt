package index.filter

import java.nio.file.Path
import kotlin.io.path.isDirectory

interface IndexingFilter {

    fun accept(path: Path): Boolean = if (path.isDirectory()) acceptDirectory(path) else acceptFile(path)

    fun acceptFile(path: Path): Boolean

    fun acceptDirectory(path: Path): Boolean

    // It's used in aggregate filter to determine the order in which filters should be applied.
    // Lower number means filter will be applied sooner.
    val priority: Int

    fun getPrintableInfo(): String = ""
}