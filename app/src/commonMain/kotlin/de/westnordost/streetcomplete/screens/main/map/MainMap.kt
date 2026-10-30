package de.westnordost.streetcomplete.screens.main.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.ui.util.rememberBooleanPreference
import de.westnordost.streetcomplete.ui.util.rememberFloatPreference
import org.maplibre.compose.interaction.ClickEvent
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.map.CameraConstraints
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.MapUiOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.overlay.MapOverlayScope

/** Presents the shared map. */
@Composable
fun MainMap(
    state: MapState,
    modifier: Modifier = Modifier,
    onPan: () -> Unit = {},
    onRotate: () -> Unit = {},
    onMapClick: (ClickEvent) -> ClickResult = { ClickResult.Pass },
    onMapLongClick: (ClickEvent) -> ClickResult = { ClickResult.Pass },
    overlay: @Composable MapOverlayScope.() -> Unit = {},
) {
    val rotateWhileZooming = rememberBooleanPreference(Prefs.ROTATE_WHILE_ZOOMING, false)
    val rotationThreshold = rememberFloatPreference(Prefs.ROTATE_ANGLE_THRESHOLD, 1.5f)
    MaplibreMap(
        modifier = modifier,
        state = state,
        uiOptions = MapUiOptions {
            bindings {
                transform {
                    rotate {
                        allowDuringZoom = rotateWhileZooming
                        startAngle = rotationThreshold.takeIf { it.isFinite() }
                            ?.coerceAtLeast(0f)?.toDouble() ?: 1.5
                    }
                }
            }
        },
        cameraConstraints = CameraConstraints {
            minZoom = 0.0
            maxZoom = 22.0
        },
        interactions = MapInteractions {
            camera {
                pan { onStart(onPan) }
                rotate { onStart(onRotate) }
            }
            callbacks {
                click { onUnhandled(onMapClick) }
                longClick { onEvent(onMapLongClick) }
            }
        },
        overlay = overlay,
    )
}
