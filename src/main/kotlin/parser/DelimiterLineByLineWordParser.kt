package parser

class DelimiterLineByLineWordParser(private vararg val delimiters: Char = charArrayOf(' ')) : LineByLineWordParser {
    override fun splitLine(line: String): Set<String> {
        return line.splitToSequence(*delimiters).map { it.trim(*delimiters) }.filter { it.isNotEmpty() }.toSet()
    }
}