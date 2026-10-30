package de.westnordost.streetcomplete.util

import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.util.ktx.nowAsEpochMilliseconds
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/** SCEE's daylight filters include civil twilight, with the Sun at least -6 degrees high. */
fun isDay(pos: LatLon): Boolean = isDayAt(pos, nowAsEpochMilliseconds())

/**
 * Uses UTC and solar altitude directly, including at the poles and across the date line.
 * Solar coordinates and sidereal time follow the US Naval Observatory's equations:
 * https://aa.usno.navy.mil/faq/sun_approx and https://aa.usno.navy.mil/faq/GAST .
 */
internal fun isDayAt(pos: LatLon, epochMilliseconds: Long): Boolean {
    val days = (epochMilliseconds - 946_728_000_000L) / 86_400_000.0
    val radians = PI / 180.0
    val anomaly = ((357.529 + 0.98560028 * days) % 360.0) * radians
    val meanLongitude = ((280.459 + 0.98564736 * days) % 360.0) * radians
    val solarLongitude = meanLongitude +
        (1.915 * sin(anomaly) + 0.020 * sin(2.0 * anomaly)) * radians
    val obliquity = (23.439 - 0.00000036 * days) * radians
    val rightAscension = atan2(cos(obliquity) * sin(solarLongitude), cos(solarLongitude))
    val sinDeclination = sin(obliquity) * sin(solarLongitude)
    val cosDeclination = sqrt(1.0 - sinDeclination * sinDeclination)

    val midnightDays = floor(days + 0.5) - 0.5
    val hoursUtc = (days - midnightDays) * 24.0
    val centuries = days / 36_525.0
    val siderealHours = (6.697375 + 0.065709824279 * midnightDays +
        1.0027379 * hoursUtc + 0.0000258 * centuries * centuries) % 24.0
    val hourAngle = (siderealHours * 15.0 + pos.longitude) * radians - rightAscension
    val latitude = pos.latitude * radians
    val sinAltitude = sin(latitude) * sinDeclination +
        cos(latitude) * cosDeclination * cos(hourAngle)
    return sinAltitude >= sin(-6.0 * radians)
}
