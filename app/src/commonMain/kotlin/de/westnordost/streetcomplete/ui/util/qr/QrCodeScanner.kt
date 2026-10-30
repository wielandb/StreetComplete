package de.westnordost.streetcomplete.ui.util.qr

import androidx.compose.runtime.Composable

fun interface QrCodeScanner {
    fun scan()
}

sealed interface QrScanResult {
    data class Success(val text: String) : QrScanResult
    data object Cancelled : QrScanResult
    data object Unavailable : QrScanResult
    data object CameraUnavailable : QrScanResult
    data object PermissionDenied : QrScanResult
}

@Composable
expect fun rememberQrCodeScanner(onResult: (QrScanResult) -> Unit): QrCodeScanner
