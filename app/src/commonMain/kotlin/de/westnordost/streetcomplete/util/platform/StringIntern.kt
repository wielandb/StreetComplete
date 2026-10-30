package de.westnordost.streetcomplete.util.platform

/** Reuse equal strings to reduce the memory used by repeated OSM tags and quest type names. */
expect fun String.internString(): String
