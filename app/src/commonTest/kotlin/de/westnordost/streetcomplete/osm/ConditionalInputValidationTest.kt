package de.westnordost.streetcomplete.osm

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConditionalInputValidationTest {
    @Test fun turnRestrictionNamesWithUnderscoresAreValid() {
        assertTrue(isValidConditionalInput("no_left_turn @ (Mo-Fr 09:00-18:00)"))
    }

    @Test fun decimalWeightAndUnitsAreValid() {
        assertTrue(isValidConditionalInput("7.5 st @ (weight > 3.5)"))
        assertTrue(isValidConditionalInput("none @ (delivery)"))
    }

    @Test fun incompleteValuesAndConditionsAreInvalid() {
        for (value in listOf(" @ (Mo-Fr)", "no_left_turn @ ()", "no_left_turn @ ( )", "no_left_turn", "no_left_turn @ Mo-Fr")) {
            assertFalse(isValidConditionalInput(value), value)
        }
    }

    @Test fun maximumOsmTagLengthIsAcceptedButLongerValuesAreNot() {
        assertTrue(isValidConditionalInput("x".repeat(249) + " @ (x)"))
        assertFalse(isValidConditionalInput("x".repeat(250) + " @ (x)"))
    }
}
