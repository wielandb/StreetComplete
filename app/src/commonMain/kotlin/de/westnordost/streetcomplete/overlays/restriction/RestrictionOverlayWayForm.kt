package de.westnordost.streetcomplete.overlays.restriction

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.edits.MapDataWithEditsSource
import de.westnordost.streetcomplete.data.osm.edits.create.CreateRelationAction
import de.westnordost.streetcomplete.data.osm.edits.delete.DeleteRelationAction
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.edits.update_tags.UpdateElementTagsAction
import de.westnordost.streetcomplete.data.osm.geometry.*
import de.westnordost.streetcomplete.data.osm.mapdata.*
import de.westnordost.streetcomplete.data.overlays.Edit
import de.westnordost.streetcomplete.data.overlays.LeaveNoteForElement
import de.westnordost.streetcomplete.data.overlays.OverlayAction
import de.westnordost.streetcomplete.osm.ALL_ROADS
import de.westnordost.streetcomplete.osm.AddConditionalDialog
import de.westnordost.streetcomplete.quests.max_weight.*
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.screens.main.bottom_sheet.EditTagsForm
import de.westnordost.streetcomplete.ui.common.DropdownButton
import de.westnordost.streetcomplete.ui.common.overlay.OverlayForm
import de.westnordost.streetcomplete.ui.common.quest.*
import de.westnordost.streetcomplete.ui.util.rememberSerializable
import de.westnordost.streetcomplete.util.math.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun RestrictionOverlayWayForm(
    on: (OverlayAction) -> Unit,
    element: Way,
    geometry: ElementGeometry,
    countryInfo: CountryInfo,
    mapDataSource: MapDataWithEditsSource = koinInject(),
) {
    var originals by remember(element) { mutableStateOf<List<RestrictionDraft>?>(null) }
    var selectedKey by rememberSaveable(element.id) { mutableStateOf<String?>(null) }
    var current by rememberSerializable(element.id) { mutableStateOf<RestrictionDraft?>(null) }
    var selectingWay by rememberSaveable(element.id) { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var editingTags by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(element, mapDataSource) {
        val loaded = withContext(Dispatchers.IO) {
            val turns = mapDataSource.getRelationsForWay(element.id)
                .filter { it.tags["type"] == "restriction" }.map { RestrictionDraft.Turn(it) }
            val weights = MaxWeightType.entries.filter {
                it.osmKey in element.tags || "${it.osmKey}:conditional" in element.tags
            }.map { RestrictionDraft.WeightLimit(it, element.tags) }
            val preferred = turns.firstOrNull {
                it.relation.isSupportedTurnRestriction() && it.relation.members.all { m -> mapDataSource.get(m.type, m.ref) != null }
            } ?: weights.firstOrNull() ?: turns.firstOrNull()
            val all = turns + weights
            if (preferred == null) all else listOf(preferred) + all.filterNot { it.key == preferred.key }
        }
        originals = loaded
        if (current == null && selectedKey == null) loaded.firstOrNull()?.let {
            current = it
            selectedKey = it.key
        }
    }
    val shown = current
    val original = originals?.firstOrNull { it.key == selectedKey }
    val relation = (shown as? RestrictionDraft.Turn)?.relation
    val relationComplete by produceState(false, relation?.members, mapDataSource) {
        value = relation != null && relation.members.isNotEmpty() && withContext(Dispatchers.IO) {
            relation.members.all { mapDataSource.get(it.type, it.ref) != null }
        }
    }
    val editGeometry by produceState<ElementGeometry>(geometry, relation, mapDataSource) {
        value = if (relation == null) geometry else withContext(Dispatchers.IO) {
            restrictionGeometry(relation, mapDataSource) ?: geometry
        }
    }
    val markers by produceState<List<Marker>>(emptyList(), relation, mapDataSource) {
        value = if (relation == null) emptyList() else withContext(Dispatchers.IO) {
            restrictionMarkers(relation, mapDataSource)
        }
    }
    val setMarkers by rememberUpdatedState(LocalMapMarkersCallback.current)
    LaunchedEffect(markers) { setMarkers?.invoke(markers) }
    DisposableEffect(Unit) { onDispose { setMarkers?.invoke(emptyList()) } }

    if (editingTags && relation != null) {
        EditTagsForm(
            onConfirmed = { changes ->
                val after = relation.tags.toMutableMap().also { changes.applyTo(it) }
                val before = (original as? RestrictionDraft.Turn)?.relation ?: relation
                on(Edit(UpdateElementTagsAction(before, restrictionTagChanges(before.tags, after).create()), editGeometry, isTagEdit = true))
            },
            onDismiss = { editingTags = false },
            originalElement = relation,
            elementGeometry = editGeometry,
        )
        return
    }

    val changed = shown != null && shown != original
    val complete = when (shown) {
        is RestrictionDraft.Turn -> relationComplete && shown.relation.isSupportedTurnRestriction()
        is RestrictionDraft.WeightLimit -> parseRestrictionWeight(shown.tags[shown.type.osmKey]) != null ||
            shown.tags["${shown.type.osmKey}:conditional"]?.contains('@') == true
        null -> false
    }
    OverlayForm(
        on = on,
        isComplete = complete && changed,
        hasChanges = changed,
        onClickOk = {
            when (shown) {
                is RestrictionDraft.Turn -> {
                    val rel = shown.relation
                    if (rel.id == 0L) on(Edit(CreateRelationAction(rel.tags, rel.members), editGeometry))
                    else (original as? RestrictionDraft.Turn)?.relation?.let { before ->
                        on(Edit(UpdateElementTagsAction(before, restrictionTagChanges(before.tags, rel.tags).create()), editGeometry))
                    }
                }
                is RestrictionDraft.WeightLimit -> on(Edit(
                    UpdateElementTagsAction(element, restrictionTagChanges(element.tags, shown.tags).create()), geometry,
                ))
                null -> Unit
            }
        },
        onClickMap = { click ->
            if (!selectingWay) false else {
                scope.launch {
                    val members = withContext(Dispatchers.IO) { selectRestrictionMembers(element, click, mapDataSource) }
                    val latest = current as? RestrictionDraft.Turn
                    if (members != null && latest != null) current = latest.copy(relation = latest.relation.copy(members = members))
                }
                true
            }
        },
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (shown) {
                is RestrictionDraft.WeightLimit -> {
                    key(shown.key) {
                        MaxWeightSignForm(
                            type = shown.type,
                            weight = parseRestrictionWeight(shown.tags[shown.type.osmKey]),
                            onWeightChange = { weight -> current = shown.copy(tags = shown.tags.toMutableMap().apply {
                                if (weight == null) remove(shown.type.osmKey) else this[shown.type.osmKey] = weight.toOsmString()
                            }) },
                            locale = countryInfo.userPreferredLocale,
                            selectableUnits = countryInfo.weightLimitUnits,
                        )
                    }
                }
                is RestrictionDraft.Turn -> {
                    if (selectingWay) Text(stringResource(Res.string.restriction_overlay_select_way))
                    val supported = shown.relation.id == 0L || shown.relation.isSupportedTurnRestriction()
                    if (shown.relation.id != 0L && !relationComplete) Text(stringResource(Res.string.restriction_overlay_relation_incomplete))
                    if (!supported) Text(stringResource(Res.string.restriction_overlay_relation_unsupported, relationDetails(shown.relation)))
                    if (supported) {
                        DropdownButton(
                            items = turnRestrictionTypes.toList(),
                            selectedItem = shown.tags.getShortRestrictionValue(),
                            onSelectedItem = { current = shown.withTags(changeTurnRestrictionType(shown.tags, it)) },
                            itemContent = { Image(painterResource(getIconForTurnRestriction(it)), it, Modifier.size(64.dp)) },
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(shown.tags["implicit"] != "yes", { signed ->
                                current = shown.withTags(shown.tags.toMutableMap().apply {
                                    if (signed) remove("implicit") else this["implicit"] = "yes"
                                })
                            })
                            Text(stringResource(Res.string.restriction_overlay_signed_switch))
                        }
                        if (shown.relation.id == 0L && shown.relation.members.isNotEmpty()) TextButton({
                            current = shown.copy(relation = shown.relation.copy(members = shown.relation.members.map {
                                it.copy(role = when (it.role) { "from" -> "to"; "to" -> "from"; else -> it.role })
                            }))
                        }) { Text(stringResource(Res.string.restriction_editor_swap_ways)) }
                        TextButton({ dialog = "exceptions" }) {
                            Text(stringResource(Res.string.restriction_overlay_exceptions,
                                shown.tags["except"]?.replace(";", ", ") ?: stringResource(Res.string.overlay_none)))
                        }
                        TextButton({ dialog = "vehicle" }) {
                            Text(stringResource(Res.string.restriction_overlay_only_for,
                                shown.tags.turnRestrictionKey().removePrefix("restriction").removePrefix(":").ifEmpty { "-" }))
                        }
                    }
                    if (shown.relation.id != 0L) TextButton({ dialog = "details" }) {
                        Text(stringResource(Res.string.restriction_overlay_show_details))
                    }
                }
                null -> Unit
            }
            if (shown != null && (shown !is RestrictionDraft.Turn || shown.relation.id == 0L || shown.relation.isSupportedTurnRestriction())) {
                val key = shown.baseKey
                val condition = shown.tags["$key:conditional"]
                if (condition != null) {
                    Text("$key:conditional = $condition")
                    TextButton({ current = shown.withTags(if (shown is RestrictionDraft.Turn) {
                        removeTurnRestrictionCondition(shown.tags)
                    } else shown.tags - "$key:conditional") }) {
                        Text(stringResource(Res.string.restriction_overlay_remove_conditional_restrictions))
                    }
                } else TextButton({ dialog = "conditional" }) {
                    Text(stringResource(Res.string.access_manager_button_add_conditional))
                }

            }
            if (original != null) TextButton({ dialog = "delete" }) {
                Text(stringResource(Res.string.quest_maxweight_remove_sign))
            }
            if (!changed && originals != null) {
                TextButton({ dialog = "add" }) { Text(stringResource(Res.string.restriction_editor_add)) }
                if (originals.orEmpty().any { it.key != selectedKey }) {
                    Text(stringResource(Res.string.restriction_overlay_other_restrictions))
                    for (item in originals.orEmpty().filter { it.key != selectedKey }) TextButton({
                        selectedKey = item.key
                        current = item
                        selectingWay = false
                    }) { RestrictionLabel(item, countryInfo.countryCode.orEmpty()) }
                }
            }
        }
    }

    when (dialog) {
        "add" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(Res.string.restriction_editor_add)) },
            text = { Column {
                TextButton({
                    selectedKey = null
                    current = RestrictionDraft.Turn(Relation(0L, emptyList(), mapOf("type" to "restriction", "restriction" to turnRestrictionTypes.first())))
                    selectingWay = true
                    dialog = null
                }) { Text(stringResource(Res.string.restriction_editor_turn)) }
                TextButton({ dialog = "weight" }) { Text(stringResource(Res.string.restriction_editor_weight)) }
            } },
            confirmButton = { TextButton({ dialog = null }) { Text(stringResource(Res.string.cancel)) } },
        )
        "weight" -> AlertDialog(
            onDismissRequest = { dialog = null },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                for (type in MaxWeightType.entries.filter { candidate -> originals.orEmpty().none {
                    it is RestrictionDraft.WeightLimit && it.type == candidate
                } }) TextButton({
                    selectedKey = null
                    current = RestrictionDraft.WeightLimit(type, element.tags)
                    selectingWay = false
                    dialog = null
                }) { RestrictionLabel(RestrictionDraft.WeightLimit(type, emptyMap()), countryInfo.countryCode.orEmpty()) }
            } },
            confirmButton = { TextButton({ dialog = null }) { Text(stringResource(Res.string.cancel)) } },
        )
        "conditional" -> if (shown != null) AddConditionalDialog(
            onDismissRequest = { dialog = null },
            keys = listOf(shown.baseKey),
            values = if (shown is RestrictionDraft.Turn) listOfNotNull(shown.tags.getShortRestrictionValue(), "none") else null,
            numberOnly = false,
            countryInfo = countryInfo,
            onClickOk = { _, condition -> current = shown.withTags(addRestrictionCondition(shown.tags, shown.baseKey, condition)); dialog = null },
        )
        "exceptions" -> if (shown is RestrictionDraft.Turn) {
            var values by remember(shown.key) { mutableStateOf(shown.tags["except"]?.split(';')?.filter { it.isNotBlank() }?.toSet().orEmpty()) }
            AlertDialog(
                onDismissRequest = { dialog = null },
                text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                    for (vehicle in (restrictionExceptions + values).distinct()) Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(vehicle in values, { checked -> values = if (checked) values + vehicle else values - vehicle })
                        Text(vehicle)
                    }
                } },
                confirmButton = { TextButton({
                    current = shown.withTags(shown.tags.toMutableMap().apply {
                        if (values.isEmpty()) remove("except") else this["except"] = values.joinToString(";")
                    }); dialog = null
                }) { Text(stringResource(Res.string.ok)) } },
                dismissButton = { TextButton({ dialog = null }) { Text(stringResource(Res.string.cancel)) } },
            )
        }
        "vehicle" -> if (shown is RestrictionDraft.Turn) AlertDialog(
            onDismissRequest = { dialog = null },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                for (vehicle in listOf<String?>(null) + onlyTurnRestriction) TextButton({
                    current = shown.withTags(changeTurnRestrictionVehicle(shown.tags, vehicle)); dialog = null
                }) { Text(vehicle ?: stringResource(Res.string.overlay_none)) }
            } },
            confirmButton = { TextButton({ dialog = null }) { Text(stringResource(Res.string.cancel)) } },
        )
        "details" -> if (relation != null) AlertDialog(
            onDismissRequest = { dialog = null },
            text = { Text(relationDetails(relation), Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton({ dialog = null }) { Text(stringResource(Res.string.ok)) } },
            dismissButton = { TextButton({ editingTags = true; dialog = null }) {
                Text(stringResource(Res.string.quest_generic_answer_show_edit_tags))
            } },
        )
        "delete" -> if (shown != null && original != null) AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(Res.string.quest_generic_confirmation_title)) },
            confirmButton = { TextButton({
                when (original) {
                    is RestrictionDraft.Turn -> on(Edit(DeleteRelationAction(original.relation), editGeometry))
                    is RestrictionDraft.WeightLimit -> {
                        val key = original.type.osmKey
                        val remaining = element.tags.filterKeys { it != key && !it.startsWith("$key:") }
                        on(Edit(UpdateElementTagsAction(element, restrictionTagChanges(element.tags, remaining).create()), geometry))
                    }
                }
                dialog = null
            }) { Text(stringResource(Res.string.quest_generic_confirmation_yes)) } },
            dismissButton = { Row {
                TextButton({ dialog = null }) { Text(stringResource(Res.string.cancel)) }
                TextButton({
                    dialog = null
                    on(LeaveNoteForElement((original as? RestrictionDraft.Turn)?.relation ?: element, editGeometry))
                }) { Text(stringResource(Res.string.leave_note)) }
            } },
        )
    }
}

