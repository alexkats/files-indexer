package parser

class DelimiterLineByLineWordParser(private vararg val delimiters: Char = charArrayOf(' ')) : LineByLineWordParser {
    override fun splitLine(line: String): Set<String> {
        return line.splitToSequence(*delimiters).map { trim(it) }.filter { it.isNotEmpty() }.toSet()
    }

    override fun trim(word: String) = word.trim(*delimiters)
}