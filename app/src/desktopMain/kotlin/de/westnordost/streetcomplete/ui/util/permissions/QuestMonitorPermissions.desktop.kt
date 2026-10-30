package de.westnordost.streetcomplete.ui.util.permissions

import androidx.compose.runtime.Composable

actual val hasSeparateLocationProviders = false

@Composable
actual fun rememberQuestMonitorPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit =
    { onResult(false) }
