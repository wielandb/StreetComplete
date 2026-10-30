package de.westnordost.streetcomplete.screens.main.map.layers

import androidx.compose.ui.graphics.Color
import de.westnordost.streetcomplete.data.osm.geometry.ElementPointGeometry
import de.westnordost.streetcomplete.data.osm.geometry.ElementPolylinesGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.osm.mapdata.Node
import de.westnordost.streetcomplete.data.osm.mapdata.Way
import de.westnordost.streetcomplete.data.overlays.OverlayStyle
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.preset_maki_circle
import de.westnordost.streetcomplete.screens.main.map.iconNamePrefix
import de.westnordost.streetcomplete.ui.ktx.id
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StyledElementTest {
    private val position = LatLon(51.0, 12.0)
    private val geometry = ElementPolylinesGeometry(listOf(listOf(position, LatLon(51.001, 12.001))), position)

    @Test fun customPointColorSelectsItsOwnIconImage() {
        val icon = Res.drawable.preset_maki_circle
        val color = Color.Red
        val features = StyledElement(
            Node(42, position), ElementPointGeometry(position), OverlayStyle.Point(icon, color = color),
        ).toGeoJsonFeatures()
        val properties = assertNotNull(features.single().properties)
        assertEquals(iconNamePrefix(color, color.darkened()) + icon.id, properties.getValue("icon-image").jsonPrimitive.content)
        assertEquals(Node(42, position).key, properties.toElementKey())
    }

    @Test fun restrictedRoadKeepsAccessDashesAndClickableElementKey() {
        for (access in listOf("no", "private", "destination", "customers", "delivery", "agricultural", "forestry", "emergency")) {
            val way = Way(7, listOf(1, 2), mapOf("highway" to "service", "access" to access))
            val features = StyledElement(way, geometry, OverlayStyle.Polyline(OverlayStyle.Stroke(Color.Blue)))
                .toGeoJsonFeatures()
            assertEquals(2, features.size, access)
            val dash = assertNotNull(features.last().properties)
            assertTrue(dash.getValue("dashed").jsonPrimitive.booleanOrNull == true)
            assertEquals(1.5f, dash.getValue("width").jsonPrimitive.float)
            assertEquals(way.key, dash.toElementKey())
        }
    }

    @Test fun publicRoadDoesNotGetAccessDashes() {
        val way = Way(7, listOf(1, 2), mapOf("highway" to "service", "access" to "yes"))
        assertEquals(1, StyledElement(way, geometry, OverlayStyle.Polyline(OverlayStyle.Stroke(Color.Blue)))
            .toGeoJsonFeatures().size)
    }

    @Test fun hiddenOrAlreadyDashedStrokeDoesNotGetAnotherAccessLine() {
        val way = Way(7, listOf(1, 2), mapOf("highway" to "service", "access" to "private"))
        for (stroke in listOf(null, OverlayStyle.Stroke(Color.Transparent), OverlayStyle.Stroke(Color.Blue, dashed = true))) {
            assertEquals(1, StyledElement(way, geometry, OverlayStyle.Polyline(stroke)).toGeoJsonFeatures().size)
        }
    }
}
