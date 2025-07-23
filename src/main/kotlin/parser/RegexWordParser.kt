package parser

import kotlin.math.max
import kotlin.math.min

class RegexWordParser : BufferedWordParser<DefaultWordParserState>() {

    companion object {
        private val WORD_PATTERN = Regex("""\b\p{L}[\p{L}\p{M}\p{Nd}_]*(?:[\p{Mn}']\p{L}+[\p{L}\p{M}\p{Nd}_]*)?\b""")
    }

    override fun processChunk(chunk: String, state: DefaultWordParserState): DefaultWordParserState {
        val text = state.currentWord.toString() + chunk
        val matches = WORD_PATTERN.findAll(text)
        val lastMatch = matches.lastOrNull()
        val endsCleanly = lastMatch != null && lastMatch.range.last == text.length - 1
        matches.forEach { state.result += it.value }

        if (endsCleanly) {
            state.currentWord.clear()
        } else {
            val startIndex = min(lastMatch?.range?.last?.plus(1) ?: 0, text.length - 1)
            val appendStartIndex = max(startIndex, state.currentWord.length)

            if (startIndex >= state.currentWord.length) {
                state.currentWord.clear()
            } else {
                state.currentWord.deleteRange(0, startIndex)
            }

            state.currentWord.append(text.substring(appendStartIndex))
        }

        return state
    }

    override fun eof(state: DefaultWordParserState): DefaultWordParserState {
        if (state.currentWord.isNotEmpty()) {
            val matches = WORD_PATTERN.findAll(state.currentWord)
            matches.forEach { state.result += it.value }
            state.currentWord.clear()
        }

        return state
    }

    override fun createState() = DefaultWordParserState(mutableSetOf(), StringBuilder())
}