@Serializable
private sealed interface RestrictionDraft {
    val key: String
    val tags: Map<String, String>
    val baseKey: String
    @Serializable data class Turn(val relation: Relation) : RestrictionDraft {
        override val key get() = "turn:${relation.id}"
        override val tags get() = relation.tags
        override val baseKey get() = tags.turnRestrictionKey()
    }
    @Serializable data class WeightLimit(val type: MaxWeightType, override val tags: Map<String, String>) : RestrictionDraft {
        override val key get() = "weight:$type"
        override val baseKey get() = type.osmKey
    }
}

private fun RestrictionDraft.withTags(tags: Map<String, String>): RestrictionDraft = when (this) {
    is RestrictionDraft.Turn -> copy(relation = relation.copy(tags = tags))
    is RestrictionDraft.WeightLimit -> copy(tags = tags)
}

@Composable
private fun RestrictionLabel(restriction: RestrictionDraft, countryCode: String) {
    val icon = when (restriction) {
        is RestrictionDraft.Turn -> getIconForTurnRestriction(restriction.tags.getShortRestrictionValue())
        is RestrictionDraft.WeightLimit -> restriction.type.getIcon(countryCode) ?: Res.drawable.quest_max_weight
    }
    Image(painterResource(icon), null, Modifier.size(48.dp))
    Text(when (restriction) {
        is RestrictionDraft.Turn -> restriction.tags.getShortRestrictionValue().orEmpty().replace('_', ' ')
        is RestrictionDraft.WeightLimit -> restriction.tags[restriction.baseKey] ?: restriction.baseKey
    })
}

