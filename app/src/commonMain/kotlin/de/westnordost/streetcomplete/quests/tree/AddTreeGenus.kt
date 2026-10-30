package de.westnordost.streetcomplete.quests.tree

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataWithGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.filter
import de.westnordost.streetcomplete.data.osm.osmquests.OsmFilterQuestType
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.common.dialogs.InfoDialog
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.copyTo
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.nameWithoutExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

class AddTreeGenus : OsmFilterQuestType<TreeAnswer>() {

    override val elementFilter = """
        nodes with
          natural = tree
          and !genus and !species and !taxon
          and !~"genus:.*" and !~"species:.*" and !~"taxon:.*"
    """
    override val changesetComment = "Add tree genus/species"
    override val defaultDisabledMessage = Res.string.quest_tree_disabled_msg
    override val wikiLink = "Key:genus"
    override val icon = Res.drawable.quest_tree
    override val title = Res.string.quest_tree_genus_title

    override fun getHighlightedElements(element: Element, mapData: MapDataWithGeometry) =
        mapData.filter("nodes with natural = tree")

    @Composable
    override fun Form(on: (QuestAction<TreeAnswer>) -> Unit, element: Element, geometry: ElementGeometry, countryInfo: CountryInfo) {
        TreeGenusForm(on)
    }

    override fun applyAnswerTo(answer: TreeAnswer, tags: Tags, geometry: ElementGeometry, timestampEdited: Long) {
        when (answer) {
            is NotTreeButStump -> tags["natural"] = "tree_stump"
            is Tree -> {
                if (answer.isSpecies)
                    tags["species"] = answer.name
                else
                    tags["genus"] = answer.name
            }
        }
    }

    @Composable
    override fun QuestSettings(onDismissRequest: () -> Unit) {
        var showElementSelection by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val revision by treeDataRevision.collectAsState()
        val hasTreeFile = remember(revision) { treeFile.exists() }
        fun import() {
            scope.launch {
                val file = FileKit.openFilePicker() ?: return@launch
                withContext(Dispatchers.IO) { readFromFile(file) }
            }
        }
        fun export() {
            scope.launch {
                val file = FileKit.openFileSaver(treeFile.nameWithoutExtension, defaultExtension = treeFile.extension) ?: return@launch
                withContext(Dispatchers.IO) { treeFile.copyTo(file) }
            }
        }
        InfoDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(stringResource(Res.string.pref_trees_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.tree_custom_quest_import_export_message))
                    Button({ import() }, Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.tree_custom_quest_import))
                    }
                    if (hasTreeFile)
                        Button({ export() }, Modifier.fillMaxWidth()) {
                            Text(stringResource(Res.string.tree_custom_quest_export))
                        }
                    Button({ showElementSelection = true }, Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.element_selection_button))
                    }
                }
            }
        )
        if (showElementSelection)
            super.QuestSettings(onDismissRequest)
    }

    companion object {
        suspend fun readFromFile(file: PlatformFile) {
            file.copyTo(treeFile)
            treeDataRevision.value += 1
        }
    }
}
