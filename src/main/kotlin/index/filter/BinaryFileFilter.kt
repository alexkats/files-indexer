package index.filter

import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Path

class BinaryFileFilter : IndexingFilter {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(BinaryFileFilter::class.simpleName)
        private const val READ_SIZE = 1024

        private val UTF_BOMS = arrayOf(
            // UTF-8
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()),
            // UTF-16 Big Endian
            byteArrayOf(0xFE.toByte(), 0xFF.toByte()),
            // UTF-16 Little Endian
            byteArrayOf(0xFF.toByte(), 0xFE.toByte()),
            // UTF-32 Big Endian
            byteArrayOf(0x00.toByte(), 0x00.toByte(), 0xFE.toByte(), 0xFF.toByte()),
            // UTF-32 Little Endian
            byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte()),
        )
    }

    override fun acceptFile(path: Path): Boolean = try {
        path.toFile().inputStream().use { inputStream ->
            val bytes = inputStream.readNBytes(READ_SIZE)
            hasUTFByteOrderMark(bytes) || bytes.none { it == 0.toByte() }
        }
    } catch (e: IOException) {
        LOGGER.debug("Failed to detect file type for $path, assuming non-binary, most likely file deleted: ", e)
        true
    }

    private fun hasUTFByteOrderMark(bytes: ByteArray): Boolean {
        return UTF_BOMS.any { bom ->
            var match = true
            if (bytes.size >= bom.size) {
                for (i in 0 until bom.size) {
                    if (bom[i] != bytes[i]) {
                        match = false
                        break
                    }
                }
            }
            match
        }
    }

    override fun acceptDirectory(path: Path): Boolean = true

    override val priority: Int
        get() = 2
}