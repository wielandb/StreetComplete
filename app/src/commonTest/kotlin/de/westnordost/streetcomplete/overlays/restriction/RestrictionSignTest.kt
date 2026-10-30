package de.westnordost.streetcomplete.overlays.restriction

import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import kotlin.test.Test
import kotlin.test.assertEquals

class RestrictionSignTest {
    @Test fun changingAllWayStopToOrdinaryStopRemovesAllWayTag() {
        assertEquals(
            mapOf("highway" to "stop", "direction" to "forward", "name" to "Example"),
            edit(mapOf("highway" to "stop", "stop" to "all", "name" to "Example"), RestrictionSign.STOP, RestrictionDirection.FORWARD),
        )
    }

    @Test fun changingLegacyBothDirectionsToOrdinaryStopRemovesDirection() {
        assertEquals(mapOf("highway" to "stop"), edit(
            mapOf("highway" to "stop", "direction" to "both"), RestrictionSign.STOP, null,
        ))
    }

    @Test fun allWayStopRemovesDirectionalTag() {
        assertEquals(mapOf("highway" to "stop", "stop" to "all"), edit(
            mapOf("highway" to "stop", "direction" to "backward"), RestrictionSign.ALL_WAY_STOP, RestrictionDirection.BACKWARD,
        ))
    }

    @Test fun giveWayRemovesStopQualifier() {
        assertEquals(mapOf("highway" to "give_way", "direction" to "backward"), edit(
            mapOf("highway" to "stop", "stop" to "minor"), RestrictionSign.GIVE_WAY, RestrictionDirection.BACKWARD,
        ))
    }

    @Test fun ordinaryStopPreservesOtherStopQualifier() {
        assertEquals(mapOf("highway" to "stop", "stop" to "minor", "direction" to "forward"), edit(
            mapOf("highway" to "stop", "stop" to "minor"), RestrictionSign.STOP, RestrictionDirection.FORWARD,
        ))
    }

    @Test fun bothSupportedAllWayEncodingsHaveNoDirection() {
        for (tags in listOf(
            mapOf("highway" to "stop", "stop" to "all", "direction" to "backward"),
            mapOf("highway" to "stop", "direction" to "both"),
        )) assertEquals(RestrictionSign.ALL_WAY_STOP to null, parseRestrictionSign(tags))
    }

    @Test fun parsesForwardAndBackwardGiveWay() {
        for (direction in RestrictionDirection.entries) {
            assertEquals(RestrictionSign.GIVE_WAY to direction, parseRestrictionSign(
                mapOf("highway" to "give_way", "direction" to direction.osmValue),
            ))
        }
    }

    private fun edit(tags: Map<String, String>, sign: RestrictionSign, direction: RestrictionDirection?): Map<String, String> {
        val changes = StringMapChangesBuilder(tags)
        applyRestrictionSign(changes, sign, direction)
        return tags.toMutableMap().also { changes.create().applyTo(it) }
    }
}
