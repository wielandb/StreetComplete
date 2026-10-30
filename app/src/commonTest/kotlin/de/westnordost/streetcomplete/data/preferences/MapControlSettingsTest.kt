package de.westnordost.streetcomplete.data.preferences

import com.russhwolf.settings.MapSettings
import de.westnordost.streetcomplete.Prefs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MapControlSettingsTest {
    @Test fun importedVolumeZoomEnablesScreenButtonsOnOtherPlatforms() {
        val settings = MapSettings()
        settings.putBoolean(Prefs.VOLUME_ZOOM, true)
        settings.putBoolean(Preferences.SHOW_ZOOM_BUTTONS, false)

        adaptImportedMapControls(settings, importedVolumeButtonZoom = true, volumeButtonsSupported = false)

        assertTrue(settings.getBoolean(Preferences.SHOW_ZOOM_BUTTONS, false))
        assertTrue(settings.getBoolean(Prefs.VOLUME_ZOOM, false))
    }

    @Test fun androidRetainsItsHardwareControlWithoutEnablingScreenButtons() {
        val settings = MapSettings()
        settings.putBoolean(Prefs.VOLUME_ZOOM, true)

        adaptImportedMapControls(settings, importedVolumeButtonZoom = true, volumeButtonsSupported = true)

        assertFalse(settings.hasKey(Preferences.SHOW_ZOOM_BUTTONS))
        assertTrue(settings.getBoolean(Prefs.VOLUME_ZOOM, false))
    }

    @Test fun disabledVolumeZoomPreservesTheSelectedScreenButtonSetting() {
        for (screenButtons in listOf(false, true)) {
            val settings = MapSettings()
            settings.putBoolean(Prefs.VOLUME_ZOOM, false)
            settings.putBoolean(Preferences.SHOW_ZOOM_BUTTONS, screenButtons)

            adaptImportedMapControls(settings, importedVolumeButtonZoom = false, volumeButtonsSupported = false)

            assertEquals(screenButtons, settings.getBoolean(Preferences.SHOW_ZOOM_BUTTONS, !screenButtons))
        }
    }

    @Test fun anUnrelatedImportDoesNotReactivateScreenButtons() {
        val settings = MapSettings()
        settings.putBoolean(Prefs.VOLUME_ZOOM, true)
        settings.putBoolean(Preferences.SHOW_ZOOM_BUTTONS, false)

        adaptImportedMapControls(settings, importedVolumeButtonZoom = null, volumeButtonsSupported = false)

        assertFalse(settings.getBoolean(Preferences.SHOW_ZOOM_BUTTONS, true))
    }
}
