package parser

class DelimiterWordParser(
    vararg delimiters: Char = charArrayOf(' '),
    treatNewlineAsDelimiter: Boolean = true
) : BufferedWordParser<DelimiterWordParserState>() {
    private val delimitersSet = if (treatNewlineAsDelimiter) {
        (delimiters + '\n')
    } else delimiters

    override fun processChunk(chunk: String, state: DelimiterWordParserState): DelimiterWordParserState {
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

    override fun eof(state: DelimiterWordParserState): DelimiterWordParserState {
        if (state.currentWord.isNotEmpty()) {
            state.result.add(state.currentWord.toString())
            state.currentWord.clear()
        }
        return state
    }

    override fun createState() = DelimiterWordParserState(mutableSetOf(), StringBuilder())

    override fun trim(word: String) = word.trim(*delimitersSet)
}

data class DelimiterWordParserState(
    override val result: MutableSet<String>,
    val currentWord: StringBuilder,
) : WordParserState
