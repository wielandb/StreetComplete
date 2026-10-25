package de.westnordost.streetcomplete.quests.charge

import de.westnordost.streetcomplete.osm.duration.DurationUnit.MINUTES
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ChargeEditorStateSerializationTest {
    @Test fun restoresRowsModesAndNextId() {
        val state = ChargeEditorState(
            rows = listOf(ChargeRow(0, 0.5, 15, MINUTES))
        ).addRow().addRow().removeRow(1)
        val restored = Json.decodeFromString<ChargeEditorState>(Json.encodeToString(state))
        assertEquals(state, restored)
        assertEquals(listOf(0, 2, 3), restored.addRow().rows.map { it.id })
    }

    @Test fun restoresIncompleteInputWithoutLosingTheSelectedUnit() {
        val state = ChargeEditorState(rows = listOf(ChargeRow(0, null, null, MINUTES)))
        assertEquals(state, Json.decodeFromString<ChargeEditorState>(Json.encodeToString(state)))
    }
}
