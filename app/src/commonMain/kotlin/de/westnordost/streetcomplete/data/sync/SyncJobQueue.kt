package de.westnordost.streetcomplete.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serial work with explicit append, keep-existing and replace-the-whole-queue policies. */
internal class SyncJobQueue(private val scope: CoroutineScope) {
    private val mutex = Mutex()
    private val jobs = mutableListOf<Job>()

    /** The returned job completes when this request has been accepted or skipped. */
    fun launch(replace: Boolean = false, enqueue: Boolean = false, block: suspend () -> Unit): Job =
        scope.launch {
            mutex.withLock {
                jobs.removeAll { it.isCompleted }
                if (replace) {
                    jobs.forEach { it.cancel() }
                    jobs.joinAll()
                    jobs.clear()
                } else if (!enqueue && jobs.isNotEmpty()) {
                    return@withLock
                }
                val previous = jobs.lastOrNull()
                val next = scope.launch(start = CoroutineStart.LAZY) {
                    previous?.join()
                    block()
                }
                jobs.add(next)
                next.invokeOnCompletion {
                    scope.launch { mutex.withLock { jobs.remove(next) } }
                }
                next.start()
            }
        }
}
