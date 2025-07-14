package index.hash

import java.nio.file.Path

interface FileHasher {
    fun hash(path: Path): FileHash
}
