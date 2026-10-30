package de.westnordost.streetcomplete.ui.common

internal enum class IncomingFileKind { GPX, GEOJSON, CUSTOM_QUESTS, TREES }

internal fun incomingFileKinds(filename: String): List<IncomingFileKind> = when (filename.substringAfterLast('.', "").lowercase()) {
    "gpx" -> listOf(IncomingFileKind.GPX)
    "geojson", "json" -> listOf(IncomingFileKind.GEOJSON)
    "txt", "csv" -> listOf(IncomingFileKind.CUSTOM_QUESTS, IncomingFileKind.TREES)
    else -> IncomingFileKind.entries
}
