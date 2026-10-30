package de.westnordost.streetcomplete.util.locale

import kotlin.test.Test
import kotlin.test.assertEquals

class FeatureSearchLanguagesTest {
    @Test fun `disabled extension retains upstream language order and fallback`() {
        val languages = listOf("de-AT", "it", "en", null)
        assertEquals(languages, featureSearchLanguages(languages, listOf("fr"), false))
    }

    @Test fun `local languages extend search without replacing app or system language priority`() {
        assertEquals(
            listOf("de-AT", "it", "en", "fr", "de", null),
            featureSearchLanguages(listOf("de-AT", "it", "en", null), listOf("fr", "de"), true),
        )
    }

    @Test fun `duplicates and empty country languages do not add invalid fallbacks`() {
        assertEquals(
            listOf("fr", "en", "de", null),
            featureSearchLanguages(listOf("fr", "en", null), listOf("fr", "", "  ", "de", "en"), true),
        )
    }
}
