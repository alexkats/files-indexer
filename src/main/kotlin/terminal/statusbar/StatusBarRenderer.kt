package terminal.statusbar

import util.ProgressTracker
import java.nio.file.Path

interface StatusBarRenderer : AutoCloseable {

    fun startTracking(path: Path, progressTracker: ProgressTracker)

    fun getProgressTrackerOrNull(path: Path): ProgressTracker?
}