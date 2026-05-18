package com.vipsearch.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vipsearch.app.ui.viewmodel.FavoritesUiState

@Composable
fun FavoritesScreen(
  state: FavoritesUiState,
  onReload: () -> Unit,
  onRemove: (String) -> Unit
) {
  LaunchedEffect(Unit) {
    onReload()
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Text("收藏", style = MaterialTheme.typography.headlineSmall)
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      if (state.favorites.isEmpty()) {
        item { Text("暂无收藏") }
      } else {
        items(state.favorites) { item ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(item.showName)
            TextButton(onClick = { onRemove(item.showName) }) {
              Text("取消")
            }
          }
        }
      }
    }
  }
}
