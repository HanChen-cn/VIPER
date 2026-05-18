package com.vipsearch.app.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vipsearch.app.data.model.Episode
import com.vipsearch.app.data.repository.HistoryRepository
import com.vipsearch.app.data.repository.SearchRepository
import com.vipsearch.app.domain.usecase.GetAltSourcesUseCase
import com.vipsearch.app.ui.state.PlaybackSession
import com.vipsearch.app.ui.state.PlaybackSessionStore
import kotlinx.coroutines.launch

data class PlayerUiState(
  val session: PlaybackSession = PlaybackSession(),
  val loadingExtraSources: Boolean = false,
  val extraSources: List<String> = emptyList(),
  val error: String = "",
  val episodes: List<Episode> = emptyList(),
  val currentEpisodeIndex: Int = -1,
  val hasNextEpisode: Boolean = false,
  val loadingEpisodes: Boolean = false,
)

class PlayerViewModel(
  private val getAltSourcesUseCase: GetAltSourcesUseCase,
  private val searchRepository: SearchRepository,
  private val historyRepository: HistoryRepository
) : ViewModel() {
  var state by mutableStateOf(PlayerUiState())
    private set

  fun bindSession(session: PlaybackSession) {
    state = state.copy(session = session, extraSources = emptyList(), error = "")
    if (session.primaryUrl.isBlank() || session.showName.isBlank() || session.episodeName.isBlank()) {
      return
    }
    loadEpisodeList(session.showName, session.episodeName)
  }

  fun requestExtraSources() {
    if (state.loadingExtraSources || state.extraSources.isNotEmpty()) return
    loadExtraSources(state.session)
  }

  private fun loadExtraSources(session: PlaybackSession) {
    viewModelScope.launch {
      state = state.copy(loadingExtraSources = true)
      val result = runCatching {
        getAltSourcesUseCase(
          showName = session.showName,
          episodeName = session.episodeName,
          primaryUrl = session.primaryUrl
        ) {
          searchRepository.getAltSources(
            showName = session.showName,
            episodeName = session.episodeName,
            currentUrl = session.primaryUrl
          )
        }
      }
      state = result.fold(
        onSuccess = { extra ->
          state.copy(loadingExtraSources = false, extraSources = extra, error = "")
        },
        onFailure = {
          state.copy(loadingExtraSources = false, extraSources = emptyList(), error = it.message ?: "备源加载失败")
        }
      )
    }
  }

  private fun loadEpisodeList(showName: String, currentEpisodeName: String) {
    viewModelScope.launch {
      state = state.copy(loadingEpisodes = true)
      val result = runCatching { searchRepository.getEpisodeList(showName) }
      state = result.fold(
        onSuccess = { episodes ->
          val index = episodes.indexOfFirst { it.name.trim() == currentEpisodeName.trim() }
          state.copy(
            loadingEpisodes = false,
            episodes = episodes,
            currentEpisodeIndex = index,
            hasNextEpisode = index >= 0 && index < episodes.size - 1
          )
        },
        onFailure = {
          state.copy(loadingEpisodes = false)
        }
      )
    }
  }

  fun switchEpisode(index: Int) {
    val ep = state.episodes.getOrNull(index) ?: return
    val newSession = PlaybackSession(
      showName = state.session.showName,
      episodeName = ep.name,
      primaryUrl = ep.playUrl,
      altUrls = ep.altUrls
    )
    PlaybackSessionStore.update(
      showName = newSession.showName,
      episodeName = newSession.episodeName,
      primaryUrl = newSession.primaryUrl,
      altUrls = newSession.altUrls
    )
    state = state.copy(
      session = newSession,
      currentEpisodeIndex = index,
      hasNextEpisode = index < state.episodes.size - 1,
      extraSources = emptyList(),
      loadingExtraSources = false,
      error = ""
    )
    viewModelScope.launch {
      historyRepository.addPlayHistory(
        showName = newSession.showName,
        episodeName = newSession.episodeName,
        url = newSession.primaryUrl
      )
    }
  }

  fun nextEpisode() {
    if (state.hasNextEpisode) {
      switchEpisode(state.currentEpisodeIndex + 1)
    }
  }

}

class PlayerViewModelFactory(
  private val getAltSourcesUseCase: GetAltSourcesUseCase,
  private val searchRepository: SearchRepository,
  private val historyRepository: HistoryRepository
) : ViewModelProvider.Factory {
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    if (modelClass.isAssignableFrom(PlayerViewModel::class.java)) {
      @Suppress("UNCHECKED_CAST")
      return PlayerViewModel(getAltSourcesUseCase, searchRepository, historyRepository) as T
    }
    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
  }
}
