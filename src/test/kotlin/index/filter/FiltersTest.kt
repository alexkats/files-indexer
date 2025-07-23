package index.filter

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FiltersTest {

    companion object {

        @JvmStatic
        fun fileExtensionFilterTestDataSource() = listOf(
            Arguments.of(
                setOf<String>(),
                listOf(
                    Paths.get("a", "b.py") to false,
                    Paths.get("a") to false
                )
            ),
            Arguments.of(
                setOf("py"),
                listOf(
                    Paths.get("a", "b.py") to true,
                    Paths.get("a") to false,
                    Paths.get("a.pyy") to false,
                    Paths.get("a.py.a") to false,
                    Paths.get("a.apy") to false
                )
            ),
            Arguments.of(
                setOf("pyy", "py", "a"),
                listOf(
                    Paths.get("a", "b.py") to true,
                    Paths.get("a") to false,
                    Paths.get("a.pyy") to true,
                    Paths.get("a.py.a") to true,
                    Paths.get("a.apy") to false,
                    Paths.get("a.py", "b") to false
                )
            )
        )

        @JvmStatic
        fun directoryNameFilterTestDataSource() = listOf(
            Arguments.of(
                setOf<String>(),
                listOf(
                    Paths.get("a", "b.py") to true,
                    Paths.get("a") to true
                )
            ),
            Arguments.of(
                setOf(".test_git"),
                listOf(
                    Paths.get("a", "b.py") to true,
                    Paths.get(".test_git") to true,
                    Paths.get(".test_git", "a") to false,
                    Paths.get(".test_git2", "a") to true,
                    Paths.get("git", "a") to true,
                    Paths.get("a", ".test_git") to true,
                )
            )
        )
    }

    @ParameterizedTest
    @MethodSource("fileExtensionFilterTestDataSource")
    fun testFileExtensionFilter(extensions: Set<String>, testCases: List<Pair<Path, Boolean>>) {
        val filter = FileExtensionFilter(extensions)
        testCases.forEach {
            assertEquals(it.second, filter.accept(it.first), "Test case: ${it.first}")
        }
    }

    @ParameterizedTest
    @MethodSource("directoryNameFilterTestDataSource")
    fun testDirectoryNameFilter(exclusions: Set<String>, testCases: List<Pair<Path, Boolean>>) {
        val filter = DirectoryNameFilter(exclusions)
        testCases.forEach {
            it.first.isDirectory()
            assertEquals(it.second, filter.accept(it.first), "Test case: ${it.first}")
        }
    }

    @Test
    fun testAggregateFilter() {
        val filter = AggregateFilter(listOf(FileExtensionFilter(setOf("py")), DirectoryNameFilter(setOf(".test_git"))))
        assertTrue(filter.accept(Paths.get("a", "b.py")))
        assertFalse(filter.accept(Paths.get("a", "b")))
        assertFalse(filter.accept(Paths.get(".test_git", "b.py")))
    }
}