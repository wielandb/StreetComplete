package de.westnordost.streetcomplete.screens.main.map.sources

import de.westnordost.streetcomplete.DayNightBehavior
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.osm.edits.MapDataWithEditsSource
import de.westnordost.streetcomplete.data.osm.geometry.ElementPointGeometry
import de.westnordost.streetcomplete.data.osm.osmquests.OsmQuest
import de.westnordost.streetcomplete.data.overlays.SelectedOverlaySource
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.data.quest.DayNightCycle
import de.westnordost.streetcomplete.data.quest.ExternalSourceQuestKey
import de.westnordost.streetcomplete.overlays.places.PlacesOverlay
import de.westnordost.streetcomplete.quests.show_poi.ShowBusiness
import de.westnordost.streetcomplete.util.getNameLabel
import de.westnordost.streetcomplete.util.isDay
import de.westnordost.streetcomplete.data.download.tiles.TilesRect
import de.westnordost.streetcomplete.data.download.tiles.enclosingTilesRect
import de.westnordost.streetcomplete.data.osm.mapdata.BoundingBox
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.osm.mapdata.ElementType
import de.westnordost.streetcomplete.data.quest.OsmNoteQuestKey
import de.westnordost.streetcomplete.data.quest.OsmQuestKey
import de.westnordost.streetcomplete.data.quest.Quest
import de.westnordost.streetcomplete.data.quest.QuestKey
import de.westnordost.streetcomplete.data.quest.QuestType
import de.westnordost.streetcomplete.data.quest.QuestTypeRegistry
import de.westnordost.streetcomplete.data.quest.VisibleQuestsSource
import de.westnordost.streetcomplete.data.visiblequests.QuestTypeOrderSource
import de.westnordost.streetcomplete.screens.main.map.layers.Pin
import de.westnordost.streetcomplete.util.math.contains
import de.westnordost.streetcomplete.util.math.normalizeLongitude
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** Source for map quest [pins] on the map. Since there can be a very, very large number of quest
 *  pins on the map, we only show those that are in view. This requires users to call [onMapMoved]
 *  so that the [pins] are updated when the viewport moves to a new area. */
