package helpers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

suspend fun wait(duration: Duration) = withContext(Dispatchers.Default) {
    delay(duration)
}

suspend fun waitCondition(
    duration: Duration,
    condition: () -> Boolean
) = withContext(Dispatchers.Default) {
    withTimeoutOrNull(duration) {
        while (!condition()) {
            delay(100.milliseconds)
        }
    }
}