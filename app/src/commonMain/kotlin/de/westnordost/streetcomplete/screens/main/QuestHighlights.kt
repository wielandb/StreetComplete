package de.westnordost.streetcomplete.screens.main

import de.westnordost.streetcomplete.data.osm.mapdata.ElementKey
import de.westnordost.streetcomplete.data.osm.osmquests.OsmQuest
import de.westnordost.streetcomplete.data.quest.Quest
import de.westnordost.streetcomplete.ui.common.quest.Marker

data class SheetHighlights(
    val markers: List<Marker> = emptyList(),
    val nearbyQuests: List<Pair<Int, List<Quest>>> = emptyList(),
)

/** Retain an element's icon, title and direction when a nearby quest supplies its color. */
internal fun mergeHighlightMarkers(elements: List<Marker>, quests: List<Marker>): List<Marker> {
    val byPosition = elements.associateByTo(linkedMapOf()) { it.geometry.center }
    for (marker in quests) {
        val position = marker.geometry.center
        byPosition[position] = byPosition[position]?.copy(color = marker.color) ?: marker
    }
    return byPosition.values.toList()
}

/** Group OSM quests by element, keeping distinct note/external quest keys distinct as well. */
internal fun nearbyQuestHighlights(selected: Quest, nearby: Collection<Quest>): SheetHighlights {
    fun Quest.groupKey(): Any = if (this is OsmQuest) ElementKey(elementType, elementId) else key
    val selectedGroup = selected.groupKey()
    val groups = nearby
        .filter { it.key != selected.key && it.type.dotColor == null }
        .sortedBy { it.groupKey() != selectedGroup }
        .groupBy { it.groupKey() }
    val colors = intArrayOf(
        0xff00ff00.toInt(), 0xffffff00.toInt(), 0xff00ffff.toInt(),
        0xffff00ff.toInt(), 0xff0000ff.toInt(), 0xffff8000.toInt()
    )
    val markers = mutableListOf<Marker>()
    var nextColor = 0
    val coloredGroups = groups.map { (key, quests) ->
        val color = if (key == selectedGroup) 0xffffffff.toInt() else {
            colors[nextColor++ % colors.size].also {
                markers.add(Marker(quests.first().geometry, color = it))
            }
        }
        color to quests
    }
    return SheetHighlights(markers, coloredGroups)
}
