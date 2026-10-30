package de.westnordost.streetcomplete.screens.main

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.view.KeyEvent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import io.github.vinceglb.filekit.PlatformFile
import de.westnordost.streetcomplete.ui.common.IncomingFileImportDialog
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import de.westnordost.streetcomplete.App
import de.westnordost.streetcomplete.AppLocaleUpdater
import de.westnordost.streetcomplete.AppViewModel
import de.westnordost.streetcomplete.MapKeyAction
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.util.ktx.loadFileKit
import de.westnordost.streetcomplete.util.logs.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.scope.AndroidScopeComponent
import org.koin.androidx.compose.scope.KoinActivityScope
import org.koin.androidx.scope.activityScope
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.scope.Scope

/** Android entry point for the shared application. */
class MainActivity : ComponentActivity(), AndroidScopeComponent {
    override val scope: Scope by activityScope()
    private val viewModel: AppViewModel by viewModel()
    private val appLocaleUpdater: AppLocaleUpdater by inject()

    private val prefs: Preferences by inject()
    private var questMonitorJob: Job? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        appLocaleUpdater.update()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate")
        loadFileKit()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            KoinActivityScope {
                val uri by viewModel.pendingUri.collectAsState()
                val fileImportUri by viewModel.pendingFileImportUri.collectAsState()
                App(
                    uri = uri,
                    onConsumedUri = viewModel::consumeUri,
                    viewModel = viewModel,
                ) {
                    fileImportUri?.let { fileUri ->
                        key(fileUri) {
                            IncomingFileImportDialog(
                                file = remember(fileUri) { PlatformFile(Uri.parse(fileUri)) },
                                onDismissRequest = viewModel::consumeFileImport,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        // Android can reset the default locales without another Application configuration callback.
        appLocaleUpdater.update()
        super.onResume()
        Log.i(TAG, "onResume")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW) {
            intent.data?.toString()?.let { uri ->
                if (intent.data?.scheme in listOf("content", "file")) viewModel.openFileImport(uri)
                else viewModel.openUri(uri)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!viewModel.isMainScreenActive) return super.dispatchKeyEvent(event)
        val action = when (event.keyCode) {
            KeyEvent.KEYCODE_MENU -> MapKeyAction.OPEN_MENU
            KeyEvent.KEYCODE_VOLUME_UP -> if (prefs.getBoolean(Prefs.VOLUME_ZOOM, false)) MapKeyAction.ZOOM_IN else null
            KeyEvent.KEYCODE_VOLUME_DOWN -> if (prefs.getBoolean(Prefs.VOLUME_ZOOM, false)) MapKeyAction.ZOOM_OUT else null
            else -> null
        } ?: return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_UP) viewModel.onMapKeyAction(action)
        return true
    }

    override fun onStart() {
        super.onStart()
        Log.i(TAG, "onStart")
        stopQuestMonitor()
    }

    override fun onPause() {
        super.onPause()
        Log.i(TAG, "onPause")
    }

    override fun onStop() {
        super.onStop()
        Log.i(TAG, "onStop")
        startQuestMonitor()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Log.i(TAG, "onSaveInstanceState")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy")
        questMonitorJob?.cancel()
        try { applicationContext.unbindService(questMonitorConnection) }
        catch (_: IllegalArgumentException) { }
    }

    private fun startQuestMonitor() {
        if (prefs.getBoolean(Prefs.QUEST_MONITOR, false) && !NearbyQuestMonitor.running) {
            questMonitorJob?.cancel()
            questMonitorJob = lifecycleScope.launch {
                // Avoid starting a background monitor during an Activity recreation.
                delay(1000)
                applicationContext.bindService(
                    Intent(this@MainActivity, NearbyQuestMonitor::class.java),
                    questMonitorConnection, BIND_AUTO_CREATE
                )
            }
        }
    }

    private fun stopQuestMonitor() {
        if (prefs.getBoolean(Prefs.QUEST_MONITOR, false) || NearbyQuestMonitor.running) {
            try { applicationContext.unbindService(questMonitorConnection) }
            catch (_: IllegalArgumentException) { }
            questMonitorJob?.cancel()
            questMonitorJob = lifecycleScope.launch {
                // Android can finish an earlier binding after the Activity has returned.
                delay(5000)
                try { applicationContext.unbindService(questMonitorConnection) }
                catch (_: IllegalArgumentException) { }
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        // The application owns the connection across Activity instances.
        private val questMonitorConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) { }
            override fun onServiceDisconnected(name: ComponentName?) { }
        }
    }
}
