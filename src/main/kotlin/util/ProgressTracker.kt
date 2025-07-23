package util

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class ProgressTracker {
    @Volatile
    var status = ProgressStatus.CREATE_QUEUED
    private val totalFiles = AtomicLong(0L)
    private val indexedFiles = AtomicLong(0L)
    private val allFilesAdded = AtomicBoolean(false)

    companion object {
        private const val BAR_WIDTH = 30
    }

    fun fileIndexed() {
        indexedFiles.incrementAndGet()
        checkDone()
    }

    fun fileAdded() {
        totalFiles.incrementAndGet()
    }

    fun allFilesAdded() {
        allFilesAdded.set(true)
        checkDone()
    }

    private fun checkDone() {
        if (allFilesAdded.get() && indexedFiles.get() == totalFiles.get()) {
            status = ProgressStatus.DONE
        }
    }

    fun getPrintableStatus(): String {
        return when (status) {
            ProgressStatus.INDEXING -> {
                val indexed = indexedFiles.get()
                val total = totalFiles.get()
                val allAdded = allFilesAdded.get()
                val progress = when {
                    total == 0L && allAdded -> 100
                    total == 0L && !allAdded -> 0
                    else -> (indexed * 100 / total).toInt()
                }
                "${status.printableStatus} - ${getProgressBar(progress)}"
            }

            else -> status.printableStatus
        }
    }

    private fun getProgressBar(progress: Int): String {
        val filled = progress * BAR_WIDTH / 100
        val empty = BAR_WIDTH - filled
        return "[" + "=".repeat(filled) + " ".repeat(empty) + "] $progress%"
    }
}

enum class ProgressStatus(val printableStatus: String, val printPriority: Int) {
    CREATE_QUEUED("Watch creation queued", 3),
    INITIALIZING_WATCHER("Initializing changes watcher", 1),
    INDEXING("Indexing in progress", 0),
    REMOVE_QUEUED("Watch removal queued", 3),
    REMOVING("Watch is being removed", 2),
    REMOVED("Watch removed", 1000),
    DONE("Indexing is done", 1000)
}