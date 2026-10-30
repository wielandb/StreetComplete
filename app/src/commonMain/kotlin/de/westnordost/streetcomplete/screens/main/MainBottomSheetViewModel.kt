package de.westnordost.streetcomplete.screens.main

import androidx.compose.runtime.Stable
import androidx.compose.ui.text.intl.LocaleList
import androidx.lifecycle.ViewModel
import de.westnordost.osmfeatures.Feature
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.externalsource.ExternalSourceQuest
import de.westnordost.streetcomplete.data.externalsource.ExternalSourceQuestController
import de.westnordost.osmfeatures.FeatureDictionary
import de.westnordost.streetcomplete.data.location.SurveyChecker
import de.westnordost.streetcomplete.data.osm.edits.ElementEditAction
import de.westnordost.streetcomplete.data.osm.edits.ElementEditType
import de.westnordost.streetcomplete.data.osm.edits.ElementEditsController
import de.westnordost.streetcomplete.data.osm.edits.MapDataWithEditsSource
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.geometry.ElementPointGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.BoundingBox
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.osm.osmquests.OsmElementQuestType
import de.westnordost.streetcomplete.data.osm.osmquests.OsmQuest
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataWithGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.MutableMapDataWithGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Node
import de.westnordost.streetcomplete.data.osm.mapdata.Way
import de.westnordost.streetcomplete.data.osm.mapdata.isWayComplete
import de.westnordost.streetcomplete.data.osm.osmquests.OsmQuestSource
import de.westnordost.streetcomplete.data.osmnotes.Note
import de.westnordost.streetcomplete.data.osmnotes.edits.NoteEditAction
import de.westnordost.streetcomplete.data.osmnotes.edits.NoteEditsController
import de.westnordost.streetcomplete.data.osmnotes.edits.NotesWithEditsSource
import de.westnordost.streetcomplete.data.osmnotes.notequests.OsmNoteQuest
import de.westnordost.streetcomplete.data.osmnotes.notequests.OsmNoteQuestSource
import de.westnordost.streetcomplete.data.osmtracks.Trackpoint
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.data.quest.ExternalSourceQuestKey
import de.westnordost.streetcomplete.data.overlays.OverlayRegistry
import de.westnordost.streetcomplete.data.quest.OsmNoteQuestKey
import de.westnordost.streetcomplete.data.quest.OsmQuestKey
import de.westnordost.streetcomplete.data.quest.Quest
import de.westnordost.streetcomplete.data.quest.QuestKey
import de.westnordost.streetcomplete.data.quest.VisibleQuestsSource
import de.westnordost.streetcomplete.data.visiblequests.QuestsHiddenController
import de.westnordost.streetcomplete.osm.getDirection
import de.westnordost.streetcomplete.osm.iconDrawableResource
import de.westnordost.streetcomplete.osm.level.levelsIntersect
import de.westnordost.streetcomplete.osm.level.parseLevelsOrNull
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.screens.main.map.getIcon
import de.westnordost.streetcomplete.screens.main.map.getTitle
import de.westnordost.streetcomplete.ui.common.quest.Marker
import de.westnordost.streetcomplete.util.ktx.launch
import de.westnordost.streetcomplete.util.ktx.truncateTo6Decimals
import de.westnordost.streetcomplete.util.locale.getLanguagesForFeatureDictionary
import de.westnordost.streetcomplete.util.math.enclosingBoundingBox
import de.westnordost.streetcomplete.util.math.enlargedBy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.DrawableResource

@Stable
abstract class MainBottomSheetViewModel : ViewModel() {
    abstract val nextQuest: Flow<QuestKey>

    abstract suspend fun getBottomSheet(selection: MainSheetSelection): ShownBottomSheet?
    abstract suspend fun getHighlights(sheet: ShownBottomSheet): SheetHighlights

    abstract fun hideQuest(questKey: QuestKey, tempHide: Boolean)

    abstract fun isSurvey(geometry: ElementGeometry): Boolean

