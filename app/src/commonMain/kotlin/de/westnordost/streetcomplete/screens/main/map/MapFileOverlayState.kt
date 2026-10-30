package de.westnordost.streetcomplete.screens.main.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.MapOverlayFiles
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.ui.util.rememberBooleanPreference
import de.westnordost.streetcomplete.util.logs.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.maplibre.spatialk.geojson.GeoJsonObject

internal data class MapFileOverlayState(
    val gpxTracks: List<List<LatLon>> = emptyList(),
    val geometry: GeoJsonObject? = null,
)

internal enum class MapFileLoadError { GPX, GEOMETRY }

@Composable
internal fun rememberMapFileOverlayState(
    preferences: Preferences,
    onError: (MapFileLoadError) -> Unit,
): MapFileOverlayState {
    val revision by MapOverlayFiles.revision.collectAsState()
    val showGpx = rememberBooleanPreference(Prefs.SHOW_GPX_TRACK, false, preferences)
    val showGeometry = rememberBooleanPreference(Prefs.SHOW_CUSTOM_GEOMETRY, false, preferences)
    val currentOnError by rememberUpdatedState(onError)
    val state by produceState(MapFileOverlayState(), revision, showGpx, showGeometry) {
        val errors = mutableListOf<MapFileLoadError>()
        value = withContext(Dispatchers.IO) {
            val tracks = if (showGpx) try {
                MapOverlayFiles.readGpxTracks()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("MapOverlayFiles", "Could not load GPX track", e)
                errors.add(MapFileLoadError.GPX)
                emptyList()
            } else emptyList()
            val geometry = if (showGeometry) try {
                MapOverlayFiles.readGeometry()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("MapOverlayFiles", "Could not load GeoJSON", e)
                errors.add(MapFileLoadError.GEOMETRY)
                null
            } else null
            MapFileOverlayState(tracks, geometry)
        }
        errors.forEach(currentOnError)
    }
    // Turning a layer off should hide it immediately, even while another file is being read.
    return state.copy(
        gpxTracks = if (showGpx) state.gpxTracks else emptyList(),
        geometry = if (showGeometry) state.geometry else null,
    )
}
