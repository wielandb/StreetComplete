package de.westnordost.streetcomplete.ui.common

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlin.time.Duration

@Composable
actual fun NativeDialogToast(text: String, duration: Duration, onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    val onDismiss by rememberUpdatedState(onDismissRequest)
    LaunchedEffect(context, text, duration) {
        val toast = Toast.makeText(context, text, Toast.LENGTH_LONG)
        try {
            toast.show()
            delay(duration)
            onDismiss()
        } finally {
            toast.cancel()
        }
    }
}