    abstract fun submitEdit(
        elementEditType: ElementEditType,
        geometry: ElementGeometry,
        elementEditAction: ElementEditAction,
        hasExtra: Boolean = false,
        key: QuestKey? = null,
    )
    abstract fun commentNote(
        note: Note,
        text: String?,
        imagePaths: List<String> = emptyList(),
        close: Boolean
    )
    abstract fun createNote(
        position: LatLon,
        text: String,
        imagePaths: List<String> = emptyList(),
        trackpoints: List<Trackpoint>? = null,
        isGpx: Boolean = false
    )
}

@Stable
class MainBottomSheetViewModelImpl(
    private val mapDataSource: MapDataWithEditsSource,
    private val notesSource: NotesWithEditsSource,
    private val osmQuestSource: OsmQuestSource,
    private val osmNoteQuestSource: OsmNoteQuestSource,
    private val elementEditsController: ElementEditsController,
    private val noteEditsController: NoteEditsController,
    private val hiddenQuestsController: QuestsHiddenController,
    private val surveyChecker: SurveyChecker,
    private val externalSource: ExternalSourceQuestController,
    private val visibleQuestsSource: VisibleQuestsSource,
    private val prefs: Preferences,
    private val overlayRegistry: OverlayRegistry,
    private val featureDictionary: Lazy<FeatureDictionary>,
) : MainBottomSheetViewModel() {
    private val nextQuests = Channel<QuestKey>(Channel.BUFFERED)
    override val nextQuest = nextQuests.receiveAsFlow()

    override suspend fun getBottomSheet(selection: MainSheetSelection): ShownBottomSheet? =
        withContext(Dispatchers.IO) { load(selection) }

    private fun load(selection: MainSheetSelection): ShownBottomSheet? = when (selection) {
        is MainSheetSelection.Quest -> getQuestBottomSheet(selection)
        is MainSheetSelection.Overlay -> getOverlayBottomSheet(selection)
        is MainSheetSelection.CreateNote -> ShownBottomSheet.CreateOsmNote(selection.trackpoints)
        is MainSheetSelection.AddPoi -> featureDictionary.value.getById(
            id = selection.featureId,
            languages = getLanguagesForFeatureDictionary()
        )?.let { ShownBottomSheet.AddPoi(selection.position, it) }
        is MainSheetSelection.InsertNode -> ShownBottomSheet.InsertNode(selection.position)
        is MainSheetSelection.EditHistory -> null
    }

    private fun getQuestBottomSheet(selection: MainSheetSelection.Quest): ShownBottomSheet? {
        val key = selection.key
        val visibleQuest = visibleQuestsSource.get(key) ?: return null
        return when (key) {
            is OsmQuestKey -> {
                val quest = if (prefs.getBoolean(Prefs.DYNAMIC_QUEST_CREATION, false)) {
                    visibleQuest as? OsmQuest
                } else {
                    osmQuestSource.get(key)
                } ?: return null
                val element = mapDataSource.get(key.elementType, key.elementId) ?: return null
                ShownBottomSheet.OsmQuest(quest, element)
            }
            is ExternalSourceQuestKey -> {
                val quest = externalSource.get(key) ?: return null
                ShownBottomSheet.ExternalSourceQuest(quest)
            }
            is OsmNoteQuestKey -> {
                val quest = osmNoteQuestSource.get(key.noteId) ?: return null
                val note = notesSource.get(key.noteId) ?: return null
                ShownBottomSheet.OsmNoteQuest(quest, note)
            }
        }
    }

    private fun getOverlayBottomSheet(selection: MainSheetSelection.Overlay): ShownBottomSheet? {
        val overlay = overlayRegistry.getByName(selection.name) ?: return null
        val key = selection.elementKey
        return if (key == null) {
            // overlay form for new element
            ShownBottomSheet.Overlay(overlay, null, null)
        } else {
            val geometry = mapDataSource.getGeometry(key.type, key.id) ?: return null
            // A note at the position of the element blocks editing that element. Instead, a
            // note quest will be shown
            val note = getNoteForElementAt(geometry.center)
            if (note != null) {
                // ... even if it is usually not visible (due to not containing a question or the
                // last commenter being this user, see `Note.shouldShowAsQuest`).
                // For this reason, we don't use
                // `val quest = osmNoteQuestSource.get(note.id)` here.
                val quest = OsmNoteQuest(note.id, geometry.center)
                ShownBottomSheet.OsmNoteQuest(quest, note)
            } else {
                val element = mapDataSource.get(key.type, key.id) ?: return null
                ShownBottomSheet.Overlay(overlay, element, geometry)
            }
        }
    }

    override suspend fun getHighlights(sheet: ShownBottomSheet): SheetHighlights = withContext(Dispatchers.IO) {
        val quest = when (sheet) {
            is ShownBottomSheet.OsmQuest -> sheet.quest
            is ShownBottomSheet.ExternalSourceQuest -> sheet.quest
            is ShownBottomSheet.OsmNoteQuest -> sheet.quest
            else -> return@withContext SheetHighlights()
        }
        val element = when (sheet) {
            is ShownBottomSheet.OsmQuest -> sheet.element
            is ShownBottomSheet.ExternalSourceQuest -> sheet.quest.elementKey?.let { mapDataSource.get(it.type, it.id) }
            else -> null
        }
        val highlightedElements = getHighlightedElements(quest, element)
        val nearby = if (prefs.getInt(Prefs.SHOW_NEARBY_QUESTS, 0) != 0) {
            val distance = prefs.getFloat(Prefs.SHOW_NEARBY_QUESTS_DISTANCE, 0f).toDouble() + 0.01
            nearbyQuestHighlights(quest, visibleQuestsSource.getNearbyQuests(quest, distance))
        } else SheetHighlights()
        nearby.copy(markers = mergeHighlightMarkers(highlightedElements, nearby.markers))
    }

    private fun getHighlightedElements(quest: Quest, element: Element?): List<Marker> {
        val radius = when (quest) {
            is OsmQuest -> quest.type.highlightedElementsRadius
            is ExternalSourceQuest -> quest.type.highlightedElementsRadius
            else -> return emptyList()
        }
        val bbox = quest.geometry.bounds.enlargedBy(radius)
        val mapData: MapDataWithGeometry by lazy {
            mapDataSource.getMapDataWithGeometry(bbox).also { data ->
                // Keep all nodes of the selected way available, even beyond the local bounds.
                if (data is MutableMapDataWithGeometry && element is Way && !data.isWayComplete(element.id)) {
                    mapDataSource.getWayComplete(element.id)?.nodes?.forEach {
                        data.put(it, ElementPointGeometry(it.position))
                    }
                }
            }
        }
        val elements = when (quest) {
            is OsmQuest -> element?.let { quest.type.getHighlightedElements(it, mapData) } ?: emptySequence()
            is ExternalSourceQuest -> quest.type.getHighlightedElements { mapData }
            else -> emptySequence()
        }
        val levels = element?.let { parseLevelsOrNull(it.tags) }
        val filterLevels = (quest as? OsmQuest)?.type?.hideHighlightedElementsOnDifferentLevel != false
        val languages = LocaleList.current.localeList.map { it.language }
        return elements.mapNotNull { other ->
            if (element == other) return@mapNotNull null
            if (filterLevels) {
                if (!levels.levelsIntersect(parseLevelsOrNull(other.tags))) return@mapNotNull null
                if (element?.tags?.get("layer") != other.tags["layer"] && other.tags["bridge"] == null) return@mapNotNull null
            }
            val geometry = mapData.getGeometry(other.type, other.id) ?: return@mapNotNull null
            Marker(
                geometry, getIcon(featureDictionary.value, other), getTitle(other.tags, languages),
                direction = (other as? Node)?.getDirection(mapDataSource),
            )
        }.toList()
    }

    override fun hideQuest(questKey: QuestKey, tempHide: Boolean) {
        launch(Dispatchers.IO) {
            if (tempHide) hiddenQuestsController.tempHide(questKey)
            else hiddenQuestsController.hide(questKey)
        }
    }

    override fun isSurvey(geometry: ElementGeometry): Boolean =
        surveyChecker.checkIsSurvey(geometry)

    override fun submitEdit(
        elementEditType: ElementEditType,
        geometry: ElementGeometry,
        elementEditAction: ElementEditAction,
        hasExtra: Boolean,
        key: QuestKey?,
    ) {
        launch(Dispatchers.IO) {
            val isNearUserLocation = surveyChecker.checkIsSurvey(geometry)
            val source = if (hasExtra) "survey,extra" else "survey"
            elementEditsController.add(elementEditType, geometry, source, elementEditAction, isNearUserLocation, key)
            if (key is OsmQuestKey && prefs.getBoolean(Prefs.DYNAMIC_QUEST_CREATION, false)) {
                // Dynamic quests have no database entry whose removal would hide their pin.
                hiddenQuestsController.tempHide(key)
            }
            if (elementEditType !is OsmElementQuestType<*> || !prefs.getBoolean(Prefs.SHOW_NEXT_QUEST_IMMEDIATELY, false))
                return@launch
            val quest = visibleQuestsSource.getAll(geometry.center.enclosingBoundingBox(0.5))
                .filterIsInstance<OsmQuest>()
                .firstOrNull { it.geometry == geometry && it.type.dotColor == null }
            if (quest == null) return@launch
            nextQuests.send(quest.key)
        }
    }

    override fun commentNote(
        note: Note,
        text: String?,
        imagePaths: List<String>,
        close: Boolean
    ) {
        launch(Dispatchers.IO) {
            val action = if (close) NoteEditAction.CLOSE else NoteEditAction.COMMENT
            noteEditsController.add(note.id, action, note.position, text, imagePaths)
        }
    }

    override fun createNote(
        position: LatLon,
        text: String,
        imagePaths: List<String>,
        trackpoints: List<Trackpoint>?,
        isGpx: Boolean
    ) {
        launch(Dispatchers.IO) {
            noteEditsController.add(0, NoteEditAction.CREATE, position, text, imagePaths, trackpoints, isGpx)
        }
    }

    private fun getNoteForElementAt(position: LatLon): Note? =
        notesSource
            .getAll(BoundingBox(position, position).enlargedBy(0.2))
            .filter { note ->
                note.position.truncateTo6Decimals() == position.truncateTo6Decimals() &&
                hiddenQuestsController.get(OsmNoteQuestKey(note.id)) == null
            }.firstOrNull()
}

