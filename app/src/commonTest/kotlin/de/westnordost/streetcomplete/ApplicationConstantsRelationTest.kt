package de.westnordost.streetcomplete

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApplicationConstantsRelationTest {
    @Test fun `retain small routes but ignore large routes of unknown type`() {
        val tags = mapOf("type" to "route", "route" to "unknown_route_type")
        assertFalse(ApplicationConstants.ignoreRelation(tags, 100))
        assertTrue(ApplicationConstants.ignoreRelation(tags, 101))
    }

    @Test fun `retain ferry routes regardless of size`() {
        assertFalse(ApplicationConstants.ignoreRelation(mapOf("type" to "route", "route" to "ferry"), 1000))
    }

    @Test fun `ignore route masters regardless of size`() {
        assertTrue(ApplicationConstants.ignoreRelation(mapOf("type" to "route_master"), 1))
    }
}
