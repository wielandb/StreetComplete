package de.westnordost.streetcomplete

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import de.westnordost.streetcomplete.data.preferences.Preferences
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

enum class MapKeyAction { OPEN_MENU, ZOOM_IN, ZOOM_OUT }

/** Application preferences and incoming requests, independent of the visible destination. */
class AppViewModel(prefs: Preferences, private val savedState: SavedStateHandle) : ViewModel() {
    val locale = MutableStateFlow(prefs.locale)
    val keepScreenOn = MutableStateFlow(prefs.keepScreenOn)

    val pendingUri = savedState.getStateFlow<String?>("uri", null)
    val pendingFileImportUri = savedState.getStateFlow<String?>("textFileUri", null)

    var isMainScreenActive = false
    private val mapKeyActionChannel = Channel<MapKeyAction>(Channel.BUFFERED)
    val mapKeyActions = mapKeyActionChannel.receiveAsFlow()

    private val listeners = listOf(
        prefs.onLocaleChanged { locale.value = it },
        prefs.onKeepScreenOnChanged { keepScreenOn.value = it },
    )

    fun openUri(uri: String) { savedState["uri"] = uri }
    fun consumeUri() { savedState["uri"] = null }
    fun openFileImport(uri: String) { savedState["textFileUri"] = uri }
    fun consumeFileImport() { savedState["textFileUri"] = null }
    fun onMapKeyAction(action: MapKeyAction) {
        if (isMainScreenActive) mapKeyActionChannel.trySend(action)
    }

    override fun onCleared() {
        listeners.forEach { it.deactivate() }
        mapKeyActionChannel.close()
    }
}
