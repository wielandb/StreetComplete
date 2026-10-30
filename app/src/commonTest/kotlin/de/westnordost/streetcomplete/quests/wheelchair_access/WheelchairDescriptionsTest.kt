package de.westnordost.streetcomplete.quests.wheelchair_access

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.osm.osmquests.OsmElementQuestType
import de.westnordost.streetcomplete.quests.answerAppliedTo
import de.westnordost.streetcomplete.quests.wheelchair_access.WheelchairAccess.YES
import de.westnordost.streetcomplete.testutils.mockPrefs3
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WheelchairDescriptionsTest {
    private val quests: List<OsmElementQuestType<WheelchairAccessAnswer>> = listOf(
        AddWheelchairAccessBusiness(),
        AddWheelchairAccessOutside(),
        AddWheelchairAccessPublicTransport(),
        AddWheelchairAccessToilets(),
    )

    @BeforeTest fun setUp() {
        Prefs.preferences = mockPrefs3()
    }

    @Test fun `description edits preserve unedited languages and other tags`() {
        val original = mapOf(
            "wheelchair:description:de" to "Alte Beschreibung",
            "wheelchair:description:fr" to "Description conservée",
            "name" to "Example",
        )
        val answer = WheelchairAccessAnswer(YES, mapOf(
            ":de" to "Ebener Zugang",
            ":en" to "Level entrance",
            "" to "Accessible entrance",
        ))
        for (quest in quests) {
            val result = apply(quest, answer, original)
            assertEquals("yes", result["wheelchair"])
            assertEquals("Ebener Zugang", result["wheelchair:description:de"])
            assertEquals("Level entrance", result["wheelchair:description:en"])
            assertEquals("Accessible entrance", result["wheelchair:description"])
            assertEquals(original["wheelchair:description:fr"], result["wheelchair:description:fr"])
            assertEquals("Example", result["name"])
        }
    }

    @Test fun `blank descriptions remove only the edited language tags`() {
        val original = mapOf(
            "wheelchair:description" to "Remove this",
            "wheelchair:description:de" to "Auch entfernen",
            "wheelchair:description:fr" to "Garder ceci",
        )
        for (quest in quests) {
            val result = apply(quest, WheelchairAccessAnswer(YES, mapOf("" to "", ":de" to "  ")), original)
            assertEquals(null, result["wheelchair:description"])
            assertEquals(null, result["wheelchair:description:de"])
            assertEquals("Garder ceci", result["wheelchair:description:fr"])
        }
    }

    @Test fun `a later answer does not inherit another elements descriptions`() {
        apply(quests.first(), WheelchairAccessAnswer(YES, mapOf(":de" to "First element")), emptyMap())
        val original = mapOf("wheelchair:description:de" to "Second element")
        for (quest in quests) {
            val result = apply(quest, WheelchairAccessAnswer(YES), original)
            assertEquals("Second element", result["wheelchair:description:de"])
        }
    }

    @Test fun `toilets inside a place do not inherit another answers description`() {
        apply(quests.first(), WheelchairAccessAnswer(YES, mapOf(":de" to "Different place")), emptyMap())
        val original = mapOf("wheelchair:description:de" to "This place")
        val result = apply(AddWheelchairAccessToiletsPart(), WheelchairAccessToiletsPart(YES), original)
        assertEquals("This place", result["wheelchair:description:de"])
        assertEquals("yes", result["toilets:wheelchair"])
    }

    private fun <T> apply(quest: OsmElementQuestType<T>, answer: T, original: Map<String, String>): Map<String, String> {
        val result = original.toMutableMap()
        quest.answerAppliedTo(answer, original).forEach { it.applyTo(result) }
        return result
    }
}
