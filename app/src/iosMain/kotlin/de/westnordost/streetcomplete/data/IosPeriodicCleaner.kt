package de.westnordost.streetcomplete.data

import de.westnordost.streetcomplete.util.ktx.pendingTaskRequests
import de.westnordost.streetcomplete.util.logs.Log
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.BackgroundTasks.BGProcessingTaskRequest
import platform.BackgroundTasks.BGTask
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.dateWithTimeIntervalSinceNow

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosPeriodicCleaner(private val clean: () -> Job) : PeriodicCleaner {
    private val scheduler = BGTaskScheduler.sharedScheduler
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + CoroutineName("Cleaner"))
    private val enqueueMutex = Mutex()

    /** Must be called once during app launch, including launches for background work. */
    fun register() {
        check(scheduler.registerForTaskWithIdentifier(TASK_ID, null) { task ->
            if (task != null) run(task)
        }) { "Background cleanup identifier is missing from BGTaskSchedulerPermittedIdentifiers" }
    }

    override fun enqueue() {
        scope.launch { scheduleNextIfMissing() }
    }

    private suspend fun scheduleNextIfMissing() {
        enqueueMutex.withLock {
            // Submitting again replaces the request and would postpone cleanup on every visit.
            if (scheduler.pendingTaskRequests().any { it.identifier == TASK_ID }) return@withLock

            val request = BGProcessingTaskRequest(TASK_ID).apply {
                earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(24 * 60 * 60.0)
            }
            memScoped {
                val error = alloc<ObjCObjectVar<NSError?>>()
                if (!scheduler.submitTaskRequest(request, error.ptr)) {
                    Log.w(TAG, "Unable to schedule background cleanup: ${error.value?.localizedDescription}")
                }
            }
        }
    }

    private fun run(task: BGTask) {
        val work = scope.launch(start = CoroutineStart.LAZY) {
            // Each BGTaskRequest launches once. Queue tomorrow before cleanup or task completion
            // can suspend the process, including when this run fails or expires.
            scheduleNextIfMissing()
            val cleaning = clean()
            try {
                cleaning.join()
                if (cleaning.isCancelled) throw CancellationException("Cleanup did not complete")
            } finally {
                // Cleaner owns its IO job. Propagate expiration and wait for its current batch
                // to stop before reporting completion to iOS.
                withContext(NonCancellable) { cleaning.cancelAndJoin() }
            }
        }
        task.expirationHandler = { work.cancel() }
        work.invokeOnCompletion { cause -> task.setTaskCompletedWithSuccess(cause == null) }
        work.start()
    }

    fun close() { scope.cancel() }

    private companion object {
        const val TAG = "Cleaner"
        const val TASK_ID = "de.westnordost.streetcomplete.expert.cleanup"
    }
}
