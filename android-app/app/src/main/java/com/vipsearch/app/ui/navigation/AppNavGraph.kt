package com.vipsearch.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.vipsearch.app.AppContainer
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
import com.vipsearch.app.ui.viewmodel.SearchViewModel
import com.vipsearch.app.ui.viewmodel.SearchViewModelFactory

private data class BottomTab(val route: String, val title: String)

@Composable
fun AppNavGraph(appContainer: AppContainer) {
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
    BottomTab(route = "search", title = "搜索"),
    BottomTab(route = "history", title = "历史"),
    BottomTab(route = "favorites", title = "收藏")
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

  Scaffold(
    bottomBar = {
      if (showBottomBar) {
        NavigationBar {
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
              label = { Text(tab.title) },
              icon = {}
            )
          }
        }
      }
    }
  ) { paddingValues ->
    NavHost(
      navController = navController,
      startDestination = "search",
      modifier = Modifier.padding(paddingValues)
    ) {
      composable("search") {
        SearchScreen(
          state = searchVm.state,
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
      composable("player") {
        val vm: PlayerViewModel = viewModel(
          factory = PlayerViewModelFactory(
            getAltSourcesUseCase = appContainer.getAltSourcesUseCase,
            searchRepository = appContainer.searchRepository
          )
        )
        LaunchedEffect(PlaybackSessionStore.session) {
          vm.bindSession(PlaybackSessionStore.session)
        }
        PlayerScreen(
          uiState = vm.state,
          onBack = { navController.popBackStack() },
          onSwitchEpisode = vm::switchEpisode,
          onNextEpisode = vm::nextEpisode,
          onRequestExtraSources = vm::requestExtraSources
        )
      }
    }
  }
}
