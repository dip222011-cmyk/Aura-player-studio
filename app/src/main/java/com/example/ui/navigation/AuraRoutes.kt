package com.example.ui.navigation

import kotlinx.serialization.Serializable

sealed interface AuraRoute

@Serializable data object HomeRoute : AuraRoute
@Serializable data object MusicLibraryRoute : AuraRoute
@Serializable data object VideoLibraryRoute : AuraRoute
@Serializable data object EditorProjectsRoute : AuraRoute
@Serializable data object TimelineEditorRoute : AuraRoute
@Serializable data object ExportRoute : AuraRoute
@Serializable data object AuraShareRoute : AuraRoute
@Serializable data object DeviceDiscoveryRoute : AuraRoute
@Serializable data object FileTransferRoute : AuraRoute
@Serializable data object TransferHistoryRoute : AuraRoute
@Serializable data object PrivateSpaceRoute : AuraRoute
@Serializable data object SmartAlbumsRoute : AuraRoute
@Serializable data object DownloadCenterRoute : AuraRoute
@Serializable data object PlaylistsRoute : AuraRoute
@Serializable data object SettingsRoute : AuraRoute
@Serializable data object NowPlayingRoute : AuraRoute
