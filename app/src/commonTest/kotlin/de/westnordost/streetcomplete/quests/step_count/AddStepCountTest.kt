package de.westnordost.streetcomplete.quests.step_count

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.osm.geometry.ElementPolylinesGeometry
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.testutils.createMapData
import de.westnordost.streetcomplete.testutils.mockPrefs2
import de.westnordost.streetcomplete.testutils.p
import de.westnordost.streetcomplete.testutils.way
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AddStepCountTest {
    private val questType = AddStepCount()

    @Test fun `flat steps still need a step count`() {
        assertNull(questType.isApplicableTo(way(tags = mapOf("highway" to "steps", "flat_steps" to "yes"))))
    }

    @Test fun `does not ask when another step count is already recorded`() {
        for (countKey in listOf("step_count", "steps")) {
            assertEquals(false, questType.isApplicableTo(way(tags = mapOf("highway" to "steps", countKey to "12"))))
        }
    }

    @Test fun `preserves configured maximum length with the upstream filter`() {
        val settings = mockPrefs2()
        settings.putInt("qs_AddStepCount_max_length", 50)
        Prefs.preferences = Preferences(settings)
        val shortSteps = way(1, tags = mapOf("highway" to "steps", "flat_steps" to "yes"))
        val longSteps = way(2, tags = mapOf("highway" to "steps"))
        val mapData = createMapData(mapOf(
            shortSteps to ElementPolylinesGeometry(listOf(listOf(p(0.0, 0.0), p(0.0001, 0.0))), p(0.00005, 0.0)),
            longSteps to ElementPolylinesGeometry(listOf(listOf(p(0.0, 0.0), p(0.001, 0.0))), p(0.0005, 0.0))
        ))

        assertEquals(listOf(shortSteps), questType.getApplicableElements(mapData).toList())
    }
}
