package de.westnordost.streetcomplete.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.AppBarDefaults
import androidx.compose.material.Button
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.download.DownloadController
import de.westnordost.streetcomplete.data.importGpx
import de.westnordost.streetcomplete.data.MapOverlayFiles
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.data.visiblequests.VisibleEditTypeController
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.common.BackIcon
import de.westnordost.streetcomplete.ui.common.ToastPopup
import de.westnordost.streetcomplete.ui.common.dialogs.SimpleListPickerDialog
import de.westnordost.streetcomplete.ui.common.settings.Preference
import de.westnordost.streetcomplete.ui.common.settings.SwitchPreference
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.exists
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun DisplaySettingsScreen(
    onClickBack: () -> Unit,
) {
    val visibleEditTypeController: VisibleEditTypeController = koinInject()
    val prefs: Preferences = koinInject()
    val scope = rememberCoroutineScope()
    val revision by MapOverlayFiles.revision.collectAsState()
    val gpxExists = remember(revision) { MapOverlayFiles.gpxFile.exists() }
    val geometryExists = remember(revision) { MapOverlayFiles.geometryFile.exists() }
    var importError by remember { mutableStateOf(false) }

    fun selectFile(type: FileKitType, importFile: suspend (PlatformFile) -> Unit) {
        scope.launch {
            try {
                val file = FileKit.openFilePicker(type) ?: return@launch
                withContext(Dispatchers.IO) { importFile(file) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                importError = true
            }
        }
    }
    var showBackgroundDialog by remember { mutableStateOf(false) }
    var showGpxDialog by remember { mutableStateOf(false) }
    var showGeometryDialog by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(Res.string.pref_screen_display)) },
            windowInsets = AppBarDefaults.topAppBarWindowInsets,
            navigationIcon = { IconButton(onClick = onClickBack) { BackIcon() } },
        )
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    )
                )
        ) {
            SwitchPreference(
                name = stringResource(Res.string.pref_way_direction),
                description = stringResource(Res.string.pref_way_direction_summary),
                default = false,
                pref = Prefs.SHOW_WAY_DIRECTION
            )
            SwitchPreference(
                name = stringResource(Res.string.pref_quest_geometries_title),
                description = stringResource(Res.string.pref_quest_geometries_summary),
                default = false,
                pref = Prefs.QUEST_GEOMETRIES,
                onCheckedChange = { visibleEditTypeController.onVisibilitiesChanged() }
            )
            SwitchPreference(
                name = stringResource(Res.string.pref_offset_fix_title2),
                description = stringResource(Res.string.pref_offset_fix_summary),
                default = false,
                pref = Prefs.OFFSET_FIX,
                onCheckedChange = {
                    // trigger map update by switching background twice
                    val old = prefs.getString(Prefs.THEME_BACKGROUND, "MAP")
                    val new = if (old == "MAP") "AERIAL" else "MAP"
                    prefs.putString(Prefs.THEME_BACKGROUND, new)
                    scope.launch {
                        delay(100.milliseconds)
                        prefs.putString(Prefs.THEME_BACKGROUND, old)
                    }
                }
            )
            SwitchPreference(
                name = stringResource(Res.string.pref_show_solved_animation),
                description = stringResource(Res.string.pref_show_solved_animation_summary),
                default = true,
                pref = Prefs.SHOW_SOLVED_ANIMATION
            )
            Preference(
                name = stringResource(Res.string.pref_background_type_select),
                description = if (prefs.getString(Prefs.THEME_BACKGROUND, "MAP") == "MAP")
                        stringResource(Res.string.background_type_map)
                    else stringResource(Res.string.background_type_aerial_esri),
                onClick = { showBackgroundDialog = true }
            )
            Preference(
                name = stringResource(Res.string.pref_gpx_track_title),
                onClick = { showGpxDialog = true },
            )
            Preference(
                name = stringResource(Res.string.pref_custom_geometry_title),
                onClick = { showGeometryDialog = true },
            )
            if (showBackgroundDialog)
                SimpleListPickerDialog(
                    onDismissRequest = { showBackgroundDialog = false },
                    items = listOf("MAP", "AERIAL"),
                    onItemSelected = { prefs.putString(Prefs.THEME_BACKGROUND, it) },
                    getItemName = {
                        if (it == "MAP") stringResource(Res.string.background_type_map)
                        else stringResource(Res.string.background_type_aerial_esri)
                    },
                    selectedItem = prefs.getString(Prefs.THEME_BACKGROUND, "MAP")
                )
            if (showGpxDialog) {
                val downloadController: DownloadController = koinInject()
                var error by remember { mutableStateOf(false) }
                AlertDialog(
                    onDismissRequest = { showGpxDialog = false },
                    confirmButton = {
                        TextButton(onClick = { showGpxDialog = false }) { Text(stringResource(Res.string.close)) }
                    },
                    title = { Text(stringResource(Res.string.pref_gpx_track_title)) },
                    text = {
                        Column {
                            Button(
                                onClick = {
                                    scope.launch {
                                        try {
                                            withContext(Dispatchers.IO) {
                                                val boxes = MapOverlayFiles.readGpxTracks().flatMap { points ->
                                                    importGpx(points, true, 10.0).getOrThrow().downloadBBoxes
                                                }.distinct()
                                                boxes.forEach { downloadController.download(it, false, true) }
                                            }
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            error = true
                                        }
                                    }
                                },
                                enabled = gpxExists,
                            ) { Text(stringResource(Res.string.pref_gpx_track_download), Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
                            Button(onClick = { selectFile(FileKitType.File("gpx"), MapOverlayFiles::importGpx) }) {
                                Text(stringResource(Res.string.pref_gpx_track_provide), Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                            }
                            if (gpxExists) SwitchPreference(
                                name = stringResource(Res.string.pref_gpx_track_enable),
                                default = false,
                                pref = Prefs.SHOW_GPX_TRACK,
                            )
                        }
                    },
                )
                if (error) ToastPopup({ error = false }, stringResource(Res.string.pref_gpx_track_loading_error), isInDialog = true)
            }
            if (showGeometryDialog) {
                AlertDialog(
                    onDismissRequest = { showGeometryDialog = false },
                    confirmButton = {
                        TextButton(onClick = { showGeometryDialog = false }) { Text(stringResource(Res.string.close)) }
                    },
                    title = { Text(stringResource(Res.string.pref_custom_geometry_title)) },
                    text = {
                        Column {
                            Text(stringResource(Res.string.pref_custom_geometry_info))
                            Button(onClick = {
                                selectFile(FileKitType.File("geojson", "json"), MapOverlayFiles::importGeometry)
                            }) {
                                Text(stringResource(Res.string.file_provide), Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                            }
                            if (geometryExists) SwitchPreference(
                                name = stringResource(Res.string.quest_enabled),
                                default = false,
                                pref = Prefs.SHOW_CUSTOM_GEOMETRY,
                            )
                        }
                    },
                )
            }
            if (importError) ToastPopup(
                onDismissRequest = { importError = false },
                text = stringResource(Res.string.import_error),
                isInDialog = showGpxDialog || showGeometryDialog,
            )
        }
    }
}
