package de.westnordost.streetcomplete.data.preferences

import com.russhwolf.settings.ObservableSettings
import de.westnordost.streetcomplete.BuildConfig

internal val supportsVolumeButtonMapZoom: Boolean get() = BuildConfig.PLATFORM == "android"

/** Preserve the zoom control's purpose when importing Android settings on another platform. */
internal fun adaptImportedMapControls(
    settings: ObservableSettings,
    importedVolumeButtonZoom: Boolean?,
    volumeButtonsSupported: Boolean = supportsVolumeButtonMapZoom,
) {
    if (!volumeButtonsSupported && importedVolumeButtonZoom == true) {
        settings.putBoolean(Preferences.SHOW_ZOOM_BUTTONS, true)
    }
    // Keep the hardware preference for exporting these settings back to Android.
}
