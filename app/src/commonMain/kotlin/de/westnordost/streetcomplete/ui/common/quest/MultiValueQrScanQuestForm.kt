package de.westnordost.streetcomplete.ui.common.quest

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.no_qr_code_handler
import de.westnordost.streetcomplete.resources.qr_scan_permission_required
import de.westnordost.streetcomplete.resources.qr_camera_unavailable
import de.westnordost.streetcomplete.ui.util.qr.QrScanResult
import de.westnordost.streetcomplete.ui.util.qr.rememberQrCodeScanner
import de.westnordost.streetcomplete.ui.common.ToastPopup
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.collections.plus

@Composable
fun MultiValueQuestQrScanForm(
    on: (QuestAction<String>) -> Unit,
    addAnotherValueText: StringResource,
    scanAnotherValueText: StringResource,
    noQrCodeHandlerResId: StringResource = Res.string.no_qr_code_handler,
    modifier: Modifier = Modifier,
    isOk: (String) -> Boolean = { true },
    simpleSuggestions: Collection<String>? = null,
    prioritySuggestions: (String) -> Collection<String>? = { null },
    onQrCodeParsed: ((String, (String?) -> Unit) -> Unit)? = null,
    minLengthForSuggestions: Int = 1,
    otherAnswers: @Composable (() -> List<AnswerItem>) = { emptyList() },
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    hint: String? = null
) {
    var values by rememberSaveable { mutableStateOf(setOf<String>()) }
    var qrError by remember { mutableStateOf<StringResource?>(null) }

    val qrScanner = rememberQrCodeScanner { result ->
        when (result) {
            is QrScanResult.Success -> {
                if (onQrCodeParsed != null) {
                    onQrCodeParsed(result.text) { if (it != null) values = values + it }
                } else {
                    values = values + result.text
                }
            }
            QrScanResult.Cancelled -> Unit
            QrScanResult.Unavailable -> qrError = noQrCodeHandlerResId
            QrScanResult.CameraUnavailable -> qrError = Res.string.qr_camera_unavailable
            QrScanResult.PermissionDenied -> qrError = Res.string.qr_scan_permission_required
        }
    }
    MultiValueQuestForm(
        on = on,
        values = values,
        onValuesChange = { values = it },
        addAnotherValueText = addAnotherValueText,
        modifier = modifier,
        isOk = isOk,
        simpleSuggestions = simpleSuggestions,
        prioritySuggestions = prioritySuggestions,
        minLengthForSuggestions = minLengthForSuggestions,
        otherAnswers = otherAnswers,
        keyboardOptions = keyboardOptions,
        hint = hint
    ) {
        TextButton(
            onClick = qrScanner::scan
        ) {
            Text(stringResource(scanAnotherValueText))
        }

        qrError?.let { error ->
            ToastPopup(
                onDismissRequest = { qrError = null },
                text = stringResource(error),
            )
        }
    }
}
