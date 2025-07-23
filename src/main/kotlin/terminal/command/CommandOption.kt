package terminal.command

enum class CommandOption(val opt: String, val description: String) {
    FILE_EXTENSION("--file-extension", "specify file extensions to be tracked separated by commas"),
    EXCLUDE("--exclude", "specify directory names to be excluded from indexing separated by commas")
}