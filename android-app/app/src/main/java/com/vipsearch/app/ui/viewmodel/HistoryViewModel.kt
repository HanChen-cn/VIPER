package com.vipsearch.app.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vipsearch.app.data.model.PlayHistoryItem
import com.vipsearch.app.data.model.SearchHistoryItem
import com.vipsearch.app.data.repository.HistoryRepository
import kotlinx.coroutines.launch

data class HistoryUiState(
  val loading: Boolean = false,
  val searchHistory: List<SearchHistoryItem> = emptyList(),
  val playHistory: List<PlayHistoryItem> = emptyList()
)

class HistoryViewModel(
  private val historyRepository: HistoryRepository
) : ViewModel() {
  var state by mutableStateOf(HistoryUiState())
    private set

  fun load() {
    viewModelScope.launch {
      state = state.copy(loading = true)
      state = state.copy(
        loading = false,
        searchHistory = historyRepository.getSearchHistory(),
        playHistory = historyRepository.getPlayHistory()
      )
    }
  }

  fun clearSearchHistory() {
    viewModelScope.launch {
      historyRepository.clearSearchHistory()
      load()
    }
  }
}

class HistoryViewModelFactory(
  private val historyRepository: HistoryRepository
) : ViewModelProvider.Factory {
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
      @Suppress("UNCHECKED_CAST")
      return HistoryViewModel(historyRepository) as T
    }
    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
  }
}
