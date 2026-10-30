package de.westnordost.streetcomplete.osm

private val conditionalInputPattern = Regex("([^@\\r\\n]+) @ \\((.+)\\)")

internal fun isValidConditionalInput(value: String): Boolean {
    if (value.length > 255) return false
    val match = conditionalInputPattern.matchEntire(value) ?: return false
    return match.groupValues[1].isNotBlank() && match.groupValues[2].isNotBlank()
}
