package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MovieCreation
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.domain.model.MediaKind
import com.example.ui.components.PersistentMiniPlayerBar
import com.example.ui.navigation.AuraRoute
import com.example.ui.navigation.AuraShareRoute
import com.example.ui.navigation.DeviceDiscoveryRoute
import com.example.ui.navigation.DownloadCenterRoute
import com.example.ui.navigation.EditorProjectsRoute
import com.example.ui.navigation.ExportRoute
import com.example.ui.navigation.FileTransferRoute
import com.example.ui.navigation.HomeRoute
import com.example.ui.navigation.MusicLibraryRoute
import com.example.ui.navigation.NowPlayingRoute
import com.example.ui.navigation.PlaylistsRoute
import com.example.ui.navigation.PrivateSpaceRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.navigation.SmartAlbumsRoute
import com.example.ui.navigation.TimelineEditorRoute
import com.example.ui.navigation.TransferHistoryRoute
import com.example.ui.navigation.VideoLibraryRoute
import com.example.ui.screens.AuraShareScreen
import com.example.ui.screens.DeviceDiscoveryScreen
import com.example.ui.screens.DownloadCenterScreen
import com.example.ui.screens.EditorProjectManagerScreen
import com.example.ui.screens.ExportScreen
import com.example.ui.screens.FileTransferScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.IncomingTransferConsentDialog
import com.example.ui.screens.MusicLibraryScreen
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.PrivateSpaceScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SmartAlbumsScreen
import com.example.ui.screens.TimelineEditorScreen
import com.example.ui.screens.TransferHistoryScreen
import com.example.ui.screens.VideoLibraryScreen
import com.example.ui.theme.AuraPlayerTheme
import com.example.ui.viewmodel.AuraShareViewModel
import com.example.ui.viewmodel.LibraryViewModel
import com.example.ui.viewmodel.VideoEditorViewModel

