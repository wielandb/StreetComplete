package de.westnordost.streetcomplete.util

import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class CheckIfDayTest {
    private val berlin = LatLon(52.52, 13.405)

    @Test fun `day and night at a temperate location`() {
        assertTrue(isDayAt(berlin, at("2026-06-21T12:00:00Z")))
        assertFalse(isDayAt(berlin, at("2026-06-21T00:00:00Z")))
    }

    @Test fun `civil twilight counts as daylight before sunrise`() {
        assertTrue(isDayAt(berlin, at("2026-12-21T06:50:00Z")))
        assertFalse(isDayAt(berlin, at("2026-12-21T06:00:00Z")))
    }

    @Test fun `use the actual month rather than the following calendar month`() {
        assertTrue(isDayAt(berlin, at("2026-07-31T19:00:00Z")))
        assertFalse(isDayAt(berlin, at("2026-08-31T19:00:00Z")))
    }

    @Test fun `polar summer remains daylight at midnight`() {
        val arctic = LatLon(80.0, 15.0)
        assertTrue(isDayAt(arctic, at("2026-06-21T00:00:00Z")))
        assertTrue(isDayAt(arctic, at("2026-06-21T12:00:00Z")))
    }

    @Test fun `polar winter remains dark at noon`() {
        val arctic = LatLon(80.0, 15.0)
        assertFalse(isDayAt(arctic, at("2026-12-21T00:00:00Z")))
        assertFalse(isDayAt(arctic, at("2026-12-21T12:00:00Z")))
    }

    @Test fun `both sides of the date line use local solar time`() {
        for (longitude in listOf(-180.0, 180.0)) {
            assertTrue(isDayAt(LatLon(0.0, longitude), at("2026-03-20T00:00:00Z")))
            assertFalse(isDayAt(LatLon(0.0, longitude), at("2026-03-20T12:00:00Z")))
        }
    }

    @Test fun `UTC midnight does not interrupt daylight near the date line`() {
        val position = LatLon(0.0, 170.0)
        assertTrue(isDayAt(position, at("2026-03-20T23:59:59Z")))
        assertTrue(isDayAt(position, at("2026-03-21T00:00:01Z")))
    }

    private fun at(time: String): Long = Instant.parse(time).toEpochMilliseconds()
}
