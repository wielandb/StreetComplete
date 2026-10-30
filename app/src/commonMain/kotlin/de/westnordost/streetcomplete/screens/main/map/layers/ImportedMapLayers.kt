package de.westnordost.streetcomplete.screens.main.map.layers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import de.westnordost.streetcomplete.screens.main.map.MapFileOverlayState
import de.westnordost.streetcomplete.screens.main.map.isArea
import de.westnordost.streetcomplete.screens.main.map.isPoint
import de.westnordost.streetcomplete.screens.main.map.toMultiLineGeometry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToString
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.not
import org.maplibre.compose.expressions.dsl.textOffset
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.spatialk.geojson.Geometry
import org.maplibre.spatialk.geojson.GeometryCollection

@Composable @MaplibreComposable
internal fun ImportedMapLayers(files: MapFileOverlayState) {
    val tracks by produceState<Geometry>(GeometryCollection(emptyList()), files.gpxTracks) {
        value = withContext(Dispatchers.Default) { files.gpxTracks.toMultiLineGeometry() }
    }
    val trackSource = rememberGeoJsonSource(GeoJsonData.Features(tracks))
    LineLayer(
        id = "imported-gpx-track",
        source = trackSource,
        color = const(Color(0xff53fe70)),
        opacity = const(0.25f),
        width = const(5.dp),
        cap = const(LineCap.Round),
    )

    val geometry = files.geometry ?: return
    val source = rememberGeoJsonSource(GeoJsonData.Features(geometry))
    val color = const(Color(0xff9e319e))
    FillLayer(
        id = "custom-geo-fill",
        source = source,
        filter = feature.isArea(),
        color = color,
        opacity = const(0.3f),
    )
    LineLayer(
        id = "custom-geo-lines",
        source = source,
        filter = !feature.isPoint(),
        color = color,
        opacity = const(0.5f),
        width = const(8.dp),
        cap = const(LineCap.Round),
    )
    CircleLayer(
        id = "custom-geo-points",
        source = source,
        filter = feature.isPoint(),
        color = color,
        radius = const(8.dp),
        opacity = const(0.6f),
    )
    SymbolLayer(
        id = "custom-geo-labels",
        source = source,
        minZoom = 14f,
        filter = feature.has("name"),
        textColor = color,
        textFont = const(listOf("Roboto Regular")),
        textField = feature["name"].convertToString(),
        textAllowOverlap = const(true),
        textIgnorePlacement = const(true),
        textAnchor = const(SymbolAnchor.Top),
        textOffset = textOffset(0.em, 1.em),
        textSize = const(16.sp),
    )
}
