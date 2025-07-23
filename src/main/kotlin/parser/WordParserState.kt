package parser

interface WordParserState {
    val result: MutableSet<String>
}

data class DefaultWordParserState(
    override val result: MutableSet<String>,
    val currentWord: StringBuilder,
) : WordParserState