/** The data necessary to show an element from the map clicked on in the bottom sheet */
sealed interface ShownBottomSheet {
    data class OsmQuest(
        val quest: de.westnordost.streetcomplete.data.osm.osmquests.OsmQuest,
        val element: Element,
    ) : ShownBottomSheet {
        override val position get() = quest.position
        override val geometry get() = quest.geometry
        override val icon get() = quest.type.icon
    }

    data class ExternalSourceQuest(
        val quest: de.westnordost.streetcomplete.data.externalsource.ExternalSourceQuest,
    ) : ShownBottomSheet {
        override val position get() = quest.position
        override val geometry get() = quest.geometry
        override val icon get() = quest.type.icon
    }

    data class OsmNoteQuest(
        val quest: de.westnordost.streetcomplete.data.osmnotes.notequests.OsmNoteQuest,
        val note: Note
    ) : ShownBottomSheet {
        override val position get() = quest.position
        override val geometry get() = quest.geometry
        override val icon get() = quest.type.icon
    }

    data class Overlay(
        val overlay: de.westnordost.streetcomplete.data.overlays.Overlay,
        val element: Element?,
        override val geometry: ElementGeometry?,
    ) : ShownBottomSheet {
        override val position get() = geometry?.center
        override val icon get() = overlay.icon
    }

    data class CreateOsmNote(
        val trackpoints: List<Trackpoint>?
    ) : ShownBottomSheet {
        override val position get() = null
        override val geometry get() = null
        override val icon get() = Res.drawable.quest_create_note
    }

    data class AddPoi(override val position: LatLon, val feature: Feature) : ShownBottomSheet {
        override val geometry get() = ElementPointGeometry(position)
        override val icon get() = feature.iconDrawableResource ?: Res.drawable.ic_add_poi
    }

    data class InsertNode(override val position: LatLon) : ShownBottomSheet {
        override val geometry get() = ElementPointGeometry(position)
        override val icon get() = Res.drawable.ic_add_poi
    }

    val position: LatLon?
    val geometry: ElementGeometry?
    val icon: DrawableResource
}
