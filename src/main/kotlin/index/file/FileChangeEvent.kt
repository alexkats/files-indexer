package index.file

import java.nio.file.Path

data class FileChangeEvent(
    val root: Path,
    val file: Path,
    val timestamp: Long,
    val eventType: EventType,
    val onFinish: () -> Unit = {},
)

enum class EventType(val opFunc: suspend (FileIndexer).(Path, Path, Long) -> Unit) {
    CREATE_ROOT({ root, _, _ -> createRoot(root) }),
    UPDATE({ root, path, timestamp -> createOrUpdateFile(root, path, timestamp) }),
    DELETE({ root, path, _ -> deleteFile(root, path) }),
    DELETE_ROOT({ root, _, _ -> deleteRoot(root) })
}