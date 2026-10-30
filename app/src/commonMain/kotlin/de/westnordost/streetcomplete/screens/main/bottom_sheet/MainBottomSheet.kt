package de.westnordost.streetcomplete.screens.main.bottom_sheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import de.westnordost.osmfeatures.Feature
import de.westnordost.streetcomplete.ApplicationConstants
import de.westnordost.streetcomplete.data.osm.edits.ElementEditAction
import de.westnordost.streetcomplete.data.osm.edits.ElementEditType
import de.westnordost.streetcomplete.data.osm.edits.MapDataWithEditsSource
import de.westnordost.streetcomplete.data.osm.edits.addNodeEdit
import de.westnordost.streetcomplete.data.osm.edits.create.CreateNodeAction
import de.westnordost.streetcomplete.data.osm.edits.create.createNodeAction
import de.westnordost.streetcomplete.data.osm.edits.tagEdit
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.geometry.ElementPointGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.quest.ExternalSourceQuestKey
import de.westnordost.streetcomplete.data.osmnotes.Note
import de.westnordost.streetcomplete.data.osmtracks.Trackpoint
import de.westnordost.streetcomplete.data.quest.OsmNoteQuestKey
import de.westnordost.streetcomplete.data.quest.OsmQuestKey
import de.westnordost.streetcomplete.data.quest.QuestKey
import de.westnordost.streetcomplete.quests.note_comments.AddNoteCommentForm
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.quest_create_note
import de.westnordost.streetcomplete.screens.main.ShownBottomSheet
import de.westnordost.streetcomplete.screens.main.bottom_sheet.note.CreateNoteForm
import de.westnordost.streetcomplete.screens.main.bottom_sheet.overlay.OverlayFormContainer
import de.westnordost.streetcomplete.screens.main.bottom_sheet.quest.OsmQuestFormContainer
import de.westnordost.streetcomplete.ui.common.dialogs.SurveyConfirmationDialog
import de.westnordost.streetcomplete.ui.common.quest.LocalMapOverlayCallback
import de.westnordost.streetcomplete.ui.common.quest.LocalMapMarkersCallback
import de.westnordost.streetcomplete.ui.common.quest.LocalMapMetersPerDp
import de.westnordost.streetcomplete.ui.common.quest.MapClick
import de.westnordost.streetcomplete.ui.common.quest.MapOverlayContent
import de.westnordost.streetcomplete.ui.common.quest.Marker
import de.westnordost.streetcomplete.util.math.PositionOnWay
import org.jetbrains.compose.resources.DrawableResource
import org.koin.compose.koinInject

/**
 * Everything that happens in the bottom sheet displayed in the main screen happens here.
 *
 * It actually ought to be displayed at full size, because bottom sheets may have elements that
 * should be displayed above the actual bottom sheet form (such as a crosshairs, or the arrow when
 * moving a node). So, the actual sliding up/down of the bottom sheet(s) is handled by the forms
 * individually. */
