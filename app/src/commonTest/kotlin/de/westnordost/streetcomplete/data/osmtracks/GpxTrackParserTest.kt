package de.westnordost.streetcomplete.data.osmtracks

import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import kotlinx.io.Buffer
import kotlinx.io.writeString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class GpxTrackParserTest {
    @Test fun `preserves segments and tracks in a namespaced GPX file`() {
        val tracks = parse("""
            <gpx xmlns="http://www.topografix.com/GPX/1/1" version="1.1">
              <wpt lat="50" lon="8"/>
              <trk><trkseg>
                <trkpt lon="13" lat="51"><ele>100</ele></trkpt>
                <trkpt lat="51.1" lon="13.1"/>
              </trkseg><trkseg><trkpt lat="52" lon="14"/></trkseg></trk>
              <trk><trkseg><trkpt lat="53" lon="15"/></trkseg></trk>
            </gpx>
        """)
        assertEquals(listOf(
            listOf(LatLon(51.0, 13.0), LatLon(51.1, 13.1)),
            listOf(LatLon(52.0, 14.0)),
            listOf(LatLon(53.0, 15.0)),
        ), tracks)
    }

    @Test fun `ignores track-like extension data`() {
        val tracks = parse("""
            <gpx><extensions><trk><trkseg><trkpt lat="1" lon="2"/></trkseg></trk></extensions></gpx>
        """)
        assertEquals(emptyList(), tracks)
    }

    @Test fun `rejects malformed and non-GPX documents`() {
        assertFails { parse("<gpx><trk>") }
        assertFails { parse("<html/>") }
    }

    @Test fun `rejects missing and invalid coordinates`() {
        for (attributes in listOf("lat=\"1\"", "lat=\"NaN\" lon=\"2\"", "lat=\"91\" lon=\"2\"", "lat=\"1\" lon=\"181\"")) {
            assertFails { parse("<gpx><trk><trkseg><trkpt $attributes/></trkseg></trk></gpx>") }
        }
    }

    private fun parse(xml: String) = parseGpxTracks(Buffer().apply { writeString(xml) })
}
