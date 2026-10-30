package de.westnordost.streetcomplete.screens.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.focusable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.westnordost.osmfeatures.FeatureDictionary
import de.westnordost.osmfeatures.GeometryType
import de.westnordost.streetcomplete.ApplicationConstants
import de.westnordost.streetcomplete.MapKeyAction
import de.westnordost.streetcomplete.screens.main.map.MapFileLoadError
import de.westnordost.streetcomplete.screens.main.map.rememberMapFileOverlayState
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.user.UserLoginController
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.osm.places.POPULAR_PLACE_FEATURE_IDS
import de.westnordost.streetcomplete.ui.common.feature.FeatureSearchDialog
import de.westnordost.streetcomplete.util.countryboundaries.CountryBoundaries
import de.westnordost.streetcomplete.data.messages.Message
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.osmtracks.Trackpoint
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.screens.MainNavViewModel
import de.westnordost.streetcomplete.screens.main.bottom_sheet.MainBottomSheet
import de.westnordost.streetcomplete.screens.main.controls.LocationState
import de.westnordost.streetcomplete.screens.main.controls.MainScreenControls
import de.westnordost.streetcomplete.screens.main.controls.PointerPinButton
import de.westnordost.streetcomplete.screens.main.edithistory.EditHistorySidebar
import de.westnordost.streetcomplete.screens.main.edithistory.EditHistoryViewModel
import de.westnordost.streetcomplete.screens.main.edithistory.icon
import de.westnordost.streetcomplete.screens.main.errors.LastCrashEffect
import de.westnordost.streetcomplete.screens.main.errors.LastDownloadErrorEffect
import de.westnordost.streetcomplete.screens.main.errors.LastUploadErrorEffect
import de.westnordost.streetcomplete.screens.main.map.BASE_STYLE
import de.westnordost.streetcomplete.screens.main.map.CameraInspectionEffect
import de.westnordost.streetcomplete.screens.main.map.MainMap
import de.westnordost.streetcomplete.screens.main.map.MainMapContent
import de.westnordost.streetcomplete.screens.main.map.MainMapTrackState
import de.westnordost.streetcomplete.screens.main.map.MainMapViewModel
import de.westnordost.streetcomplete.screens.main.map.PinsMode
import de.westnordost.streetcomplete.screens.main.map.getTrackBearing
import de.westnordost.streetcomplete.screens.main.map.offsetInWindow
import de.westnordost.streetcomplete.screens.main.map.positionAtCenter
import de.westnordost.streetcomplete.screens.main.map.rememberMainMapCameraState
import de.westnordost.streetcomplete.screens.main.map.toDpPadding
import de.westnordost.streetcomplete.screens.main.map.toStreetCompleteBoundingBox
import de.westnordost.streetcomplete.screens.main.messages.MessageDialog
import de.westnordost.streetcomplete.screens.main.urlconfig.ApplyUrlConfigDialog
import de.westnordost.streetcomplete.ui.common.ToastPopup
import de.westnordost.streetcomplete.ui.common.dialogs.ConfirmationDialog
import de.westnordost.streetcomplete.ui.common.quest.MapClick
import de.westnordost.streetcomplete.ui.common.quest.Marker
import de.westnordost.streetcomplete.ui.ktx.dir
import de.westnordost.streetcomplete.ui.theme.Dimensions
import de.westnordost.streetcomplete.ui.util.rememberSerializable
import de.westnordost.streetcomplete.util.ktx.toLatLon
import de.westnordost.streetcomplete.util.ktx.toPosition
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.maplibre.compose.location.LocationRequest
import kotlin.time.Duration.Companion.seconds
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.maplibre.compose.interaction.ClickEvent
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.location.HeadingRequest
import org.maplibre.compose.location.LocationEvent
import org.maplibre.compose.location.LocationMeasurement
import org.maplibre.compose.location.LocationPermission
import org.maplibre.compose.location.LocationUnavailableReason
import org.maplibre.compose.location.rememberDefaultHeadingProvider
import org.maplibre.compose.location.rememberDefaultLocationProvider
import org.maplibre.compose.location.rememberSystemSettingsLauncher
import org.maplibre.compose.map.MapRuntime
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.overlay.GeographicLayout
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.DpPadding
import org.maplibre.spatialk.units.Bearing
import org.maplibre.spatialk.units.extensions.inDegrees
import kotlin.time.Duration.Companion.milliseconds

