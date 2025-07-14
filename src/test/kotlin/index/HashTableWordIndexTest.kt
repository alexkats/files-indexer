package index

import helpers.MockWordParser
import index.hash.FileHash
import index.word.HashTableWordIndex
import index.word.WordIndex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// There's no test on updating a file with the same hash, as the content should be the same
// (and we won't actually do it from FileWatcher without deleting the index first)
class HashTableWordIndexTest {

    private val wordParser = MockWordParser()

    @Test
    fun testBasicIndexing() {
        val index = HashTableWordIndex(wordParser)
        val hash = FileHash("hash")
        index.indexFile("aaa bbb", hash) { hash }

        assertTrue(index.isFileHashIndexed(hash))
        index.assertExpected(setOf(hash), "aaa")
        index.assertExpected(setOf(hash), "bbb")
        index.assertEmpty("aaa bbb")
    }

    @Test
    fun testIndexRemoval() {
        val index = HashTableWordIndex(wordParser)
        val hash = FileHash("hash")
        index.indexFile("aaa bbb", hash) { hash }
        index.deleteIndex(hash)

        assertFalse(index.isFileHashIndexed(hash))
        index.assertEmpty("aaa")
        index.assertEmpty("bbb")
        index.assertEmpty("aaa bbb")
    }

    @Test
    fun testMultipleHashes() {
        val index = HashTableWordIndex(wordParser)
        val hash1 = FileHash("hash1")
        val hash2 = FileHash("hash2")
        index.indexFile("aaa bbb", hash1) { hash1 }
        index.indexFile("ccc ddd aaa", hash2) { hash2 }

        assertTrue(index.isFileHashIndexed(hash1))
        assertTrue(index.isFileHashIndexed(hash2))

        index.assertExpected(setOf(hash1, hash2), "aaa")
        index.assertExpected(setOf(hash1), "bbb")
        index.assertExpected(setOf(hash2), "ccc")
        index.assertExpected(setOf(hash2), "ddd")

        index.deleteIndex(hash1)

        index.assertExpected(setOf(hash2), "aaa")
        index.assertEmpty("bbb")

        index.deleteIndex(hash2)

        index.assertEmpty("ccc")
        index.assertEmpty("ddd")
    }

    @Test
    fun testReindexing() {
        val index = HashTableWordIndex(wordParser)
        val hash = FileHash("hash")
        index.indexFile("aaa bbb", hash) { hash }
        index.deleteIndex(hash)
        index.indexFile("ccc ddd", hash) { hash }

        assertTrue(index.isFileHashIndexed(hash))
        index.assertEmpty("aaa")
        index.assertEmpty("bbb")
        index.assertExpected(setOf(hash), "ccc")
        index.assertExpected(setOf(hash), "ddd")

        index.deleteIndex(hash)

        assertFalse(index.isFileHashIndexed(hash))
        index.assertEmpty("ccc")
        index.assertEmpty("ddd")

        index.indexFile("ccc ddd", hash) { hash }

        assertTrue(index.isFileHashIndexed(hash))
        index.assertExpected(setOf(hash), "ccc")
        index.assertExpected(setOf(hash), "ddd")
    }

    private fun WordIndex.assertExpected(expected: Set<FileHash>, word: String) {
        assertEquals(expected, queryIndex(word), "Index for word \"$word\" is unexpected")
    }

    private fun WordIndex.assertEmpty(word: String) {
        assertEquals(setOf(), queryIndex(word), "Index is not empty for word \"$word\"")
    }
}