package de.westnordost.streetcomplete.screens.main.map.layers

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import de.westnordost.streetcomplete.screens.main.map.iconNamePrefix
import de.westnordost.streetcomplete.ui.theme.GeometryMarker
import de.westnordost.streetcomplete.data.osm.geometry.ElementPointGeometry
import de.westnordost.streetcomplete.data.osm.geometry.ElementPolygonsGeometry
import de.westnordost.streetcomplete.data.osm.geometry.ElementPolylinesGeometry
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.preset_maki_circle
import de.westnordost.streetcomplete.screens.main.map.toGeometry
import de.westnordost.streetcomplete.ui.common.quest.Marker
import de.westnordost.streetcomplete.ui.ktx.id
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.Geometry
import kotlin.collections.set

fun Marker.toGeoJsonFeature(haloColor: Color): List<Feature<Geometry, JsonObject>> {
    val markerColor = Color(color ?: Color.GeometryMarker.toArgb())
    val colorProperty = "color" to JsonPrimitive(markerColor.toRgbaString())
    val features = ArrayList<Feature<Geometry, JsonObject>>(3)
    // point marker or any marker with title or icon
    if (icon != null || title != null || geometry is ElementPointGeometry) {
        val p = hashMapOf<String, JsonElement>(colorProperty)
        val iconId = (icon ?: Res.drawable.preset_maki_circle).id
        p["icon"] = JsonPrimitive(iconId)
        p["icon-image"] = JsonPrimitive(iconNamePrefix(markerColor, haloColor) + iconId)
        p["rotation"] = JsonPrimitive(rotation ?: 0.0)
        if (direction != null && geometry is ElementPointGeometry) {
            p["direction"] = JsonPrimitive(direction)
            val directionColor = if (color == null) markerColor.copy(alpha = 0.5f) else markerColor
            p["direction-color"] = JsonPrimitive(directionColor.toRgbaString())
        }
        if (title != null) {
            p["label"] = JsonPrimitive(title)
        }
        features.add(Feature(geometry.center.toGeometry(), JsonObject(p)))
    }

    // polygon / polylines marker(s)
    if (geometry is ElementPolygonsGeometry || geometry is ElementPolylinesGeometry) {
        features.add(Feature(geometry.toGeometry(), JsonObject(mapOf(colorProperty))))
    }
    return features
}
