package de.westnordost.streetcomplete.data.download

import com.russhwolf.settings.ObservableSettings
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.osm.mapdata.BoundingBox
import de.westnordost.streetcomplete.data.sync.SyncJobQueue
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class DesktopDownloadController(
    private val downloader: Downloader,
    private val prefs: ObservableSettings,
) : DownloadController {
    // Downloader already logs errors and notifies its listeners.
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, _ -> }
    )
    private val queue = SyncJobQueue(scope)

    override fun download(bbox: BoundingBox, isUserInitiated: Boolean, enqueue: Boolean) {
        queue.launch(replace = isUserInitiated && !enqueue, enqueue = enqueue) {
            downloader.download(bbox, isUserInitiated, prefs.getBoolean(Prefs.MANUAL_DOWNLOAD_OVERRIDE_CACHE, true))
        }
    }

    fun close() { scope.cancel() }
}
