package de.westnordost.streetcomplete.screens.main.map.sources

import de.westnordost.streetcomplete.data.externalsource.ExternalSourceQuestHidden
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.quests.custom.CustomQuest
import de.westnordost.streetcomplete.quests.custom.CustomQuestList
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class EditHistoryPinsSourceTest {
    @Test fun hiddenExternalQuestSurvivesPinRoundTrip() {
        // Avoid mocking the Compose settings method; only real type metadata is needed here.
        val type = CustomQuest(CustomQuestList())
        val hidden = ExternalSourceQuestHidden("issue-123", type, LatLon(51.0, 12.0), 1000)
        val pin = hidden.toEditPin(3)
        assertEquals(hidden.position, pin.position)
        assertEquals(hidden.key, JsonObject(pin.properties.toMap()).toEditKey())
    }
}