private data class TopNavItem(
    val label: String,
    val route: AuraRoute,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val TopNavItems = listOf(
    TopNavItem("Home", HomeRoute, Icons.Rounded.Home, Icons.Outlined.Home, "nav_home"),
    TopNavItem("Music", MusicLibraryRoute, Icons.Rounded.MusicNote, Icons.Outlined.MusicNote, "nav_music"),
    TopNavItem("Video", VideoLibraryRoute, Icons.Rounded.VideoLibrary, Icons.Outlined.VideoLibrary, "nav_video"),
    TopNavItem("Studio", EditorProjectsRoute, Icons.Rounded.MovieCreation, Icons.Outlined.MovieCreation, "nav_studio"),
    TopNavItem("Share", AuraShareRoute, Icons.Rounded.Share, Icons.Outlined.Share, "nav_share")
)

@Composable
fun AuraPlayerApp(
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    editorViewModel: VideoEditorViewModel = hiltViewModel(),
    shareViewModel: AuraShareViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val settings by libraryViewModel.settings.collectAsStateWithLifecycle()
    val playbackState by libraryViewModel.playbackState.collectAsStateWithLifecycle()
    val audioItems by libraryViewModel.audioItems.collectAsStateWithLifecycle()
    val videoItems by libraryViewModel.videoItems.collectAsStateWithLifecycle()
    val allPublicItems by libraryViewModel.allPublicItems.collectAsStateWithLifecycle()
    val privateItems by libraryViewModel.privateItems.collectAsStateWithLifecycle()
    val isPrivateUnlocked by libraryViewModel.isPrivateUnlocked.collectAsStateWithLifecycle()
    val pinError by libraryViewModel.pinError.collectAsStateWithLifecycle()
    val smartAlbums by libraryViewModel.smartAlbums.collectAsStateWithLifecycle()
    val playlists by libraryViewModel.playlists.collectAsStateWithLifecycle()
    val selectedPlaylistId by libraryViewModel.selectedPlaylistId.collectAsStateWithLifecycle()
    val selectedPlaylistTracks by libraryViewModel.selectedPlaylistTracks.collectAsStateWithLifecycle()
    val downloads by libraryViewModel.downloads.collectAsStateWithLifecycle()

    val projects by editorViewModel.projects.collectAsStateWithLifecycle()
    val activeProject by editorViewModel.activeProject.collectAsStateWithLifecycle()
    val selectedClipId by editorViewModel.selectedClipId.collectAsStateWithLifecycle()
    val selectedAudioId by editorViewModel.selectedAudioId.collectAsStateWithLifecycle()
    val selectedTextId by editorViewModel.selectedTextId.collectAsStateWithLifecycle()
    val canUndo by editorViewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by editorViewModel.canRedo.collectAsStateWithLifecycle()
    val lastSavedLabel by editorViewModel.lastSavedLabel.collectAsStateWithLifecycle()
    val exportState by editorViewModel.exportState.collectAsStateWithLifecycle()
    val voiceOverState by editorViewModel.voiceOverState.collectAsStateWithLifecycle()

    val discoveredPeers by shareViewModel.discoveredPeers.collectAsStateWithLifecycle()
    val isDiscovering by shareViewModel.isDiscovering.collectAsStateWithLifecycle()
    val activeTransfer by shareViewModel.activeTransfer.collectAsStateWithLifecycle()
    val incomingPrompt by shareViewModel.incomingPrompt.collectAsStateWithLifecycle()
    val isSharePaused by shareViewModel.isPaused.collectAsStateWithLifecycle()
    val serverPort by shareViewModel.serverPort.collectAsStateWithLifecycle()
    val selectedShareFiles by shareViewModel.selectedFiles.collectAsStateWithLifecycle()
    val selectedPeer by shareViewModel.selectedPeer.collectAsStateWithLifecycle()
    val qrBitmap by shareViewModel.qrBitmap.collectAsStateWithLifecycle()
    val pairingUriText by shareViewModel.pairingUriText.collectAsStateWithLifecycle()
    val pairingPinDisplay by shareViewModel.pairingPinDisplay.collectAsStateWithLifecycle()
    val shareError by shareViewModel.shareError.collectAsStateWithLifecycle()
    val transferHistory by shareViewModel.transferHistory.collectAsStateWithLifecycle()

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination

    val isTopLevel = TopNavItems.any { item ->
        currentDest?.hasRoute(item.route::class) == true
    }
    val showMiniPlayer = currentDest?.hasRoute(NowPlayingRoute::class) != true &&
        currentDest?.hasRoute(TimelineEditorRoute::class) != true &&
        currentDest?.hasRoute(ExportRoute::class) != true

    fun navigateTopLevel(route: AuraRoute) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    AuraPlayerTheme(
        themeMode = settings.themeMode,
        dynamicColor = settings.dynamicColor
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    if (!useRail) {
                        Column {
                            if (showMiniPlayer) {
                                PersistentMiniPlayerBar(
                                    playbackState = playbackState,
                                    onTogglePlayPause = libraryViewModel::togglePlayPause,
                                    onNext = libraryViewModel::nextTrack,
                                    onOpenNowPlaying = { navController.navigate(NowPlayingRoute) }
                                )
                            }
                            if (isTopLevel) {
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ) {
                                    TopNavItems.forEach { item ->
                                        val selected = currentDest?.hasRoute(item.route::class) == true
                                        NavigationBarItem(
                                            selected = selected,
                                            onClick = { navigateTopLevel(item.route) },
                                            icon = {
                                                Icon(
                                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                    contentDescription = item.label
                                                )
                                            },
                                            label = { Text(item.label) },
                                            modifier = Modifier.testTag(item.testTag)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail && isTopLevel) {
                        NavigationRail(
                            modifier = Modifier.fillMaxHeight(),
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            TopNavItems.forEach { item ->
                                val selected = currentDest?.hasRoute(item.route::class) == true
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(item.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label
                                        )
                                    },
                                    label = { Text(item.label) },
                                    modifier = Modifier.testTag(item.testTag)
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        NavHost(
                            navController = navController,
                            startDestination = HomeRoute
                        ) {
                            composable<HomeRoute> {
                                HomeScreen(
                                    playbackState = playbackState,
                                    audioCount = audioItems.size,
                                    videoCount = videoItems.size,
                                    recentProjects = projects,
                                    smartAlbums = smartAlbums,
                                    onTogglePlayPause = libraryViewModel::togglePlayPause,
                                    onOpenNowPlaying = { navController.navigate(NowPlayingRoute) },
                                    onOpenVideoEditor = { navigateTopLevel(EditorProjectsRoute) },
                                    onOpenProject = { id ->
                                        editorViewModel.openProject(id)
                                        navController.navigate(TimelineEditorRoute)
                                    },
                                    onOpenShare = { navigateTopLevel(AuraShareRoute) },
                                    onOpenPrivateSpace = { navController.navigate(PrivateSpaceRoute) },
                                    onOpenSmartAlbums = { navController.navigate(SmartAlbumsRoute) },
                                    onOpenPlaylists = { navController.navigate(PlaylistsRoute) },
                                    onOpenDownloads = { navController.navigate(DownloadCenterRoute) },
                                    onOpenSettings = { navController.navigate(SettingsRoute) },
                                    onOpenMusicLibrary = { navigateTopLevel(MusicLibraryRoute) },
                                    onOpenVideoLibrary = { navigateTopLevel(VideoLibraryRoute) }
                                )
                            }

                            composable<MusicLibraryRoute> {
                                MusicLibraryScreen(
                                    tracks = audioItems,
                                    playlists = playlists,
                                    playbackState = playbackState,
                                    onPlayTrack = { track -> libraryViewModel.playTrack(track, audioItems) },
                                    onImportAudio = { uri -> libraryViewModel.importMediaUri(uri, MediaKind.AUDIO) },
                                    onMoveToPrivate = { id -> libraryViewModel.moveToPrivateSpace(id, true) },
                                    onAddToPlaylist = libraryViewModel::addTrackToPlaylist,
                                    onShareItem = { item ->
                                        shareViewModel.addLibraryItemToShare(
                                            item.uri,
                                            item.title,
                                            item.mimeType,
                                            item.sizeBytes,
                                            item.isPrivate
                                        )
                                        navigateTopLevel(AuraShareRoute)
                                    },
                                    onOpenNowPlaying = { navController.navigate(NowPlayingRoute) }
                                )
                            }

                            composable<NowPlayingRoute> {
                                NowPlayingScreen(
                                    playbackState = playbackState,
                                    onBack = { navController.popBackStack() },
                                    onTogglePlayPause = libraryViewModel::togglePlayPause,
                                    onSeekTo = libraryViewModel::seekTo,
                                    onNext = libraryViewModel::nextTrack,
                                    onPrevious = libraryViewModel::previousTrack,
                                    onCycleRepeat = libraryViewModel::cycleRepeat,
                                    onToggleShuffle = libraryViewModel::toggleShuffle,
                                    onSetSpeed = libraryViewModel::setSpeed
                                )
                            }

                            composable<VideoLibraryRoute> {
                                VideoLibraryScreen(
                                    videos = videoItems,
                                    onImportVideo = { uri -> libraryViewModel.importMediaUri(uri, MediaKind.VIDEO) },
                                    onEditInStudio = { video ->
                                        editorViewModel.openMediaInNewProject(video.uri, video.title, isVideo = true)
                                        navController.navigate(TimelineEditorRoute)
                                    },
                                    onShareVideo = { video ->
                                        shareViewModel.addLibraryItemToShare(
                                            video.uri,
                                            video.title,
                                            video.mimeType,
                                            video.sizeBytes,
                                            video.isPrivate
                                        )
                                        navigateTopLevel(AuraShareRoute)
                                    },
                                    onMoveToPrivate = { id -> libraryViewModel.moveToPrivateSpace(id, true) }
                                )
                            }

                            composable<EditorProjectsRoute> {
                                EditorProjectManagerScreen(
                                    projects = projects,
                                    onCreateProject = { name, ratio ->
                                        editorViewModel.createAndOpenProject(name, ratio)
                                        navController.navigate(TimelineEditorRoute)
                                    },
                                    onOpenProject = { id ->
                                        editorViewModel.openProject(id)
                                        navController.navigate(TimelineEditorRoute)
                                    },
                                    onDuplicateProject = editorViewModel::duplicateProject,
                                    onDeleteProject = editorViewModel::deleteProject
                                )
                            }

                            composable<TimelineEditorRoute> {
                                TimelineEditorScreen(
                                    project = activeProject,
                                    selectedClipId = selectedClipId,
                                    selectedAudioId = selectedAudioId,
                                    selectedTextId = selectedTextId,
                                    canUndo = canUndo,
                                    canRedo = canRedo,
                                    lastSavedLabel = lastSavedLabel,
                                    voiceOverState = voiceOverState,
                                    viewModel = editorViewModel,
                                    onOpenExport = { navController.navigate(ExportRoute) },
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<ExportRoute> {
                                ExportScreen(
                                    project = activeProject,
                                    capabilities = editorViewModel.exportCapabilities,
                                    exportState = exportState,
                                    estimatedSizeMb = editorViewModel::estimateSizeMb,
                                    onStartExport = editorViewModel::startExport,
                                    onCancelExport = editorViewModel::cancelExport,
                                    onResetExport = editorViewModel::resetExport,
                                    onOpenVideoLibrary = {
                                        navController.popBackStack(HomeRoute, inclusive = false)
                                        navigateTopLevel(VideoLibraryRoute)
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<AuraShareRoute> {
                                AuraShareScreen(
                                    viewModel = shareViewModel,
                                    selectedFiles = selectedShareFiles,
                                    selectedPeer = selectedPeer,
                                    qrBitmap = qrBitmap,
                                    pairingUriText = pairingUriText,
                                    pairingPinDisplay = pairingPinDisplay,
                                    serverPort = serverPort,
                                    shareError = shareError,
                                    onOpenDiscovery = { navController.navigate(DeviceDiscoveryRoute) },
                                    onOpenTransferScreen = { navController.navigate(FileTransferRoute) },
                                    onOpenHistory = { navController.navigate(TransferHistoryRoute) }
                                )
                            }

                            composable<DeviceDiscoveryRoute> {
                                DeviceDiscoveryScreen(
                                    viewModel = shareViewModel,
                                    peers = discoveredPeers,
                                    isDiscovering = isDiscovering,
                                    onPeerSelected = { navController.popBackStack() },
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<FileTransferRoute> {
                                FileTransferScreen(
                                    activeTransfer = activeTransfer,
                                    isPaused = isSharePaused,
                                    onPause = shareViewModel::pauseTransfer,
                                    onResume = shareViewModel::resumeTransfer,
                                    onCancel = shareViewModel::cancelTransfer,
                                    onOpenHistory = { navController.navigate(TransferHistoryRoute) },
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<TransferHistoryRoute> {
                                TransferHistoryScreen(
                                    records = transferHistory,
                                    onDeleteRecord = shareViewModel::deleteTransferRecord,
                                    onClearAll = shareViewModel::clearTransferHistory,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<PrivateSpaceRoute> {
                                PrivateSpaceScreen(
                                    hasPin = settings.hasPrivatePin,
                                    isUnlocked = isPrivateUnlocked,
                                    pinError = pinError,
                                    privateItems = privateItems,
                                    onSetupPin = libraryViewModel::setupPrivatePin,
                                    onUnlock = libraryViewModel::unlockPrivateSpace,
                                    onLock = libraryViewModel::lockPrivateSpace,
                                    onImportPrivateMedia = { uri ->
                                        libraryViewModel.importMediaUri(uri, isPrivate = true)
                                    },
                                    onRemoveFromPrivate = { id ->
                                        libraryViewModel.moveToPrivateSpace(id, false)
                                    },
                                    onPlayAudio = { item ->
                                        libraryViewModel.playTrack(item, listOf(item))
                                        navController.navigate(NowPlayingRoute)
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<SmartAlbumsRoute> {
                                SmartAlbumsScreen(
                                    albums = smartAlbums,
                                    allPublicItems = allPublicItems,
                                    onPlayAudio = { item ->
                                        libraryViewModel.playTrack(item, audioItems)
                                        navController.navigate(NowPlayingRoute)
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<PlaylistsRoute> {
                                PlaylistsScreen(
                                    playlists = playlists,
                                    selectedPlaylistId = selectedPlaylistId,
                                    selectedTracks = selectedPlaylistTracks,
                                    onSelectPlaylist = libraryViewModel::selectPlaylist,
                                    onCreatePlaylist = libraryViewModel::createPlaylist,
                                    onDeletePlaylist = libraryViewModel::deletePlaylist,
                                    onPlayTrack = { track, q ->
                                        libraryViewModel.playTrack(track, q)
                                        navController.navigate(NowPlayingRoute)
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<DownloadCenterRoute> {
                                DownloadCenterScreen(
                                    downloads = downloads,
                                    onEnqueuePack = libraryViewModel::enqueueSampleDownload,
                                    onTogglePause = libraryViewModel::togglePauseDownload,
                                    onDelete = libraryViewModel::deleteDownload,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<SettingsRoute> {
                                SettingsScreen(
                                    settings = settings,
                                    capabilities = libraryViewModel.exportCapabilities,
                                    onSetThemeMode = libraryViewModel::setThemeMode,
                                    onSetDynamicColor = libraryViewModel::setDynamicColor,
                                    onSetAutosave = libraryViewModel::setAutosaveEditor,
                                    onSetMagneticSnap = libraryViewModel::setMagneticSnapDefault,
                                    onSetPauseOnNoisy = libraryViewModel::setPauseOnNoisy,
                                    onSetDeviceAlias = libraryViewModel::setDeviceAlias,
                                    onSelectShareFolder = { uri ->
                                        libraryViewModel.setShareDownloadFolderUri(uri.toString())
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }

            incomingPrompt?.let { prompt ->
                IncomingTransferConsentDialog(
                    prompt = prompt,
                    onAccept = {
                        shareViewModel.respondToIncomingConsent(true)
                        navController.navigate(FileTransferRoute)
                    },
                    onDecline = { shareViewModel.respondToIncomingConsent(false) }
                )
            }
        }
    }
}
