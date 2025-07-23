import index.FilesWatcherImpl
import kotlinx.coroutines.runBlocking
import parser.RegexWordParser
import terminal.TerminalHandler
import terminal.command.Command
import terminal.command.CommandOption
import util.ProgressTracker
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

fun main() {
    runBlocking {
        FilesWatcherImpl(RegexWordParser(), this).use { filesWatcher ->
            TerminalHandler(
                filesWatcher.getCurrentlyWatchedLive(),
                this
            ) { command, arg, options, statusBarRenderer, print ->
                when (command) {
                    Command.ADD -> {
                        val path = Path(arg).toAbsolutePath().normalize()
                        val progressTracker = ProgressTracker()
                        val (result, comment) = filesWatcher.startWatching(
                            path,
                            options.getOrDefault(CommandOption.EXCLUDE, setOf()),
                            options.getOrDefault(CommandOption.FILE_EXTENSION, setOf()),
                            progressTracker
                        )
                        if (comment.isNotBlank()) {
                            print(comment)
                        }
                        if (result) {
                            print("Successfully started watching the path")
                            statusBarRenderer.startTracking(path, progressTracker)
                        } else {
                            print("Failed to start watching the path")
                        }
                    }

                    Command.REMOVE -> {
                        val path = Path(arg).toAbsolutePath().normalize()
                        val (result, comment) = filesWatcher.stopWatching(
                            path,
                            statusBarRenderer.getProgressTrackerOrNull(path)
                        )
                        if (comment.isNotBlank()) {
                            print(comment)
                        }
                        if (result) {
                            print("Successfully stopped watching the path")
                        } else {
                            print("Failed to stop watching the path")
                        }
                    }

                    Command.QUERY -> {
                        val result = filesWatcher.queryIndex(arg)
                        if (result.isEmpty())
                            print("No files matching")
                        else
                            print(result.joinToString("\n"))
                    }

                    Command.WATCHES -> print(
                        filesWatcher.getCurrentWatchesPrintableInfo().joinToString("\n").ifEmpty { "No watches" }
                    )

                    Command.PWD -> print(
                        "Current working directory: ${Path("").normalize().absolutePathString()}"
                    )

                    Command.HELP, Command.EXIT -> Unit
                }
            }.run()
        }
    }
}