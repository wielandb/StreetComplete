package de.westnordost.streetcomplete.quests.wheelchair_access

enum class WheelchairAccess(val osmValue: String) {
    YES("yes"),
    LIMITED("limited"),
    NO("no"),
}

/** Description edits belong to one answer, rather than to a shared enum constant. */
data class WheelchairAccessAnswer(
    val access: WheelchairAccess,
    val updatedDescriptions: Map<String, String>? = null,
)
