package de.westnordost.streetcomplete.overlays.restriction

import de.westnordost.streetcomplete.data.meta.WeightMeasurementUnit
import de.westnordost.streetcomplete.quests.max_weight.Weight

internal fun parseRestrictionWeight(value: String?): Weight? {
    if (value == null) return null
    val normalized = value.trim().replace(',', '.')
    val unit = when {
        normalized.endsWith("lbs") -> WeightMeasurementUnit.POUND
        normalized.endsWith("st") -> WeightMeasurementUnit.SHORT_TON
        else -> WeightMeasurementUnit.METRIC_TON
    }
    val number = normalized.removeSuffix("lbs").removeSuffix("st").removeSuffix("t").trim().toDoubleOrNull()
        ?.takeIf { it.isFinite() && it > 0.0 } ?: return null
    return Weight(number, unit)
}
