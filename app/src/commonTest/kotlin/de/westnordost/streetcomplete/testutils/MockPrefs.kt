package de.westnordost.streetcomplete.testutils

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.ObservableSettings
import de.westnordost.streetcomplete.data.preferences.Preferences

/** Fresh settings with real getters, setters and listeners for tests on every platform. */
fun mockPrefs2(): ObservableSettings = MapSettings()

fun mockPrefs3() = Preferences(mockPrefs2())
