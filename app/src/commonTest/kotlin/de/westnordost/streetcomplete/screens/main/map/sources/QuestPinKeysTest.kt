package de.westnordost.streetcomplete.screens.main.map.sources

import de.westnordost.streetcomplete.data.osm.mapdata.ElementType
import de.westnordost.streetcomplete.data.quest.ExternalSourceQuestKey
import de.westnordost.streetcomplete.data.quest.OsmNoteQuestKey
import de.westnordost.streetcomplete.data.quest.OsmQuestKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QuestPinKeysTest {
    @Test fun `all quest groups survive conversion to map properties and back`() {
        val keys = listOf(
            OsmQuestKey(ElementType.WAY, Long.MAX_VALUE, "AddRoadWidth"),
            OsmNoteQuestKey(4294967296L),
            ExternalSourceQuestKey("custom,42/ä", "notes"),
            ExternalSourceQuestKey("123-456", "osmose"),
        )
        for (key in keys) {
            val properties = JsonObject(key.toPinProperties().toMap())
            val fromMap = Json.parseToJsonElement(properties.toString()).jsonObject
            assertEquals(key, fromMap.toQuestKey())
        }
    }

    @Test fun `non-quest map features have no quest key`() {
        assertNull(JsonObject(mapOf("point_count" to JsonPrimitive(3))).toQuestKey())
    }
}
