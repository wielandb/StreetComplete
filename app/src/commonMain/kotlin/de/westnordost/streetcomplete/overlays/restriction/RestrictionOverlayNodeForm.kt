package de.westnordost.streetcomplete.overlays.restriction

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.Text
import androidx.compose.runtime.*
import de.westnordost.streetcomplete.ui.util.rememberSerializable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.westnordost.streetcomplete.data.elementfilter.toElementFilterExpression
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.edits.MapDataWithEditsSource
import de.westnordost.streetcomplete.data.osm.edits.create.createNodeAction
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.edits.update_tags.UpdateElementTagsAction
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.geometry.ElementPointGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.*
import de.westnordost.streetcomplete.data.overlays.Edit
import de.westnordost.streetcomplete.data.overlays.OverlayAction
import de.westnordost.streetcomplete.osm.ALL_ROADS
import de.westnordost.streetcomplete.osm.oneway.isNotOnewayForCyclists
import de.westnordost.streetcomplete.osm.oneway.isOneway
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.common.DropdownButton
import de.westnordost.streetcomplete.ui.common.item_select.ImageWithLabel
import de.westnordost.streetcomplete.ui.common.overlay.OverlayForm
import de.westnordost.streetcomplete.ui.common.quest.LocalMapMarkersCallback
import de.westnordost.streetcomplete.ui.common.quest.LocalMapMetersPerDp
import de.westnordost.streetcomplete.ui.common.quest.LocalMapRotation
import de.westnordost.streetcomplete.ui.common.quest.Marker
import de.westnordost.streetcomplete.ui.ktx.selectionFrame
import de.westnordost.streetcomplete.ui.util.ClipCirclePainter
import de.westnordost.streetcomplete.util.math.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun RestrictionOverlayNodeForm(
    on: (OverlayAction) -> Unit,
    element: Node?,
    geometry: ElementGeometry,
    countryInfo: CountryInfo,
    mapDataSource: MapDataWithEditsSource = koinInject(),
) {
    val original = remember(element) { parseRestrictionSign(element?.tags.orEmpty()) }
    var selectedSign by rememberSerializable(element?.id) { mutableStateOf(original.first) }
    var direction by rememberSerializable(element?.id) { mutableStateOf(original.second) }
    var data by remember(mapDataSource) { mutableStateOf<MapDataWithGeometry?>(null) }
    var loadedCenter by remember(mapDataSource) { mutableStateOf<LatLon?>(null) }
    val center = element?.position ?: geometry.center
    LaunchedEffect(mapDataSource, center) {
        if (loadedCenter?.distanceTo(center)?.let { it < 50.0 } == true) return@LaunchedEffect
        data = withContext(Dispatchers.IO) { mapDataSource.getMapDataWithGeometry(center.enclosingBoundingBox(150.0)) }
        loadedCenter = center
    }
    val roads = remember(data) {
        val snapshot = data
        snapshot?.filter(restrictionRoadsFilter)?.filterIsInstance<Way>()?.mapNotNull { way ->
            val positions = way.nodeIds.mapNotNull { snapshot.getNode(it)?.position }
            if (positions.size == way.nodeIds.size && positions.size >= 2) way to positions else null
        }?.toList().orEmpty()
    }
    val metersPerDp = LocalMapMetersPerDp.current
    val candidate = remember(element, center, metersPerDp, roads, data) {
        if (element != null || metersPerDp <= 0.0) null
        else center.getPositionOnWays(roads, metersPerDp * 24, metersPerDp * 12)?.takeUnless { position ->
            position is VertexOfWay && data?.getNode(position.nodeId)?.tags?.let {
                "highway" in it || "crossing" in it
            } == true
        }
    }
    val isJunction = candidate is VertexOfWay && restrictionRoadCount(candidate.nodeId, roads.map { it.first }) > 1
    val sign = if (element == null && selectedSign == RestrictionSign.STOP && isJunction) {
        RestrictionSign.ALL_WAY_STOP
    } else selectedSign
    val positionOnWay = candidate.takeUnless { sign == RestrictionSign.GIVE_WAY && isJunction }
    val road = when {
        element != null -> roads.firstOrNull { element.id in it.first.nodeIds }
        positionOnWay is PositionOnWaySegment -> roads.firstOrNull { it.first.id == positionOnWay.wayId }
        positionOnWay is VertexOfWay -> roads.firstOrNull { it.first.id in positionOnWay.wayIds }
        else -> null
    }
    val showDirection = road != null && sign != RestrictionSign.ALL_WAY_STOP &&
        !(positionOnWay is VertexOfWay && positionOnWay.wayIds.size > 1) &&
        restrictionNeedsDirection(road.first, countryInfo.isLeftHandTraffic)
    val bearing = when {
        positionOnWay is PositionOnWaySegment -> positionOnWay.segment.first.initialBearingTo(positionOnWay.segment.second)
        road != null -> restrictionRoadBearing(road, element?.id ?: (positionOnWay as? VertexOfWay)?.nodeId)
        else -> 0.0
    }
    val setMarkers by rememberUpdatedState(LocalMapMarkersCallback.current)
    LaunchedEffect(element, positionOnWay, sign) {
        if (element == null) {
            setMarkers?.invoke(if (positionOnWay != null) listOf(Marker(
                ElementPointGeometry(positionOnWay.position), (sign ?: RestrictionSign.STOP).image,
            )) else emptyList())
        }
    }
    DisposableEffect(Unit) { onDispose { setMarkers?.invoke(emptyList()) } }

    OverlayForm(
        on = on,
        isComplete = sign != null && (element != null || positionOnWay != null),
        hasChanges = original != (sign to direction),
        onClickOk = {
            val chosen = sign
            if (chosen != null) {
                val action = if (element != null) {
                    val changes = StringMapChangesBuilder(element.tags)
                    applyRestrictionSign(changes, chosen, direction)
                    UpdateElementTagsAction(element, changes.create())
                } else positionOnWay?.let { position ->
                    createNodeAction(position, mapDataSource) { applyRestrictionSign(it, chosen, direction) }
                }
                if (action != null) {
                    val position = element?.position ?: positionOnWay?.position
                    on(Edit(action, position?.let { ElementPointGeometry(it) }))
                }
            }
        },
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            DropdownButton(
                items = listOf(RestrictionSign.GIVE_WAY, RestrictionSign.STOP),
                onSelectedItem = { selectedSign = it },
                selectedItem = sign,
                itemContent = { ImageWithLabel(painterResource(it.image), stringResource(it.text)) },
            )
            if (showDirection) {
                Text(stringResource(Res.string.restriction_overlay_direction_text))
                val rotation = bearing.toFloat() - LocalMapRotation.current
                Row {
                    for (value in RestrictionDirection.entries) {
                        val arrow = painterResource(if (value == RestrictionDirection.FORWARD) Res.drawable.oneway_yes else Res.drawable.oneway_yes_reverse)
                        Box(
                            modifier = Modifier.selectionFrame(direction == value)
                                .selectable(direction == value) { direction = value },
                            contentAlignment = Alignment.Center,
                        ) {
                            ImageWithLabel(
                                painter = remember(arrow) { ClipCirclePainter(arrow) },
                                label = null,
                                imageRotation = rotation,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun restrictionRoadCount(nodeId: Long, roads: Collection<Way>): Int {
    val containing = roads.filter { nodeId in it.nodeIds }
    return if (containing.size == 2 && containing.all {
        it.nodeIds.firstOrNull() == nodeId || it.nodeIds.lastOrNull() == nodeId
    }) 1 else containing.size
}

private fun restrictionNeedsDirection(way: Way, leftHandTraffic: Boolean): Boolean =
    if (way.tags["highway"] in ALL_ROADS) {
        !isOneway(way.tags) || isNotOnewayForCyclists(way.tags, leftHandTraffic)
    } else {
        !isOneway(way.tags) && way.tags["oneway:bicycle"] !in listOf("yes", "-1")
    }

private fun restrictionRoadBearing(road: Pair<Way, List<LatLon>>, nodeId: Long?): Double {
    if (nodeId == null) return 0.0
    val index = road.first.nodeIds.indexOf(nodeId)
    val positions = road.second
    if (index < 0 || positions.size < 2) return 0.0
    return if (index < positions.lastIndex) positions[index].initialBearingTo(positions[index + 1])
    else positions[index - 1].initialBearingTo(positions[index])
}

private val restrictionRoadsFilter = """
    ways with area != yes and (
        highway ~ ${ALL_ROADS.joinToString("|")}|cycleway
        or (highway ~ path|footpath|bridleway and bicycle ~ yes|designated)
    )
""".toElementFilterExpression()

private val RestrictionSign.text get() = when (this) {
    RestrictionSign.GIVE_WAY -> Res.string.restriction_overlay_sign_give_way
    RestrictionSign.STOP -> Res.string.restriction_overlay_sign_stop
    RestrictionSign.ALL_WAY_STOP -> Res.string.restriction_overlay_sign_stop_all_way
}
private val RestrictionSign.image get() = when (this) {
    RestrictionSign.GIVE_WAY -> Res.drawable.ic_restriction_give_way
    RestrictionSign.STOP, RestrictionSign.ALL_WAY_STOP -> Res.drawable.ic_restriction_stop
}
