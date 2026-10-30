package de.westnordost.streetcomplete.screens.main

import de.westnordost.streetcomplete.data.osm.geometry.ElementPointGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.ElementType
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.osm.osmquests.OsmElementQuestType
import de.westnordost.streetcomplete.data.osm.osmquests.OsmQuest
import de.westnordost.streetcomplete.data.osmnotes.notequests.OsmNoteQuest
import de.westnordost.streetcomplete.data.quest.TestQuestTypeA
import de.westnordost.streetcomplete.data.quest.TestQuestTypeB
import de.westnordost.streetcomplete.ui.common.quest.Marker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class QuestHighlightsTest {
    @Test fun `other questions on selected element come first without an extra marker`() {
        val selected = quest(1)
        val sameElement = quest(1, TestQuestTypeB())
        val otherElement = quest(2)
        val dots = quest(3, DotQuestType())

        val result = nearbyQuestHighlights(selected, listOf(otherElement, dots, sameElement, selected))

        assertEquals(listOf(listOf(sameElement), listOf(otherElement)), result.nearbyQuests.map { it.second })
        assertEquals(0xffffffff.toInt(), result.nearbyQuests.first().first)
        assertEquals(listOf(otherElement.geometry), result.markers.map { it.geometry })
        assertEquals(result.nearbyQuests.last().first, result.markers.single().color)
    }

    @Test fun `different note keys with the same hash remain separate groups`() {
        val a = OsmNoteQuest(1L, LatLon(1.0, 1.0))
        val b = OsmNoteQuest(1L shl 32, LatLon(2.0, 2.0))
        assertEquals(a.key.hashCode(), b.key.hashCode())

        val result = nearbyQuestHighlights(quest(1), listOf(a, b))

        assertEquals(listOf(listOf(a), listOf(b)), result.nearbyQuests.map { it.second })
        assertEquals(2, result.markers.size)
        assertNotEquals(result.nearbyQuests[0].first, result.nearbyQuests[1].first)
    }

    @Test fun `colors cycle without merging distinct elements`() {
        val nearby = (2L..9L).map { quest(it) }
        val result = nearbyQuestHighlights(quest(1), nearby)
        assertEquals(8, result.nearbyQuests.size)
        assertEquals(8, result.markers.size)
        assertEquals(result.nearbyQuests[0].first, result.nearbyQuests[6].first)
    }

    @Test fun `quest color preserves highlighted element title and direction`() {
        val geometry = ElementPointGeometry(LatLon(1.0, 1.0))
        val element = Marker(geometry, title = "Tree", rotation = 15.0, direction = 90.0)
        val quest = Marker(geometry, color = 0xff00ff00.toInt())
        val other = Marker(ElementPointGeometry(LatLon(2.0, 2.0)), color = 0xffffff00.toInt())

        assertEquals(
            listOf(element.copy(color = quest.color), other),
            mergeHighlightMarkers(listOf(element), listOf(quest, other))
        )
    }

    private fun quest(id: Long, type: OsmElementQuestType<*> = TestQuestTypeA()) =
        OsmQuest(type, ElementType.NODE, id, ElementPointGeometry(LatLon(id.toDouble(), 0.0)))

    private class DotQuestType : TestQuestTypeA() {
        override val dotColor = "#ff0000"
    }
}
