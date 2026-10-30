package de.westnordost.streetcomplete.ui.common

import androidx.compose.runtime.Composable
import kotlin.time.Duration

/** A transient message above native dialogs without taking keyboard or touch focus. */
@Composable
expect fun NativeDialogToast(text: String, duration: Duration, onDismissRequest: () -> Unit)