@OptIn(ExperimentalCoroutinesApi::class)
class MapQuestPinsSource(
    private val questTypeOrderSource: QuestTypeOrderSource,
    private val questTypeRegistry: QuestTypeRegistry,
    private val visibleQuestsSource: VisibleQuestsSource,
    private val mapDataSource: MapDataWithEditsSource,
    private val selectedOverlaySource: SelectedOverlaySource,
    private val prefs: Preferences,
) {
    private val displayedRect = MutableStateFlow<TilesRect?>(null)

    private val reversedOrder = MutableStateFlow(false)
    private val isDaylight = MutableStateFlow(true)

    fun setReverseQuestOrder(reverse: Boolean) { reversedOrder.value = reverse }

    val pins: Flow<Collection<Pin>> = flow {
        val pinsByQuest = mutableMapOf<QuestKey, CachedPins>()
        var orders = emptyMap<QuestType, Int>()

        emitAll(combine(displayedRect, reversedOrder, isDaylight) { rect, reverse, daylight ->
            Triple(rect, reverse, daylight)
        }.flatMapLatest { (rect, reverse, daylight) ->
            if (rect == null) return@flatMapLatest flowOf(emptyList())
            val bbox = rect.asBoundingBox(TILES_ZOOM)
            events().map { event ->
                when (event) {
                    Event.Reload -> {
                        val types = questTypeRegistry.toMutableList()
                        withContext(Dispatchers.IO) { questTypeOrderSource.sort(types) }
                        if (prefs.getString(Prefs.DAY_NIGHT_BEHAVIOR, "IGNORE") == DayNightBehavior.PRIORITY.name) {
                            val preferred = if (daylight) DayNightCycle.ONLY_DAY else DayNightCycle.ONLY_NIGHT
                            types.sortBy { it.dayNightCycle != preferred }
                        }
                        if (reverse) types.reverse()
                        orders = types.withIndex().associate { it.value to it.index }
                        // Long-way pins retained from the previous viewport must change order too.
                        pinsByQuest.entries.forEach { entry ->
                            val cached = entry.value
                            entry.setValue(cached.copy(pins = cached.pins.map { it.copy(order = orders[cached.type] ?: 0) }))
                        }

                        /* Usually, we would call pinsByQuest.clear() here. However,
                           quests have only a single position, but may have multiple pins (see
                           Quest::markerLocations), e.g. at the start and end of a long road. A pin
                           of a quest whose center is outside the current view may hence be within
                           the current view. Quest pins like these should not disappear when panning
                           the map. Therefore, remove all quests that are not in view anymore that
                           ... (#5802)
                          */
                        pinsByQuest.entries.removeAll { (_, cached) ->
                            val pins = cached.pins
                            // only have one pin (pin position = quest position)
                            pins.size == 1
                            // or has no pins in the current view
                            || pins.none { it.position in bbox }
                        }
                        val quests = withContext(Dispatchers.IO) { visibleQuestsSource.getAll(bbox) }
                        withContext(Dispatchers.IO) {
                            quests.forEach { pinsByQuest[it.key] = it.toCachedPins(orders) }
                        }
                    }
                    is Event.Updated -> {
                        event.removed.forEach { pinsByQuest.remove(it) }
                        for (quest in event.added) {
                            if (quest.markerLocations.any { it in bbox }) {
                                pinsByQuest[quest.key] = withContext(Dispatchers.IO) { quest.toCachedPins(orders) }
                            } else {
                                pinsByQuest.remove(quest.key)
                            }
                        }
                    }
                }
                pinsByQuest.values.flatMap { it.pins }
            }
        })
    }.flowOn(Dispatchers.Default)

    // Callbacks may arrive on different threads; only the collector mutates the displayed data.
    private sealed interface Event {
        data object Reload : Event
        data class Updated(val added: List<Quest>, val removed: List<QuestKey>) : Event
    }

    private fun events(): Flow<Event> = callbackFlow {
        val questsListener = object : VisibleQuestsSource.Listener {
            override fun onUpdated(added: Collection<Quest>, removed: Collection<QuestKey>) {
                trySend(Event.Updated(added.toList(), removed.toList()))
            }
            override fun onInvalidated() { trySend(Event.Reload) }
        }
        val orderListener = object : QuestTypeOrderSource.Listener {
            override fun onQuestTypeOrderAdded(item: QuestType, toAfter: QuestType) {
                trySend(Event.Reload)
            }
            override fun onQuestTypeOrdersChanged() { trySend(Event.Reload) }
        }
        val overlayListener = object : SelectedOverlaySource.Listener {
            override fun onSelectedOverlayChanged() { trySend(Event.Reload) }
        }
        val settingsListeners = listOf(
            prefs.prefs.addStringListener(Prefs.DAY_NIGHT_BEHAVIOR, "IGNORE") { trySend(Event.Reload) },
            prefs.prefs.addBooleanListener(Prefs.QUEST_GEOMETRIES, false) { trySend(Event.Reload) },
        )
        visibleQuestsSource.addListener(questsListener)
        questTypeOrderSource.addListener(orderListener)
        selectedOverlaySource.addListener(overlayListener)
        trySend(Event.Reload)
        awaitClose {
            visibleQuestsSource.removeListener(questsListener)
            questTypeOrderSource.removeListener(orderListener)
            selectedOverlaySource.removeListener(overlayListener)
            settingsListeners.forEach { it.deactivate() }
        }
    }.buffer(Channel.UNLIMITED)

    private fun Quest.toCachedPins(orders: Map<QuestType, Int>): CachedPins {
        val color = type.dotColor
        val geometry = this.geometry.takeIf {
            color == null && it !is ElementPointGeometry && prefs.getBoolean(Prefs.QUEST_GEOMETRIES, false)
        }
        val label = if (color != null && this is OsmQuest) getLabel(this) else null
        val properties = key.toPinProperties() + listOfNotNull(label?.let { "label" to JsonPrimitive(it) })
        val pins = markerLocations.map {
            Pin(it, type.icon, properties, orders[type] ?: 0, geometry, color)
        }
        return CachedPins(type, pins)
    }

    private fun getLabel(quest: OsmQuest): String? {
        if (quest.type is ShowBusiness && selectedOverlaySource.selectedOverlay is PlacesOverlay) return null
        val sources = quest.type.dotLabelSources.ifEmpty { return null }
        val tags = mapDataSource.get(quest.elementType, quest.elementId)?.tags ?: return null
        return sources.firstNotNullOfOrNull { if (it == "label") getNameLabel(tags) else tags[it] }
    }

    fun onMapMoved(zoom: Double, displayedArea: BoundingBox?) {
        if (displayedArea == null) {
            displayedRect.value = null
            return
        }
        val longitude = (displayedArea.min.longitude + displayedArea.max.longitude) / 2 +
            if (displayedArea.crosses180thMeridian) 180.0 else 0.0
        isDaylight.value = isDay(LatLon(
            (displayedArea.min.latitude + displayedArea.max.latitude) / 2,
            normalizeLongitude(longitude),
        ))
        // Keep the loaded data when zooming out, including clusters at zoom 13–14.
        if (zoom < 14) return
        val rect = displayedArea.enclosingTilesRect(TILES_ZOOM)
        if (rect.size > 32) return
        if (displayedRect.value?.contains(rect) != true) displayedRect.value = rect
    }

    fun getQuestKey(properties: JsonObject): QuestKey? = properties.toQuestKey()

    companion object {
        private const val TILES_ZOOM = 16
    }
}