private fun restrictionTagChanges(before: Map<String, String>, after: Map<String, String>) =
    StringMapChangesBuilder(before).apply {
        for (key in before.keys - after.keys) remove(key)
        for ((key, value) in after) if (before[key] != value) this[key] = value
    }

private fun selectRestrictionMembers(from: Way, click: MapClick, source: MapDataWithEditsSource): List<RelationMember>? {
    val viaNodes = listOfNotNull(from.nodeIds.firstOrNull(), from.nodeIds.lastOrNull()).distinct().filter { node ->
        source.getWaysForNode(node).count { it.tags["highway"] in ALL_ROADS } > 2
    }
    if (viaNodes.isEmpty()) return null
    val data = source.getMapDataWithGeometry(click.position.enclosingBoundingBox(click.clickAreaSizeInMeters.coerceAtLeast(10.0)))
    val candidates = data.ways.mapNotNull { way ->
        if (way.id == from.id || way.tags["highway"] !in ALL_ROADS || way.nodeIds.firstOrNull() == way.nodeIds.lastOrNull()) return@mapNotNull null
        val shared = listOfNotNull(way.nodeIds.firstOrNull(), way.nodeIds.lastOrNull()).singleOrNull { it in viaNodes } ?: return@mapNotNull null
        val points = (data.getGeometry(way.type, way.id) as? ElementPolylinesGeometry)?.polylines?.singleOrNull() ?: return@mapNotNull null
        Triple(way, shared, click.position.distanceToArcs(points))
    }
    val (to, via) = candidates.minByOrNull { it.third } ?: return null
    if (source.getNode(via) == null) return null
    return listOf(RelationMember(from.type, from.id, "from"), RelationMember(to.type, to.id, "to"), RelationMember(ElementType.NODE, via, "via"))
}

