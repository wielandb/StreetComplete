package de.westnordost.streetcomplete.ui.common

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.MapOverlayFiles
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.data.quest.VisibleQuestsSource
import de.westnordost.streetcomplete.quests.custom.CustomQuestList
import de.westnordost.streetcomplete.quests.tree.AddTreeGenus
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.common.dialogs.AlertDialog
import de.westnordost.streetcomplete.util.logs.Log
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** The same importer for files opened by another app on Android and iOS. */
@Composable
fun IncomingFileImportDialog(
    file: PlatformFile,
    onDismissRequest: () -> Unit,
    prefs: Preferences = koinInject(),
    customQuests: CustomQuestList = koinInject(),
    visibleQuests: VisibleQuestsSource = koinInject(),
) {
    val scope = rememberCoroutineScope()
    var running by remember(file) { mutableStateOf(false) }
    var failed by remember(file) { mutableStateOf(false) }
    val filename = remember(file) { runCatching { file.name }.getOrDefault("") }
    val kinds = remember(filename) { incomingFileKinds(filename) }
    fun importFile(kind: IncomingFileKind) {
        if (running) return
        running = true
        failed = false
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    when (kind) {
                        IncomingFileKind.GPX -> {
                            MapOverlayFiles.importGpx(file)
                            prefs.putBoolean(Prefs.SHOW_GPX_TRACK, true)
                        }
                        IncomingFileKind.GEOJSON -> {
                            MapOverlayFiles.importGeometry(file)
                            prefs.putBoolean(Prefs.SHOW_CUSTOM_GEOMETRY, true)
                        }
                        IncomingFileKind.CUSTOM_QUESTS -> {
                            customQuests.readFromFile(file)
                            visibleQuests.clearCache()
                        }
                        IncomingFileKind.TREES -> AddTreeGenus.readFromFile(file)
                    }
                }
                onDismissRequest()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("IncomingFileImport", "Could not import file", e)
                failed = true
            } finally {
                running = false
            }
        }
    }
    AlertDialog(
        onDismissRequest = { if (!running) onDismissRequest() },
        title = { Text(filename.ifEmpty { stringResource(Res.string.file_provide) }) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (running) CircularProgressIndicator()
                if (failed) Text(stringResource(Res.string.import_error))
                for (kind in kinds) TextButton({ importFile(kind) }, enabled = !running, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(when (kind) {
                        IncomingFileKind.GPX -> Res.string.pref_gpx_track_enable
                        IncomingFileKind.GEOJSON -> Res.string.pref_custom_geometry_title
                        IncomingFileKind.CUSTOM_QUESTS -> Res.string.pref_custom_title
                        IncomingFileKind.TREES -> Res.string.pref_trees_title
                    }))
                }
            }
        },
        buttonRow = {
            TextButton(onDismissRequest, enabled = !running) { Text(stringResource(Res.string.cancel)) }
        },
    )
}
