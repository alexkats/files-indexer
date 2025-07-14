package index.hash

@JvmInline
value class FileHash(val hash: String) {
    companion object {
        val EMPTY = FileHash("EMPTY")
    }
}