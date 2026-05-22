package com.vipsearch.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.MutableState
import com.vipsearch.app.AppContainer
import com.vipsearch.app.ui.theme.ThemeMode
import com.vipsearch.app.BuildConfig
import com.vipsearch.app.data.remote.AppUpdate
import com.vipsearch.app.data.remote.UpdateChecker
import com.vipsearch.app.ui.components.UpdateDialog
import kotlinx.coroutines.launch
import com.vipsearch.app.ui.screens.FavoritesScreen
import com.vipsearch.app.ui.screens.HistoryScreen
import com.vipsearch.app.ui.screens.PlayerScreen
import com.vipsearch.app.ui.screens.SearchScreen
import com.vipsearch.app.ui.state.PlaybackSessionStore
import com.vipsearch.app.ui.viewmodel.PlayerViewModel
import com.vipsearch.app.ui.viewmodel.PlayerViewModelFactory
import com.vipsearch.app.ui.viewmodel.HistoryViewModel
import com.vipsearch.app.ui.viewmodel.HistoryViewModelFactory
import com.vipsearch.app.ui.viewmodel.FavoritesViewModel
import com.vipsearch.app.ui.viewmodel.FavoritesViewModelFactory
import com.vipsearch.app.dlna.CastState
import com.vipsearch.app.ui.viewmodel.CastViewModel
import com.vipsearch.app.ui.viewmodel.CastViewModelFactory
import com.vipsearch.app.ui.viewmodel.SearchViewModel
import com.vipsearch.app.ui.viewmodel.SearchViewModelFactory

private data class BottomTab(
  val route: String,
  val title: String,
  val icon: ImageVector,
  val selectedIcon: ImageVector
)

