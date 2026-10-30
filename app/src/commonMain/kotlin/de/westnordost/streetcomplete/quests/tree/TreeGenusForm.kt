package de.westnordost.streetcomplete.quests.tree

import de.westnordost.streetcomplete.util.platform.internString

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import de.westnordost.streetcomplete.data.osm.edits.MapDataWithEditsSource
import de.westnordost.streetcomplete.data.osm.mapdata.Node
import de.westnordost.streetcomplete.data.osm.osmquests.Answer
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.data.preferences.addLastPicked
import de.westnordost.streetcomplete.data.preferences.getLastPicked
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.quest_leafType_tree_is_just_a_stump
import de.westnordost.streetcomplete.screens.main.map.getTreeGenus
import de.westnordost.streetcomplete.ui.common.auto_complete_text.AutoCompleteTextField
import de.westnordost.streetcomplete.ui.common.quest.AnswerItem
import de.westnordost.streetcomplete.ui.common.quest.LocalLastMapClick
import de.westnordost.streetcomplete.ui.common.quest.QuestForm
import de.westnordost.streetcomplete.ui.theme.largeInput
import de.westnordost.streetcomplete.util.math.distanceTo
import de.westnordost.streetcomplete.util.math.enclosingBoundingBox
import de.westnordost.streetcomplete.util.takeFavorites
import de.westnordost.streetcomplete.util.platform.normalizeToNfd
import de.westnordost.streetcomplete.util.logs.Log
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.readString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun TreeGenusForm(on: (QuestAction<TreeAnswer>) -> Unit) {
    val revision by treeDataRevision.collectAsState()
    val treeSet by produceState<Set<Tree>>(emptySet(), revision) {
        value = withContext(Dispatchers.IO) { loadTrees() }
    }
    var name by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var selectedTree by remember { mutableStateOf<Tree?>(null) }
    val prefs: Preferences = koinInject()
    val lastPicked = remember { prefs.getLastPicked<String>("AddTreeGenusForm").takeFavorites(20, 50, 1) }
    LaunchedEffect(name, treeSet) { selectedTree = getSelectedTree(name.text.trim(), lastPicked, treeSet) }

    val mapDataSource: MapDataWithEditsSource = koinInject()
    val mapClick = LocalLastMapClick.current
    LaunchedEffect(mapClick) { // todo: this doesn't work, also for road name form -> maybe SC issue?
        if (mapClick == null) return@LaunchedEffect
        val position = mapClick.position
        val maxDist = mapClick.clickAreaSizeInMeters + 5
        val data = mapDataSource.getMapDataWithGeometry(position.enclosingBoundingBox(maxDist))
        var bestTree: Pair<String, Double>? = null

        data.forEach { element ->
            if (element is Node && element.tags["natural"] == "tree") {
                val name = getTreeGenus(element.tags) ?: return@forEach
                val distance = element.position.distanceTo(position)
                if (distance < (bestTree?.second ?: maxDist))
                    bestTree = Pair(name, distance)
            }
        }
        val bestName = bestTree?.let { getTrees(it.first, lastPicked, treeSet).firstOrNull()?.toDisplayString() }
        if (bestName != null) name = TextFieldValue(bestName)
    }

    QuestForm(
        on,
        isComplete = selectedTree != null,
        onClickOk = {
            val tree = selectedTree!!
            prefs.addLastPicked("AddTreeGenusForm", "${tree.isSpecies}§${tree.name}")
            on(Answer(tree))
        },
        otherAnswers = { listOf(AnswerItem(stringResource(Res.string.quest_leafType_tree_is_just_a_stump)) {
            on(Answer(NotTreeButStump))})
        }
    ) {
        AutoCompleteTextField(
            value = name,
            onValueChange = { name = it },
            suggestions = getTrees(name.text.trim(), lastPicked, treeSet).map { it.toDisplayString() },
            textStyle = MaterialTheme.typography.largeInput,
            startExpanded = true,
            startExpandedWithoutFocus = true,
            filterSuggestions = false,
        )
    }
}

internal val treeDataRevision = MutableStateFlow(0L)

private suspend fun loadTrees(): Set<Tree> {
    val treeSet = mutableSetOf<Tree>()
    // load from file, assuming format: <Genus/Species> (<localName>)
    //  assume species if it contains a space character
    try {
        if (treeFile.exists())
            treeSet.addAll(treeFile.readString().lines().mapNotNull { it.toTree(it.substringBefore(" (").contains(" ")) })
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) { Log.w("TreeGenusForm", "Cannot read custom tree list", e) }

    try {
        for ((file, isSpecies) in listOf(
            "otherDataGenus.txt" to false, "otherDataSpecies.txt" to true,
            "osmGenus.txt" to false, "osmSpecies.txt" to true,
        )) {
            Res.readBytes("files/tree/$file").decodeToString().lineSequence()
                .mapNotNullTo(treeSet) { it.toTree(isSpecies) }
        }
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) { Log.w("TreeGenusForm", "Cannot read bundled tree list", e) }
    return treeSet
}

private fun String.toTree(isSpecies: Boolean): Tree? {
    val line = trim()
    if (line.isBlank()) return null
    val localName = if (line.contains(" (") && line.contains(')'))
        line.substringAfter("(").substringBeforeLast(")")
    else null
    return Tree(line.substringBefore(" (").internString(), isSpecies, localName)
}

private fun getTrees(fullSearch: String, lastPickedAnswers: List<String>, treeSet: Set<Tree>): List<Tree> {
    val search = fullSearch.trim()
    // not working, i need a tree with the same name and species, but local name?
    if (search.isEmpty()) return lastPickedAnswers.mapNotNull { answer ->
        val treeString = answer.split('§')
        if (treeString.size != 2) return@mapNotNull null
        treeSet.firstOrNull { it.name == treeString[1] && it.isSpecies == (treeString[0] == "true") }
    }
    return treeSet.filter { tree ->
        tree.toDisplayString() == search
            || tree.toDisplayString().startsWith(search, true)
            || tree.name == search
            || tree.name.split(" ").any { it.startsWith(search, true) }
            || tree.localName?.contains(search, true) == true
        //sorting: genus-only first, then prefer trees with localName
    }.sortedBy { it.localName == null }.sortedBy { it.isSpecies }
}

val treeFile: PlatformFile get() = PlatformFile(FileKit.filesDir, "trees.csv")

private fun getSelectedTree(input: String, lastPickedAnswers: List<String>, treeSet: Set<Tree>): Tree? {
    return getTrees(input, lastPickedAnswers, treeSet).firstOrNull { canonicalize(it.toDisplayString()) == canonicalize(input) }
}

private val FIND_DIACRITICS = Regex("[\\u0300-\\u036f]+")

private fun canonicalize(str: String): String {
    return stripDiacritics(str).lowercase()
}

private fun stripDiacritics(str: String): String {
    return FIND_DIACRITICS.replace(str.normalizeToNfd(), "")
}
