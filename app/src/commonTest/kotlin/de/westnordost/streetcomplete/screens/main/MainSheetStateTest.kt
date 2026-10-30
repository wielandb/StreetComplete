package de.westnordost.streetcomplete.screens.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.snapshots.Snapshot
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.edithistory.Edit
import de.westnordost.streetcomplete.data.edithistory.EditKey
import de.westnordost.streetcomplete.data.edithistory.ElementEditKey
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.data.osm.edits.ElementEditAction
import de.westnordost.streetcomplete.data.osm.edits.ElementEditType
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.osmnotes.Note
import de.westnordost.streetcomplete.data.osmtracks.Trackpoint
import de.westnordost.streetcomplete.data.quest.QuestKey
import de.westnordost.streetcomplete.screens.main.edithistory.EditHistoryViewModel
import de.westnordost.streetcomplete.screens.main.edithistory.EditItem
import de.westnordost.streetcomplete.testutils.mockPrefs2
import de.westnordost.streetcomplete.testutils.osmQuestKey
import de.westnordost.streetcomplete.testutils.pGeom
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class MainSheetStateTest {
    @Test fun `opening history selects newest edit by default`() = runTest {
        val fixture = observeState()
        fixture.state.showEditHistory()
        assertEquals(MainSheetSelection.EditHistory(ElementEditKey(2)), fixture.state.selection)
    }

    @Test fun `disabled automatic selection keeps history open without a selected edit`() = runTest {
        val fixture = observeState(selectFirstEdit = false)
        fixture.state.showEditHistory()
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertEquals(MainSheetSelection.EditHistory(null), fixture.state.selection)
        assertNull(fixture.state.shownEdit)

        fixture.edits.value = listOf(editItem(3))
        runCurrent()
        assertEquals(MainSheetSelection.EditHistory(null), fixture.state.selection)
        assertNull(fixture.state.shownEdit)

        fixture.edits.value = emptyList()
        runCurrent()
        assertNull(fixture.state.selection)
    }

    @Test fun `empty history does not open a sidebar`() = runTest {
        val fixture = observeState(initialEdits = emptyList())
        fixture.state.showEditHistory()
        assertNull(fixture.state.selection)
    }

    @Test fun `next quest opens after the previous form closes`() = runTest {
        val fixture = observeState()
        val next = osmQuestKey()
        fixture.requests.emit(next)
        runCurrent()
        assertEquals(MainSheetSelection.Quest(next), fixture.state.selection)
    }

    @Test fun `saving an edit does not replace a newly opened form with its next quest`() = runTest {
        val fixture = observeState()
        val current = MainSheetSelection.Overlay("BuildingsOverlay")
        fixture.state.show(current)
        fixture.requests.emit(osmQuestKey())
        runCurrent()
        assertEquals(current, fixture.state.selection)
    }

    private fun TestScope.observeState(
        selectFirstEdit: Boolean = true,
        initialEdits: List<EditItem> = listOf(editItem(1), editItem(2)),
    ): Fixture {
        val requests = MutableSharedFlow<QuestKey>(extraBufferCapacity = 1)
        val viewModel = SheetViewModel(requests)
        val edits = MutableStateFlow<List<EditItem>?>(initialEdits)
        val editHistory = HistoryViewModel(edits)
        val settings = mockPrefs2()
        settings.putBoolean(Prefs.SELECT_FIRST_EDIT, selectFirstEdit)
        val holder = object : SaveableStateHolder {
            @Composable
            override fun SaveableStateProvider(key: Any, content: @Composable () -> Unit) = content()
            override fun removeState(key: Any) = Unit
        }
        val state = MainSheetState(
            viewModel, editHistory, holder, mutableStateOf(null), mutableStateOf(""), Preferences(settings)
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.observe() }
        return Fixture(state, edits, requests)
    }

    private fun editItem(id: Long): EditItem {
        val edit = mock<Edit> { every { key } returns ElementEditKey(id) }
        return EditItem(edit, showDate = false, showTime = false)
    }

    // Lifecycle ViewModel has final bookkeeping methods that are not mockable. These doubles
    // provide only the state used by MainSheetState and fail on unexpected editing operations.
    private class SheetViewModel(override val nextQuest: Flow<QuestKey>) : MainBottomSheetViewModel() {
        override suspend fun getBottomSheet(selection: MainSheetSelection) = ShownBottomSheet.CreateOsmNote(null)
        override suspend fun getHighlights(sheet: ShownBottomSheet): SheetHighlights = error("Unexpected highlight request")
        override fun hideQuest(questKey: QuestKey, tempHide: Boolean): Unit = error("Unexpected quest hiding")
        override fun isSurvey(geometry: ElementGeometry) = false
        override fun submitEdit(
            elementEditType: ElementEditType,
            geometry: ElementGeometry,
            elementEditAction: ElementEditAction,
            hasExtra: Boolean,
            key: QuestKey?,
        ): Unit = error("Unexpected edit submission")
        override fun commentNote(note: Note, text: String?, imagePaths: List<String>, close: Boolean): Unit =
            error("Unexpected note comment")
        override fun createNote(
            position: LatLon,
            text: String,
            imagePaths: List<String>,
            trackpoints: List<Trackpoint>?,
            isGpx: Boolean,
        ): Unit = error("Unexpected note creation")
    }

    private class HistoryViewModel(override val editItems: StateFlow<List<EditItem>?>) : EditHistoryViewModel() {
        override suspend fun getEditElement(edit: Edit): Element? = null
        override suspend fun getEditGeometry(edit: Edit) = pGeom()
        override fun undo(editKey: EditKey): Unit = error("Unexpected undo")
        override fun updateEdits() = Unit
    }

    private data class Fixture(
        val state: MainSheetState,
        val edits: MutableStateFlow<List<EditItem>?>,
        val requests: MutableSharedFlow<QuestKey>,
    )
}
