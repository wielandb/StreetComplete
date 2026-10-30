package de.westnordost.streetcomplete.data.preferences

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SnapshotSettingsTest {
    @Test fun `snapshot keeps types and values after reopening storage`() {
        val storage = MapSettings()
        val settings = SnapshotSettings(storage)
        settings.putBoolean("enabled", true)
        settings.putInt("count", 12)
        settings.putLong("id", 4_000_000_000L)
        settings.putFloat("radius", 1.5f)
        settings.putDouble("latitude", 52.123456789)
        settings.putString("filter", "nodes with amenity=cafe")

        assertEquals(mapOf(
            "enabled" to true, "count" to 12, "id" to 4_000_000_000L,
            "radius" to 1.5f, "latitude" to 52.123456789,
            "filter" to "nodes with amenity=cafe",
        ), SnapshotSettings(storage).all)
        assertEquals(settings.all.keys, settings.keys)
        assertEquals(6, settings.size)
    }

    @Test fun `existing Android settings remain available without being read first`() {
        val storage = MapSettings()
        storage.putString("12_qs_AddShopType_filter", "nodes with shop")
        val settings = SnapshotSettings(storage) {
            mapOf("12_qs_AddShopType_filter" to storage.getStringOrNull("12_qs_AddShopType_filter"))
        }
        assertEquals("nodes with shop", settings.all["12_qs_AddShopType_filter"])
    }

    @Test fun `reading an old numeric preference records its logical type`() {
        val storage = MapSettings()
        storage.putDouble("map.latitude", 51.25)
        val settings = SnapshotSettings(storage) { mapOf("map.latitude" to 51.25.toRawBits()) }
        assertEquals(51.25, settings.getDouble("map.latitude", 0.0))
        assertEquals(51.25, settings.all["map.latitude"])
        assertEquals(51.25, SnapshotSettings(storage).all["map.latitude"])
    }

    @Test fun `listener handles new custom overlay keys and can be removed`() {
        val settings = SnapshotSettings(MapSettings())
        val changes = mutableListOf<String>()
        val listener = settings.onChange { changes += it }
        settings.putString("custom_overlay_4_name", "Trees")
        settings.putInt("custom_overlay_selected", 4)
        settings.remove("custom_overlay_4_name")
        listener.deactivate()
        settings.putString("custom_overlay_5_name", "Benches")
        assertEquals(listOf("custom_overlay_4_name", "custom_overlay_selected", "custom_overlay_4_name"), changes)
        assertFalse("custom_overlay_4_name" in settings.all)
    }

    @Test fun `removing and clearing values also removes snapshot entries`() {
        val storage = MapSettings()
        val settings = SnapshotSettings(storage)
        settings.putInt("first", 1)
        settings.putInt("second", 2)
        settings.remove("first")
        assertEquals(mapOf("second" to 2), SnapshotSettings(storage).all)
        settings.clear()
        assertTrue(SnapshotSettings(storage).all.isEmpty())
        assertTrue(settings.keys.isEmpty())
    }
}
