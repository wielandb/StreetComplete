package de.westnordost.streetcomplete.screens.main.map.layers

import androidx.compose.runtime.Composable
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.map_oneway_arrow
import de.westnordost.streetcomplete.screens.main.map.isLines
import de.westnordost.streetcomplete.ui.util.rememberBooleanPreference
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import org.maplibre.compose.expressions.ast.Expression
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.value.ColorValue
import org.maplibre.compose.expressions.value.IconRotationAlignment
import org.maplibre.compose.expressions.value.SymbolPlacement
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.VectorSource
import org.maplibre.compose.util.MaplibreComposable

@Composable @MaplibreComposable
internal fun WayDirectionLayer(
    id: String,
    source: VectorSource,
    color: Expression<ColorValue>,
    preferences: Preferences = koinInject(),
) {
    if (!rememberBooleanPreference(Prefs.SHOW_WAY_DIRECTION, false, preferences)) return
    SymbolLayer(
        id = id,
        source = source,
        filter = feature.isLines(),
        iconImage = image(painterResource(Res.drawable.map_oneway_arrow), drawAsSdf = true),
        iconColor = color,
        iconOpacity = const(0.7f),
        placement = const(SymbolPlacement.Line),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
        iconRotate = const(90),
        iconRotationAlignment = const(IconRotationAlignment.Map),
    )
}
