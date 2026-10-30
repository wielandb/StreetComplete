package de.westnordost.streetcomplete.util.platform

actual fun String.internString(): String = intern()
