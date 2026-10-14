package app.ammusic.ui

import androidx.activity.compose.BackHandler
import app.ammusic.youtube.tr

import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.platform.LocalWindowInfo
import app.ammusic.ui.player.PlayerSheet
import app.ammusic.ui.player.rememberPlayerSheetState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.ui.platform.LocalContext
import app.ammusic.ui.components.AmIcons
import app.ammusic.ui.components.AppActions
import app.ammusic.ui.components.FloatingBars
import app.ammusic.ui.components.FloatingBarsHeight
import app.ammusic.ui.components.LocalAccount
import app.ammusic.ui.components.LocalActions
import app.ammusic.ui.components.LocalBottomInset
import app.ammusic.ui.components.LocalHazeState
import app.ammusic.ui.components.LocalNowPlaying
import app.ammusic.ui.components.LocalScreenScope
import app.ammusic.ui.components.LocalSharedScope
import app.ammusic.ui.components.MenuAction
import app.ammusic.ui.components.MenuArtwork
import app.ammusic.ui.components.NoticeHost
import app.ammusic.ui.components.NowPlaying
import app.ammusic.ui.components.RowCoverSize
import app.ammusic.ui.components.Tab
import app.ammusic.ui.components.MenuRequest
import app.ammusic.ui.components.MenuSheet
import app.ammusic.ui.components.DownloadsInfo
import app.ammusic.ui.components.LocalDownloads
import app.ammusic.data.library.DownloadState
import app.ammusic.ui.components.ContextMenu
import app.ammusic.ui.components.PreviewRequest
import app.ammusic.ui.components.PromptRequest
import app.ammusic.ui.components.TextPrompt
import app.ammusic.ui.account.LoginScreen
import app.ammusic.ui.library.LibraryListScreen
import androidx.navigation.toRoute
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.rememberUpdatedState
import app.ammusic.ui.components.sharedKey
import app.ammusic.ui.detail.ArtistScreen
import app.ammusic.ui.detail.BrowseScreen
import app.ammusic.ui.detail.CollectionScreen
import app.ammusic.youtube.model.YtAlbum
import app.ammusic.youtube.model.YtArtist
import app.ammusic.youtube.model.YtPlaylist
import app.ammusic.youtube.model.YtTrack
import app.ammusic.ui.home.HomeScreen
import app.ammusic.ui.library.LibraryScreen
import app.ammusic.ui.library.RecapScreen
import app.ammusic.ui.library.AddSongsScreen
import app.ammusic.ui.search.SearchScreen
import app.ammusic.ui.settings.SettingsScreen
import app.ammusic.ui.settings.EqualizerScreen
import app.ammusic.ui.theme.AmTheme
import app.ammusic.ui.theme.ThemeMode
import dev.chrisbanes.haze.rememberHazeState

private val Tab.route: Any
    get() = when (this) {
        Tab.Home -> HomeRoute
        Tab.Search -> SearchRoute
        Tab.Library -> LibraryRoute
    }

private val SlideSpec = spring<IntOffset>(dampingRatio = 0.9f, stiffness = 340f)
private val FadeSpec = spring<Float>(stiffness = 380f)

private class BarsScrollConnection(private val onCollapsed: (Boolean) -> Unit) : NestedScrollConnection {
    private var travelled = 0f

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val dy = available.y
        if (dy == 0f) return Offset.Zero
        if (dy * travelled < 0f) travelled = 0f
        travelled += dy
        if (travelled < -48f) onCollapsed(true) else if (travelled > 48f) onCollapsed(false)
        return Offset.Zero
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppRoot(
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    accentIndex: Int,
    onAccent: (Int) -> Unit,
) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    var currentTab by rememberSaveable { mutableStateOf(Tab.Home) }
    var collapsed by remember { mutableStateOf(false) }
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val player by playerViewModel.state.collectAsStateWithLifecycle()
    val sheet = rememberPlayerSheetState()
    sheet.travelPx = LocalWindowInfo.current.containerSize.height * 0.8f
    val scroll = remember { BarsScrollConnection { collapsed = it } }

