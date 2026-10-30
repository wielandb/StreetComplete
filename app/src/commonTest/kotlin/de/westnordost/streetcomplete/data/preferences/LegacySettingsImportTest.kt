package de.westnordost.streetcomplete.data.preferences

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LegacySettingsImportTest {
    @Test fun legacyAndroidCameraValuesBecomePortableDoubles() {
        val expected = mapOf("map.latitude" to -33.9, "map.longitude" to 151.2, "map.rotation2" to 45.0, "map.tilt2" to 30.0, "map.zoom2" to 18.5)
        val settings = SnapshotSettings(MapSettings())
        importLegacyLongSettings(expected.mapValues { it.value.toRawBits() }, settings)
        assertEquals(expected, settings.all)
        for ((key, value) in expected) assertEquals(value, settings.getDoubleOrNull(key))
    }

    @Test fun timestampsAndOtherLongSettingsKeepTheirTypeAndValue() {
        val expected = mapOf("last_download" to 1_793_300_000_000L, "osm_user_id" to 123456789L)
        val settings = SnapshotSettings(MapSettings())
        importLegacyLongSettings(expected, settings)
        assertEquals(expected, settings.all)
        for ((key, value) in expected) assertEquals(value, settings.getLongOrNull(key))
    }

    @Test fun corruptCameraValueDoesNotPartiallyImportTheBatch() {
        val settings = SnapshotSettings(MapSettings())
        settings.putString("existing", "preserved")
        assertFailsWith<IllegalArgumentException> {
            importLegacyLongSettings(mapOf("other" to 123L, "map.latitude" to Double.NaN.toRawBits()), settings)
        }
        assertEquals(mapOf("existing" to "preserved"), settings.all)
    }
}
