package index.filter

import java.nio.file.Path

class AggregateFilter(filtersList: List<IndexingFilter>) : IndexingFilter {

    private val filters = filtersList.sortedBy { it.priority }

    override fun acceptFile(path: Path): Boolean {
        return filters.all { it.acceptFile(path) }
    }

    override fun acceptDirectory(path: Path): Boolean {
        return filters.all { it.acceptDirectory(path) }
    }

    override val priority: Int
        get() = 1000

    override fun getPrintableInfo(): String {
        return filters.asSequence().map { it.getPrintableInfo() }.filter { it.isNotBlank() }.joinToString()
    }
}