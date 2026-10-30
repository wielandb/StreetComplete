package de.westnordost.streetcomplete.quests.osmose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import de.westnordost.osmfeatures.FeatureDictionary
import de.westnordost.streetcomplete.data.externalsource.ExternalSourceQuest
import de.westnordost.streetcomplete.data.externalsource.ExternalSourceQuestController
import de.westnordost.streetcomplete.data.osm.edits.MapDataWithEditsSource
import de.westnordost.streetcomplete.data.osm.edits.update_tags.UpdateElementTagsAction
import de.westnordost.streetcomplete.data.osm.mapdata.ElementKey
import de.westnordost.streetcomplete.data.osm.osmquests.Action
import de.westnordost.streetcomplete.data.osm.osmquests.EditElement
import de.westnordost.streetcomplete.data.osm.osmquests.ExternalAction
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.quests.questPrefix
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.screens.main.bottom_sheet.EditTagsForm
import de.westnordost.streetcomplete.ui.common.dialogs.AlertDialog
import de.westnordost.streetcomplete.ui.common.dialogs.ConfirmationDialog
import de.westnordost.streetcomplete.ui.common.quest.AnswerItem
import de.westnordost.streetcomplete.ui.common.quest.LocalElement
import de.westnordost.streetcomplete.ui.common.quest.LocalMapMarkersCallback
import de.westnordost.streetcomplete.ui.common.quest.Marker
import de.westnordost.streetcomplete.ui.util.rememberSerializable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import de.westnordost.streetcomplete.ui.common.quest.QuestForm
import de.westnordost.streetcomplete.util.nameAndLocationLabel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun OsmoseForm(on: (ExternalAction) -> Unit, quest: ExternalSourceQuest) {
    val osmoseDao: OsmoseDao = koinInject()
    val questController: ExternalSourceQuestController = koinInject()
    val mapDataSource: MapDataWithEditsSource = koinInject()
    val issue = remember(quest.key) { osmoseDao.getIssue(quest.key.id) }
    if (issue == null) {
        LaunchedEffect(quest.key) {
            questController.delete(quest.key)
            on(Action.TempHideQuest)
        }
        return
    }
    val setMarkers by rememberUpdatedState(LocalMapMarkersCallback.current)
    val markers by produceState<List<Marker>>(emptyList(), issue.elements, mapDataSource) {
        value = if (issue.elements.size <= 1) emptyList() else withContext(Dispatchers.IO) {
            issue.elements.mapNotNull { key ->
                mapDataSource.getGeometry(key.type, key.id)?.let {
                    Marker(it, title = "${key.type} ${key.id}")
                }
            }
        }
    }
    LaunchedEffect(markers) { setMarkers?.invoke(markers) }
    DisposableEffect(quest.key) { onDispose { setMarkers?.invoke(emptyList()) } }
    val featureDictionary: FeatureDictionary = koinInject()
    var confirmFalsePositive by rememberSaveable(quest.key) { mutableStateOf(false) }
    var editTags by rememberSaveable(quest.key) { mutableStateOf(false) }
    var elementToEdit by rememberSerializable(quest.key) { mutableStateOf<ElementKey?>(null) }
    var showIgnoreDialog by rememberSaveable(quest.key) { mutableStateOf(false) }
    if (elementToEdit != null) {
        val element = mapDataSource.get(elementToEdit!!.type, elementToEdit!!.id)
        if (element == null) elementToEdit = null
        else EditTagsForm(
            { on(EditElement(UpdateElementTagsAction(element, it))); osmoseDao.setDone(issue.uuid); on(Action.TempHideQuest) },
            { elementToEdit = null },
            element,
            elementGeometry = mapDataSource.getGeometry(element.type, element.id) ?: quest.geometry,
        )
    }
    if (elementToEdit == null) {
        QuestForm(
            on,
            answers = listOfNotNull(
                if (issue.elements.isEmpty()) null else
                    AnswerItem(stringResource(Res.string.quest_generic_answer_show_edit_tags)) {
                        editTags = true
                    },
                AnswerItem(stringResource(Res.string.quest_osmose_false_positive)) {
                    confirmFalsePositive = true
                }
            ),
            otherAnswers = { listOf(
                AnswerItem(stringResource(Res.string.quest_osmose_hide_type)) { showIgnoreDialog = true },
                AnswerItem(stringResource(Res.string.quest_osmose_delete_this_issue)) {
                    questController.delete(quest.key)
                    on(Action.TempHideQuest)
                },
            ) },
            title = stringResource(Res.string.quest_osmose_title) + " ${issue.title}",
            subtitle = LocalElement.current?.let { element ->
                nameAndLocationLabel(element, featureDictionary)
            }
        ) {
            Text(stringResource(Res.string.quest_osmose_message_for_element, "${issue.item}/${issue.itemClass}", issue.subtitle))
            if (confirmFalsePositive) {
                ConfirmationDialog(
                    { confirmFalsePositive = false },
                    // todo: do some kind of edit, so it can be undone? the edit could be deleted on upload (see also ExternalSourceModule commented stuff)
                    { osmoseDao.setAsFalsePositive(issue.uuid); on(Action.TempHideQuest) },
                    title = { Text(stringResource(Res.string.quest_osmose_false_positive)) },
                    confirmButtonText = stringResource(Res.string.quest_generic_confirmation_yes),
                    text = { Text(stringResource(Res.string.quest_osmose_no_undo)) }
                )
            }

        }
    }
    if (editTags) {
        if (issue.elements.size == 1) {
            elementToEdit = issue.elements.single()
            editTags = false
        } else {
            AlertDialog(
                onDismissRequest = { editTags = false },
                buttonRow = { TextButton({ editTags = false }) { Text(stringResource(Res.string.cancel)) } },
                title = { Text(stringResource(Res.string.quest_osmose_select_element)) },
                text = {
                    Column {
                        issue.elements.forEach {
                            TextButton({ elementToEdit = it; editTags = false }, Modifier.fillMaxWidth()) { Text("${it.type} ${it.id}") }
                        }
                    }
                }
            )
        }
    }
    if (showIgnoreDialog) {
        val prefs: Preferences = koinInject()
        fun addToIgnoreList(item: String) {
            val types = prefs.getString(questPrefix(prefs) + PREF_OSMOSE_ITEMS, OSMOSE_DEFAULT_IGNORED_ITEMS)
                .split("§§")
                .mapNotNull { if (it.isNotBlank()) it.trim() else null }
                .toMutableSet()
            types.add(item)
            prefs.putString(questPrefix(prefs) + PREF_OSMOSE_ITEMS,types.sorted().joinToString("§§"))
            osmoseDao.reloadIgnoredItems()
            questController.invalidate()
            on(Action.TempHideQuest)
        }
        AlertDialog(
            onDismissRequest = { showIgnoreDialog = false },
            buttonRow = { TextButton({ showIgnoreDialog = false }) { Text(stringResource(Res.string.cancel)) } },
            title = { Text(stringResource(Res.string.quest_osmose_hide_type)) },
            text = {
                Column {
                    TextButton({ addToIgnoreList(issue.item.toString()) }) { Text("item: ${issue.item}") }
                    TextButton({ addToIgnoreList("${issue.item}/${issue.itemClass}") }) { Text("item/class: ${issue.item}/${issue.itemClass}") }
                    if (issue.subtitle.isNotBlank())
                        TextButton({ addToIgnoreList(issue.subtitle) }) { Text("subtitle: ${issue.subtitle}") }
                }
            }
        )
    }
}
