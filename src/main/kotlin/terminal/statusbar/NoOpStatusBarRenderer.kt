package terminal.statusbar

import util.ProgressTracker
import java.nio.file.Path

class NoOpStatusBarRenderer : StatusBarRenderer {

    override fun startTracking(path: Path, progressTracker: ProgressTracker) = Unit

    override fun getProgressTrackerOrNull(path: Path): ProgressTracker? = null

    override fun close() = Unit
}