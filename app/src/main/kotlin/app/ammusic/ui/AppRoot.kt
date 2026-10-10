package app.ammusic.ui

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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ammusic.ui.components.FloatingBars
import app.ammusic.ui.components.FloatingBarsHeight
import app.ammusic.ui.components.LocalBottomInset
import app.ammusic.ui.components.LocalHazeState
import app.ammusic.ui.components.Tab
import app.ammusic.ui.home.HomeScreen
import app.ammusic.ui.library.LibraryScreen
import app.ammusic.ui.search.SearchScreen
import app.ammusic.ui.settings.SettingsScreen
import app.ammusic.ui.theme.AmTheme
import app.ammusic.ui.theme.ThemeMode
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object SearchRoute
@Serializable data object LibraryRoute
@Serializable data object SettingsRoute

private val Tab.route: Any
    get() = when (this) {
        Tab.Home -> HomeRoute
        Tab.Search -> SearchRoute
        Tab.Library -> LibraryRoute
    }

private val SlideSpec = spring<IntOffset>(dampingRatio = 0.9f, stiffness = 340f)
private val FadeSpec = spring<Float>(stiffness = 380f)

/** Replie les barres flottantes quand on descend dans une liste, les déplie quand on remonte. */
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
    val scroll = remember { BarsScrollConnection { collapsed = it } }

    LaunchedEffect(entry) {
        val destination = entry?.destination ?: return@LaunchedEffect
        Tab.entries.firstOrNull { destination.hasRoute(it.route::class) }?.let { currentTab = it }
        collapsed = false
    }

    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + FloatingBarsHeight
    val openSettings = { nav.navigate(SettingsRoute) { launchSingleTop = true } }

    CompositionLocalProvider(
        LocalHazeState provides rememberHazeState(),
        LocalBottomInset provides bottomInset,
    ) {
        Box(Modifier.fillMaxSize().background(AmTheme.colors.background).nestedScroll(scroll)) {
            NavHost(
                navController = nav,
                startDestination = HomeRoute,
                enterTransition = { fadeIn(FadeSpec) + scaleIn(FadeSpec, initialScale = 0.985f) },
                exitTransition = { fadeOut(FadeSpec) },
                popEnterTransition = { fadeIn(FadeSpec) },
                popExitTransition = { fadeOut(FadeSpec) },
            ) {
                composable<HomeRoute> { HomeScreen(onProfile = openSettings, onPlay = playerViewModel::play) }
                composable<SearchRoute> { SearchScreen(onProfile = openSettings, onPlay = playerViewModel::play) }
                composable<LibraryRoute> { LibraryScreen(onProfile = openSettings) }
                composable<SettingsRoute>(
                    enterTransition = { slideInHorizontally(SlideSpec) { it } },
                    popExitTransition = { slideOutHorizontally(SlideSpec) { it } },
                ) {
                    SettingsScreen(themeMode, onThemeMode, accentIndex, onAccent, onBack = { nav.popBackStack() })
                }
            }

            FloatingBars(
                current = currentTab,
                onSelect = { tab ->
                    nav.navigate(tab.route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                collapsed = collapsed,
                onExpand = { collapsed = false },
                player = player,
                onTogglePlay = playerViewModel::toggle,
                onNext = playerViewModel::next,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
