package helpers

import parser.DelimiterWordParser
import parser.WordParser

class MockWordParser(private val delegate: WordParser = DelimiterWordParser()) : WordParser by delegate {

    // We treat fileName as a text to split for test purposes
    override fun splitAll(fileName: String): Set<String> {
        return delegate.splitAll(fileName.reader())
    }
}