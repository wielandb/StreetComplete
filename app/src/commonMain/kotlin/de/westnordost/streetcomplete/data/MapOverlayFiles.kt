package de.westnordost.streetcomplete.data

import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.osmtracks.parseGpxTracks
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.readString
import io.github.vinceglb.filekit.writeString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.io.Buffer
import kotlinx.io.writeString
import org.maplibre.spatialk.geojson.GeoJsonObject

/** Shared files displayed over the map. Getters wait until FileKit has been initialized. */
object MapOverlayFiles {
    val gpxFile get() = PlatformFile(FileKit.filesDir, "display_track.gpx")
    val geometryFile get() = PlatformFile(FileKit.filesDir, "customGeometry.geojson")

    private val changes = MutableStateFlow(0L)
    val revision = changes.asStateFlow()

    suspend fun importGpx(file: PlatformFile) {
        val text = file.readString()
        parseTracks(text) // Validate before replacing a previously working file.
        gpxFile.writeString(text)
        changes.update { it + 1 }
    }

    suspend fun importGeometry(file: PlatformFile) {
        val text = file.readString()
        GeoJsonObject.fromJson(text)
        geometryFile.writeString(text)
        changes.update { it + 1 }
    }

    suspend fun readGpxTracks(): List<List<LatLon>> = parseTracks(gpxFile.readString())

    suspend fun readGeometry(): GeoJsonObject = GeoJsonObject.fromJson(geometryFile.readString())

    private fun parseTracks(text: String): List<List<LatLon>> {
        val tracks = parseGpxTracks(Buffer().apply { writeString(text) })
        require(tracks.sumOf { it.size } >= 2) { "GPX contains fewer than two track points" }
        return tracks
    }
}
