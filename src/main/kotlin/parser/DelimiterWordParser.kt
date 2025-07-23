package parser

class DelimiterWordParser(
    vararg delimiters: Char = charArrayOf(' '),
    treatNewlineAsDelimiter: Boolean = true
) : BufferedWordParser<DefaultWordParserState>() {
    private val delimitersSet = if (treatNewlineAsDelimiter) {
        (delimiters + '\n')
    } else delimiters

    override fun processChunk(chunk: String, state: DefaultWordParserState): DefaultWordParserState {
        chunk.forEach {
            if (it in delimitersSet && state.currentWord.isNotEmpty()) {
                state.result.add(state.currentWord.toString())
                state.currentWord.clear()
            } else if (it !in delimitersSet) {
                state.currentWord.append(it)
            }
        }
        return state
    }

    override fun eof(state: DefaultWordParserState): DefaultWordParserState {
        if (state.currentWord.isNotEmpty()) {
            state.result.add(state.currentWord.toString())
            state.currentWord.clear()
        }
        return state
    }

    override fun createState() = DefaultWordParserState(mutableSetOf(), StringBuilder())
}
