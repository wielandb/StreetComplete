package de.westnordost.streetcomplete.data.osmtracks

import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.util.ktx.attribute
import kotlinx.io.Source
import nl.adaptivity.xmlutil.EventType.END_ELEMENT
import nl.adaptivity.xmlutil.EventType.START_ELEMENT
import nl.adaptivity.xmlutil.core.kxio.newReader
import nl.adaptivity.xmlutil.xmlStreaming

/** Reads GPX tracks on all platforms, preserving gaps between track segments. */
fun parseGpxTracks(source: Source): List<List<LatLon>> {
    val reader = xmlStreaming.newReader(source)
    val path = mutableListOf<String>()
    val segments = mutableListOf<MutableList<LatLon>>()
    var segment: MutableList<LatLon>? = null
    var hasRoot = false
    try {
        reader.forEach { event ->
            when (event) {
                START_ELEMENT -> {
                    val name = reader.localName
                    if (path.isEmpty()) {
                        require(name == "gpx") { "Not a GPX document" }
                        hasRoot = true
                    }
                    path.add(name)
                    if (path.size == 3 && path[1] == "trk" && name == "trkseg") {
                        segment = mutableListOf<LatLon>().also { segments.add(it) }
                    }
                    if (path.size == 4 && path[1] == "trk" && path[2] == "trkseg" && name == "trkpt") {
                        val latitude = reader.attribute("lat").toDouble()
                        val longitude = reader.attribute("lon").toDouble()
                        require(latitude in -90.0..90.0 && longitude in -180.0..180.0) {
                            "Invalid GPX coordinates"
                        }
                        segment?.add(LatLon(latitude, longitude))
                    }
                }
                END_ELEMENT -> {
                    if (path.size == 3 && path[1] == "trk" && reader.localName == "trkseg") segment = null
                    path.removeAt(path.lastIndex)
                }
                else -> {}
            }
        }
        require(hasRoot) { "Empty GPX document" }
        return segments.filter { it.isNotEmpty() }
    } finally {
        reader.close()
    }
}
