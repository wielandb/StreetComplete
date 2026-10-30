package de.westnordost.streetcomplete.data.osmtracks

import kotlin.time.Instant

/** Formats a recorded track as GPX 1.1 on every platform. */
fun formatGpxTrack(name: String, points: List<Trackpoint>): String = buildString {
    appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
    appendLine("<gpx version=\"1.1\" creator=\"SCEE\" xmlns=\"http://www.topografix.com/GPX/1/1\" xmlns:scee=\"https://github.com/Helium314/SCEE/gpx/1\">")
    appendLine("  <trk>")
    appendLine("    <name>${name.escapeXml()}</name>")
    appendLine("    <trkseg>")
    for (point in points) {
        appendLine("      <trkpt lon=\"${point.position.longitude}\" lat=\"${point.position.latitude}\">")
        if (point.elevation != 0f && point.elevation.isFinite()) {
            appendLine("        <ele>${point.elevation}</ele>")
        }
        appendLine("        <time>${Instant.fromEpochMilliseconds(point.time)}</time>")
        if (point.accuracy > 0f && point.accuracy.isFinite()) {
            appendLine("        <extensions>")
            appendLine("          <scee:horizontalAccuracy unit=\"m\">${point.accuracy}</scee:horizontalAccuracy>")
            appendLine("        </extensions>")
        }
        appendLine("      </trkpt>")
    }
    appendLine("    </trkseg>")
    appendLine("  </trk>")
    append("</gpx>")
}

private fun String.escapeXml() = replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")
