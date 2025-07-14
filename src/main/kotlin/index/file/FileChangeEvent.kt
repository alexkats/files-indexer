package index.file

import java.nio.file.Path
import kotlin.time.Duration

data class FileChangeEvent(
    val path: Path,
    val eventType: EventType,
    val timestamp: Duration,
)

enum class EventType(val opFunc: suspend (FileIndexer).(Path) -> Unit) {
    UPDATE({ createOrUpdateFile(it) }),
    DELETE({ deleteFile(it) }),
}