package de.westnordost.streetcomplete.quests.charge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.osmquests.Answer
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.common.ChargeInput
import de.westnordost.streetcomplete.ui.common.quest.AnswerItem
import de.westnordost.streetcomplete.ui.common.quest.QuestForm
import de.westnordost.streetcomplete.ui.theme.extraLargeInput
import de.westnordost.streetcomplete.ui.util.rememberSerializable
import de.westnordost.streetcomplete.util.locale.CurrencyFormatElements
import de.westnordost.streetcomplete.util.locale.CurrencyFormatter
import org.jetbrains.compose.resources.stringResource

@Composable
fun AddChargeForm(
    on: (QuestAction<ParkingCharges>) -> Unit,
    countryInfo: CountryInfo,
    existingCharge: String? = null,
) {
    val currencyFormatInfo = remember(countryInfo) {
        CurrencyFormatElements.of(countryInfo.userPreferredLocale)
    }
    val currency = remember(countryInfo) {
        CurrencyFormatter(countryInfo.userPreferredLocale).currencyCode
    }
    val initialState = remember(existingCharge, currency, currencyFormatInfo.decimalDigits) {
        currency?.let {
            ChargeEditorState.fromOsmValue(existingCharge, it, currencyFormatInfo.decimalDigits)
        }
    }
    var state by rememberSerializable { mutableStateOf(initialState ?: ChargeEditorState()) }
    val unsupported = initialState == null
    val answer = if (unsupported || currency == null) null else {
        state.toAnswer(currency, currencyFormatInfo.decimalDigits)
    }
    val tooLong = answer?.isTooLong == true

    QuestForm(
        on = on,
        isComplete = answer != null && !tooLong,
        hasChanges = state.rows != (initialState ?: ChargeEditorState()).rows,
        isResurvey = existingCharge != null,
        onClickOk = {
            if (answer != null && !tooLong) on(Answer(answer))
        },
        otherAnswers = {
            buildList {
                if (!unsupported && !state.multipleTariffs) {
                    add(AnswerItem(stringResource(Res.string.quest_parking_charge_multiple_tariffs)) {
                        state = state.addRow()
                    })
                }
                if (!unsupported && !state.customDuration) {
                    add(AnswerItem(stringResource(Res.string.quest_parking_charge_custom_interval)) {
                        state = state.copy(customDuration = true)
                    })
                }
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (unsupported) {
                Text(stringResource(Res.string.quest_parking_charge_unsupported))
                existingCharge?.let { Text(it) }
            } else {
                if (state.multipleTariffs) {
                    Text(
                        text = stringResource(Res.string.quest_parking_charge_tariffs_hint),
                        style = MaterialTheme.typography.body2,
                    )
                }
                state.rows.forEachIndexed { index, row ->
                    key(row.id) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (state.multipleTariffs) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(stringResource(Res.string.quest_parking_charge_tariff_number, index + 1))
                                    Spacer(Modifier.weight(1f))
                                    TextButton(
                                        onClick = { state = state.removeRow(row.id) },
                                        enabled = state.rows.size > 1,
                                    ) {
                                        Text(stringResource(Res.string.quest_parking_charge_remove_tariff))
                                    }
                                }
                            }
                            ProvideTextStyle(MaterialTheme.typography.extraLargeInput) {
                                ChargeInput(
                                    amount = row.amount,
                                    onAmountChange = { value ->
                                        state = state.updateRow(row.id) { it.copy(amount = value) }
                                    },
                                    currencyFormatInfo = currencyFormatInfo,
                                    durationUnit = row.timeUnit,
                                    onDurationUnitChange = { value ->
                                        state = state.updateRow(row.id) { it.copy(timeUnit = value) }
                                    },
                                    perLabel = stringResource(Res.string.quest_parking_charge_time_unit_label),
                                    durationValue = row.timeValue,
                                    onDurationValueChange = if (state.customDuration) {
                                        { value -> state = state.updateRow(row.id) { it.copy(timeValue = value) } }
                                    } else null,
                                )
                            }
                        }
                        if (index < state.rows.lastIndex) Divider()
                    }
                }
                if (state.multipleTariffs) {
                    TextButton(onClick = { state = state.addRow() }) {
                        Text(stringResource(Res.string.quest_parking_charge_add_tariff))
                    }
                }
                if (tooLong) {
                    Text(
                        text = stringResource(Res.string.quest_parking_charge_too_long),
                        color = MaterialTheme.colors.error,
                    )
                } else if (answer == null && state.rows.any { it.amount != null || it.timeValue != 1 }) {
                    Text(
                        text = stringResource(Res.string.quest_parking_charge_complete_all),
                        style = MaterialTheme.typography.body2,
                    )
                }
            }
        }
    }
}
