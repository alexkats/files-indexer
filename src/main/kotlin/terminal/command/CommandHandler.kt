package terminal.command

import terminal.statusbar.StatusBarRenderer

fun interface CommandHandler {

    suspend fun handle(
        command: Command,
        argument: String,
        options: Map<CommandOption, Set<String>>,
        statusBarRenderer: StatusBarRenderer,
        printer: (String) -> Unit
    )
}