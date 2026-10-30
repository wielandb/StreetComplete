package de.westnordost.streetcomplete.quests.sidewalk

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.data.osm.geometry.ElementPolylinesGeometry
import de.westnordost.streetcomplete.testutils.TestMapDataWithGeometry
import de.westnordost.streetcomplete.testutils.p
import de.westnordost.streetcomplete.testutils.way
import de.westnordost.streetcomplete.testutils.mockPrefs3
import de.westnordost.streetcomplete.util.math.translate
import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AddSidewalkTest {

    private val questType = AddSidewalk()

    @BeforeTest fun setUp() {
        Prefs.preferences = mockPrefs3()
    }

    @Test fun `custom highway selection excludes unselected untagged roads`() {
        Prefs.preferences.putString("qs_AddSidewalk_highway_selection", "primary")
        val road = way(tags = mapOf("highway" to "residential"))
        assertEquals(false, questType.isApplicableTo(road))
        assertTrue(questType.getApplicableElements(TestMapDataWithGeometry(listOf(road))).none())
    }

    @Test fun `custom highway selection includes selected service roads`() {
        Prefs.preferences.putString("qs_AddSidewalk_highway_selection", "service")
        val road = way(tags = mapOf("highway" to "service", "lit" to "yes"))
        assertTrue(questType.isApplicableTo(road))
        assertEquals(listOf(road), questType.getApplicableElements(TestMapDataWithGeometry(listOf(road))).toList())
    }

    @Test fun `custom highway selection uses selected preset`() {
        Prefs.preferences.putBoolean(Prefs.QUEST_SETTINGS_PER_PRESET, true)
        Prefs.preferences.putLong(Preferences.SELECTED_EDIT_TYPE_PRESET, 7)
        Prefs.preferences.putString("qs_AddSidewalk_highway_selection", "residential")
        Prefs.preferences.putString("7_qs_AddSidewalk_highway_selection", "service")
        assertTrue(questType.isApplicableTo(way(tags = mapOf("highway" to "service", "lit" to "yes"))))
        assertEquals(false, questType.isApplicableTo(way(tags = mapOf("highway" to "residential"))))
    }

    @Test fun `not applicable to road with sidewalk`() {
        val road = way(tags = mapOf(
            "highway" to "primary",
            "sidewalk" to "both"
        ))
        val mapData = TestMapDataWithGeometry(listOf(road))
        assertEquals(0, questType.getApplicableElements(mapData).toList().size)
        assertEquals(false, questType.isApplicableTo(road))
    }

    @Test fun `applicable to road with missing sidewalk`() {
        val road = way(tags = mapOf(
            "highway" to "primary",
            "lit" to "yes"
        ))
        val mapData = TestMapDataWithGeometry(listOf(road))
        assertEquals(1, questType.getApplicableElements(mapData).toList().size)
        assertTrue(questType.isApplicableTo(road))
    }

    @Test fun `applicable to road with incomplete sidewalk tagging`() {
        val road = way(tags = mapOf(
            "highway" to "residential",
            "sidewalk:left" to "yes"
        ))
        val mapData = TestMapDataWithGeometry(listOf(road))
        assertEquals(1, questType.getApplicableElements(mapData).toList().size)
        assertTrue(questType.isApplicableTo(road))
    }

    @Test fun `applicable to road with invalid sidewalk tagging`() {
        val road = way(tags = mapOf(
            "highway" to "residential",
            "sidewalk" to "something"
        ))
        val footway = way(2, listOf(3, 4), mapOf(
            "highway" to "footway"
        ))
        val mapData = TestMapDataWithGeometry(listOf(road, footway))
        val p1 = p(0.0, 0.0)
        val p2 = p1.translate(50.0, 45.0)
        val p3 = p1.translate(13.0, 135.0)
        val p4 = p3.translate(50.0, 45.0)

        mapData.wayGeometriesById[1L] = ElementPolylinesGeometry(listOf(listOf(p1, p2)), p1)
        mapData.wayGeometriesById[2L] = ElementPolylinesGeometry(listOf(listOf(p3, p4)), p3)

        assertEquals(1, questType.getApplicableElements(mapData).toList().size)
        assertTrue(questType.isApplicableTo(road))
    }

    @Test fun `applicable to road with overloaded sidewalk tagging`() {
        val road = way(tags = mapOf(
            "highway" to "residential",
            "sidewalk" to "left",
            "sidewalk:right" to "yes"
        ))
        val footway = way(2, listOf(3, 4), mapOf(
            "highway" to "footway"
        ))
        val mapData = TestMapDataWithGeometry(listOf(road, footway))
        val p1 = p(0.0, 0.0)
        val p2 = p1.translate(50.0, 45.0)
        val p3 = p1.translate(13.0, 135.0)
        val p4 = p3.translate(50.0, 45.0)

        mapData.wayGeometriesById[1L] = ElementPolylinesGeometry(listOf(listOf(p1, p2)), p1)
        mapData.wayGeometriesById[2L] = ElementPolylinesGeometry(listOf(listOf(p3, p4)), p3)

        assertEquals(1, questType.getApplicableElements(mapData).toList().size)
        assertTrue(questType.isApplicableTo(road))
    }

    @Test fun `not applicable to motorways`() {
        val road = way(tags = mapOf(
            "highway" to "motorway",
        ))
        val mapData = TestMapDataWithGeometry(listOf(road))
        assertEquals(0, questType.getApplicableElements(mapData).toList().size)
        assertEquals(false, questType.isApplicableTo(road))
    }

    @Test fun `applicable to motorways marked as legally accessible to pedestrians`() {
        val road = way(tags = mapOf(
            "highway" to "motorway",
            "foot" to "yes"
        ))
        val mapData = TestMapDataWithGeometry(listOf(road))
        assertEquals(1, questType.getApplicableElements(mapData).toList().size)
        assertTrue(questType.isApplicableTo(road))
    }

    @Test fun `applicable to motorways marked as legally accessible to pedestrians and with tagged speed limit`() {
        val road = way(tags = mapOf(
            "highway" to "motorway",
            "foot" to "yes",
            "maxspeed" to "65 mph",
        ))
        val mapData = TestMapDataWithGeometry(listOf(road))
        assertEquals(1, questType.getApplicableElements(mapData).toList().size)
        assertTrue(questType.isApplicableTo(road))
    }

    @Test fun `not applicable to road with very low speed limit`() {
        val road = way(tags = mapOf(
            "highway" to "residential",
            "maxspeed" to "9",
        ))
        val mapData = TestMapDataWithGeometry(listOf(road))
        assertEquals(0, questType.getApplicableElements(mapData).toList().size)
        assertEquals(false, questType.isApplicableTo(road))
    }

    @Test fun `applicable to road with implicit speed limit`() {
        val road = way(tags = mapOf(
            "highway" to "primary",
            "maxspeed" to "DE:zone30",
        ))
        val mapData = TestMapDataWithGeometry(listOf(road))
        assertEquals(1, questType.getApplicableElements(mapData).toList().size)
        assertTrue(questType.isApplicableTo(road))
    }

    @Test fun `applicable to road with urban speed limit`() {
        val road = way(tags = mapOf(
            "highway" to "primary",
            "maxspeed" to "60",
        ))
        val mapData = TestMapDataWithGeometry(listOf(road))
        assertEquals(1, questType.getApplicableElements(mapData).toList().size)
        assertTrue(questType.isApplicableTo(road))
    }
}