@Composable
fun AppNavGraph(appContainer: AppContainer, themeMode: MutableState<ThemeMode>) {
  val navController = rememberNavController()
  val scope = rememberCoroutineScope()
  val pendingUpdate = remember { mutableStateOf<AppUpdate?>(null) }

  LaunchedEffect(Unit) {
    scope.launch {
      val update = UpdateChecker().check(BuildConfig.VERSION_NAME)
      if (update != null) pendingUpdate.value = update
    }
  }

  pendingUpdate.value?.let { update ->
    UpdateDialog(
      update = update,
      onDismiss = { pendingUpdate.value = null }
    )
  }

  val tabs = listOf(
    BottomTab("search", "搜索", Icons.Outlined.Search, Icons.Filled.Search),
    BottomTab("history", "历史", Icons.Outlined.History, Icons.Filled.History),
    BottomTab("favorites", "收藏", Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite)
  )
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination = navBackStackEntry?.destination
  val showBottomBar = tabs.any { tab ->
    currentDestination?.hierarchy?.any { it.route == tab.route } == true
  }
  val searchVm: SearchViewModel = viewModel(
    factory = SearchViewModelFactory(
      searchUseCase = appContainer.searchUseCase,
      historyRepository = appContainer.historyRepository,
      favoritesRepository = appContainer.favoritesRepository
    )
  )

  fun navigateToSearchAndQuery(keyword: String) {
    searchVm.searchWithKeyword(keyword)
    navController.navigate("search") {
      popUpTo(navController.graph.startDestinationId) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  val colors = MaterialTheme.colorScheme

  Scaffold(
    containerColor = colors.background,
    contentColor = colors.onBackground,
    bottomBar = {
      if (showBottomBar) {
        NavigationBar(
          containerColor = colors.surface,
          contentColor = colors.onSurface
        ) {
          tabs.forEach { tab ->
            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
            NavigationBarItem(
              selected = selected,
              onClick = {
                navController.navigate(tab.route) {
                  popUpTo(navController.graph.startDestinationId) { saveState = true }
                  launchSingleTop = true
                  restoreState = true
                }
              },
              label = {
                Text(
                  tab.title,
                  color = if (selected) colors.primary else colors.onSurfaceVariant
                )
              },
              icon = {
                Icon(
                  imageVector = if (selected) tab.selectedIcon else tab.icon,
                  contentDescription = tab.title,
                  modifier = Modifier.size(22.dp)
                )
              },
              colors = NavigationBarItemDefaults.colors(
                selectedIconColor = colors.primary,
                selectedTextColor = colors.primary,
                unselectedIconColor = colors.onSurfaceVariant,
                unselectedTextColor = colors.onSurfaceVariant,
                indicatorColor = colors.primary.copy(alpha = 0.12f)
              )
            )
          }
        }
      }
    }
  ) { paddingValues ->
    val tabTransitionIn = fadeIn(tween(200))
    val tabTransitionOut = fadeOut(tween(200))

    NavHost(
      navController = navController,
      startDestination = "search",
      modifier = Modifier.padding(paddingValues),
      enterTransition = { tabTransitionIn },
      exitTransition = { tabTransitionOut },
      popEnterTransition = { tabTransitionIn },
      popExitTransition = { tabTransitionOut }
    ) {
      composable("search") {
        SearchScreen(
          state = searchVm.state,
          themeMode = themeMode,
          onKeywordChange = searchVm::onKeywordChange,
          onSearch = searchVm::search,
          onAddFavorite = searchVm::addFavorite,
          onEpisodeClick = { showName, episodeName, playUrl, altUrls ->
            searchVm.recordPlay(showName = showName, episodeName = episodeName, url = playUrl)
            PlaybackSessionStore.update(
              showName = showName,
              episodeName = episodeName,
              primaryUrl = playUrl,
              altUrls = altUrls
            )
            navController.navigate("player")
          }
        )
      }
      composable("history") {
        val vm: HistoryViewModel = viewModel(
          factory = HistoryViewModelFactory(appContainer.historyRepository)
        )
        HistoryScreen(
          state = vm.state,
          onReload = vm::load,
          onClearSearchHistory = vm::clearSearchHistory,
          onSearchKeyword = { keyword ->
            navigateToSearchAndQuery(keyword)
          },
          onPlayHistory = { showName, episodeName, url ->
            PlaybackSessionStore.update(
              showName = showName,
              episodeName = episodeName,
              primaryUrl = url,
              altUrls = emptyList()
            )
            navController.navigate("player")
          }
        )
      }
      composable("favorites") {
        val vm: FavoritesViewModel = viewModel(
          factory = FavoritesViewModelFactory(appContainer.favoritesRepository)
        )
        FavoritesScreen(
          state = vm.state,
          onReload = vm::load,
          onRemove = vm::remove,
          onSearchFavorite = { showName ->
            navigateToSearchAndQuery(showName)
          }
        )
      }
      composable(
        "player",
        enterTransition = { fadeIn(tween(350)) },
        exitTransition = { fadeOut(tween(250)) },
        popEnterTransition = { fadeIn(tween(250)) },
        popExitTransition = { fadeOut(tween(250)) }
      ) {
        val vm: PlayerViewModel = viewModel(
          factory = PlayerViewModelFactory(
            getAltSourcesUseCase = appContainer.getAltSourcesUseCase,
            searchRepository = appContainer.searchRepository,
            historyRepository = appContainer.historyRepository
          )
        )
        val castVm: CastViewModel = viewModel(
          factory = CastViewModelFactory(appContainer.dlnaSessionManager)
        )
        val castState by castVm.castState.collectAsState()
        val castDevices by castVm.devices.collectAsState()
        val connectedDevice by castVm.connectedDevice.collectAsState()
        val castPlaybackInfo by castVm.playbackInfo.collectAsState()
        LaunchedEffect(PlaybackSessionStore.session) {
          vm.bindSession(PlaybackSessionStore.session)
        }
        PlayerScreen(
          uiState = vm.state,
          onBack = { navController.popBackStack() },
          onSwitchEpisode = vm::switchEpisode,
          onNextEpisode = vm::nextEpisode,
          onRequestExtraSources = vm::requestExtraSources,
          castState = castState,
          castDevices = castDevices,
          connectedDevice = connectedDevice,
          castPlaybackInfo = castPlaybackInfo,
          onCastButtonClick = { castVm.startDiscovery() },
          onCastDeviceSelected = { device ->
            val url = PlaybackSessionStore.session.primaryUrl
            val title = "${PlaybackSessionStore.session.showName} · ${PlaybackSessionStore.session.episodeName}"
            castVm.connectAndPlay(device, url, title)
          },
          onCastDismiss = { castVm.stopDiscovery() },
          onCastPlay = { castVm.play() },
          onCastPause = { castVm.pause() },
          onCastSeek = { pos -> castVm.seek(pos) },
          onCastVolumeChange = { vol -> castVm.setVolume(vol) },
          onCastDisconnect = { castVm.disconnect() },
          onCastSwitchMedia = { url, title -> castVm.switchMedia(url, title) }
        )
      }
    }
  }
}
