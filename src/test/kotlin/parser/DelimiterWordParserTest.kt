package parser

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.Test
import kotlin.test.assertEquals

class DelimiterWordParserTest {

    companion object {
        data class TestCaseData(
            val text: String,
            val delimiters: CharArray,
            val expectedDelimiterLineByLine: Set<String>,
            val expectedDelimiter: Set<String>,
            val expectedDelimiterNewlineIsDelimiter: Set<String>,
        ) {
            private fun String.replaceWithExplicitWhitespaces() =
                replace(' ', '\u2423').replace("\n", "\\n")

            override fun toString(): String {
                val textString = "\"${text.replaceWithExplicitWhitespaces()}\""
                val delimitersString = delimiters.joinToString(prefix = "[", postfix = "]") {
                    "'${it.toString().replaceWithExplicitWhitespaces()}'"
                }
                return "Case: text = $textString, delimiters = $delimitersString"
            }
        }

        @JvmStatic
        fun splitTestDataSource() = listOf(
            TestCaseData(
                text = "abc",
                delimiters = charArrayOf(' '),
                expectedDelimiterLineByLine = setOf("abc"),
                expectedDelimiter = setOf("abc"),
                expectedDelimiterNewlineIsDelimiter = setOf("abc")
            ),
            TestCaseData(
                text = "aaa bbb",
                delimiters = charArrayOf(' '),
                expectedDelimiterLineByLine = setOf("aaa", "bbb"),
                expectedDelimiter = setOf("aaa", "bbb"),
                expectedDelimiterNewlineIsDelimiter = setOf("aaa", "bbb")
            ),
            TestCaseData(
                text = "aaa bbb\nccc",
                delimiters = charArrayOf(' '),
                expectedDelimiterLineByLine = setOf("aaa", "bbb", "ccc"),
                expectedDelimiter = setOf("aaa", "bbb\nccc"),
                expectedDelimiterNewlineIsDelimiter = setOf("aaa", "bbb", "ccc")
            ),
            TestCaseData(
                text = "aaa bbb\nccc\nbbb",
                delimiters = charArrayOf(' '),
                expectedDelimiterLineByLine = setOf("aaa", "bbb", "ccc"),
                expectedDelimiter = setOf("aaa", "bbb\nccc\nbbb"),
                expectedDelimiterNewlineIsDelimiter = setOf("aaa", "bbb", "ccc")
            ),
            TestCaseData(
                text = "aaa     bbb",
                delimiters = charArrayOf(' '),
                expectedDelimiterLineByLine = setOf("aaa", "bbb"),
                expectedDelimiter = setOf("aaa", "bbb"),
                expectedDelimiterNewlineIsDelimiter = setOf("aaa", "bbb")
            ),
            TestCaseData(
                text = "aaa   \n  bbb",
                delimiters = charArrayOf(' '),
                expectedDelimiterLineByLine = setOf("aaa", "bbb"),
                expectedDelimiter = setOf("aaa", "\n", "bbb"),
                expectedDelimiterNewlineIsDelimiter = setOf("aaa", "bbb")
            ),
        )
    }

    @ParameterizedTest
    @MethodSource("splitTestDataSource")
    fun testSplitting(testCaseData: TestCaseData) {
        val text = testCaseData.text
        val delimiters = testCaseData.delimiters
        assertEquals(
            testCaseData.expectedDelimiterLineByLine,
            DelimiterLineByLineWordParser(*delimiters).splitAll(text.reader())
        )
        assertEquals(
            testCaseData.expectedDelimiter,
            DelimiterWordParser(*delimiters, treatNewlineAsDelimiter = false).splitAll(text.reader())
        )
        assertEquals(
            testCaseData.expectedDelimiterNewlineIsDelimiter,
            DelimiterWordParser(*delimiters).splitAll(text.reader())
        )
    }

    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    @Test
    fun testParallelSplitting() {
        val wordParser = DelimiterWordParser()
        val text = generateSequence('a') {
            when (it) {
                'z' -> ' '
                ' ' -> 'a'
                else -> it + 1
            }
        }.take(10000 * 27).joinToString("")
        runBlocking {
            repeat(1000) {
                launch(newFixedThreadPoolContext(8, "ParallelSplittingPool")) {
                    assertEquals(setOf("abcdefghijklmnopqrstuvwxyz"), wordParser.splitAll(text.reader()))
                }
            }
        }
    }
}