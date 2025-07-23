package index.file

import java.nio.file.Path

data class FileChangeEvent(
    val root: Path,
    val file: Path,
    val eventType: EventType,
    val onFinish: () -> Unit = {},
)

enum class EventType(val opFunc: suspend (FileIndexer).(Path, Path) -> Unit) {
    CREATE_ROOT({ root, _ -> createRoot(root) }),
    UPDATE({ root, path -> createOrUpdateFile(root, path) }),
    DELETE({ root, path -> deleteFile(root, path) }),
    DELETE_ROOT({ root, _ -> deleteRoot(root) })
}