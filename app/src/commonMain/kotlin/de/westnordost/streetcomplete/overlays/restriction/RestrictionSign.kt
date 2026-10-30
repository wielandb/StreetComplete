package de.westnordost.streetcomplete.overlays.restriction

import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import kotlinx.serialization.Serializable

@Serializable internal enum class RestrictionSign { GIVE_WAY, STOP, ALL_WAY_STOP }
@Serializable internal enum class RestrictionDirection(val osmValue: String) { FORWARD("forward"), BACKWARD("backward") }

internal fun applyRestrictionSign(changes: StringMapChangesBuilder, sign: RestrictionSign, direction: RestrictionDirection?) {
    val newDirection = direction?.takeUnless { sign == RestrictionSign.ALL_WAY_STOP }?.osmValue
    if (newDirection == null) changes.remove("direction") else changes["direction"] = newDirection
    changes["highway"] = if (sign == RestrictionSign.GIVE_WAY) "give_way" else "stop"
    when {
        sign == RestrictionSign.ALL_WAY_STOP -> changes["stop"] = "all"
        sign == RestrictionSign.GIVE_WAY || changes["stop"] == "all" -> changes.remove("stop")
    }
}

internal fun parseRestrictionSign(tags: Map<String, String>): Pair<RestrictionSign?, RestrictionDirection?> {
    val sign = when {
        tags["highway"] == "give_way" -> RestrictionSign.GIVE_WAY
        tags["highway"] == "stop" && (tags["stop"] == "all" || tags["direction"] == "both") -> RestrictionSign.ALL_WAY_STOP
        tags["highway"] == "stop" -> RestrictionSign.STOP
        else -> null
    }
    val direction = if (sign == RestrictionSign.GIVE_WAY || sign == RestrictionSign.STOP) {
        RestrictionDirection.entries.firstOrNull { it.osmValue == tags["direction"] }
    } else null
    return sign to direction
}
