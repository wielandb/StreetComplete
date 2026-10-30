package de.westnordost.streetcomplete.data.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskInvalid

/** Sync work survives screen changes and may finish during iOS's background grace period. */
internal class IosSyncJob(private val name: String) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + CoroutineName(name))
    private val queue = SyncJobQueue(scope)

    fun launch(replace: Boolean = false, enqueue: Boolean = false, block: suspend () -> Unit) {
        queue.launch(replace, enqueue) {
            val app = UIApplication.sharedApplication
            var taskId = UIBackgroundTaskInvalid

            fun endTask() {
                if (taskId != UIBackgroundTaskInvalid) {
                    app.endBackgroundTask(taskId)
                    taskId = UIBackgroundTaskInvalid
                }
            }

            taskId = app.beginBackgroundTaskWithName(name, expirationHandler = {
                // Cancel the whole queue: starting the next job cannot extend iOS's deadline.
                scope.coroutineContext.job.children.forEach { it.cancel() }
                endTask()
            })
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Downloader/Uploader already log the error and notify their listeners.
            } finally {
                endTask()
            }
        }
    }

    fun close() { scope.cancel() }
}
