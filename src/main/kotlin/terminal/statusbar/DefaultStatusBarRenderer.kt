package terminal.statusbar

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import org.jline.terminal.Terminal
import util.ProgressStatus
import util.ProgressTracker
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class DefaultStatusBarRenderer(
    private val terminal: Terminal,
    private val reservedHeight: Int,
    scope: CoroutineScope
) : StatusBarRenderer {
    private val rootToProgress: MutableMap<Path, ProgressTracker> = ConcurrentHashMap()

    override fun startTracking(path: Path, progressTracker: ProgressTracker) {
        rootToProgress.put(path, progressTracker)
    }

    override fun getProgressTrackerOrNull(path: Path) = rootToProgress[path]

    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val job = scope.launch(dispatcher, start = CoroutineStart.LAZY) {
        while (true) {
            cleanUpStatusMapNew()
            renderStatusBar(terminal, reservedHeight)
            delay(500.milliseconds)
        }
    }

    init {
        write("\u001B[1;${terminal.height - reservedHeight}r") // reserve scrollable region
        write("\u001B[2J") // clear screen
        write("\u001B[1:1H") // goto 1:1
        job.start()
    }

    private fun cleanUpStatusMapNew() {
        for (path in rootToProgress.keys) {
            rootToProgress.compute(path) { _, v ->
                v?.let { if (it.status == ProgressStatus.REMOVED) null else v }
            }
        }
    }

    private fun getStatusMessages() =
        rootToProgress.asSequence().filterNot { it.value.status == ProgressStatus.DONE }.sortedBy {
            it.value.status.printPriority
        }.map {
            "${it.key}: ${it.value.getPrintableStatus()}"
        }

    private fun renderStatusBar(terminal: Terminal, reservedHeight: Int) {
        write("\u001B7") // save cursor position
        write("\u001B[${terminal.height - reservedHeight + 1};1H") // goto first row after scrollable region
        write("\u001B[1;34mSTATUS: ${rootToProgress.size} path(s) are being watched\u001B[0m") // blue bold status line
        val maxMessages = reservedHeight - 2
        val startingIndex = terminal.height - reservedHeight + 2

        val messages = sequence {
            val iterator = getStatusMessages().iterator()
            var count = 0

            while (iterator.hasNext() && count < maxMessages) {
                yield(iterator.next())
                count++
            }

            if (iterator.hasNext()) {
                yield("...")
            } else {
                repeat(maxMessages - count + 1) {
                    yield(null)
                }
            }
        }

        messages.forEachIndexed { i, message ->
            write("\u001B[${startingIndex + i};1H") // goto first row after status (and then iterate)
            write("\u001B[2K") // clear line
            message?.also { write(it.padEnd(terminal.width)) }
        }
        write("\u001B8") // restore cursor position
        terminal.flush()
    }

    private fun write(s: String) = terminal.writer().write(s)

    override fun close() {
        job.cancel()
        dispatcher.close()
        write("\u001B[r") // reset scrollable region
        terminal.flush()
    }
}

