package io.github.bryancassell.bclauncher.data

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/** A [DataStore] kept in memory, whose reads the test can make fail. */
class FakeDataStore<T>(initial: T) : DataStore<T> {
    private val value = MutableStateFlow(initial)

    /** What reading [data] throws, if anything. */
    var readError: Throwable? = null

    /** What was last saved, or the initial value. */
    val current: T get() = value.value

    override val data: Flow<T> = flow {
        readError?.let { throw it }
        emitAll(value)
    }

    override suspend fun updateData(transform: suspend (t: T) -> T): T {
        value.value = transform(value.value)
        return value.value
    }
}
