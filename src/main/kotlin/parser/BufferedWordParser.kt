package parser

import java.io.Reader

abstract class BufferedWordParser<T : WordParserState> : WordParser {

    protected abstract fun createState(): T
    abstract fun processChunk(chunk: String, state: T): T
    protected abstract fun eof(state: T): T

    override fun splitAll(reader: Reader): Set<String> {
        var state = createState()
        reader.buffered(DEFAULT_BUFFER_SIZE).use { reader ->
            val buffer = CharArray(DEFAULT_BUFFER_SIZE)
            var count: Int
            while (reader.read(buffer).also { count = it } != -1) {
                state = processChunk(String(buffer, 0, count), state)
            }
            state = eof(state)
        }

        return state.result
    }
}