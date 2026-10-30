package de.westnordost.streetcomplete.data.download

import com.russhwolf.settings.ObservableSettings
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.osm.mapdata.BoundingBox
import de.westnordost.streetcomplete.data.sync.IosSyncJob

class IosDownloadController(
    private val downloader: Downloader,
    private val prefs: ObservableSettings,
) : DownloadController {
    private val job = IosSyncJob(Downloader.TAG)

    override fun download(bbox: BoundingBox, isUserInitiated: Boolean, enqueue: Boolean) {
        job.launch(replace = isUserInitiated && !enqueue, enqueue = enqueue) {
            downloader.download(bbox, isUserInitiated, prefs.getBoolean(Prefs.MANUAL_DOWNLOAD_OVERRIDE_CACHE, true))
        }
    }

    fun close() { job.close() }
}
