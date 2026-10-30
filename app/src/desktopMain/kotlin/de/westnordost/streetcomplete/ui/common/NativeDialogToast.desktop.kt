package de.westnordost.streetcomplete.ui.common

import androidx.compose.runtime.Composable
import kotlin.time.Duration

@Composable
actual fun NativeDialogToast(text: String, duration: Duration, onDismissRequest: () -> Unit) {
    ToastPopup(onDismissRequest, text, duration)
}