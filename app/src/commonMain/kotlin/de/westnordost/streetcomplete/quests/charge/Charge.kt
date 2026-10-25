package de.westnordost.streetcomplete.quests.charge

import de.westnordost.streetcomplete.osm.duration.DurationUnit

internal const val MAX_CHARGE_TAG_LENGTH = 255
internal const val MAX_CHARGE_DURATION = 9999

/** One independently available tariff, not a step in a cumulative pricing scheme. */
data class Charge(
    val amount: String,
    val currency: String,
    val timeUnit: DurationUnit,
    val timeValue: Int = 1,
) {
    init {
        require(amount.matches(Regex("[0-9]+(?:\\.[0-9]+)?")))
        require(amount.toDoubleOrNull()?.let { it.isFinite() && it > 0.0 } == true)
        require(currency.matches(Regex("[A-Z]{3}")))
        require(timeValue in 1..MAX_CHARGE_DURATION)
    }

    fun toOsmValue(): String {
        val duration = if (timeValue == 1) {
            timeUnit.toOsmValue(false)
        } else {
            "$timeValue ${timeUnit.toOsmValue(true)}"
        }
        return "$amount $currency/$duration"
    }
}

/** Alternatives such as an hourly ticket and a day ticket. Conditions are deliberately excluded. */
data class ParkingCharges(val charges: List<Charge>) {
    init {
        require(charges.isNotEmpty())
    }

    private val value: String get() = charges.joinToString(";") { it.toOsmValue() }

    val isTooLong: Boolean get() = value.length > MAX_CHARGE_TAG_LENGTH

    fun toOsmValue(): String {
        val result = value
        require(result.length <= MAX_CHARGE_TAG_LENGTH) { "Parking charge exceeds the OSM tag limit" }
        return result
    }

    companion object {
        private val tariffPattern = Regex(
            """([0-9]+(?:\.[0-9]+)?)\s+([A-Z]{3})\s*/\s*(?:([0-9]+)\s+)?(minutes?|hours?|days?)"""
        )

        /** All-or-nothing parsing: never silently discard an unsupported tariff. */
        fun parse(value: String): ParkingCharges? {
            if (value.length > MAX_CHARGE_TAG_LENGTH) return null
            val charges = value.split(';').map { part ->
                val match = tariffPattern.matchEntire(part.trim()) ?: return null
                val (amount, currency, count, unit) = match.destructured
                val timeValue = if (count.isEmpty()) 1 else count.toIntOrNull() ?: return null
                val timeUnit = when (unit.removeSuffix("s")) {
                    "minute" -> DurationUnit.MINUTES
                    "hour" -> DurationUnit.HOURS
                    "day" -> DurationUnit.DAYS
                    else -> return null
                }
                runCatching { Charge(amount, currency, timeUnit, timeValue) }.getOrNull()
                    ?: return null
            }
            return ParkingCharges(charges)
        }
    }
}
