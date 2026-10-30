package de.westnordost.streetcomplete.data.osmtracks

import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import kotlinx.io.Buffer
import kotlinx.io.writeString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GpxTrackWriterTest {
    @Test fun `points with and without elevation form a readable track`() {
        val points = listOf(
            Trackpoint(LatLon(51.0, 13.0), 0L, 4f, 0f),
            Trackpoint(LatLon(51.1, 13.1), 1000L, 3f, 120f),
        )
        val gpx = formatGpxTrack("track", points)
        val parsed = parseGpxTracks(Buffer().apply { writeString(gpx) })
        assertEquals(listOf(points.map { it.position }), parsed)
        assertTrue(gpx.contains("<time>1970-01-01T00:00:00Z</time>"))
        assertTrue(gpx.contains("<ele>120.0</ele>"))
        assertFalse(gpx.contains("<time>\""))
    }

    @Test fun `track names cannot introduce XML markup`() {
        val gpx = formatGpxTrack("A & <B>", listOf(Trackpoint(LatLon(1.0, 2.0), 0L, 0f, 0f)))
        assertTrue(gpx.contains("<name>A &amp; &lt;B&gt;</name>"))
        assertEquals(1, parseGpxTracks(Buffer().apply { writeString(gpx) }).single().size)
    }
    @Test fun `horizontal accuracy is exported in meters even without elevation`() {
        val gpx = formatGpxTrack("track", listOf(Trackpoint(LatLon(1.0, 2.0), 0L, 4f, 0f)))
        assertTrue(gpx.contains("<scee:horizontalAccuracy unit=\"m\">4.0</scee:horizontalAccuracy>"))
        assertFalse(gpx.contains("<hdop>"))
        assertFalse(gpx.contains("<ele>"))
        assertEquals(listOf(listOf(LatLon(1.0, 2.0))), parseGpxTracks(Buffer().apply { writeString(gpx) }))
    }

    @Test fun `unknown and invalid accuracy does not create misleading metadata`() {
        for (accuracy in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            val gpx = formatGpxTrack("track", listOf(Trackpoint(LatLon(1.0, 2.0), 0L, accuracy, 1f)))
            assertFalse(gpx.contains("<scee:horizontalAccuracy"))
            assertFalse(gpx.contains("<hdop>"))
        }
    }
}
