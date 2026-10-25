package de.westnordost.streetcomplete.quests.charge

import de.westnordost.streetcomplete.osm.duration.DurationUnit.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParkingChargesTest {
    @Test fun keepsSingleRateFormat() {
        assertEquals("1.50 EUR/hour", Charge("1.50", "EUR", HOURS).toOsmValue())
        assertEquals("5 EUR/day", Charge("5", "EUR", DAYS).toOsmValue())
        assertEquals("0.05 EUR/minute", Charge("0.05", "EUR", MINUTES).toOsmValue())
    }

    @Test fun customIntervals() {
        assertEquals("0.50 EUR/15 minutes", Charge("0.50", "EUR", MINUTES, 15).toOsmValue())
        assertEquals("2 EUR/4 hours", Charge("2", "EUR", HOURS, 4).toOsmValue())
        assertEquals("30 EUR/3 days", Charge("30", "EUR", DAYS, 3).toOsmValue())
    }

    @Test fun joinsAlternativeTariffsWithSemicolons() {
        val answer = ParkingCharges(listOf(
            Charge("0.50", "EUR", MINUTES, 15),
            Charge("2", "EUR", HOURS, 4),
            Charge("5", "EUR", DAYS),
        ))
        assertEquals("0.50 EUR/15 minutes;2 EUR/4 hours;5 EUR/day", answer.toOsmValue())
    }

    @Test fun keepsOrderAndDoesNotConvertRates() {
        val value = "5 EUR/day;0.50 EUR/15 minutes"
        assertEquals(value, ParkingCharges.parse(value)?.toOsmValue())
    }

    @Test fun parseSingleAndMultiple() {
        val answer = assertNotNull(ParkingCharges.parse("2 EUR/hour;10 EUR/day"))
        assertEquals(listOf(Charge("2", "EUR", HOURS), Charge("10", "EUR", DAYS)), answer.charges)
    }

    @Test fun parseWhitespace() {
        val answer = assertNotNull(ParkingCharges.parse(" 0.50 EUR / 15 minutes ; 2 EUR / 4 hours "))
        assertEquals("0.50 EUR/15 minutes;2 EUR/4 hours", answer.toOsmValue())
    }

    @Test fun normalizesExplicitOneToSingularUnit() {
        assertEquals("2 EUR/hour", ParkingCharges.parse("2 EUR/1 hour")?.toOsmValue())
        assertEquals("2 EUR/hour", ParkingCharges.parse("2 EUR/1 hours")?.toOsmValue())
    }

    @Test fun parseRejectsUnsupportedPartWithoutKeepingPartialAnswer() {
        listOf(
            "2 EUR/hour;free for customers",
            "2 EUR/hour;10 EUR/night",
            "2 EUR/hour;1 EUR/hour @ (18:00-08:00)",
            "2 EUR/person/hour",
            "2 EUR/0.5 hour",
        ).forEach { assertNull(ParkingCharges.parse(it), it) }
    }

    @Test fun parseRejectsEmptyParts() {
        listOf("", " ", ";", "2 EUR/hour;", ";2 EUR/hour", "2 EUR/hour;;10 EUR/day")
            .forEach { assertNull(ParkingCharges.parse(it), it) }
    }

    @Test fun rejectsInvalidAmounts() {
        listOf("0", "0.00", "-1", "NaN", "Infinity", "1,50", "1e2", "1;2", "1 EUR")
            .forEach { amount ->
                assertFailsWith<IllegalArgumentException>(amount) { Charge(amount, "EUR", HOURS) }
            }
    }

    @Test fun rejectsInvalidCurrencyCodes() {
        listOf("€", "eur", "EURO", "???", "EUR;USD").forEach {
            assertFailsWith<IllegalArgumentException>(it) { Charge("1", it, HOURS) }
        }
    }

    @Test fun rejectsInvalidDurationCounts() {
        listOf(0, -1, 10000, Int.MAX_VALUE).forEach {
            assertFailsWith<IllegalArgumentException> { Charge("1", "EUR", HOURS, it) }
        }
        assertEquals("1 EUR/9999 minutes", Charge("1", "EUR", MINUTES, 9999).toOsmValue())
    }

    @Test fun rejectsEmptyAnswer() {
        assertFailsWith<IllegalArgumentException> { ParkingCharges(emptyList()) }
    }

    @Test fun acceptsExactly255Characters() {
        val amount = "1".repeat(255 - " EUR/hour".length)
        val answer = ParkingCharges(listOf(Charge(amount, "EUR", HOURS)))
        assertFalse(answer.isTooLong)
        assertEquals(255, answer.toOsmValue().length)
        assertNotNull(ParkingCharges.parse(answer.toOsmValue()))
    }

    @Test fun rejects256CharactersWithoutTruncation() {
        val amount = "1".repeat(256 - " EUR/hour".length)
        val answer = ParkingCharges(listOf(Charge(amount, "EUR", HOURS)))
        assertTrue(answer.isTooLong)
        assertFailsWith<IllegalArgumentException> { answer.toOsmValue() }
        assertNull(ParkingCharges.parse("$amount EUR/hour"))
    }

    @Test fun rejectsNumericOverflowWhileParsing() {
        listOf("2 EUR/999999999999999 hours", "2 EUR/0 hours", "2 EUR/10000 minutes")
            .forEach { assertNull(ParkingCharges.parse(it), it) }
    }
}
