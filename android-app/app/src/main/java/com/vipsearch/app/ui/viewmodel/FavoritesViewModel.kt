package com.vipsearch.app.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vipsearch.app.data.model.FavoriteItem
import com.vipsearch.app.data.repository.FavoritesRepository
import kotlinx.coroutines.launch

data class FavoritesUiState(
  val loading: Boolean = false,
  val favorites: List<FavoriteItem> = emptyList()
)

class FavoritesViewModel(
  private val favoritesRepository: FavoritesRepository
) : ViewModel() {
  var state by mutableStateOf(FavoritesUiState())
    private set

  fun load() {
    viewModelScope.launch {
      state = state.copy(loading = true)
      state = state.copy(
        loading = false,
        favorites = favoritesRepository.getFavorites()
      )
    }
  }

  fun remove(showName: String) {
    viewModelScope.launch {
      favoritesRepository.removeFavorite(showName)
      load()
    }
  }
}

class FavoritesViewModelFactory(
  private val favoritesRepository: FavoritesRepository
) : ViewModelProvider.Factory {
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    if (modelClass.isAssignableFrom(FavoritesViewModel::class.java)) {
      @Suppress("UNCHECKED_CAST")
      return FavoritesViewModel(favoritesRepository) as T
    }
    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
  }
}