private fun restrictionGeometry(relation: Relation, source: MapDataWithEditsSource): ElementGeometry? {
    if (relation.id != 0L) source.getGeometry(relation.type, relation.id)?.let { return it }
    val lines = relation.members.filter { it.type == ElementType.WAY }.mapNotNull { member ->
        val points = (source.getGeometry(member.type, member.ref) as? ElementPolylinesGeometry)?.polylines?.singleOrNull()
        points?.let { member.ref to it }
    }.toMap()
    return ElementGeometryCreator().create(relation, lines)
}

private fun restrictionMarkers(relation: Relation, source: MapDataWithEditsSource): List<Marker> {
    val result = relation.members.mapNotNull { member ->
        source.getGeometry(member.type, member.ref)?.let { Marker(it) }
    }.toMutableList()
    val from = relation.members.singleOrNull { it.role == "from" && it.type == ElementType.WAY }
        ?.let { source.getWay(it.ref) } ?: return result
    val viaMembers = relation.members.filter { it.role == "via" }.mapNotNull { source.get(it.type, it.ref) }
    val first = viaMembers.any { it is Node && it.id == from.nodeIds.firstOrNull() || it is Way && from.nodeIds.firstOrNull() in listOf(it.nodeIds.firstOrNull(), it.nodeIds.lastOrNull()) }
    val last = viaMembers.any { it is Node && it.id == from.nodeIds.lastOrNull() || it is Way && from.nodeIds.lastOrNull() in listOf(it.nodeIds.firstOrNull(), it.nodeIds.lastOrNull()) }
    if (first == last) return result
    val nodes = (if (first) from.nodeIds.take(2).reversed() else from.nodeIds.takeLast(2)).mapNotNull { source.getNode(it) }
    if (nodes.size == 2) result += Marker(ElementPointGeometry(nodes.last().position),
        getIconForTurnRestriction(relation.tags.getShortRestrictionValue()), rotation = nodes.first().position.finalBearingTo(nodes.last().position))
    return result
}

