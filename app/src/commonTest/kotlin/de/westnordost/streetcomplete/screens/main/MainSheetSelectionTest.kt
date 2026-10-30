package de.westnordost.streetcomplete.screens.main

import de.westnordost.streetcomplete.data.quest.ExternalSourceQuestKey
import de.westnordost.streetcomplete.testutils.p
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class MainSheetSelectionTest {
    @Test fun `SCEE selections survive saved state serialization`() {
        val selections = listOf(
            MainSheetSelection.EditHistory(null),
            MainSheetSelection.Quest(ExternalSourceQuestKey("123", "osmose")),
            MainSheetSelection.AddPoi(p(51.0, 13.0), "amenity/post_box"),
            MainSheetSelection.InsertNode(p(51.0, 13.0)),
        )
        for (selection in selections) {
            val serialized = Json.encodeToString<MainSheetSelection>(selection)
            assertEquals(selection, Json.decodeFromString<MainSheetSelection>(serialized))
        }
    }
}
