package de.westnordost.streetcomplete.quests.crossing_markings

import androidx.compose.runtime.Composable
import de.westnordost.streetcomplete.data.elementfilter.toElementFilterExpression
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataWithGeometry
import de.westnordost.streetcomplete.data.osm.osmquests.Answer
import de.westnordost.streetcomplete.data.osm.osmquests.OsmElementQuestType
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.data.user.achievements.EditTypeAchievement.PEDESTRIAN
import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.osm.isCrossing
import de.westnordost.streetcomplete.quests.BooleanQuestSettingsDialog
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.common.quest.YesNoQuestForm
import de.westnordost.streetcomplete.util.ktx.toYesNo
import de.westnordost.streetcomplete.ui.common.item_select.ImageWithLabel
import de.westnordost.streetcomplete.ui.common.quest.AnswerItem
import de.westnordost.streetcomplete.ui.common.quest.QuestForm
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import kotlin.collections.set

class AddCrossingMarkings : OsmElementQuestType<Set<CrossingMarkings>> {

    private val crossingFilter by lazy { """
        nodes with
          highway = crossing
          and foot != no
          and $crossingMarkingExpression
          and (!crossing:signals or crossing:signals = no)
    """.toElementFilterExpression() }
    /* only looking for crossings that have no crossing=* at all set because if the crossing was
     * - if it had markings, it would be tagged with "marked","zebra" or "uncontrolled"
     * - if it hadn't, it would be tagged with "unmarked"
     * - and in case of "traffic_signals", we currently assume that when there are traffic signals
     *   it would be spammy to ask about markings because the answer would almost always be "yes".
     *   Might differ per country, research necessary. */

    private val excludedWaysFilter by lazy { """
        ways with
          highway and access ~ private|no
          or highway = service and service = driveway
    """.toElementFilterExpression() }

    override val changesetComment = "Specify type or existence of pedestrian crossing markings"
    override val wikiLink = "Key:crossing:markings"
    override val icon = Res.drawable.quest_pedestrian_crossing
    override val title = Res.string.quest_pedestrian_crossing_markings
    override val achievements = listOf(PEDESTRIAN)

    override fun getHighlightedElements(element: Element, mapData: MapDataWithGeometry) =
        mapData.filter { it.isCrossing() }.asSequence()

    override fun getApplicableElements(mapData: MapDataWithGeometry): Iterable<Element> {
        val excludedWayNodeIds = mapData.ways
            .filter { excludedWaysFilter.matches(it) }
            .flatMapTo(HashSet()) { it.nodeIds }

        return mapData.nodes
            .filter { crossingFilter.matches(it) && it.id !in excludedWayNodeIds }
    }

    override fun isApplicableTo(element: Element): Boolean? =
        if (!crossingFilter.matches(element)) false else null

    @Composable
    override fun Form(on: (QuestAction<Set<CrossingMarkings>>) -> Unit, element: Element, geometry: ElementGeometry, countryInfo: CountryInfo) {
        if (prefs.getBoolean(PREF_CROSSING_MARKING_EXTENDED, false)) {
            CrossingMarkingsForm(
                on = on,
                items = CrossingMarkings.entries.filter { it != CrossingMarkings.YES },
                itemContent = { ImageWithLabel(painterResource(it.imageRes!!), stringResource(it.titleRes!!)) },
            )
        } else {
            QuestForm(
                on = on,
                answers = listOf(
                    AnswerItem(stringResource(Res.string.quest_generic_hasFeature_no)) { on(Answer(setOf(CrossingMarkings.NO))) },
                    AnswerItem(stringResource(Res.string.quest_generic_hasFeature_yes)) { on(Answer(setOf(CrossingMarkings.YES))) }
                ),
            )
        }
    }

    override fun applyAnswerTo(answer: Set<CrossingMarkings>, tags: Tags, geometry: ElementGeometry, timestampEdited: Long) {
        tags["crossing:markings"] = answer.map { it.osmValue }.sorted().joinToString(";")
        val crossing = tags["crossing"]
        if (answer == setOf(CrossingMarkings.NO) && (crossing == "marked" || crossing == "uncontrolled")) {
            tags["crossing"] = "unmarked"
        }
    }

    override val hasQuestSettings: Boolean = true

    @Composable override fun QuestSettings(onDismissRequest: () -> Unit) {
        BooleanQuestSettingsDialog(
            prefs,
            PREF_CROSSING_MARKING_EXTENDED,
            false,
            Res.string.pref_quest_pedestrian_crossing_markings_extended,
            Res.string.quest_generic_hasFeature_yes,
            Res.string.quest_generic_hasFeature_no,
            onDismissRequest
        )
    }

    private val crossingMarkingExpression = if (prefs.getBoolean(PREF_CROSSING_MARKING_EXTENDED, false)) {
        """(
            (!crossing:markings or crossing:markings = yes)
            and crossing !~ zebra|unmarked and crossing_ref != zebra
           )
        """.trimIndent()
    } else {
        "!crossing:markings and (!crossing or crossing = island)"
    }
}

private const val PREF_CROSSING_MARKING_EXTENDED = "qs_AddCrossingMarkings_extended"
