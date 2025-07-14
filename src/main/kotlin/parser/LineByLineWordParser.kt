package parser

import java.io.Reader

interface LineByLineWordParser : WordParser {

    fun splitLine(line: String): Set<String>

    override fun splitAll(reader: Reader): Set<String> {
        return reader.useLines { it.map { splitLine(it) }.toSet() }.flatten().toSet()
    }
}