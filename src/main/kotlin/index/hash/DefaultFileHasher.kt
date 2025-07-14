package index.hash

import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Path
import io.methvin.watcher.hashing.FileHasher as ExternalFileHasher

class DefaultFileHasher : FileHasher {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(DefaultFileHasher::class.simpleName)
    }

    override fun hash(path: Path): FileHash = try {
        FileHash(ExternalFileHasher.DEFAULT_FILE_HASHER.hash(path).asString())
    } catch (e: IOException) {
        LOGGER.debug("Unable to calculate hash for file $path", e)
        FileHash.EMPTY
    }
}