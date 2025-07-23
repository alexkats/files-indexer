package parser

import java.io.FileReader
import java.io.Reader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Path

interface WordParser {
    fun splitAll(reader: Reader): Set<String>

    fun splitAll(file: Path, charset: Charset = StandardCharsets.UTF_8): Set<String> =
        splitAll(FileReader(file.toFile(), charset))
}
