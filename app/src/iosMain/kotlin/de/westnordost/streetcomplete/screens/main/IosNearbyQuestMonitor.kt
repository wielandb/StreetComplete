@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package de.westnordost.streetcomplete.screens.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import de.westnordost.streetcomplete.ApplicationConstants
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.connection.ActiveNetworkConnection
import de.westnordost.streetcomplete.data.download.DownloadController
import de.westnordost.streetcomplete.data.download.Downloader
import de.westnordost.streetcomplete.data.download.tiles.DownloadedTilesSource
import de.westnordost.streetcomplete.data.download.tiles.enclosingTilePos
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.data.quest.VisibleQuestsSource
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.quest_monitor_found
import de.westnordost.streetcomplete.ui.util.rememberBooleanPreference
import de.westnordost.streetcomplete.ui.util.rememberFloatPreference
import de.westnordost.streetcomplete.util.buildGeoUri
import de.westnordost.streetcomplete.util.ktx.nowAsEpochMilliseconds
import de.westnordost.streetcomplete.util.logs.Log
import de.westnordost.streetcomplete.util.math.distanceTo
import de.westnordost.streetcomplete.util.math.enclosingBoundingBox
import kotlinx.cinterop.useContents
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.getString
import org.koin.compose.koinInject
import platform.CoreLocation.*
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.*
import platform.UserNotifications.*
import platform.darwin.NSObject
import kotlin.math.max

@Composable
fun IosNearbyQuestMonitor() {
    val enabled = rememberBooleanPreference(Prefs.QUEST_MONITOR, false)
    if (!enabled) return
    val prefs: Preferences = koinInject()
    val quests: VisibleQuestsSource = koinInject()
    val downloads: DownloadController = koinInject()
    val downloader: Downloader = koinInject()
    val tiles: DownloadedTilesSource = koinInject()
    val network: ActiveNetworkConnection = koinInject()
    val highAccuracy = rememberBooleanPreference(Prefs.QUEST_MONITOR_GPS, false)
    val autoDownload = rememberBooleanPreference(Prefs.QUEST_MONITOR_DOWNLOAD, false)
    val radius = rememberFloatPreference(Prefs.QUEST_MONITOR_RADIUS, 50f)
        .takeIf { it.isFinite() && it > 0f }?.toDouble() ?: 50.0
    DisposableEffect(highAccuracy, autoDownload, radius) {
        val monitor = IosNearbyQuestMonitor(
            prefs, quests, downloads, downloader, tiles, network,
            radius, highAccuracy, autoDownload,
        )
        onDispose { monitor.close() }
    }
}

