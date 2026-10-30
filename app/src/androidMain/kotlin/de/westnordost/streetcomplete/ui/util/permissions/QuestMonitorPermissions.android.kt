package de.westnordost.streetcomplete.ui.util.permissions

import android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.action_settings
import de.westnordost.streetcomplete.resources.cancel
import de.westnordost.streetcomplete.resources.pref_quest_monitor_title
import de.westnordost.streetcomplete.resources.quest_monitor_background_access
import de.westnordost.streetcomplete.ui.common.dialogs.AlertDialog
import org.jetbrains.compose.resources.stringResource

actual val hasSeparateLocationProviders = true

@Composable
actual fun rememberQuestMonitorPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    var pending by remember { mutableStateOf(false) }
    var showBackgroundExplanation by remember { mutableStateOf(false) }
    fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    val finish: (Boolean) -> Unit = { granted ->
        pending = false
        currentOnResult(granted)
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), finish)
    val requestNotifications: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 33 && !hasPermission(POST_NOTIFICATIONS)) {
            notifications.launch(POST_NOTIFICATIONS)
        } else {
            finish(true)
        }
    }
    val background = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) requestNotifications() else finish(false)
    }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (hasPermission(ACCESS_FINE_LOCATION) && hasPermission(ACCESS_BACKGROUND_LOCATION)) {
            requestNotifications()
        } else {
            finish(false)
        }
    }
    val requestBackground: () -> Unit = {
        when {
            Build.VERSION.SDK_INT < 29 || hasPermission(ACCESS_BACKGROUND_LOCATION) -> requestNotifications()
            Build.VERSION.SDK_INT == 29 -> background.launch(ACCESS_BACKGROUND_LOCATION)
            else -> showBackgroundExplanation = true
        }
    }
    val foreground = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasPermission(ACCESS_FINE_LOCATION)) requestBackground() else finish(false)
    }
    if (showBackgroundExplanation && Build.VERSION.SDK_INT >= 30) {
        AlertDialog(
            onDismissRequest = { showBackgroundExplanation = false; finish(false) },
            title = { Text(stringResource(Res.string.pref_quest_monitor_title)) },
            text = {
                Text(stringResource(
                    Res.string.quest_monitor_background_access,
                    context.packageManager.backgroundPermissionOptionLabel.toString(),
                ))
            },
            buttonRow = {
                TextButton({ showBackgroundExplanation = false; finish(false) }) {
                    Text(stringResource(Res.string.cancel))
                }
                TextButton({
                    showBackgroundExplanation = false
                    settings.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }) {
                    Text(stringResource(Res.string.action_settings))
                }
            },
        )
    }
    return {
        if (!pending) {
            pending = true
            if (hasPermission(ACCESS_FINE_LOCATION)) requestBackground()
            else foreground.launch(arrayOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION))
        }
    }
}
