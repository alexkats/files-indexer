package terminal.command

import org.jline.reader.LineReader

enum class Command(
    val arity: Int,
    val usage: String,
    val description: String,
    val options: Array<CommandOption> = arrayOf()
) {
    ADD(
        2,
        "add <path>",
        "start watching the provided path",
        arrayOf(CommandOption.FILE_EXTENSION, CommandOption.EXCLUDE)
    ),
    REMOVE(2, "remove <path>", "stop watching the provided path"),
    QUERY(2, "query <word>", "query the index for a given word"),
    WATCHES(1, "watches", "show currently watched paths"),
    PWD(1, "pwd", "print current working directory"),
    HELP(1, "help", "shows this help"),
    EXIT(1, "exit", "exits the program");

    fun printUsage(lineReader: LineReader) {
        lineReader.printAbove("Usage: $usage")
        printOptionsInfo(lineReader)
    }

    fun printDescription(lineReader: LineReader) {
        lineReader.printAbove("$usage - $description")
        printOptionsInfo(lineReader)
    }

    private fun printOptionsInfo(lineReader: LineReader) {
        options.forEach { optionSpec ->
            lineReader.printAbove("  ${optionSpec.opt} <arg> - ${optionSpec.description}")
        }
    }
}