@Composable
fun MainBottomSheet(
    onDismiss: () -> Unit,
    onSolved: (icon: DrawableResource, position: LatLon) -> Unit,
    onHideQuest: (QuestKey, Boolean) -> Unit,
    isSurvey: (ElementGeometry) -> Boolean,
    onSubmitEdit: (ElementEditType, ElementGeometry, ElementEditAction, Boolean, QuestKey?) -> Unit,
    onCommentNote: (Note, String?, List<String>, Boolean) -> Unit,
    onCreateNote: (LatLon, String, List<String>, List<Trackpoint>?, Boolean) -> Unit,
    shownBottomSheet: ShownBottomSheet,
    mapRotation: Float,
    mapTilt: Float,
    mapPosition: LatLon,
    mapMetersPerDp: Double,
    onSetMapMarkers: (Iterable<Marker>?) -> Unit,
    onSetMapOverlay: (MapOverlayContent?) -> Unit,
    lastMapClick: MapClick?,
    modifier: Modifier = Modifier
) {
    var confirmEdit by remember { mutableStateOf<PendingEdit?>(null) }
    var submitted by remember { mutableStateOf(false) }

    fun submitEdit(
        elementEditType: ElementEditType,
        geometry: ElementGeometry,
        elementEditAction: ElementEditAction,
        hasExtra: Boolean = false,
        key: QuestKey? = null,
    ) {
        if (submitted) return
        submitted = true
        onSubmitEdit(elementEditType, geometry, elementEditAction, hasExtra, key)
    }

    fun requestEdit(edit: PendingEdit) {
        if (SuppressSurveyConfirmation || isSurvey(edit.geometry)) {
            submitEdit(edit.elementEditType, edit.geometry, edit.elementEditAction, edit.hasExtra, edit.key)
            onSolved(shownBottomSheet.icon, shownBottomSheet.position ?: edit.geometry.center)
            onDismiss()
        } else {
            confirmEdit = edit
        }
    }

    fun createNote(
        position: LatLon,
        text: String,
        imagePaths: List<String>,
        trackpoints: List<Trackpoint>? = null,
        isGpx: Boolean = false,
    ) {
        if (submitted) return
        submitted = true
        onCreateNote(position, text, imagePaths, trackpoints, isGpx)
    }

    fun commentNote(note: Note, text: String?, imagePaths: List<String>, close: Boolean) {
        if (submitted) return
        submitted = true
        onCommentNote(note, text, imagePaths, close)
    }

    when (shownBottomSheet) {
        is ShownBottomSheet.CreateOsmNote -> {
            CreateNoteForm(
                onLeaveNote = { noteText, noteImagePaths, trackpoints, isGpx ->
                    createNote(
                        position = mapPosition,
                        text = noteText,
                        imagePaths = noteImagePaths,
                        trackpoints = trackpoints,
                        isGpx = isGpx
                    )
                    onSolved(Res.drawable.quest_create_note, mapPosition)
                    onDismiss()
                },
                onDismiss = onDismiss,
                trackpoints = shownBottomSheet.trackpoints,
                lastMapClick = lastMapClick,
                modifier = modifier,
                position = mapPosition,
            )
        }
        is ShownBottomSheet.OsmNoteQuest -> {
            AddNoteCommentForm(
                onDismiss = onDismiss,
                onCommentNote = { noteText, noteImagePaths, close ->
                    commentNote(
                        note = shownBottomSheet.note,
                        text = noteText,
                        imagePaths = noteImagePaths,
                        close = close
                    )
                    onSolved(shownBottomSheet.quest.type.icon, shownBottomSheet.quest.position)
                    onDismiss()
                },
                onHideQuest = {
                    val key = OsmNoteQuestKey(shownBottomSheet.note.id)
                    onHideQuest(key, it)
                    onDismiss()
                },
                quest = shownBottomSheet.quest,
                note = shownBottomSheet.note,
                lastMapClick = lastMapClick,
                modifier = modifier,
            )
        }
        is ShownBottomSheet.OsmQuest -> {
            OsmQuestFormContainer(
                onDismiss = onDismiss,
                onEdit = { action, isTagEdit, isEditInContextOf ->
                    requestEdit(PendingEdit(
                        elementEditType = if (isTagEdit) tagEdit else shownBottomSheet.quest.type,
                        geometry = shownBottomSheet.quest.geometry,
                        elementEditAction = action,
                        hasExtra = isEditInContextOf && !isTagEdit,
                        key = shownBottomSheet.quest.key,
                    ))
                },
                onLeaveNote = { noteText, noteImagePaths, isGpx ->
                    createNote(
                        position = shownBottomSheet.quest.geometry.center,
                        text = noteText,
                        imagePaths = noteImagePaths,
                        isGpx = isGpx
                    )
                    onSolved(shownBottomSheet.quest.type.icon, shownBottomSheet.quest.position)
                    onDismiss()
                },
                onHideQuest = {
                    val key = OsmQuestKey(
                        shownBottomSheet.element.type,
                        shownBottomSheet.element.id, shownBottomSheet.quest.type.name)
                    onHideQuest(key, it)
                    onDismiss()
                },
                questType = shownBottomSheet.quest.type,
                element = shownBottomSheet.element,
                geometry = shownBottomSheet.quest.geometry,
                mapPosition = mapPosition,
                mapRotation = mapRotation,
                mapTilt = mapTilt,
                mapMetersPerDp = mapMetersPerDp,
                onSetMapMarkers = onSetMapMarkers,
                onSetMapOverlay = onSetMapOverlay,
                lastMapClick = lastMapClick,
                modifier = modifier,
            )
        }
        is ShownBottomSheet.ExternalSourceQuest -> {
            ExternalSourceQuestFormContainer(
                onDismiss = onDismiss,
                onEdit = { action ->
                    requestEdit(PendingEdit(
                        elementEditType = shownBottomSheet.quest.type,
                        geometry = shownBottomSheet.quest.geometry,
                        elementEditAction = action,
                        key = shownBottomSheet.quest.key,
                    ))
                },
                onLeaveNote = { noteText, noteImagePaths, isGpx ->
                    createNote(
                        position = shownBottomSheet.quest.geometry.center,
                        text = noteText,
                        imagePaths = noteImagePaths,
                        isGpx = isGpx
                    )
                    onSolved(shownBottomSheet.quest.type.icon, shownBottomSheet.quest.position)
                    onDismiss()
                },
                onHideQuest = {
                    val key = ExternalSourceQuestKey(shownBottomSheet.quest.id, shownBottomSheet.quest.source)
                    onHideQuest(key, it)
                    onDismiss()
                },
                quest = shownBottomSheet.quest,
                mapPosition = mapPosition,
                mapRotation = mapRotation,
                mapTilt = mapTilt,
                mapMetersPerDp = mapMetersPerDp,
                onSetMapMarkers = onSetMapMarkers,
                onSetMapOverlay = onSetMapOverlay,
                lastMapClick = lastMapClick,
                modifier = modifier,
            )
        }
        is ShownBottomSheet.Overlay -> {
            OverlayFormContainer(
                onDismiss = onDismiss,
                onEdit = { action, isTagEdit, isEditInContextOf, editGeometry ->
                    requestEdit(PendingEdit(
                        elementEditType = if (isTagEdit) tagEdit else shownBottomSheet.overlay,
                        geometry = editGeometry ?: shownBottomSheet.geometry ?: ElementPointGeometry(mapPosition),
                        elementEditAction = action,
                        hasExtra = isEditInContextOf && !isTagEdit,
                    ))
                },
                onLeaveNote = { noteText, noteImagePaths, isGpx, notePosition ->
                    val center = notePosition ?: shownBottomSheet.geometry?.center ?: mapPosition
                    createNote(
                        position = center,
                        text = noteText,
                        imagePaths = noteImagePaths,
                        isGpx = isGpx
                    )
                    onSolved(shownBottomSheet.overlay.icon, center)
                    onDismiss()
                },
                overlay = shownBottomSheet.overlay,
                element = shownBottomSheet.element,
                geometry = shownBottomSheet.geometry,
                mapRotation = mapRotation,
                mapTilt = mapTilt,
                mapPosition = mapPosition,
                mapMetersPerDp = mapMetersPerDp,
                onSetMapMarkers = onSetMapMarkers,
                onSetMapOverlay = onSetMapOverlay,
                lastMapClick = lastMapClick,
                modifier = modifier,
            )
        }
        is ShownBottomSheet.AddPoi -> {
            AddPoiForm(
                feature = shownBottomSheet.feature,
                position = mapPosition, //shownBottomSheet.position,
                onAdd = { submitEdit(
                    elementEditType = addNodeEdit,
                    geometry = ElementPointGeometry(it.position),
                    elementEditAction = CreateNodeAction(it.position, it.tags)
                ) },
                onDismiss = onDismiss,
                onSetMapMarkers = onSetMapMarkers,
            )
        }
        is ShownBottomSheet.InsertNode -> {
            var selected by remember { mutableStateOf<Pair<Feature, PositionOnWay>?>(null) }
            if (selected == null) {
                CompositionLocalProvider(
                    LocalMapMetersPerDp provides mapMetersPerDp,
                    LocalMapMarkersCallback provides onSetMapMarkers,
                    LocalMapOverlayCallback provides onSetMapOverlay,
                ) {
                    InsertNodeForm(
                        mapPosition,
                        onDismiss,
                        { f, p -> selected = f to p }
                    )
                }
            } else {
                val mapDataSource: MapDataWithEditsSource = koinInject()
                AddPoiForm(
                    feature = selected!!.first,
                    position = selected!!.second.position,
                    onAdd = { element ->
                        val action = createNodeAction(selected!!.second, mapDataSource) { changeBuilder ->
                            changeBuilder.keys.forEach { if (it !in element.tags) changeBuilder.remove(it) } // remove tags, only relevant if there are startTags
                            element.tags.forEach { changeBuilder[it.key] = it.value } // and add changes
                        }
                        submitEdit(
                            elementEditType = addNodeEdit,
                            geometry = ElementPointGeometry(element.position),
                            elementEditAction = action!!
                        )
                    },
                    onDismiss = onDismiss,
                    onSetMapMarkers = onSetMapMarkers,
                    showPin = false
                )
            }
        }
    }

    confirmEdit?.let { pendingEdit ->
        SurveyConfirmationDialog(
            onDismissRequest = { confirmEdit = null },
            onConfirmed = {
                confirmEdit = null
                submitEdit(
                    pendingEdit.elementEditType,
                    pendingEdit.geometry,
                    pendingEdit.elementEditAction,
                    pendingEdit.hasExtra,
                    pendingEdit.key,
                )
                onDismiss()
            },
            onToggleDontShowAgain = { SuppressSurveyConfirmation = it }
        )
    }
}

private data class PendingEdit(
    val elementEditType: ElementEditType,
    val geometry: ElementGeometry,
    val elementEditAction: ElementEditAction,
    val hasExtra: Boolean = false,
    val key: QuestKey? = null,
)

private var SuppressSurveyConfirmation = ApplicationConstants.DEBUG
