package terminal

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import org.jline.reader.EndOfFileException
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.reader.UserInterruptException
import org.jline.reader.impl.DefaultParser
import org.jline.terminal.Terminal.TYPE_DUMB
import org.jline.terminal.Terminal.TYPE_DUMB_COLOR
import org.jline.terminal.TerminalBuilder
import org.slf4j.LoggerFactory
import terminal.command.Command
import terminal.command.CommandCompleter
import terminal.command.CommandHandler
import terminal.command.CommandOption
import terminal.statusbar.DefaultStatusBarRenderer
import terminal.statusbar.NoOpStatusBarRenderer
import terminal.statusbar.StatusBarRenderer
import java.nio.file.Path

class TerminalHandler(
    private val liveWatches: Set<Path>,
    private val scope: CoroutineScope,
    private val commandHandler: CommandHandler
) {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(TerminalHandler::class.simpleName)

        private const val PROMPT = "Indexer > "
        private const val RESERVED_HEIGHT = 5
    }

    private val parser = DefaultParser()

    suspend fun run() {
        LOGGER.info("Started")
        TerminalBuilder.builder().system(true).build().use { terminal ->
            val isDumbTerminal = terminal.type in arrayOf(TYPE_DUMB, TYPE_DUMB_COLOR)
            val completer = CommandCompleter(liveWatches)
            val lineReader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(completer)
                .parser(parser)
                .appName("Indexer")
                .option(LineReader.Option.AUTO_FRESH_LINE, true)
                .build()
            val statusBarRenderer = if (isDumbTerminal) NoOpStatusBarRenderer() else {
                DefaultStatusBarRenderer(terminal, RESERVED_HEIGHT, scope)
            }
            statusBarRenderer.use { statusBarRenderer ->
                runEventLoop(lineReader, statusBarRenderer)
            }
        }
    }

    private fun parseAndRemoveOptionsFromTokens(
        command: Command,
        tokens: MutableList<String>
    ): Map<CommandOption, Set<String>> {
        val options = command.options
        if (options.isEmpty()) {
            return mapOf()
        }

        val result = mutableMapOf<CommandOption, MutableSet<String>>()
        var currentFlag: CommandOption? = null

        tokens.removeIf { token ->
            if (currentFlag != null) {
                result.compute(currentFlag!!) { k, v ->
                    val res = v ?: mutableSetOf()
                    res.addAll(token.split(","))
                    res
                }
                currentFlag = null
                true
            } else {
                currentFlag = options.singleOrNull { it.opt == token }
                currentFlag != null
            }
        }

        return result
    }

    private suspend fun runEventLoop(
        lineReader: LineReader,
        statusBarRenderer: StatusBarRenderer
    ) {
        fun print(s: String) = lineReader.printAbove(s)
        val commands = enumValues<Command>()

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
                print("Unknown command: $cmdString")
                continue
            }

            val options = parseAndRemoveOptionsFromTokens(command, tokens)

            if (command.arity != tokens.size) {
                command.printUsage(lineReader)
                continue
            }

            try {
                commandHandler.handle(command, tokens.getOrNull(1) ?: "", options, statusBarRenderer, ::print)

                if (command == Command.HELP) {
                    commands.forEach { it.printDescription(lineReader) }
                } else if (command == Command.EXIT) {
                    LOGGER.info("Exiting")
                    break
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LOGGER.error("Unexpected error occurred: ", e)
                print("Some unexpected error occurred, please check logs for details")
            }
        }
    }
}