    LaunchedEffect(entry) {
        val destination = entry?.destination ?: return@LaunchedEffect
        Tab.entries.firstOrNull { destination.hasRoute(it.route::class) }?.let { currentTab = it }
        collapsed = false
    }

    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + FloatingBarsHeight
    val openSettings = { nav.navigate(SettingsRoute) { launchSingleTop = true } }
    val selectTab = { tab: Tab ->
        currentTab = tab
        nav.navigate(tab.route) {
            popUpTo(nav.graph.id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val atOtherTabRoot = entry?.destination?.let { destination ->
        !destination.hasRoute(HomeRoute::class) && Tab.entries.any { destination.hasRoute(it.route::class) }
    } == true
    BackHandler(enabled = atOtherTabRoot) { selectTab(Tab.Home) }
    val barsAway by animateFloatAsState(
        targetValue = if (entry?.destination?.hasRoute(LoginRoute::class) == true) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 300f),
        label = "barsAway",
    )
    var sheetRequest by remember { mutableStateOf<MenuRequest?>(null) }
    var previewRequest by remember { mutableStateOf<PreviewRequest?>(null) }
    var promptRequest by remember { mutableStateOf<PromptRequest?>(null) }
    val context = LocalContext.current
    val libraryViewModel: LibraryViewModel = hiltViewModel()
    val playlists by libraryViewModel.playlists.collectAsStateWithLifecycle()
    val likedIds by libraryViewModel.likedIds.collectAsStateWithLifecycle()
    val downloadStates by libraryViewModel.downloadStates.collectAsStateWithLifecycle()
    val downloadRunning by libraryViewModel.downloadRunning.collectAsStateWithLifecycle()
    val account by libraryViewModel.account.collectAsStateWithLifecycle()

    val openArtist = { id: String, name: String -> nav.navigate(ArtistRoute(id, name, null, "", RowCoverSize)) }

    val choosePlaylist = { track: YtTrack ->
        val create = MenuAction(AmIcons.Plus, tr("Nouvelle playlist…", "New playlist…"), artwork = MenuArtwork(null, accent = true)) {
            promptRequest = PromptRequest(tr("Nouvelle playlist", "New playlist"), "", null) { title -> libraryViewModel.addToNewPlaylist(title, track) }
        }
        val existing = playlists.filter { it.editable }.map { playlist ->
            MenuAction(AmIcons.Playlist, playlist.title, detail = playlist.subtitle, artwork = MenuArtwork(playlist.thumbnail)) {
                libraryViewModel.addToPlaylist(playlist, track)
            }
        }
        sheetRequest = MenuRequest(tr("Ajouter à une playlist", "Add to a playlist"), track.title, track.thumbnail, listOf(create) + existing)
    }

    val trackActions = { track: YtTrack ->
        val liked = track.id in likedIds
        listOfNotNull(
            MenuAction(AmIcons.PlayNext, tr("Lire ensuite", "Play next")) { playerViewModel.playNext(track) },
            MenuAction(AmIcons.QueueAdd, tr("Ajouter à la file d'attente", "Add to queue")) { playerViewModel.addToQueue(track) },
            MenuAction(if (liked) AmIcons.HeartFilled else AmIcons.Heart, if (liked) tr("Retirer des titres aimés", "Remove from liked songs") else tr("Ajouter aux titres aimés", "Add to liked songs")) {
                libraryViewModel.toggleLike(track)
            },
            MenuAction(AmIcons.Playlist, tr("Ajouter à une playlist", "Add to a playlist")) { choosePlaylist(track) },
            MenuAction(AmIcons.Radio, tr("Lancer la radio du titre", "Start song radio")) { playerViewModel.play(track) },
            if (downloadStates[track.id] == null || downloadStates[track.id] == DownloadState.Failed) {
                MenuAction(AmIcons.Download, tr("Télécharger", "Download")) { libraryViewModel.download(listOf(track)) }
            } else {
                MenuAction(AmIcons.Downloaded, tr("Supprimer le téléchargement", "Remove download")) { libraryViewModel.removeDownloads(listOf(track.id)) }
            },
            track.albumId?.let { id ->
                MenuAction(AmIcons.Album, tr("Voir l'album", "Go to album")) {
                    sheet.collapse()
                    nav.navigate(CollectionRoute(true, id, track.album.orEmpty(), track.artists, track.thumbnail, "", RowCoverSize))
                }
            },
            track.artistId?.let { id ->
                MenuAction(AmIcons.Artist, tr("Voir l'artiste", "Go to artist")) { sheet.collapse(); openArtist(id, track.artists.substringBefore(", ")) }
            },
            MenuAction(AmIcons.Share, tr("Partager", "Share")) {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/watch?v=${track.id}")
                context.startActivity(Intent.createChooser(send, null))
            },
        )
    }
    val currentTrackActions by rememberUpdatedState(trackActions)

    val actions = remember(nav, playerViewModel) {
        AppActions(
            open = { item, origin, coverSize ->
                val key = sharedKey(origin, item)
                when (item) {
                    is YtTrack -> playerViewModel.play(item)
                    is YtAlbum -> nav.navigate(CollectionRoute(true, item.id, item.title, item.subtitle, item.thumbnail, key, coverSize))
                    is YtPlaylist -> nav.navigate(CollectionRoute(false, item.id, item.title, item.subtitle, item.thumbnail, key, coverSize))
                    is YtArtist -> nav.navigate(ArtistRoute(item.id, item.title, item.thumbnail, key, coverSize))
                }
            },
            playAll = playerViewModel::playAll,
            download = libraryViewModel::download,
            removeDownloads = libraryViewModel::removeDownloads,
            addSongs = { id, title -> nav.navigate(AddSongsRoute(id, title)) },
            toggle = { playerViewModel.toggle() },
            menu = { track, extras ->
                val (pinned, rest) = extras.partition { it.pinned }
                sheetRequest = MenuRequest(track.title, track.artists, track.thumbnail, pinned + currentTrackActions(track) + rest)
            },
            preview = { track, extras, from -> previewRequest = PreviewRequest(MenuRequest(track.title, track.artists, track.thumbnail, currentTrackActions(track) + extras), from) },
            sheet = { sheetRequest = it },
            prompt = { title, initial, thumbnail, onConfirm -> promptRequest = PromptRequest(title, initial, thumbnail, onConfirm) },
            openLibrary = { kind -> if (kind == LibraryKind.Recap) nav.navigate(RecapRoute) else nav.navigate(LibraryListRoute(kind)) },
            openLogin = { nav.navigate(LoginRoute) { launchSingleTop = true } },
            openMood = { nav.navigate(BrowseRoute(it.browseId, it.params, it.title, artist = false)) },
            openMore = { title, browseId, params -> nav.navigate(BrowseRoute(browseId, params, title, artist = true)) },
            openArtist = openArtist,
            back = { nav.popBackStack() },
        )
    }

    CompositionLocalProvider(
        LocalHazeState provides rememberHazeState(),
        LocalBottomInset provides bottomInset,
        LocalActions provides actions,
        LocalAccount provides account,
        LocalNowPlaying provides NowPlaying(player.track?.id, player.isPlaying),
        LocalDownloads provides DownloadsInfo(downloadStates, downloadRunning),
    ) {
        Box(Modifier.fillMaxSize().background(AmTheme.colors.background).nestedScroll(scroll)) {
            SharedTransitionLayout {
                val screen: @Composable AnimatedVisibilityScope.(@Composable () -> Unit) -> Unit = { content ->
                    CompositionLocalProvider(LocalSharedScope provides this@SharedTransitionLayout, LocalScreenScope provides this, content = content)
                }
                NavHost(
                    navController = nav,
                    startDestination = HomeRoute,
                    enterTransition = { fadeIn(FadeSpec) + scaleIn(FadeSpec, initialScale = 0.985f) },
                    exitTransition = { fadeOut(FadeSpec) },
                    popEnterTransition = { fadeIn(FadeSpec) },
                    popExitTransition = { fadeOut(FadeSpec) },
                ) {
                    composable<HomeRoute> { screen { HomeScreen(onProfile = openSettings) } }
                    composable<SearchRoute> { screen { SearchScreen(onProfile = openSettings) } }
                    composable<LibraryRoute> { screen { LibraryScreen(onProfile = openSettings) } }
                    composable<CollectionRoute> { screen { CollectionScreen() } }
                    composable<ArtistRoute> { screen { ArtistScreen() } }
                    composable<BrowseRoute> { screen { BrowseScreen() } }
                    composable<LibraryListRoute> { entry -> screen { LibraryListScreen(entry.toRoute<LibraryListRoute>().kind) } }
                    composable<RecapRoute> { screen { RecapScreen() } }
                    composable<AddSongsRoute>(
                        enterTransition = { slideInVertically(SlideSpec) { it } },
                        popExitTransition = { slideOutVertically(SlideSpec) { it } },
                    ) {
                        AddSongsScreen()
                    }
                    composable<LoginRoute>(
                        enterTransition = { slideInVertically(SlideSpec) { it } },
                        popExitTransition = { slideOutVertically(SlideSpec) { it } },
                    ) {
                        LoginScreen()
                    }
                    composable<SettingsRoute>(
                        enterTransition = { slideInHorizontally(SlideSpec) { it } },
                        popExitTransition = { slideOutHorizontally(SlideSpec) { it } },
                    ) {
                        SettingsScreen(themeMode, onThemeMode, accentIndex, onAccent, onBack = { nav.popBackStack() }, onEqualizer = { nav.navigate(EqualizerRoute) })
                    }
                    composable<EqualizerRoute>(
                        enterTransition = { slideInHorizontally(SlideSpec) { it } },
                        popExitTransition = { slideOutHorizontally(SlideSpec) { it } },
                    ) {
                        EqualizerScreen()
                    }
                }
            }

            FloatingBars(
                current = currentTab,
                onSelect = selectTab,
                collapsed = collapsed,
                onExpand = { collapsed = false },
                player = player,
                sheet = sheet,
                onTogglePlay = playerViewModel::toggle,
                onNext = playerViewModel::next,
                modifier = Modifier.align(Alignment.BottomCenter).graphicsLayer {
                    translationY = barsAway * 240.dp.toPx()
                    alpha = 1f - barsAway
                },
            )

            if (sheet.isOpen) PlayerSheet(sheet, player, playerViewModel)

            ContextMenu(previewRequest, onDismiss = { previewRequest = null })
            MenuSheet(sheetRequest, onDismiss = { sheetRequest = null })
            TextPrompt(promptRequest, onDismiss = { promptRequest = null })
            NoticeHost(libraryViewModel.notices, Modifier.align(Alignment.TopCenter))
        }
    }
}
