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
        data class TestCaseData<T>(
            val text: String,
            val delimiters: CharArray,
            val expectedDelimiterLineByLine: T,
            val expectedDelimiter: T,
            val expectedDelimiterNewlineIsDelimiter: T,
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

        @JvmStatic
        fun trimTestDataSource() = listOf(
            TestCaseData(
                text = " abc",
                delimiters = charArrayOf(' '),
                expectedDelimiterLineByLine = "abc",
                expectedDelimiter = "abc",
                expectedDelimiterNewlineIsDelimiter = "abc"
            ),
            TestCaseData(
                text = " abc   ",
                delimiters = charArrayOf(' '),
                expectedDelimiterLineByLine = "abc",
                expectedDelimiter = "abc",
                expectedDelimiterNewlineIsDelimiter = "abc"
            ),
            TestCaseData(
                text = " abc \n ",
                delimiters = charArrayOf(' '),
                expectedDelimiterLineByLine = "abc \n",
                expectedDelimiter = "abc \n",
                expectedDelimiterNewlineIsDelimiter = "abc"
            ),
            TestCaseData(
                text = " abc \n ",
                delimiters = charArrayOf(' ', '\n'),
                expectedDelimiterLineByLine = "abc",
                expectedDelimiter = "abc",
                expectedDelimiterNewlineIsDelimiter = "abc"
            ),
            TestCaseData(
                text = " abc \n ",
                delimiters = charArrayOf(' ', '\n', 'a'),
                expectedDelimiterLineByLine = "bc",
                expectedDelimiter = "bc",
                expectedDelimiterNewlineIsDelimiter = "bc"
            ),
            TestCaseData(
                text = "     ",
                delimiters = charArrayOf(' '),
                expectedDelimiterLineByLine = "",
                expectedDelimiter = "",
                expectedDelimiterNewlineIsDelimiter = ""
            ),
            TestCaseData(
                text = "  \n ",
                delimiters = charArrayOf(' '),
                expectedDelimiterLineByLine = "\n",
                expectedDelimiter = "\n",
                expectedDelimiterNewlineIsDelimiter = ""
            ),
            TestCaseData(
                text = "  \n ",
                delimiters = charArrayOf(' ', '\n'),
                expectedDelimiterLineByLine = "",
                expectedDelimiter = "",
                expectedDelimiterNewlineIsDelimiter = ""
            ),
        )
    }

    @ParameterizedTest
    @MethodSource("splitTestDataSource")
    fun testSplitting(testCaseData: TestCaseData<Set<String>>) {
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

    @ParameterizedTest
    @MethodSource("trimTestDataSource")
    fun testTrimming(testCaseData: TestCaseData<String>) {
        val text = testCaseData.text
        val delimiters = testCaseData.delimiters
        assertEquals(testCaseData.expectedDelimiterLineByLine, DelimiterLineByLineWordParser(*delimiters).trim(text))
        assertEquals(
            testCaseData.expectedDelimiter,
            DelimiterWordParser(*delimiters, treatNewlineAsDelimiter = false).trim(text)
        )
        assertEquals(testCaseData.expectedDelimiterNewlineIsDelimiter, DelimiterWordParser(*delimiters).trim(text))
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