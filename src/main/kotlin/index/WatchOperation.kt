package index

import util.ProgressTracker
import java.nio.file.Path

sealed class WatchOperation<T> {
    abstract val data: T

    data class Start(override val data: WatchStartOperationData) : WatchOperation<WatchStartOperationData>()
    data class Stop(override val data: WatchStopOperationData) : WatchOperation<WatchStopOperationData>()
}

data class WatchStartOperationData(
    val root: Path,
    val excludePatterns: Set<String>,
    val extensions: Set<String>,
    val progressTracker: ProgressTracker
)

data class WatchStopOperationData(val root: Path)