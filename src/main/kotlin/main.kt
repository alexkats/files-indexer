import index.FilesWatcher
import index.FilesWatcherImpl
import kotlinx.coroutines.runBlocking
import org.jline.builtins.Completers
import org.jline.reader.Candidate
import org.jline.reader.Completer
import org.jline.reader.EndOfFileException
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.reader.Parser
import org.jline.reader.UserInterruptException
import org.jline.reader.impl.DefaultParser
import org.jline.reader.impl.completer.ArgumentCompleter
import org.jline.reader.impl.completer.NullCompleter
import org.jline.reader.impl.completer.StringsCompleter
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import org.slf4j.LoggerFactory
import parser.DelimiterWordParser
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

private val LOGGER = LoggerFactory.getLogger("Main")
private const val PROMPT = "Indexer > "

private enum class Command(val arity: Int, val usage: String, val description: String) {
    ADD(2, "add <path>", "start watching the provided path"),
    REMOVE(2, "remove <path>", "stop watching the provided path"),
    QUERY(2, "query <word>", "query the index for a given word"),
    WATCHES(1, "watches", "show currently watched paths"),
    PWD(1, "pwd", "print current working directory"),
    HELP(1, "help", "shows this help"),
    EXIT(1, "exit", "exits the program"),
}

fun main() {
    LOGGER.info("Started")
    runBlocking {
        FilesWatcherImpl(DelimiterWordParser(), this).use { filesWatcher ->
            TerminalBuilder.builder().system(true).build().use { terminal ->
                val watches = filesWatcher.getCurrentlyWatchedLive()
                val commandCompleters = Command.entries.asSequence().map { command ->
                    val commandName = command.name.lowercase()
                    commandName to (when (command) {
                        Command.ADD -> ArgumentCompleter(
                            StringsCompleter(commandName),
                            Completers.FileNameCompleter()
                        )

                        Command.REMOVE -> ArgumentCompleter(
                            StringsCompleter(commandName),
                            { reader, line, candidates -> watches.forEach { candidates += Candidate(it.toString()) } }
                        )

                        else -> ArgumentCompleter(StringsCompleter(commandName), NullCompleter.INSTANCE)
                    })
                }.toMap()
                val completer = Completer { reader, line, candidates ->
                    val words = line.words()
                    val command = words.firstOrNull()
                    val finalCompleter = commandCompleters[command] ?: StringsCompleter(commandCompleters.keys)
                    finalCompleter.complete(reader, line, candidates)
                }
                val parser = DefaultParser()
                val lineReader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer(completer)
                    .parser(parser)
                    .appName("Indexer")
                    .option(LineReader.Option.AUTO_FRESH_LINE, true)
                    .build()
                val commands = enumValues<Command>()

                runEventLoop(terminal, lineReader, parser, commands, filesWatcher)
            }
        }
    }
}

private suspend fun runEventLoop(
    terminal: Terminal,
    lineReader: LineReader,
    parser: Parser,
    commands: Array<Command>,
    filesWatcher: FilesWatcher
) {
    fun print(s: String) = terminal.writer().println(s)
    fun flush() = terminal.writer().flush()
    fun printAndFlush(s: String) {
        print(s)
        flush()
    }

    while (true) {
        val line = try {
            lineReader.readLine(PROMPT)
        } catch (_: UserInterruptException) {
            LOGGER.debug("SIGINT received")
            continue
        } catch (_: EndOfFileException) {
            LOGGER.info("EOF was found, exiting")
            break
        }

        val tokens = parser.parse(line, line.length).words().toMutableList()
        val cmdString = tokens.firstOrNull() ?: ""

        if (cmdString.isBlank()) {
            continue
        }

        val command = commands.find { it.name.lowercase() == cmdString }
        tokens.removeIf { it.isBlank() }

        if (command == null) {
            printAndFlush("Unknown command: $cmdString")
            continue
        }

        if (command.arity != tokens.size) {
            printAndFlush("Usage: ${command.usage}")
            continue
        }

        when (command) {
            Command.ADD -> {
                val (result, comment) = filesWatcher.startWatching(Path(tokens[1]))
                if (comment.isNotBlank()) {
                    print(comment)
                }
                if (result) {
                    printAndFlush("Successfully started watching the path")
                } else {
                    printAndFlush("Failed to start watching the path")
                }
            }

            Command.REMOVE -> {
                val (result, comment) = filesWatcher.stopWatching(Path(tokens[1]))
                if (comment.isNotBlank()) {
                    print(comment)
                }
                if (result) {
                    printAndFlush("Successfully stopped watching the path")
                } else {
                    printAndFlush("Failed to stop watching the path")
                }
            }

            Command.QUERY -> {
                val result = filesWatcher.queryIndex(tokens[1])
                if (result.isEmpty())
                    printAndFlush("No files matching")
                else
                    printAndFlush(result.joinToString("\n"))
            }

            Command.WATCHES -> printAndFlush(
                filesWatcher.getCurrentlyWatchedSnapshot().joinToString("\n").ifEmpty { "No watches" }
            )

            Command.PWD -> printAndFlush(
                "Current working directory: ${Path("").normalize().absolutePathString()}"
            )

            Command.HELP -> {
                commands.forEach { print("${it.usage} - ${it.description}") }
                flush()
            }

            Command.EXIT -> {
                LOGGER.info("Exiting")
                break
            }
        }
    }
}