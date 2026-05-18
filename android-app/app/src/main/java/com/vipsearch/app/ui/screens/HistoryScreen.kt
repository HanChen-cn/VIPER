package com.vipsearch.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vipsearch.app.ui.viewmodel.HistoryUiState

@Composable
fun HistoryScreen(
  state: HistoryUiState,
  onReload: () -> Unit,
  onClearSearchHistory: () -> Unit
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
    Text("历史", style = MaterialTheme.typography.headlineSmall)
    Button(
      onClick = onClearSearchHistory,
      modifier = Modifier.fillMaxWidth()
    ) {
      Text("清空搜索历史")
    }

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      item {
        Text("搜索历史", style = MaterialTheme.typography.titleMedium)
      }
      if (state.searchHistory.isEmpty()) {
        item { Text("暂无搜索历史") }
      } else {
        items(state.searchHistory) { item ->
          Text("🔍 ${item.keyword}")
        }
      }

      item {
        Text("播放历史", style = MaterialTheme.typography.titleMedium)
      }
      if (state.playHistory.isEmpty()) {
        item { Text("暂无播放历史") }
      } else {
        items(state.playHistory) { item ->
          Text("▶ ${item.showName} - ${item.episodeName}")
        }
      }
    }
  }
}