private data class CachedPins(val type: QuestType, val pins: List<Pin>)

private const val MARKER_QUEST_GROUP = "quest_group"

private const val MARKER_ELEMENT_TYPE = "element_type"
private const val MARKER_ELEMENT_ID = "element_id"
private const val MARKER_QUEST_TYPE = "quest_type"
private const val MARKER_NOTE_ID = "note_id"
private const val MARKER_OTHER_ID = "other_id"
private const val MARKER_OTHER_SOURCE = "other_source"

private const val QUEST_GROUP_OSM = "osm"
private const val QUEST_GROUP_OSM_NOTE = "osm_note"
private const val QUEST_GROUP_OTHER = "other"

internal fun QuestKey.toPinProperties(): List<Pair<String, JsonPrimitive>> = when (this) {
    is OsmNoteQuestKey -> listOf(
        MARKER_QUEST_GROUP to JsonPrimitive(QUEST_GROUP_OSM_NOTE),
        MARKER_NOTE_ID to JsonPrimitive(noteId)
    )
    is ExternalSourceQuestKey -> listOf(
        MARKER_QUEST_GROUP to JsonPrimitive(QUEST_GROUP_OTHER),
        MARKER_OTHER_ID to JsonPrimitive(id),
        MARKER_OTHER_SOURCE to JsonPrimitive(source),
    )
    is OsmQuestKey -> listOf(
        MARKER_QUEST_GROUP to JsonPrimitive(QUEST_GROUP_OSM),
        MARKER_ELEMENT_TYPE to JsonPrimitive(elementType.name),
        MARKER_ELEMENT_ID to JsonPrimitive(elementId),
        MARKER_QUEST_TYPE to JsonPrimitive(questTypeName)
    )
}

internal fun JsonObject.toQuestKey(): QuestKey? {
    val questGroup = get(MARKER_QUEST_GROUP)?.jsonPrimitive?.contentOrNull
    return when (questGroup) {
        QUEST_GROUP_OTHER -> ExternalSourceQuestKey(
            getValue(MARKER_OTHER_ID).jsonPrimitive.content,
            getValue(MARKER_OTHER_SOURCE).jsonPrimitive.content,
        )
        QUEST_GROUP_OSM_NOTE ->
            OsmNoteQuestKey(getValue(MARKER_NOTE_ID).jsonPrimitive.long)
        QUEST_GROUP_OSM ->
            OsmQuestKey(
                ElementType.valueOf(getValue(MARKER_ELEMENT_TYPE).jsonPrimitive.content),
                getValue(MARKER_ELEMENT_ID).jsonPrimitive.long,
                getValue(MARKER_QUEST_TYPE).jsonPrimitive.content
            )
        else -> null
    }
}
