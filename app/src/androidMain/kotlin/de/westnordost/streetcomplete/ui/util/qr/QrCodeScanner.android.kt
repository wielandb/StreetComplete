package de.westnordost.streetcomplete.ui.util.qr

import android.app.Activity.RESULT_OK
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

@Composable
actual fun rememberQrCodeScanner(onResult: (QrScanResult) -> Unit): QrCodeScanner {
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.getStringExtra("SCAN_RESULT")
        currentOnResult(if (result.resultCode == RESULT_OK && !text.isNullOrBlank()) {
            QrScanResult.Success(text)
        } else {
            QrScanResult.Cancelled
        })
    }
    return remember(launcher) {
        QrCodeScanner {
            try {
                launcher.launch(Intent("com.google.zxing.client.android.SCAN"))
            } catch (_: ActivityNotFoundException) {
                currentOnResult(QrScanResult.Unavailable)
            }
        }
    }
}