/** The session starts before backgrounding; When In Use permission then covers that session. */
private class IosNearbyQuestMonitor(
    private val prefs: Preferences,
    private val quests: VisibleQuestsSource,
    private val downloads: DownloadController,
    private val downloader: Downloader,
    private val tiles: DownloadedTilesSource,
    private val network: ActiveNetworkConnection,
    private val radius: Double,
    private val highAccuracy: Boolean,
    private val autoDownload: Boolean,
) : NSObject(), CLLocationManagerDelegateProtocol {
    private val manager = CLLocationManager()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val notifications = UNUserNotificationCenter.currentNotificationCenter()
    private var scanJob: Job? = null
    private var lastCenter: LatLon? = null
    private var lastScanTime = 0L
    private var background = false
    private var running = false
    private var closed = false
    private val observers = listOf(
        observe(UIApplicationWillResignActiveNotification) { start() },
        observe(UIApplicationDidEnterBackgroundNotification) {
            background = true
            lastCenter = null
            manager.location?.let { scan(it) }
        },
        observe(UIApplicationDidBecomeActiveNotification) { stop() },
    )

    init {
        manager.delegate = this
        if (UIApplication.sharedApplication.applicationState != UIApplicationState.UIApplicationStateActive) {
            background = UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateBackground
            start()
        }
    }

    private fun observe(name: String?, action: () -> Unit) =
        NSNotificationCenter.defaultCenter.addObserverForName(name, null, NSOperationQueue.mainQueue) { action() }

    private fun start() {
        if (running || closed || manager.authorizationStatus !in listOf(
                kCLAuthorizationStatusAuthorizedWhenInUse, kCLAuthorizationStatusAuthorizedAlways,
            )) return
        manager.desiredAccuracy = if (highAccuracy) kCLLocationAccuracyBest else radius
        manager.distanceFilter = max(1.0, radius * 0.7)
        manager.pausesLocationUpdatesAutomatically = false
        manager.allowsBackgroundLocationUpdates = true
        manager.showsBackgroundLocationIndicator = true
        manager.startUpdatingLocation()
        running = true
    }

    private fun stop() {
        background = false
        running = false
        scanJob?.cancel()
        manager.stopUpdatingLocation()
        manager.allowsBackgroundLocationUpdates = false
        lastCenter = null
        lastScanTime = 0L
        clearNotification()
    }

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        (didUpdateLocations.lastOrNull() as? CLLocation)?.let { scan(it) }
    }

    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        if (manager.authorizationStatus == kCLAuthorizationStatusDenied ||
            manager.authorizationStatus == kCLAuthorizationStatusRestricted) stop()
    }

    private fun scan(location: CLLocation) {
        if (!background || !running || closed || location.horizontalAccuracy < 0 ||
            location.horizontalAccuracy > radius) return
        val now = nowAsEpochMilliseconds()
        if (now - (location.timestamp.timeIntervalSince1970 * 1000).toLong() > 120_000) return
        val center = location.coordinate.useContents { LatLon(latitude, longitude) }
        if (lastCenter?.let { it.distanceTo(center) < radius * 0.7 } == true) return
        if (now - lastScanTime < prefs.getInt(Prefs.LOCATION_INTERVAL, 0).coerceAtLeast(0) * 1000L) return
        lastCenter = center
        lastScanTime = now
        scanJob?.cancel()
        scanJob = scope.launch {
            try {
                val nearby = withContext(Dispatchers.IO) {
                    quests.getAll(center.enclosingBoundingBox(radius)).filter { it.type.dotColor == null }
                }
                if (!background || closed) return@launch
                if (nearby.isEmpty()) {
                    clearNotification()
                    if (!autoDownload || downloader.isDownloadInProgress ||
                        network.capabilities.first()?.hasInternet != true) return@launch
                    val retention = prefs.getInt(Prefs.DATA_RETAIN_TIME, ApplicationConstants.DELETE_OLD_DATA_AFTER_DAYS)
                        .coerceAtLeast(0) * 24L * 60 * 60 * 1000
                    val tile = center.enclosingTilePos(ApplicationConstants.DOWNLOAD_TILE_ZOOM).toTilesRect()
                    val cached = withContext(Dispatchers.IO) { tiles.contains(tile, now - retention) }
                    if (!cached) downloads.download(center.enclosingBoundingBox(max(150.0, radius)))
                    return@launch
                }
                val closest = nearby.minBy { center.distanceTo(it.position) }
                val content = UNMutableNotificationContent().apply {
                    setTitle(getString(Res.string.quest_monitor_found, nearby.size))
                    setBody(getString(closest.type.title))
                    setSound(UNNotificationSound.defaultSound)
                    setUserInfo(mapOf(QUEST_NOTIFICATION_URI to buildGeoUri(closest.position.latitude, closest.position.longitude)))
                }
                if (!background || closed) return@launch
                notifications.addNotificationRequest(
                    UNNotificationRequest.requestWithIdentifier(FOUND_NOTIFICATION, content, null),
                ) { error ->
                    if (error != null) Log.w(TAG, error.localizedDescription)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Unable to check nearby quests", e)
            }
        }
    }

    private fun clearNotification() {
        notifications.removeDeliveredNotificationsWithIdentifiers(listOf(FOUND_NOTIFICATION))
        notifications.removePendingNotificationRequestsWithIdentifiers(listOf(FOUND_NOTIFICATION))
    }

    fun close() {
        if (closed) return
        closed = true
        stop()
        observers.forEach { NSNotificationCenter.defaultCenter.removeObserver(it) }
        manager.delegate = null
        scope.cancel()
    }

}

private const val TAG = "NearbyQuestMonitor"
private const val FOUND_NOTIFICATION = "scee.nearby-quest"
