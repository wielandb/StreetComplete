package de.westnordost.streetcomplete.ui.util.permissions

import androidx.compose.runtime.Composable

/** Requests the native permissions needed to continue monitoring after the app is backgrounded. */
@Composable
expect fun rememberQuestMonitorPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit

/** Android exposes separate providers; iOS chooses providers for the requested accuracy. */
expect val hasSeparateLocationProviders: Boolean