/** The map and its controls, forms, and sidebars. */
@Composable
fun MainScreen(
    tracks: MainMapTrackState,
    onClickSettings: () -> Unit,
    onClickQuestSettings: () -> Unit,
    onClickAbout: () -> Unit,
    onClickProfile: () -> Unit,
    onClickLogin: () -> Unit,
    onClickEnterTeamMode: () -> Unit,
    onShowOverlaysTutorial: () -> Unit,
    navViewModel: MainNavViewModel,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = koinViewModel(),
    editHistoryViewModel: EditHistoryViewModel = koinViewModel(),
    mainBottomSheetViewModel: MainBottomSheetViewModel = koinViewModel(),
    mapViewModel: MainMapViewModel = koinViewModel(),
    runtime: MapRuntime = koinInject(),
    prefs: Preferences = koinInject(),
    mapKeyActions: Flow<MapKeyAction> = emptyFlow(),
) {
    //region state
    val scope = rememberCoroutineScope()

    val headingProvider = rememberDefaultHeadingProvider()
    val locationProvider = rememberDefaultLocationProvider()

    val emailAppLauncher = rememberEmailAppLauncher()
    val mapAppLauncher = rememberMapAppLauncher()
    val systemSettingsLauncher = rememberSystemSettingsLauncher()

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    val starsCount by viewModel.starsCount.collectAsState()
    val isShowingStarsCurrentWeek by viewModel.isShowingStarsCurrentWeek.collectAsState()

    val overlays by viewModel.overlays.collectAsState()
    val selectedOverlay by viewModel.selectedOverlay.collectAsState()
    val isCreateNodeEnabled by remember { derivedStateOf { selectedOverlay?.isCreateNodeEnabled == true } }

    val isAutoSync by viewModel.isAutoSync.collectAsState()
    val unsyncedEditsCount by viewModel.unsyncedEditsCount.collectAsState()

    val isTeamMode by viewModel.isTeamMode.collectAsState()
    val indexInTeam by viewModel.indexInTeam.collectAsState()

    val messagesCount by viewModel.messagesCount.collectAsState()

    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val isUploadingOrDownloading by viewModel.isUploadingOrDownloading.collectAsState()

    val urlConfig by navViewModel.urlConfig.collectAsState()

    val lastCrashReport by viewModel.lastCrashReport.collectAsState()
    val lastDownloadError by viewModel.lastDownloadError.collectAsState()
    val lastUploadError by viewModel.lastUploadError.collectAsState()

    val showZoomButtons by viewModel.showZoomButtons.collectAsState()

    val isRequestingLogin by viewModel.isRequestingLogin.collectAsState()

    val geoUri by navViewModel.geoUri.collectAsState()

    val showOverlaySelector by viewModel.showOverlaySelector.collectAsState()
    val showQuickSettings by viewModel.showQuickSettings.collectAsState()

    var confirmReplaceDownload by remember { mutableStateOf(false) }
    var showLocationPermissionRationaleDialog by remember { mutableStateOf(false) }
    var showApplicationSettingsDialog by remember { mutableStateOf(false) }
    var showLocationSettingsDialog by remember { mutableStateOf(false) }
    var shownMessage by remember { mutableStateOf<Message?>(null) }
    var showToast by remember { mutableStateOf<Toast?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.banCheckFailureWarnings.collect { showToast = Toast.BanCheckFailed }
    }
    var showMapContextMenu by remember { mutableStateOf(false) }
    var addPoiPosition by rememberSerializable { mutableStateOf<LatLon?>(null) }
    // retained while the context menu is dismissed so that it does not move during its exit animation
    var lastMapLongClick by remember { mutableStateOf<MapClick?>(null) }
    var lastQuestSolved by remember { mutableStateOf<QuestSolvedEvent?>(null) }

    var location by rememberSerializable { mutableStateOf<LocationMeasurement?>(null) }
    var heading by remember { mutableStateOf<Float?>(null) }
    var locationState by remember { mutableStateOf<LocationState?>(null) }
    val fileOverlays = rememberMapFileOverlayState(prefs) { error ->
        showToast = if (error == MapFileLoadError.GPX) Toast.GpxLoadError else Toast.GeometryLoadError
    }

    val sheet = rememberMainSheetState(mainBottomSheetViewModel, editHistoryViewModel)

    var pendingSheetSelection by remember { mutableStateOf<MainSheetSelection?>(null) }

    val sheetSelection = sheet.selection
    val shownBottomSheet = sheet.shownBottomSheet
    //endregion

    //region map state

    val downloadedTiles by mapViewModel.downloadedTiles.collectAsStateWithLifecycle()

    var mapPositionInWindow by remember { mutableStateOf(Offset.Zero) }

    val initialCamera = remember(viewModel) { viewModel.initialCamera }
    val highlightsState = remember(shownBottomSheet) { mutableStateOf(SheetHighlights()) }
    val highlights by highlightsState
    LaunchedEffect(shownBottomSheet) {
        shownBottomSheet?.let { highlightsState.value = mainBottomSheetViewModel.getHighlights(it) }
    }

    val markers = sheet.formMarkers ?: highlights.markers

    val pinsMode = when (sheetSelection) {
        is MainSheetSelection.EditHistory -> PinsMode.EditHistory
        null -> PinsMode.Quests // quest pins are only shown if no form is open
        else -> PinsMode.None
    }

    val showOverlay =
        selectedOverlay != null
        && (sheetSelection is MainSheetSelection.Overlay || sheetSelection == null)

    val mapState = rememberMapState(
        runtime = runtime,
        baseStyle = BaseStyle.Json(BASE_STYLE),
        initialCameraPosition = initialCamera
    ) {
        MainMapContent(
            viewModel = mapViewModel,
            location = location,
            heading = heading,
            isRecording = tracks.isRecording,
            trackpoints = tracks.recentTrackPositions,
            oldTrackpointsLists = tracks.olderTrackPositions,
            shownBottomSheet = shownBottomSheet,
            selectedEdit = sheet.shownEdit,
            shownMarkers = markers,
            hiddenLabels = selectedOverlay?.hiddenLabels.orEmpty(),
            showOverlay = showOverlay,
            downloadedTiles = downloadedTiles,
            fileOverlays = fileOverlays,
            pinsMode = pinsMode,
            consumeClick = !sheet.isOpen,
            onClickQuest = { pendingSheetSelection = MainSheetSelection.Quest(it) },
            onClickEdit = { pendingSheetSelection = MainSheetSelection.EditHistory(it) },
            onClickElement = { key ->
                pendingSheetSelection = selectedOverlay?.let { MainSheetSelection.Overlay(it.name, key) }
            },
        )
    }

    val cameraState = rememberMainMapCameraState(mapState, viewModel.initiallyFollowing, viewModel.initiallyNavigating)

    val sheetPadding = Dimensions.getOpenQuestFormMapPadding(
        LocalWindowInfo.current,
        WindowInsets.safeDrawing.asPaddingValues()
    ).toDpPadding(layoutDirection)

    //endregion

    //region actions

    fun getOffset(position: LatLon): Offset? =
        mapState.offsetInWindow(position, mapPositionInWindow, density)

    fun getCrosshairPosition(): LatLon? =
        mapState.positionAtCenter(sheetPadding)

    fun getMetersPerDp(): Double =
        mapState.metersPerDpAtLatitude(mapState.cameraPosition.center.latitude) ?: 0.0

    fun ClickEvent.toMapClick(): MapClick? =
        position?.let { MapClick(it.toLatLon(), screenOffset, clickAreaSizeInMeters = getMetersPerDp() * 20) }

    fun followPosition() {
        scope.launch {
            cameraState.followPosition(location?.position?.toLatLon(), getTrackBearing(tracks.currentTrack))
        }
    }

    fun jumpToEdit() {
        scope.launch {
            sheet.shownEdit?.geometry?.let { cameraState.focus(it, DpPadding.Zero) }
        }
    }

    fun jumpToBottomSheetFocus() {
        scope.launch {
            shownBottomSheet?.geometry?.let { cameraState.focus(it, sheetPadding) }
        }
    }

    fun toggleNavigationMode() {
        if (prefs.getBoolean(Prefs.DISABLE_NAVIGATION_MODE, false) && !cameraState.isNavigationMode) return
        scope.launch {
            cameraState.setNavigationMode(
                value = !cameraState.isNavigationMode,
                position = location?.position?.toLatLon(),
                bearing = getTrackBearing(tracks.currentTrack)
            )
        }
    }

    fun zoomBy(amount: Double) {
        scope.launch {
            cameraState.zoomBy(amount)
        }
    }

    fun composeNote(position: LatLon, trackpoints: List<Trackpoint>? = null) {
        sheet.show(MainSheetSelection.CreateNote(position, trackpoints))
    }

    fun download(enqueue: Boolean = false) {
        val displayedArea = mapState.viewport?.visibleBounds?.toStreetCompleteBoundingBox()
        if (displayedArea == null) {
            showToast = Toast.CannotFindBounds
        } else if (!viewModel.download(displayedArea, mapState.cameraPosition.center.toLatLon(), enqueue)) {
            showToast = Toast.DownloadAreaTooBig
        }
    }

    fun onClickLocation() {
        val permission = locationProvider.permission.value
        if (permission is LocationPermission.NotGranted) {
            when {
                permission.canRequest == false -> {
                    if (systemSettingsLauncher.canOpenApplicationSettings) showApplicationSettingsDialog = true
                    else showToast = Toast.NoLocation
                }
                permission.shouldShowRationale -> showLocationPermissionRationaleDialog = true
                else -> locationProvider.requestPermission()
            }
        } else if (locationState == LocationState.ALLOWED) {
            if (systemSettingsLauncher.canOpenLocationServicesSettings) showLocationSettingsDialog = true
            else showToast = Toast.NoLocation
        } else if (!cameraState.isFollowingPosition) {
            followPosition()
        } else {
            toggleNavigationMode()
        }
    }

    fun onClickDownload() {
        if (viewModel.isConnected) {
            if (viewModel.isUserInitiatedDownloadInProgress) {
                confirmReplaceDownload = true
            } else {
                download()
            }
        } else {
            showToast = Toast.Offline
        }
    }

    fun onClickUpload() {
        if (viewModel.isConnected) {
            viewModel.upload()
        } else {
            if (ApplicationConstants.DEBUG && !UserLoginController.loggedIn) viewModel.upload()
            else showToast = Toast.Offline
        }
    }

    fun sendErrorReport(errorReport: String) {
        if (!emailAppLauncher.isAvailable()) {
            showToast = Toast.NoEmailClient
        } else {
            emailAppLauncher.compose(
                email = ApplicationConstants.ERROR_REPORTS_EMAIL,
                subject = ApplicationConstants.USER_AGENT + " " + "Error Report",
                body = "Describe how to reproduce it here:\n\n\n\n$errorReport"
            )
        }
    }

    fun sendErrorReport(error: Exception) {
        scope.launch {
            val report = viewModel.createErrorReport(error)
            sendErrorReport(report)
        }
    }
    //endregion

    //region effects

    LaunchedEffect(viewModel, mapViewModel) {
        viewModel.reverseQuestOrder.collect(mapViewModel::setReverseQuestOrder)
    }

    LaunchedEffect(mapKeyActions) {
        mapKeyActions.collect { action ->
            when (action) {
                MapKeyAction.OPEN_MENU -> viewModel.showMainMenuDialog.value = true
                MapKeyAction.ZOOM_IN -> zoomBy(1.0)
                MapKeyAction.ZOOM_OUT -> zoomBy(-1.0)
            }
        }
    }

    LaunchedEffect(pendingSheetSelection, sheet.isOpen) {
        val selection = pendingSheetSelection
        if (selection != null) {
            if (!sheet.isOpen) {
                pendingSheetSelection = null
                sheet.show(selection)
            }
        }
    }

    LaunchedEffect(geoUri) {
        geoUri?.let {
            cameraState.moveTo(it)
            navViewModel.consumeGeoUri()
        }
    }

    // Apply the MapLibre viewport to StreetComplete's quest and overlay data sources.
    LaunchedEffect(mapState, mapViewModel) {
        snapshotFlow { mapState.cameraPosition.zoom to mapState.viewport?.visibleBounds }
            .collect { (zoom, bounds) -> mapViewModel.onViewportChanged(zoom, bounds?.toStreetCompleteBoundingBox()) }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.saveCamera(
            camera = mapState.cameraPosition,
            following = cameraState.isFollowingPosition,
            navigating = cameraState.isNavigationMode
        )
    }

    LaunchedEffect(headingProvider) {
        val request = HeadingRequest { minimumInterval = 33.milliseconds }
        headingProvider.updates(request).collect { headingMeasurement ->
            heading = (headingMeasurement.bearing - Bearing.North).inDegrees.toFloat()
        }
    }

    LaunchedEffect(locationProvider) {
        val request = LocationRequest {
            if (prefs.contains(Prefs.LOCATION_INTERVAL)) {
                minimumInterval = prefs.getInt(Prefs.LOCATION_INTERVAL, 0).coerceAtLeast(0).seconds
            }
        }
        locationProvider.updates(request).collect { event ->
            when (event) {
                is LocationEvent.Update -> {
                    val measurement = event.measurement
                    location = measurement
                    locationState = LocationState.UPDATING
                    tracks.addLocation(measurement)
                    launch {
                        cameraState.animateToPositionIfFollowing(
                            position = measurement.position.toLatLon(),
                            bearing = getTrackBearing(tracks.currentTrack)
                        )
                    }
                }
                is LocationEvent.Unavailable -> {
                    location = null
                    locationState = when (event.reason) {
                        LocationUnavailableReason.ServicesDisabled -> LocationState.ALLOWED
                        LocationUnavailableReason.TemporarilyUnavailable -> LocationState.SEARCHING
                        LocationUnavailableReason.PermissionDenied -> LocationState.DENIED
                        else -> null // unsupported or unclassified
                    }

                    tracks.clear()
                    launch { cameraState.setNavigationMode(false, null, null) }
                }
                else -> Unit
            }
        }
    }

    CameraInspectionEffect(cameraState, sheet, location?.position, tracks, sheetPadding)

    LaunchedEffect(selectedOverlay) {
        val selection = sheet.selection as? MainSheetSelection.Overlay
        if (selection != null && selection.name != selectedOverlay?.name) sheet.close()
    }

    LaunchedEffect(isTeamMode) {
        // always show this toast on start to remind user that it is still on
        if (isTeamMode) {
            showToast = Toast.TeamModeActive
        }
        // show this only once when turning it off
        else if (viewModel.teamModeChanged) {
            showToast = Toast.TeamModeDeactivated
            viewModel.teamModeChanged = false
        }
    }

    lastDownloadError?.let { error ->
        LastDownloadErrorEffect(lastError = error, onReportError = ::sendErrorReport)
    }
    lastUploadError?.let { error ->
        LastUploadErrorEffect(lastError = error, onReportError = ::sendErrorReport)
    }
    lastCrashReport?.let { report ->
        LastCrashEffect(lastReport = report, onReport = ::sendErrorReport)
    }

    //endregion

    //region content

    Box(modifier) {
        MainMap(
            state = mapState,
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { mapPositionInWindow = it.positionInWindow() },
            onPan = { cameraState.onPan(location != null) },
            onRotate = { cameraState.onRotate(location != null) },
            onMapClick = { event ->
                when (sheet.selection) {
                    null -> {}
                    is MainSheetSelection.EditHistory -> sheet.close()
                    // forms react to clicks near the click position, e.g. to suggest a name
                    else -> event.toMapClick()?.let { sheet.lastMapClick = it }
                }
                ClickResult.Consume
            },
            onMapLongClick = { event ->
                if (!sheet.isOpen) {
                    lastMapLongClick = event.toMapClick()
                    showMapContextMenu = lastMapLongClick != null
                }
                ClickResult.Consume
            },
            overlay = {
                sheet.formMapOverlay?.invoke(this)

                GeographicLayout(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                    val shownEdit = sheet.shownEdit?.edit
                    shownEdit?.position?.toPosition()?.let { position ->
                        val icon = shownEdit.icon ?: Res.drawable.empty_128
                        PointerPinButton(
                            targetPosition = position,
                            onClick = ::jumpToEdit,
                            contentPadding = PaddingValues(6.dp),
                        ) {
                            Image(painterResource(icon), null, modifier = Modifier.size(36.dp))
                        }
                    }

                    shownBottomSheet?.position?.toPosition()?.let { position ->
                        PointerPinButton(
                            targetPosition = position,
                            onClick = ::jumpToBottomSheetFocus,
                            contentPadding = PaddingValues(6.dp),
                        ) {
                            Image(painterResource(shownBottomSheet.icon), null, modifier = Modifier.size(36.dp))
                        }
                    }

                    val position = location?.position
                    if (position != null) {
                        PointerPinButton(targetPosition = position, onClick = ::followPosition) {
                            Image(painterResource(Res.drawable.location_dot_small), null)
                        }
                    }
                }

                MainScreenControls(
                    starsCount = starsCount,
                    isShowingStarsCurrentWeek = isShowingStarsCurrentWeek,
                    isUploadingOrDownloading = isUploadingOrDownloading,
                    onToggleShowStarsCurrentWeek = { viewModel.toggleShowingCurrentWeek() },

                    messagesCount = messagesCount,
                    onClickMessages = { scope.launch { shownMessage = viewModel.popMessage() } },

                    overlays = overlays,
                    selectedOverlay = selectedOverlay,
                    onSelectOverlay = viewModel::selectOverlay,
                    onShowOverlaysTutorial = onShowOverlaysTutorial.takeUnless {
                        viewModel.hasShownOverlaysTutorial
                    },

                    shownUnsyncedEdits = if (!isAutoSync) unsyncedEditsCount else 0,
                    shownIndexInTeam = if (isTeamMode) indexInTeam else null,
                    onClickMainMenu = { viewModel.showMainMenuDialog.value = true },

                    showZoomButtons = showZoomButtons,
                    onClickZoomIn = { zoomBy(1.0) },
                    onClickZoomOut = { zoomBy(-1.0) },
                    onZoomDrag = { zoomBy(it / 20.0) },

                    mapRotation = { mapState.cameraPosition.bearing.toFloat() },
                    mapTilt = { mapState.cameraPosition.pitch.toFloat() },
                    onClickCompass = { scope.launch { cameraState.resetCompass() } },

                    locationState = locationState,
                    isNavigationMode = cameraState.isNavigationMode,
                    isFollowingPosition = cameraState.isFollowingPosition,
                    onClickLocation = ::onClickLocation,

                    isRecordingTracks = tracks.isRecording,
                    onClickStopTrackRecording = {
                        val recorded = tracks.stopRecording()
                        val latLon = location?.position?.toLatLon()
                        if (latLon != null) {
                            composeNote(latLon, recorded.takeIf { it.isNotEmpty() })
                        }
                    },

                    isCreateNodeEnabled = isCreateNodeEnabled,
                    onClickCreate = {
                        if (mapState.cameraPosition.zoom >= 17.0) {
                            selectedOverlay?.let { sheet.show(MainSheetSelection.Overlay(it.name)) }
                        } else {
                            showToast = Toast.DownloadAreaTooBig
                        }
                    },

                    hasEdits = sheet.hasEdits,
                    isUndoEnabled = !isUploadingOrDownloading,
                    onClickUndo = sheet::showEditHistory,

                    metersPerDp = { getMetersPerDp() },
                    mapPosition = { mapState.cameraPosition.center.toLatLon() },
                    showOverlaySelector = shownBottomSheet == null && showOverlaySelector,
                    nearbyQuests = highlights.nearbyQuests.takeIf { sheetSelection is MainSheetSelection.Quest },
                    showQuestDetails = {
                        sheet.show(MainSheetSelection.Quest(it.key))
                    },
                    showQuickSettings = showQuickSettings,
                    viewModel = viewModel,
                )
            },
        )

        val dir = LocalLayoutDirection.current.dir
        AnimatedVisibility(
            visible = sheetSelection is MainSheetSelection.EditHistory && sheet.hasEdits,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { -it * dir }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { -it * dir }),
        ) {
            EditHistorySidebar(
                editItems = sheet.editItems.orEmpty(),
                selectedEdit = sheet.shownEdit?.edit,
                onSelectEdit = { sheet.show(MainSheetSelection.EditHistory(it.key)) },
                onUndoEdit = { editHistoryViewModel.undo(it.key) },
                onDismissRequest = sheet::close,
                getEditElement = editHistoryViewModel::getEditElement,
            )
        }

        AnimatedContent(
            targetState = shownBottomSheet?.let { sheet.id to it },
            contentKey = { it?.first },
            transitionSpec = {
                if (initialState != null && targetState != null) {
                    fadeIn() + slideInVertically { it / 16 } togetherWith fadeOut()
                } else {
                    // Size transform with snap is necessary so that it doesn't animate the bounds
                    // from zero (=no form) which looks weird
                    (fadeIn() + slideInVertically { it } togetherWith
                    fadeOut() + slideOutVertically { it / 2 }) using SizeTransform(clip = false)
                }
            },
        ) { content ->
            if (content != null) {
                val (id, shownBottomSheet) = content
                val mapCamera = mapState.cameraPosition
                sheet.formStateHolder.SaveableStateProvider(id) {
                    MainBottomSheet(
                        onDismiss = sheet::close,
                        onSolved = { icon, position ->
                            getOffset(position)?.let { lastQuestSolved = QuestSolvedEvent(icon, it) }
                        },
                        onHideQuest = mainBottomSheetViewModel::hideQuest,
                        isSurvey = mainBottomSheetViewModel::isSurvey,
                        onSubmitEdit = mainBottomSheetViewModel::submitEdit,
                        onCommentNote = mainBottomSheetViewModel::commentNote,
                        onCreateNote = mainBottomSheetViewModel::createNote,
                        shownBottomSheet = shownBottomSheet,
                        mapRotation = mapCamera.bearing.toFloat(),
                        mapTilt = mapCamera.pitch.toFloat(),
                        mapPosition = getCrosshairPosition() ?: mapCamera.center.toLatLon(),
                        mapMetersPerDp = getMetersPerDp(),
                        onSetMapMarkers = { if (id == sheet.id) sheet.formMarkers = it?.toList() },
                        onSetMapOverlay = { if (id == sheet.id) sheet.formMapOverlay = it },
                        lastMapClick = sheet.lastMapClick,
                    )
                }
            }
        }
    }

    if (prefs.getBoolean(Prefs.SHOW_SOLVED_ANIMATION, true)) {
        lastQuestSolved?.let { LastQuestSolvedEffect(it) }
    }

    MapContextMenu(
        expanded = showMapContextMenu,
        onDismissRequest = { showMapContextMenu = false },
        onClickCreateNote = {
            if (mapState.cameraPosition.zoom < ApplicationConstants.NOTE_MIN_ZOOM) showToast = Toast.ImpreciseNote
            else lastMapLongClick?.let { composeNote(it.position) }
        },
        onClickCreateTrack = { tracks.startRecording() },
        isOpenLocationAvailable = mapAppLauncher.isAvailable(),
        onClickOpenLocation = {
            lastMapLongClick?.let { mapAppLauncher.openAt(it.position, mapState.cameraPosition.zoom) }
        },
        isExpertMode = prefs.expertMode && lastMapLongClick != null,
        onClickAddNode = { addPoiPosition = lastMapLongClick?.position },
        onClickInsertNode = {
            lastMapLongClick?.position?.let { sheet.show(MainSheetSelection.InsertNode(it)) }
        },
        offset = lastMapLongClick?.screenOffset ?: DpOffset.Zero,
    )

    addPoiPosition?.let { position ->
        val countryBoundaries: CountryBoundaries = koinInject()
        val featureDictionary: FeatureDictionary = koinInject()
        val country = countryBoundaries.getIds(position).firstOrNull()
        val defaultFeatureIds = prefs.getString(Prefs.CREATE_POI_RECENT_FEATURE_IDS, "")
            .split("§").filter { it.isNotBlank() && it != "shop" }
            .ifEmpty { POPULAR_PLACE_FEATURE_IDS }
        FeatureSearchDialog(
            onDismissRequest = { addPoiPosition = null },
            onSelectedFeature = { feature ->
                val recent = prefs.getString(Prefs.CREATE_POI_RECENT_FEATURE_IDS, "")
                    .split("§").filter { it.isNotBlank() }.toMutableList()
                recent.remove(feature.id)
                recent.add(feature.id)
                prefs.putString(Prefs.CREATE_POI_RECENT_FEATURE_IDS, recent.takeLast(35).joinToString("§"))
                sheet.show(MainSheetSelection.AddPoi(position, feature.id))
            },
            featureDictionary = featureDictionary,
            geometryType = GeometryType.POINT,
            countryCode = country,
            filterFn = { true },
            codesOfDefaultFeatures = defaultFeatureIds.reversed(),
            showKeyboardOnFocus = prefs.getBoolean(Prefs.CREATE_NODE_SHOW_KEYBOARD, true),
        )
    }

    //endregion

    //region dialogs and toast popups

    if (showLocationPermissionRationaleDialog) {
        ConfirmationDialog(
            onDismissRequest = { showLocationPermissionRationaleDialog = false },
            onConfirmed = { locationProvider.requestPermission() },
            title = { Text(stringResource(Res.string.no_location_permission_warning_title)) },
            text = { Text(stringResource(Res.string.no_location_permission_warning)) },
        )
    }
    if (showApplicationSettingsDialog) {
        ConfirmationDialog(
            onDismissRequest = { showApplicationSettingsDialog = false },
            onConfirmed = { systemSettingsLauncher.openApplicationSettings() },
            text = { Text(stringResource(Res.string.turn_on_location_request)) },
        )
    }
    if (showLocationSettingsDialog) {
        ConfirmationDialog(
            onDismissRequest = { showLocationSettingsDialog = false },
            onConfirmed = { systemSettingsLauncher.openLocationServicesSettings() },
            text = { Text(stringResource(Res.string.turn_on_location_request)) },
        )
    }

    shownMessage?.let { message ->
        val questIcons = remember { viewModel.allQuestTypes.map { it.icon } }
        MessageDialog(
            message = message,
            onDismissRequest = { shownMessage = null },
            allQuestIcons = questIcons,
            onClickOpenQuestSettings = onClickQuestSettings,
            onToggleDontNotifyAgain = { messageType, dontNotifyAgain ->
                viewModel.toggleDisableMessageType(messageType, dontNotifyAgain)
            }
        )
    }

    if (viewModel.showMainMenuDialog.value) {
        val requester = remember { FocusRequester() } // necessary for receiving key event
        MainMenuDialog(
            onDismissRequest = { viewModel.showMainMenuDialog.value = false },
            onClickProfile = onClickProfile,
            onClickLogin = onClickLogin,
            onClickSettings = onClickSettings,
            onClickAbout = onClickAbout,
            onClickDownload = ::onClickDownload,
            onClickUpload = ::onClickUpload,
            onClickEnterTeamMode = onClickEnterTeamMode,
            onClickExitTeamMode = { viewModel.disableTeamMode() },
            isLoggedIn = isLoggedIn,
            indexInTeam = if (isTeamMode) indexInTeam else null,
            unsyncedEditsCount = if (!isAutoSync) unsyncedEditsCount else null,
            isUploadingOrDownloading = isUploadingOrDownloading,
            modifier = Modifier
                .focusRequester(requester)
                .focusable()
                .onKeyEvent {
                    if (it.key == Key.Menu && it.type == KeyEventType.KeyUp){
                        onClickSettings()
                        viewModel.showMainMenuDialog.value = false
                        return@onKeyEvent true
                    }
                    false
                }
        )
        LaunchedEffect(Unit) { requester.requestFocus() }
    }

    urlConfig?.let { config ->
        ApplyUrlConfigDialog(
            presetName = config.urlConfig.presetName,
            presetNameAlreadyExists = config.alreadyExists,
            onDismissRequest = navViewModel::consumeUrlConfig,
            onConfirmed = { navViewModel.applyUrlConfig(config.urlConfig) },
        )
    }

    if (isRequestingLogin) {
        RequestLoginDialog(
            onDismissRequest = { viewModel.finishRequestingLogin() },
            onConfirmed = onClickLogin
        )
    }

    if (confirmReplaceDownload) {
        ConfirmReplaceDownloadDialog(
            onDismissRequest = { confirmReplaceDownload = false },
            onConfirmed = { download() },
            onEnqueue = { download(enqueue = true) },
        )
    }

    showToast?.messageResource?.let { message ->
        ToastPopup(
            onDismissRequest = { showToast = null },
            text = stringResource(message)
        )
    }

    //endregion
}

private enum class Toast {
    Offline,
    TeamModeActive,
    TeamModeDeactivated,
    DownloadAreaTooBig,
    NoEmailClient,
    CannotFindBounds,
    NoLocation,
    ImpreciseNote,
    GpxLoadError,
    GeometryLoadError,
    BanCheckFailed,
}

private val Toast.messageResource: StringResource get() =  when (this) {
    Toast.CannotFindBounds -> Res.string.cannot_find_bbox_or_reduce_tilt
    Toast.NoLocation -> Res.string.no_gps_no_quests
    Toast.ImpreciseNote -> Res.string.create_new_note_unprecise
    Toast.Offline -> Res.string.offline
    Toast.TeamModeActive -> Res.string.team_mode_active
    Toast.TeamModeDeactivated -> Res.string.team_mode_deactivated
    Toast.DownloadAreaTooBig -> Res.string.download_area_too_big
    Toast.NoEmailClient -> Res.string.no_email_client
    Toast.GpxLoadError -> Res.string.pref_gpx_track_loading_error
    Toast.GeometryLoadError -> Res.string.import_error
    Toast.BanCheckFailed -> Res.string.ban_check_fails
}
