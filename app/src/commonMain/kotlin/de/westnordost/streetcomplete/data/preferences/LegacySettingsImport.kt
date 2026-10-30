package de.westnordost.streetcomplete.data.preferences

import com.russhwolf.settings.ObservableSettings

/** SharedPreferencesSettings encoded these historical Double values as raw Long bits. */
private val legacyDoubleKeys = setOf("map.latitude", "map.longitude", "map.rotation2", "map.tilt2", "map.zoom2")

internal fun importLegacyLongSettings(values: Map<String, Long>, settings: ObservableSettings) {
    val doubles = values.filterKeys { it in legacyDoubleKeys }.mapValues { (key, value) ->
        Double.fromBits(value).also { require(it.isFinite()) { "Invalid legacy camera setting: $key" } }
    }
    // Validate before changing anything. Ordinary longs, such as timestamps, stay longs.
    for ((key, value) in values) if (key !in legacyDoubleKeys) settings.putLong(key, value)
    for ((key, value) in doubles) settings.putDouble(key, value)
}
