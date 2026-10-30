package de.westnordost.streetcomplete.ui.util.qr

import androidx.compose.runtime.Composable

@Composable
actual fun rememberQrCodeScanner(onResult: (QrScanResult) -> Unit): QrCodeScanner =
    QrCodeScanner { onResult(QrScanResult.Unavailable) }
