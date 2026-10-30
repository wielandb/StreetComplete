package de.westnordost.streetcomplete.util.platform

/** Canonically decompose Unicode text, for searches that ignore accents. */
expect fun String.normalizeToNfd(): String
