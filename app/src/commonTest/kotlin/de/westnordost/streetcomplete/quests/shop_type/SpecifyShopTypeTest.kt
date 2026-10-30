package de.westnordost.streetcomplete.quests.shop_type

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryDelete
import de.westnordost.streetcomplete.quests.answerApplied
import de.westnordost.streetcomplete.quests.answerAppliedTo
import de.westnordost.streetcomplete.testutils.node
import de.westnordost.streetcomplete.testutils.mockPrefs3
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpecifyShopTypeTest {
    private val questType = SpecifyShopType()

    @BeforeTest fun setUp() {
        Prefs.preferences = mockPrefs3()
    }

    @Test fun `is applicable to legacy shop amenities`() {
        for (amenity in listOf("shop", "shopping")) {
            assertTrue(questType.isApplicableTo(node(tags = mapOf("amenity" to amenity))))
            assertFalse(questType.isApplicableTo(node(tags = mapOf("amenity" to amenity, "shop" to "bakery"))))
        }
    }

    @Test fun `excludes private and inaccessible shops including legacy amenities`() {
        for (access in listOf("private", "no")) {
            for (tags in listOf(mapOf("shop" to "yes"), mapOf("amenity" to "shop"), mapOf("amenity" to "shopping"))) {
                assertFalse(questType.isApplicableTo(node(tags = tags + ("access" to access))))
            }
        }
    }

    @Test fun `cleans up legacy amenity when marking a shop vacant`() {
        assertEquals(
            setOf(
                StringMapEntryDelete("amenity", "shopping"),
                StringMapEntryAdd("disused:shop", "yes")
            ),
            questType.answerAppliedTo(ShopTypeAnswer.IsShopVacant, mapOf("amenity" to "shopping"))
        )
    }

    @Test fun `is applicable to undefined shop`() {
        assertTrue(questType.isApplicableTo(
            node(tags = mapOf("shop" to "yes"))
        ))
    }

    @Test fun `is not applicable when shop=yes is used as a property`() {
        assertFalse(questType.isApplicableTo(
            node(tags = mapOf("amenity" to "fuel", "shop" to "yes"))
        ))
    }

    @Test fun `is not applicable when other primary tags are present`() {
        assertFalse(questType.isApplicableTo(
            node(tags = mapOf("power" to "plant", "shop" to "yes"))
        ))
    }

    @Test fun `mentions old value in disused tag`() {
        assertEquals(
            setOf(
                StringMapEntryAdd("disused:shop", "supermarket"),
                StringMapEntryDelete("shop", "supermarket")
            ),
            questType.answerAppliedTo(
                ShopTypeAnswer.IsShopVacant,
                mapOf("shop" to "supermarket")
            )
        )
        assertEquals(
            setOf(
                StringMapEntryAdd("disused:shop", "yes"),
            ),
            questType.answerApplied(ShopTypeAnswer.IsShopVacant)
        )
    }

    @Test fun `removes previous shop-related tags`() {
        assertEquals(
            setOf(
                StringMapEntryAdd("disused:shop", "yes"),
                StringMapEntryDelete("opening_hours", "24/7"),
            ),
            questType.answerAppliedTo(
                ShopTypeAnswer.IsShopVacant,
                mapOf("opening_hours" to "24/7")
            )
        )
    }
}
