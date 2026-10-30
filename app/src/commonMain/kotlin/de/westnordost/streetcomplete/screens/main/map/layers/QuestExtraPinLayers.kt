package de.westnordost.streetcomplete.screens.main.map.layers

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import de.westnordost.streetcomplete.screens.main.map.MapColors
import de.westnordost.streetcomplete.screens.main.map.Light
import de.westnordost.streetcomplete.screens.main.map.Night
import de.westnordost.streetcomplete.screens.main.map.isArea
import de.westnordost.streetcomplete.screens.main.map.isLines
import de.westnordost.streetcomplete.screens.main.map.toGeometry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import org.maplibre.compose.expressions.dsl.all
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToColor
import org.maplibre.compose.expressions.dsl.convertToNumber
import org.maplibre.compose.expressions.dsl.convertToString
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.gt
import org.maplibre.compose.expressions.dsl.step
import org.maplibre.compose.expressions.dsl.textOffset
import org.maplibre.compose.expressions.dsl.zoom
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.GeometryCollection
import org.maplibre.spatialk.geojson.Point

/** SCEE POI dots and optional quest geometries, separate from the clustered quest pins. */
@Composable @MaplibreComposable
internal fun QuestExtraPinLayers(
    pins: Collection<Pin>,
    onClickPin: (JsonObject) -> ClickResult,
) {
    val dots by produceState<List<Feature<Point, JsonObject>>>(emptyList(), pins) {
        value = withContext(Dispatchers.Default) {
            pins.filter { it.color != null }.map { it.toGeoJsonFeature() }
        }
    }
    val geometries by produceState(GeometryCollection(emptyList()), pins) {
        value = withContext(Dispatchers.Default) {
            GeometryCollection(pins.mapNotNull { it.geometry }.distinct().map { it.toGeometry() })
        }
    }
    val dotSource = rememberGeoJsonSource(GeoJsonData.Features(FeatureCollection(dots)))
    val geometrySource = rememberGeoJsonSource(GeoJsonData.Features(geometries))
    val colors = if (MaterialTheme.colors.isLight) MapColors.Light else MapColors.Night

    LineLayer(
        id = "quest-geometry-lines",
        source = geometrySource,
        minZoom = 16f,
        filter = feature.isLines(),
        color = const(Color(0xff0092d1)),
        opacity = const(0.4f),
        width = const(10.dp),
        cap = const(LineCap.Round),
    )
    FillLayer(
        id = "quest-geometry-areas",
        source = geometrySource,
        minZoom = 17f,
        filter = feature.isArea(),
        color = const(Color(0xff0092d1)),
        opacity = const(0.2f),
    )
    SymbolLayer(
        id = "quest-dot-labels",
        source = dotSource,
        filter = all(zoom().gt(const(14)), feature.has("label")),
        textField = feature["label"].convertToString(),
        textFont = const(listOf("Roboto Regular")),
        textSize = const(16.sp),
        textColor = const(colors.text),
        textHaloColor = const(colors.textOutline),
        textHaloWidth = const(2.5.dp),
        textAnchor = const(SymbolAnchor.Top),
        textOffset = textOffset(0.em, 0.5.em),
        textOptional = const(true),
        textAllowOverlap = step(
            input = zoom(),
            fallback = const(false),
            21 to const(true),
        ),
        sortKey = feature["dot-order"].convertToNumber(),
    )
    CircleLayer(
        id = "quest-dots",
        source = dotSource,
        filter = zoom().gt(const(16)),
        color = feature["dot-color"].convertToColor(),
        strokeColor = const(if (MaterialTheme.colors.isLight) Color(0xff666666) else Color(0xff333333)),
        radius = const(8.dp),
        strokeWidth = const(1.dp),
        sortKey = feature["dot-order"].convertToNumber(),
        hitPadding = 8.dp,
        onClick = { features ->
            features.firstOrNull()?.properties?.let(onClickPin) ?: ClickResult.Pass
        },
    )
}
