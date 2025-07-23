package terminal.command

import org.jline.builtins.Completers
import org.jline.reader.Candidate
import org.jline.reader.Completer
import org.jline.reader.LineReader
import org.jline.reader.ParsedLine
import org.jline.reader.impl.completer.ArgumentCompleter
import org.jline.reader.impl.completer.NullCompleter
import org.jline.reader.impl.completer.StringsCompleter
import java.nio.file.Path

class CommandCompleter(private val liveWatches: Set<Path>) : Completer {

    private val completer = buildFinalCompleter()

    override fun complete(reader: LineReader, line: ParsedLine, candidates: List<Candidate>) {
        completer.complete(reader, line, candidates)
    }

    private fun buildCompleterForAddCommand(command: Command) = Completer { reader, line, candidates ->
        val words: List<String> = line.words() // 1st is command
        val optionStrings = command.options.map { it.opt }
        var insideKnownOption = false
        var couldNotParse = false
        var insideUnknownOrIncompleteOption = false
        var wasFileArg = false
        var currentStep = 0 // 0 - file, 1 - option, 2 - option argument

        for (i in 1 until words.size) {
            val word = words[i]

            if (word.startsWith("--")) {
                if (insideKnownOption) {
                    couldNotParse = true
                } else if (word in optionStrings) {
                    insideKnownOption = true
                } else {
                    insideUnknownOrIncompleteOption = true
                }
                currentStep = 1
            } else if (insideKnownOption) {
                insideKnownOption = false
                currentStep = 2
            } else if (insideUnknownOrIncompleteOption) {
                couldNotParse = true
            } else if (wasFileArg) {
                currentStep = 1
                insideUnknownOrIncompleteOption = true
            } else {
                wasFileArg = true
                currentStep = 0
            }

            if (couldNotParse) {
                break
            }
        }

        if (!couldNotParse) {
            when (currentStep) {
                0 -> Completers.FileNameCompleter().complete(reader, line, candidates)
                1 -> StringsCompleter(optionStrings).complete(reader, line, candidates)
            }
        }
    }

    private fun buildFinalCompleter(): Completer {
        val commandCompleters = Command.entries.asSequence().map { command ->
            val commandName = command.name.lowercase()
            commandName to (when (command) {
                Command.ADD -> buildCompleterForAddCommand(command)

                Command.REMOVE -> ArgumentCompleter(
                    StringsCompleter(commandName),
                    { reader, line, candidates -> liveWatches.forEach { candidates += Candidate(it.toString()) } }
                )

                else -> ArgumentCompleter(StringsCompleter(commandName), NullCompleter.INSTANCE)
            })
        }.toMap()

        return Completer { reader, line, candidates ->
            val words = line.words()
            val command = words.firstOrNull()
            val finalCompleter = commandCompleters[command] ?: StringsCompleter(commandCompleters.keys)
            finalCompleter.complete(reader, line, candidates)
        }
    }
}