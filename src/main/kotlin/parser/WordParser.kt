package parser

import java.io.FileReader
import java.io.Reader

interface WordParser {
    fun splitAll(reader: Reader): Set<String>

    fun splitAll(fileName: String): Set<String> = splitAll(FileReader(fileName))

    fun trim(word: String): String = word
}

interface WordParserState {
    val result: MutableSet<String>
}