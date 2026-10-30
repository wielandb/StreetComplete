package de.westnordost.streetcomplete.ui.util.permissions

import androidx.compose.runtime.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.maplibre.compose.location.AppleLocationPermissionRequester
import org.maplibre.compose.location.LocationAccuracyAuthorization
import org.maplibre.compose.location.LocationPermission
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

actual val hasSeparateLocationProviders = false

@Composable
actual fun rememberQuestMonitorPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit {
    val requester = remember { AppleLocationPermissionRequester() }
    val currentOnResult by rememberUpdatedState(onResult)
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf(false) }
    DisposableEffect(requester) { onDispose { requester.close() } }
    return {
        if (!pending) scope.launch {
            pending = true
            try {
                var permission = requester.status.value
                if (permission is LocationPermission.NotGranted && permission.canRequest == true) {
                    requester.requestForegroundPermission()
                    permission = requester.status.first {
                        it is LocationPermission.Granted ||
                            (it is LocationPermission.NotGranted && it.canRequest == false)
                    }
                }
                if (permission !is LocationPermission.Granted ||
                    permission.accuracy != LocationAccuracyAuthorization.Precise) {
                    currentOnResult(false)
                    return@launch
                }
                // A foreground-started Core Location session with background updates enabled
                // continues with When In Use authorization until the user terminates the app.
                val notificationsAllowed = suspendCancellableCoroutine { continuation ->
                    UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
                        UNAuthorizationOptionAlert or UNAuthorizationOptionSound,
                    ) { granted, _ ->
                        if (continuation.isActive) continuation.resume(granted)
                    }
                }
                currentOnResult(notificationsAllowed)
            } finally {
                pending = false
            }
        }
    }
}
