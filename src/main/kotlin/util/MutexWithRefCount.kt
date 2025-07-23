package util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

class MutexWithRefCount {
    private val mutex = Mutex()
    private val counter = AtomicInteger(1)

    suspend fun <T> withLock(action: suspend () -> T): T = mutex.withLock {
        action()
    }

    fun acquire() {
        counter.incrementAndGet()
    }

    fun releaseAndIsLast(): Boolean {
        return counter.decrementAndGet() == 0
    }
}