private fun relationDetails(relation: Relation): String =
    relation.tags.entries.sortedBy { it.key }.joinToString("\n") { "${it.key} = ${it.value}" } + "\n\n" +
        relation.members.joinToString("\n") { "${it.role}: ${it.key}" }

private val restrictionExceptions = listOf("bicycle", "psv", "bus", "emergency", "agricultural", "hgv", "moped", "destination", "motorcar")



private fun getIconForTurnRestriction(type: String?) = when (type) {
    "no_right_turn" -> Res.drawable.ic_restriction_no_right_turn
    "no_left_turn" -> Res.drawable.ic_restriction_no_left_turn
    "no_u_turn" -> Res.drawable.ic_restriction_no_u_turn
    "no_straight_on" -> Res.drawable.ic_restriction_no_straight_on
    "only_right_turn" -> Res.drawable.ic_restriction_only_right_turn
    "only_left_turn" -> Res.drawable.ic_restriction_only_left_turn
    "only_straight_on" -> Res.drawable.ic_restriction_only_straight_on
    else -> Res.drawable.ic_restriction_unknown
}

fun Map<String, String>.getShortRestrictionValue(): String? {
    get("restriction")?.let { return it }
    get("restriction:conditional")?.let { return it.substringBefore('@').trim() }
    entries.firstOrNull { it.key.startsWith("restriction:") }?.let { return it.value.substringBefore('@').trim() }
    return null
}
