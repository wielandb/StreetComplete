package de.westnordost.streetcomplete.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import de.westnordost.streetcomplete.data.preferences.Preferences
import org.koin.compose.koinInject

@Composable
fun rememberBooleanPreference(
    key: String,
    defaultValue: Boolean,
    preferences: Preferences = koinInject(),
): Boolean {
    var value by remember(preferences, key, defaultValue) {
        mutableStateOf(preferences.getBoolean(key, defaultValue))
    }
    DisposableEffect(preferences, key, defaultValue) {
        val listener = preferences.prefs.addBooleanListener(key, defaultValue) { value = it }
        value = preferences.getBoolean(key, defaultValue)
        onDispose { listener.deactivate() }
    }
    return value
}

@Composable
fun rememberFloatPreference(
    key: String,
    defaultValue: Float,
    preferences: Preferences = koinInject(),
): Float {
    var value by remember(preferences, key, defaultValue) {
        mutableStateOf(preferences.getFloat(key, defaultValue))
    }
    DisposableEffect(preferences, key, defaultValue) {
        val listener = preferences.prefs.addFloatListener(key, defaultValue) { value = it }
        value = preferences.getFloat(key, defaultValue)
        onDispose { listener.deactivate() }
    }
    return value
}