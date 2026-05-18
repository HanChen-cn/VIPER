package com.vipsearch.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vipsearch.app.data.model.Show
import com.vipsearch.app.data.repository.FavoritesRepository
import com.vipsearch.app.data.repository.HistoryRepository
import com.vipsearch.app.domain.usecase.SearchUseCase
import kotlinx.coroutines.launch

data class SearchUiState(
  val keyword: String = "",
  val loading: Boolean = false,
  val error: String = "",
  val results: List<Show> = emptyList(),
  val notice: String = ""
)

class SearchViewModel(
  private val searchUseCase: SearchUseCase,
  private val historyRepository: HistoryRepository,
  private val favoritesRepository: FavoritesRepository
) : ViewModel() {
  var state by mutableStateOf(SearchUiState())
    private set

  fun onKeywordChange(value: String) {
    state = state.copy(keyword = value)
  }

  fun search() {
    val keyword = state.keyword.trim()
    if (keyword.isBlank()) return
    state = state.copy(loading = true, error = "", notice = "")
    viewModelScope.launch {
      val result = runCatching { searchUseCase(keyword) }
      state = result.fold(
        onSuccess = { shows ->
          historyRepository.addSearchHistory(keyword)
          state.copy(loading = false, results = shows, error = if (shows.isEmpty()) "未找到资源" else "")
        },
        onFailure = {
          state.copy(loading = false, error = it.message ?: "搜索失败")
        }
      )
    }
  }

  fun addFavorite(show: Show) {
    viewModelScope.launch {
      runCatching { favoritesRepository.addFavorite(show) }
      state = state.copy(notice = "已收藏：${show.name}")
    }
  }

  fun recordPlay(showName: String, episodeName: String, url: String) {
    viewModelScope.launch {
      historyRepository.addPlayHistory(showName = showName, episodeName = episodeName, url = url)
    }
  }

  fun consumeNotice() {
    if (state.notice.isNotBlank()) {
      state = state.copy(notice = "")
    }
  }
}

class SearchViewModelFactory(
  private val searchUseCase: SearchUseCase,
  private val historyRepository: HistoryRepository,
  private val favoritesRepository: FavoritesRepository
) : ViewModelProvider.Factory {
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
      @Suppress("UNCHECKED_CAST")
      return SearchViewModel(searchUseCase, historyRepository, favoritesRepository) as T
    }
    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
  }
}
