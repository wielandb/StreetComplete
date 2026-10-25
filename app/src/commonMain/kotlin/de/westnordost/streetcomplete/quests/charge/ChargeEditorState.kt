package de.westnordost.streetcomplete.quests.charge

import de.westnordost.streetcomplete.osm.duration.DurationUnit
import de.westnordost.streetcomplete.util.ktx.formatPadded
import kotlinx.serialization.Serializable

/** Stable row IDs keep remembered text-field state attached to the right tariff after removal. */
@Serializable
internal data class ChargeRow(
    val id: Int,
    val amount: Double? = null,
    val timeValue: Int? = 1,
    val timeUnit: DurationUnit = DurationUnit.HOURS,
) {
    fun toCharge(currency: String, decimalDigits: Int): Charge? {
        val amount = amount ?: return null
        val timeValue = timeValue ?: return null
        if (!amount.isFinite() || amount <= 0.0 || amount > Int.MAX_VALUE.toDouble()) return null
        if (timeValue !in 1..MAX_CHARGE_DURATION) return null
        if (!currency.matches(Regex("[A-Z]{3}"))) return null
        // formatPadded is also used by the original form. Do not accept a rounded/clipped amount.
        val formatted = amount.formatPadded(decimalDigits, true)
        if (formatted.toDoubleOrNull() != amount) return null
        return runCatching { Charge(formatted, currency, timeUnit, timeValue) }.getOrNull()
    }
}

@Serializable
internal data class ChargeEditorState(
    val rows: List<ChargeRow> = listOf(ChargeRow(0)),
    val multipleTariffs: Boolean = false,
    val customDuration: Boolean = false,
    val nextRowId: Int = 1,
) {
    fun addRow(): ChargeEditorState = copy(
        rows = rows + ChargeRow(nextRowId),
        nextRowId = nextRowId + 1,
        multipleTariffs = true,
        customDuration = true,
    )

    fun removeRow(id: Int): ChargeEditorState =
        if (rows.size <= 1) this else copy(rows = rows.filterNot { it.id == id })

    fun updateRow(id: Int, update: (ChargeRow) -> ChargeRow): ChargeEditorState =
        copy(rows = rows.map { if (it.id == id) update(it) else it })

    fun toAnswer(currency: String, decimalDigits: Int): ParkingCharges? {
        if (rows.isEmpty()) return null
        val charges = rows.map { it.toCharge(currency, decimalDigits) ?: return null }
        return ParkingCharges(charges)
    }

    companion object {
        /** null means the existing tag cannot be edited without losing information. */
        fun fromOsmValue(value: String?, currency: String, decimalDigits: Int): ChargeEditorState? {
            if (!currency.matches(Regex("[A-Z]{3}"))) return null
            if (value == null) return ChargeEditorState()
            val charges = ParkingCharges.parse(value)?.charges ?: return null
            val rows = charges.mapIndexed { index, charge ->
                if (charge.currency != currency) return null
                val row = ChargeRow(index, charge.amount.toDouble(), charge.timeValue, charge.timeUnit)
                val roundTrip = row.toCharge(currency, decimalDigits) ?: return null
                // Comparing decimal strings additionally detects precision loss in large Double values.
                if (normalizeAmount(roundTrip.amount) != normalizeAmount(charge.amount)) return null
                row
            }
            return ChargeEditorState(
                rows = rows,
                multipleTariffs = rows.size > 1,
                customDuration = rows.size > 1 || rows.any { it.timeValue != 1 },
                nextRowId = rows.size,
            )
        }

        private fun normalizeAmount(value: String): String {
            val integer = value.substringBefore('.').trimStart('0').ifEmpty { "0" }
            val fraction = value.substringAfter('.', "").trimEnd('0')
            return integer + if (fraction.isEmpty()) "" else ".$fraction"
        }
    }
}
