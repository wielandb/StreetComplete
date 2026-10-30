package de.westnordost.streetcomplete.overlays.restriction

import kotlin.test.Test
import kotlin.test.assertEquals

class RestrictionEditingTest {
    @Test fun changingSignKeepsExceptionConditions() {
        val tags = mapOf("restriction" to "no_left_turn", "restriction:conditional" to "none @ (Mo-Fr)", "except" to "bicycle")
        assertEquals(tags + ("restriction" to "only_right_turn"), changeTurnRestrictionType(tags, "only_right_turn"))
    }

    @Test fun changingConditionalOnlySignDoesNotCreateUnconditionalRestriction() {
        assertEquals(mapOf("restriction:hgv:conditional" to "no_right_turn @ (weight > 7.5)"), changeTurnRestrictionType(
            mapOf("restriction:hgv:conditional" to "no_left_turn @ (weight > 7.5)"), "no_right_turn",
        ))
    }

    @Test fun movingVehicleClassKeepsBothConditionalAndOrdinaryTags() {
        assertEquals(mapOf("restriction:bus" to "no_left_turn", "restriction:bus:conditional" to "none @ (Su)", "except" to "bicycle"),
            changeTurnRestrictionVehicle(mapOf("restriction:hgv" to "no_left_turn", "restriction:hgv:conditional" to "none @ (Su)", "except" to "bicycle"), "bus"))
    }

    @Test fun removingVehicleClassRestoresBothGenericKeys() {
        assertEquals(mapOf("restriction" to "no_left_turn", "restriction:conditional" to "none @ (Su)"),
            changeTurnRestrictionVehicle(mapOf("restriction:hgv" to "no_left_turn", "restriction:hgv:conditional" to "none @ (Su)"), null))
    }

    @Test fun changingVehicleDoesNotOverwriteAnotherRestriction() {
        val tags = mapOf("restriction:hgv" to "no_left_turn", "restriction:bus" to "only_right_turn")
        assertEquals(tags, changeTurnRestrictionVehicle(tags, "bus"))
    }

    @Test fun removingConditionRestoresConditionalOnlyValue() {
        assertEquals(mapOf("restriction:hgv" to "no_left_turn"), removeTurnRestrictionCondition(
            mapOf("restriction:hgv:conditional" to "no_left_turn @ (Mo-Fr)"),
        ))
    }

    @Test fun removingExceptionConditionKeepsOrdinaryRestriction() {
        assertEquals(mapOf("restriction" to "no_left_turn"), removeTurnRestrictionCondition(
            mapOf("restriction" to "no_left_turn", "restriction:conditional" to "none @ (Su)"),
        ))
    }

    @Test fun conditionalWeightLimitRemovesOrdinaryLimit() {
        assertEquals(mapOf("maxweight:conditional" to "7.5 @ (Mo-Fr)", "surface" to "asphalt"), addRestrictionCondition(
            mapOf("maxweight" to "7.5", "surface" to "asphalt"), "maxweight", "7.5 @ (Mo-Fr)",
        ))
    }

    @Test fun conditionalWeightExceptionPreservesOrdinaryLimit() {
        assertEquals(mapOf("maxweight" to "7.5", "maxweight:conditional" to "none @ (delivery)"), addRestrictionCondition(
            mapOf("maxweight" to "7.5"), "maxweight", "none @ (delivery)",
        ))
    }
}
