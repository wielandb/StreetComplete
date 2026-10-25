package de.westnordost.streetcomplete.quests.charge

import de.westnordost.streetcomplete.osm.duration.DurationUnit.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChargeEditorStateTest {
    @Test fun startsWithOneBlankHourlyTariff() {
        val state = ChargeEditorState()
        assertEquals(listOf(ChargeRow(0)), state.rows)
        assertFalse(state.multipleTariffs)
        assertFalse(state.customDuration)
        assertNull(state.toAnswer("EUR", 2))
    }

    @Test fun expandingDurationDoesNotChangeEnteredData() {
        val state = ChargeEditorState(rows = listOf(ChargeRow(0, 2.0)))
        assertEquals(state.rows, state.copy(customDuration = true).rows)
    }

    @Test fun addingRowPreservesExistingTariff() {
        val first = ChargeRow(0, 0.5, 15, MINUTES)
        val state = ChargeEditorState(rows = listOf(first)).addRow()
        assertEquals(first, state.rows[0])
        assertEquals(ChargeRow(1), state.rows[1])
        assertTrue(state.multipleTariffs)
        assertTrue(state.customDuration)
        assertNull(state.toAnswer("EUR", 2))
    }

    @Test fun neverReusesRemovedRowIds() {
        val state = ChargeEditorState().addRow().addRow().removeRow(1).addRow()
        assertEquals(listOf(0, 2, 3), state.rows.map { it.id })
        assertEquals(4, state.nextRowId)
    }

    @Test fun cannotRemoveLastRow() {
        val state = ChargeEditorState()
        assertEquals(state, state.removeRow(0))
    }

    @Test fun removingUnknownRowDoesNothing() {
        val state = ChargeEditorState().addRow()
        assertEquals(state, state.removeRow(99))
    }

    @Test fun changingOneRowDoesNotAffectOthers() {
        val state = ChargeEditorState().addRow().addRow()
        val modified = state.updateRow(1) { it.copy(amount = 2.0, timeValue = 4) }
        assertEquals(state.rows[0], modified.rows[0])
        assertEquals(ChargeRow(1, 2.0, 4, HOURS), modified.rows[1])
        assertEquals(state.rows[2], modified.rows[2])
    }

    @Test fun completeMultipleTariffs() {
        val state = ChargeEditorState(rows = listOf(
            ChargeRow(0, 0.5, 15, MINUTES),
            ChargeRow(1, 2.0, 4, HOURS),
            ChargeRow(2, 5.0, 1, DAYS),
        ))
        assertEquals("0.50 EUR/15 minutes;2 EUR/4 hours;5 EUR/day", state.toAnswer("EUR", 2)?.toOsmValue())
    }

    @Test fun blankMiddleRowIsNotSilentlyDropped() {
        val state = ChargeEditorState(rows = listOf(ChargeRow(0, 2.0), ChargeRow(1), ChargeRow(2, 10.0, 1, DAYS)))
        assertNull(state.toAnswer("EUR", 2))
        assertEquals("2 EUR/hour;10 EUR/day", state.removeRow(1).toAnswer("EUR", 2)?.toOsmValue())
    }

    @Test fun rejectsEmptyDuration() {
        assertNull(ChargeRow(0, 2.0, null).toCharge("EUR", 2))
    }

    @Test fun rejectsZeroAndNegativeDuration() {
        assertNull(ChargeRow(0, 2.0, 0).toCharge("EUR", 2))
        assertNull(ChargeRow(0, 2.0, -1).toCharge("EUR", 2))
    }

    @Test fun rejectsZeroNegativeAndNonFinitePrice() {
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach {
            assertNull(ChargeRow(0, it).toCharge("EUR", 2))
        }
    }

    @Test fun rejectsRoundingAndFormatterOverflow() {
        listOf(0.001, 1.999, 2147483648.0).forEach {
            assertNull(ChargeRow(0, it).toCharge("EUR", 2))
        }
    }

    @Test fun respectsCurrencyPrecision() {
        assertEquals("100 JPY/hour", ChargeRow(0, 100.0).toCharge("JPY", 0)?.toOsmValue())
        assertEquals("0.001 KWD/hour", ChargeRow(0, 0.001).toCharge("KWD", 3)?.toOsmValue())
        assertNull(ChargeRow(0, 0.5).toCharge("JPY", 0))
        assertNull(ChargeRow(0, 1.0).toCharge("???", 2))
    }

    @Test fun simpleResurveyStaysCompact() {
        val state = assertNotNull(ChargeEditorState.fromOsmValue("2.00 EUR/hour", "EUR", 2))
        assertFalse(state.multipleTariffs)
        assertFalse(state.customDuration)
        assertEquals(2.0, state.rows.single().amount)
        assertEquals("2 EUR/hour", state.toAnswer("EUR", 2)?.toOsmValue())
    }

    @Test fun customIntervalResurveyShowsNumericInput() {
        val state = assertNotNull(ChargeEditorState.fromOsmValue("0.50 EUR/15 minutes", "EUR", 2))
        assertFalse(state.multipleTariffs)
        assertTrue(state.customDuration)
        assertEquals(15, state.rows.single().timeValue)
    }

    @Test fun multipleTariffsAreRestoredOnResurvey() {
        val value = "0.50 EUR/15 minutes;2 EUR/4 hours;5 EUR/day"
        val state = assertNotNull(ChargeEditorState.fromOsmValue(value, "EUR", 2))
        assertTrue(state.multipleTariffs)
        assertTrue(state.customDuration)
        assertEquals(value, state.toAnswer("EUR", 2)?.toOsmValue())
        assertEquals(listOf(0, 1, 2, 3), state.addRow().rows.map { it.id })
    }

    @Test fun blocksUnsupportedExistingCurrency() {
        assertNull(ChargeEditorState.fromOsmValue("2 USD/hour", "EUR", 2))
        assertNull(ChargeEditorState.fromOsmValue("2 EUR/hour;10 USD/day", "EUR", 2))
    }

    @Test fun blocksUnsupportedExistingPriceInsteadOfErasingIt() {
        listOf("2 EUR/hour;free on Sundays", "2 EUR/hour @ (Mo-Fr)", "2 EUR/night", "").forEach {
            assertNull(ChargeEditorState.fromOsmValue(it, "EUR", 2))
        }
    }

    @Test fun blocksLossOfPrecisionOnResurvey() {
        listOf("0.005 EUR/hour", "2147483648 EUR/hour", "1.0000000000000001 EUR/hour").forEach {
            assertNull(ChargeEditorState.fromOsmValue(it, "EUR", 2))
        }
    }

    @Test fun acceptsHarmlessAmountFormattingOnResurvey() {
        val state = assertNotNull(ChargeEditorState.fromOsmValue("002.000 EUR/hour", "EUR", 2))
        assertEquals("2 EUR/hour", state.toAnswer("EUR", 2)?.toOsmValue())
    }

    @Test fun nullExistingTagStartsNewForm() {
        assertEquals(ChargeEditorState(), ChargeEditorState.fromOsmValue(null, "EUR", 2))
    }

    @Test fun overlyLongAnswerIsVisibleButCannotBeSerialized() {
        val state = ChargeEditorState(rows = List(30) { ChargeRow(it, 1.0) })
        val answer = assertNotNull(state.toAnswer("EUR", 2))
        assertTrue(answer.isTooLong)
        assertFailsWith<IllegalArgumentException> { answer.toOsmValue() }
    }

    @Test fun unknownCurrencyDoesNotStartEditableForm() {
        assertNull(ChargeEditorState.fromOsmValue(null, "???", 2))
    }

    @Test fun sequentialEditsUseTheLatestRowState() {
        val state = ChargeEditorState()
            .updateRow(0) { it.copy(amount = 0.5) }
            .updateRow(0) { it.copy(timeValue = 15, timeUnit = MINUTES) }
        assertEquals("0.50 EUR/15 minutes", state.toAnswer("EUR", 2)?.toOsmValue())
    }

    @Test fun emptyStateCannotSubmit() {
        assertNull(ChargeEditorState(rows = emptyList()).toAnswer("EUR", 2))
    }
}
