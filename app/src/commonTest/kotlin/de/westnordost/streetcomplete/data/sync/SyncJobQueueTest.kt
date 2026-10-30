package de.westnordost.streetcomplete.data.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncJobQueueTest {
    @Test fun queuedWorkRunsInOrder(): Unit = runBlocking {
        withTimeout(5_000) {
            val scope = CoroutineScope(coroutineContext + SupervisorJob())
            try {
                val queue = SyncJobQueue(scope)
                val events = mutableListOf<Int>()
                val release = CompletableDeferred<Unit>()
                val started = CompletableDeferred<Unit>()
                val done = CompletableDeferred<Unit>()
                queue.launch { events.add(1); started.complete(Unit); release.await(); events.add(2) }.join()
                started.await()
                queue.launch(enqueue = true) { events.add(3) }.join()
                queue.launch(enqueue = true) { events.add(4); done.complete(Unit) }.join()
                assertEquals(listOf(1), events)
                release.complete(Unit)
                done.await()
                assertEquals(listOf(1, 2, 3, 4), events)
            } finally { scope.cancel() }
        }
    }

    @Test fun replacementCancelsRunningAndQueuedWork(): Unit = runBlocking {
        withTimeout(5_000) {
            val scope = CoroutineScope(coroutineContext + SupervisorJob())
            try {
                val queue = SyncJobQueue(scope)
                val events = mutableListOf<String>()
                val started = CompletableDeferred<Unit>()
                val never = CompletableDeferred<Unit>()
                val done = CompletableDeferred<Unit>()
                queue.launch {
                    events.add("started")
                    started.complete(Unit)
                    try { never.await() } finally { events.add("cancelled") }
                }.join()
                started.await()
                queue.launch(enqueue = true) { events.add("obsolete") }.join()
                queue.launch(replace = true) { events.add("replacement"); done.complete(Unit) }.join()
                done.await()
                assertEquals(listOf("started", "cancelled", "replacement"), events)
            } finally { scope.cancel() }
        }
    }

    @Test fun automaticRequestsKeepExistingWork(): Unit = runBlocking {
        withTimeout(5_000) {
            val scope = CoroutineScope(coroutineContext + SupervisorJob())
            try {
                val queue = SyncJobQueue(scope)
                val events = mutableListOf<Int>()
                val started = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val done = CompletableDeferred<Unit>()
                queue.launch { events.add(1); started.complete(Unit); release.await(); done.complete(Unit) }.join()
                started.await()
                queue.launch { events.add(2) }.join()
                release.complete(Unit)
                done.await()
                assertEquals(listOf(1), events)
            } finally { scope.cancel() }
        }
    }

    @Test fun queuedWorkContinuesAfterFailure(): Unit = runBlocking {
        withTimeout(5_000) {
            var failures = 0
            val scope = CoroutineScope(coroutineContext + SupervisorJob() + CoroutineExceptionHandler { _, _ -> failures++ })
            try {
                val queue = SyncJobQueue(scope)
                val started = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val done = CompletableDeferred<Unit>()
                queue.launch { started.complete(Unit); release.await(); error("download failed") }.join()
                started.await()
                queue.launch(enqueue = true) { done.complete(Unit) }.join()
                release.complete(Unit)
                done.await()
                assertEquals(1, failures)
            } finally { scope.cancel() }
        }
    }
}
