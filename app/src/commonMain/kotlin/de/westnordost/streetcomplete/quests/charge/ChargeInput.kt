package de.westnordost.streetcomplete.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.westnordost.streetcomplete.osm.duration.DurationUnit
import de.westnordost.streetcomplete.osm.duration.DurationUnitDropdown
import de.westnordost.streetcomplete.ui.common.input.DecimalInput
import de.westnordost.streetcomplete.ui.theme.largeInput
import de.westnordost.streetcomplete.util.locale.CurrencyFormatElements

/** The original compact input, optionally expanded to accept a numeric billing interval. */
@Composable
fun ChargeInput(
    amount: Double?,
    onAmountChange: (Double?) -> Unit,
    currencyFormatInfo: CurrencyFormatElements,
    durationUnit: DurationUnit,
    onDurationUnitChange: (DurationUnit) -> Unit,
    perLabel: String,
    modifier: Modifier = Modifier,
    durationValue: Int? = 1,
    onDurationValueChange: ((Int?) -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChargeAmountInput(amount, onAmountChange, currencyFormatInfo)
            if (onDurationValueChange == null) {
                Text(text = perLabel, style = MaterialTheme.typography.body1)
                DurationUnitDropdown(
                    selectedDuration = durationUnit,
                    onSelectedDuration = onDurationUnitChange,
                    alwaysSingular = true,
                )
            }
        }
        // Keep the extra field on a separate line to avoid crowding the money input.
        if (onDurationValueChange != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = perLabel, style = MaterialTheme.typography.body1)
                DecimalInput(
                    value = durationValue?.toDouble(),
                    onValueChange = { onDurationValueChange(it?.toInt()) },
                    maxIntegerDigits = 4,
                    maxFractionDigits = 0,
                    isUnsigned = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.largeInput,
                    modifier = Modifier.width(96.dp),
                )
                DurationUnitDropdown(
                    selectedDuration = durationUnit,
                    onSelectedDuration = onDurationUnitChange,
                    alwaysSingular = durationValue == 1,
                )
            }
        }
    }
}

@Composable
private fun RowScope.ChargeAmountInput(
    amount: Double?,
    onAmountChange: (Double?) -> Unit,
    currencyFormatInfo: CurrencyFormatElements,
) {
    if (currencyFormatInfo.isSymbolBeforeAmount) {
        Text(
            text = currencyFormatInfo.symbol,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
        )
    }
    AutoFitTextFieldFontSize(
        value = amount.toString(),
        modifier = Modifier.weight(1f, fill = false),
    ) {
        DecimalInput(
            value = amount,
            onValueChange = onAmountChange,
            maxFractionDigits = currencyFormatInfo.decimalDigits,
            isUnsigned = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (currencyFormatInfo.decimalDigits > 0) {
                    KeyboardType.Decimal
                } else {
                    KeyboardType.Number
                }
            ),
        )
    }
    if (!currencyFormatInfo.isSymbolBeforeAmount) {
        Text(
            text = currencyFormatInfo.symbol,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
        )
    }
}
