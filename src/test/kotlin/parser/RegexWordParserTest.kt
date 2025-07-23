package parser

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals

class RegexWordParserTest {

    private val regexWordParser = RegexWordParser()

    companion object {

        @JvmStatic
        fun splitTestDataSource() = listOf(
            arrayOf("abc", setOf("abc")),
            arrayOf("a.b", setOf("a", "b")),
            arrayOf("[a]", setOf("a")),
            arrayOf("[a.b]", setOf("a", "b")),
            arrayOf("a_b", setOf("a_b")),
            arrayOf("a'b", setOf("a'b")),
            arrayOf("a''b", setOf("a", "b")),
            arrayOf("a'b'c", setOf("a'b", "c")),
            arrayOf("'abc'", setOf("abc")),
            arrayOf("a'_b", setOf("a")),
            arrayOf("a'_b_a", setOf("a")),
            arrayOf("a_b_a", setOf("a_b_a")),
            arrayOf("_b_a", setOf<String>()),
            arrayOf("'a.b'", setOf("a", "b")),
        )
    }

    @ParameterizedTest
    @MethodSource("splitTestDataSource")
    fun testSplitting(text: String, expected: Set<String>) {
        assertEquals(expected, regexWordParser.splitAll(text.reader()))
    }
}