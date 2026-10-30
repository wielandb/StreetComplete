package de.westnordost.streetcomplete.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AppBarDefaults
import androidx.compose.material.IconButton
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import de.westnordost.streetcomplete.DayNightBehavior
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.osm.osmquests.OsmQuestController
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.data.preferences.ResurveyIntervalsUpdater
import de.westnordost.streetcomplete.data.visiblequests.DayNightQuestFilter
import de.westnordost.streetcomplete.data.visiblequests.QuestTypeOrderController
import de.westnordost.streetcomplete.data.visiblequests.VisibleEditTypeController
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.util.permissions.rememberQuestMonitorPermissionRequest
import de.westnordost.streetcomplete.ui.util.permissions.hasSeparateLocationProviders
import de.westnordost.streetcomplete.ui.common.ToastPopup
import de.westnordost.streetcomplete.ui.common.BackIcon
import de.westnordost.streetcomplete.ui.common.TextField2
import de.westnordost.streetcomplete.ui.common.dialogs.AlertDialog
import de.westnordost.streetcomplete.ui.common.dialogs.SimpleListPickerDialog
import de.westnordost.streetcomplete.ui.common.settings.Preference
import de.westnordost.streetcomplete.ui.common.settings.SwitchPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun QuestSettingsScreen(
    onClickBack: () -> Unit,
) {
    val prefs: Preferences = koinInject()
    val scope = rememberCoroutineScope()
    val visibleEditTypeController: VisibleEditTypeController = koinInject()
    val dayNightQuestFilter: DayNightQuestFilter = koinInject()
    val questTypeOrderController: QuestTypeOrderController = koinInject()
    val resurveyIntervalsUpdater: ResurveyIntervalsUpdater = koinInject()
    var showDayNightDialog by remember { mutableStateOf(false) }
    var advancedResurveyDialog by remember { mutableStateOf(false) }
    var questMonitorDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(Res.string.pref_screen_quests)) },
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
            Preference(
                name = stringResource(Res.string.pref_day_night_title),
                onClick = { showDayNightDialog = true },
            ) {
                Text(stringResource(DayNightBehavior.valueOf(prefs.getString(Prefs.DAY_NIGHT_BEHAVIOR, "IGNORE")).titleRes))
            }
            if (prefs.expertMode)
                Preference(
                    name = stringResource(Res.string.advanced_resurvey_title),
                    onClick = { advancedResurveyDialog = true },
                    description = stringResource(Res.string.pref_advanced_resurvey_summary)
                )
            if (prefs.expertMode)
                SwitchPreference(
                    name = stringResource(Res.string.pref_quest_settings_preset_title),
                    description = stringResource(Res.string.pref_quest_settings_preset_summary),
                    pref = Prefs.QUEST_SETTINGS_PER_PRESET,
                    default = false,
                    onCheckedChange = { OsmQuestController.reloadQuestTypes() },
                )
            if (prefs.expertMode)
                SwitchPreference(
                    name = stringResource(Res.string.pref_dynamic_quest_creation_title),
                    description = stringResource(Res.string.pref_dynamic_quest_creation_summary),
                    pref = Prefs.DYNAMIC_QUEST_CREATION,
                    default = false,
                    onCheckedChange = { scope.launch(Dispatchers.IO) { visibleEditTypeController.onVisibilitiesChanged() } }
                )
            Preference(
                name = stringResource(Res.string.pref_quest_monitor_title),
                onClick = { questMonitorDialog = true },
                description = stringResource(Res.string.pref_quest_monitor_summary)
            )
            SwitchPreference(
                name = stringResource(Res.string.pref_hide_overlay_quests),
                pref = Prefs.HIDE_OVERLAY_QUESTS,
                default = true,
                onCheckedChange = { scope.launch(Dispatchers.IO) { visibleEditTypeController.onVisibilitiesChanged() } }
            )
        }
    }
    if (showDayNightDialog)
        SimpleListPickerDialog(
            onDismissRequest = { showDayNightDialog = false },
            items = DayNightBehavior.entries,
            onItemSelected = {
                prefs.putString(Prefs.DAY_NIGHT_BEHAVIOR, it.name)
                scope.launch(Dispatchers.IO) {
                    dayNightQuestFilter.reload()
                    visibleEditTypeController.onVisibilitiesChanged()
                    questTypeOrderController.onQuestTypeOrderChanged()
                }
            },
            title = { Text(stringResource(Res.string.pref_day_night_title)) },
            selectedItem = DayNightBehavior.valueOf(prefs.getString(Prefs.DAY_NIGHT_BEHAVIOR, "IGNORE")),
            getItemName = { stringResource(it.titleRes) }
        )
    if (advancedResurveyDialog) {
        var date by remember { mutableStateOf(TextFieldValue(prefs.getString(Prefs.RESURVEY_DATE, ""))) }
        var keys by remember { mutableStateOf(TextFieldValue(prefs.getString(Prefs.RESURVEY_KEYS, ""))) }
        AlertDialog(
            onDismissRequest = { advancedResurveyDialog = false },
            title = { Text(stringResource(Res.string.advanced_resurvey_title)) },
            buttonRow = {
                TextButton({ advancedResurveyDialog = false }) { Text(stringResource(Res.string.cancel)) }
                TextButton({
                    prefs.putString(Prefs.RESURVEY_DATE, date.text)
                    prefs.putString(Prefs.RESURVEY_KEYS, keys.text)
                    resurveyIntervalsUpdater.update()
                    advancedResurveyDialog = false
                }) { Text(stringResource(Res.string.ok)) }
            },
            text = {
                Column {
                    Text(stringResource(Res.string.advanced_resurvey_message_keys))
                    TextField2(keys, { keys = it }, label = { Text(stringResource(Res.string.advanced_resurvey_hint_keys)) })
                    Text(stringResource(Res.string.advanced_resurvey_message_date))
                    TextField2(date, { date = it }, label = { Text(stringResource(Res.string.advanced_resurvey_hint_date)) })
                }
            }
        )
    }
    if (questMonitorDialog) {
        var enable by remember { mutableStateOf(prefs.getBoolean(Prefs.QUEST_MONITOR, false)) }
        var showPermissionWarning by remember { mutableStateOf(false) }
        val requestMonitorPermission = rememberQuestMonitorPermissionRequest { granted ->
            enable = granted
            showPermissionWarning = !granted
        }
        val onEnableChange: (Boolean) -> Unit = { checked ->
            if (checked) requestMonitorPermission() else enable = false
        }
        var download by remember { mutableStateOf(prefs.getBoolean(Prefs.QUEST_MONITOR_DOWNLOAD, false)) }
        var gps by remember { mutableStateOf(prefs.getBoolean(Prefs.QUEST_MONITOR_GPS, false)) }
        var net by remember { mutableStateOf(prefs.getBoolean(Prefs.QUEST_MONITOR_NET, false)) }
        var radius by remember { mutableStateOf(TextFieldValue(prefs.getFloat(Prefs.QUEST_MONITOR_RADIUS, 50f).toString())) }
        AlertDialog(
            onDismissRequest = { questMonitorDialog = false },
            title = { Text(stringResource(Res.string.pref_quest_monitor_title)) },
            buttonRow = {
                TextButton({ questMonitorDialog = false }) { Text(stringResource(Res.string.cancel)) }
                TextButton({
                    prefs.putBoolean(Prefs.QUEST_MONITOR, enable)
                    prefs.putBoolean(Prefs.QUEST_MONITOR_GPS, gps)
                    prefs.putBoolean(Prefs.QUEST_MONITOR_NET, net)
                    prefs.putBoolean(Prefs.QUEST_MONITOR_DOWNLOAD, download)
                    prefs.putFloat(Prefs.QUEST_MONITOR_RADIUS, radius.text.toFloatOrNull() ?: 50f)
                    questMonitorDialog = false
                }, enabled = radius.text.toFloatOrNull()?.let { it.isFinite() && it > 0f } == true) { Text(stringResource(Res.string.ok)) }
            },
            text = {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { onEnableChange(!enable) },
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(Res.string.pref_quest_monitor_title))
                        Switch(enable, onEnableChange)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { download = !download },
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(Res.string.pref_quest_monitor_download))
                        Switch(download, { download = it })
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { gps = !gps },
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(if (hasSeparateLocationProviders) Res.string.quest_monitor_gps else Res.string.quest_monitor_high_accuracy))
                        Switch(gps, { gps = it })
                    }
                    if (hasSeparateLocationProviders) Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { net = !net },
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(Res.string.quest_monitor_net))
                        Switch(net, { net = it })
                    }
                    Text(stringResource(Res.string.quest_monitor_search_radius_text))
                    TextField2(radius, { radius = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
            }
        )
        if (showPermissionWarning) {
            ToastPopup(
                onDismissRequest = { showPermissionWarning = false },
                text = stringResource(Res.string.quest_monitor_permission_required),
                isInDialog = true,
            )
        }
    }
}
