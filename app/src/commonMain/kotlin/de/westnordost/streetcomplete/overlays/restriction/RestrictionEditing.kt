package de.westnordost.streetcomplete.overlays.restriction




/** The ordinary and conditional restriction keys always move together. */
internal fun Map<String, String>.turnRestrictionKey(): String = when {
    "restriction" in this || "restriction:conditional" in this -> "restriction"
    else -> keys.firstOrNull {
        it.startsWith("restriction:") && it.removePrefix("restriction:").removeSuffix(":conditional") in onlyTurnRestrictionSet
    }?.removeSuffix(":conditional") ?: "restriction"
}

internal fun changeTurnRestrictionType(tags: Map<String, String>, value: String): Map<String, String> {
    val key = tags.turnRestrictionKey()
    return tags.toMutableMap().apply {
        if (key in this || "$key:conditional" !in this) this[key] = value
        get("$key:conditional")?.let { old ->
            // A `none` condition is an exception, so it must stay `none` when the sign changes.
            if (old.substringBefore('@').trim() != "none") this["$key:conditional"] = "$value @ ${old.substringAfter('@').trim()}"
        }
    }
}

internal fun changeTurnRestrictionVehicle(tags: Map<String, String>, vehicle: String?): Map<String, String> {
    val from = tags.turnRestrictionKey()
    val to = if (vehicle == null) "restriction" else "restriction:$vehicle"
    if (from == to || to in tags || "$to:conditional" in tags) return tags
    return tags.toMutableMap().apply {
        remove(from)?.let { this[to] = it }
        remove("$from:conditional")?.let { this["$to:conditional"] = it }
    }
}

internal fun removeTurnRestrictionCondition(tags: Map<String, String>): Map<String, String> {
    val key = tags.turnRestrictionKey()
    return tags.toMutableMap().apply {
        val condition = remove("$key:conditional") ?: return@apply
        if (key !in this) {
            val value = condition.substringBefore('@').trim()
            if (value != "none") this[key] = value
        }
    }
}

internal fun addRestrictionCondition(tags: Map<String, String>, key: String, condition: String): Map<String, String> =
    tags.toMutableMap().apply {
        this["$key:conditional"] = condition
        if (condition.substringBefore('@').trim() != "none") remove(key)
    }

internal val onlyTurnRestriction = listOf("hgv", "caravan", "motorcar", "bus", "agricultural", "motorcycle", "bicycle", "hazmat")
val onlyTurnRestrictionSet = onlyTurnRestriction.toHashSet()
