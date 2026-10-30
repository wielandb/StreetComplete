package de.westnordost.streetcomplete.quests

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuestSettingsDiffTest {
    @Test fun `unchanged lines are omitted and both sides of a change are marked`() {
        val diff = createQuestFilterDiff("nodes with\n  amenity = bench", "nodes with\n  amenity = shelter")
        assertFalse(diff.contains("nodes with"))
        assertTrue(diff.contains("<del>bench</del>"))
        assertTrue(diff.contains("<ins>shelter</ins>"))
    }

    @Test fun `whitespace-only changes are ignored`() {
        assertEquals("", createQuestFilterDiff("nodes with amenity = bench", "nodes  with  amenity = bench"))
    }

    @Test fun `filter text is escaped before being rendered as HTML`() {
        val diff = createQuestFilterDiff("nodes with name = a", "nodes with name = <script>")
        assertFalse(diff.contains("<script>"))
        assertTrue(diff.contains("&lt;script&gt;"))
    }
}