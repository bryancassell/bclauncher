package io.github.bryancassell.bclauncher.data

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A [DataStore] kept in memory, whose reads the test can make fail. Like DataStore, it runs one update at a time, and
 * an update fails as a read does, since it reads the current value first.
 */
class FakeDataStore<T>(initial: T) : DataStore<T> {
    private val value = MutableStateFlow(initial)
    private val updating = Mutex()

    /** What reading [data] or updating throws, if anything. */
    var readError: Throwable? = null

    /** What was last saved, or the initial value. */
    val current: T get() = value.value

    override val data: Flow<T> = flow {
        readError?.let { throw it }
        emitAll(value)
    }

    override suspend fun updateData(transform: suspend (t: T) -> T): T = updating.withLock {
        readError?.let { throw it }
        value.value = transform(value.value)
        value.value
    }
}
