package de.westnordost.streetcomplete.screens.main.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import de.westnordost.streetcomplete.ApplicationConstants
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.preferences.Preferences

internal data class MapBackground(
    val tileUrl: String?,
    val maxZoom: Int,
    val hideLabels: Boolean,
)

/** Translate the imagery URL placeholders accepted by SCEE into MapLibre tile templates. */
internal fun rasterTileUrl(template: String): String = template
    .replace("{zoom}", "{z}")
    .replace("{bbox}", "{bbox-epsg-3857}")
    .replace("{width}", "256")
    .replace("{height}", "256")
    .replace("{proj}", "EPSG:3857")

@Composable
internal fun rememberMapBackground(preferences: Preferences): MapBackground {
    fun read() = MapBackground(
        tileUrl = if (preferences.getString(Prefs.THEME_BACKGROUND, "MAP") == "MAP") null
            else rasterTileUrl(preferences.getString(Prefs.RASTER_TILE_URL, ApplicationConstants.RASTER_DEFAULT_URL)),
        maxZoom = preferences.getInt(Prefs.RASTER_TILE_MAXZOOM, ApplicationConstants.RASTER_DEFAULT_MAXZOOM),
        hideLabels = preferences.getBoolean(Prefs.NO_SATELLITE_LABEL, false),
    )
    var value by remember(preferences) { mutableStateOf(read()) }
    DisposableEffect(preferences) {
        val settings = preferences.prefs
        val listeners = listOf(
            settings.addStringListener(Prefs.THEME_BACKGROUND, "MAP") { value = read() },
            settings.addStringListener(Prefs.RASTER_TILE_URL, ApplicationConstants.RASTER_DEFAULT_URL) { value = read() },
            settings.addIntListener(Prefs.RASTER_TILE_MAXZOOM, ApplicationConstants.RASTER_DEFAULT_MAXZOOM) { value = read() },
            settings.addBooleanListener(Prefs.NO_SATELLITE_LABEL, false) { value = read() },
        )
        value = read()
        onDispose { listeners.forEach { it.deactivate() } }
    }
    return value
}
