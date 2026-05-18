package com.vipsearch.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vipsearch.app.data.model.Show
import com.vipsearch.app.ui.viewmodel.SearchUiState

@Composable
fun SearchScreen(
  state: SearchUiState,
  onKeywordChange: (String) -> Unit,
  onSearch: () -> Unit,
  onAddFavorite: (show: Show) -> Unit,
  onEpisodeClick: (showName: String, episodeName: String, playUrl: String, altUrls: List<String>) -> Unit
) {
  val expandedEpisodes = remember { mutableStateMapOf<String, Boolean>() }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Text(
      text = "VIP 视频搜索",
      style = MaterialTheme.typography.headlineSmall
    )

    OutlinedTextField(
      value = state.keyword,
      onValueChange = onKeywordChange,
      modifier = Modifier.fillMaxWidth(),
      label = { Text("输入剧名 / 动漫名") },
      singleLine = true
    )

    Button(
      onClick = onSearch,
      modifier = Modifier.fillMaxWidth(),
      enabled = state.keyword.isNotBlank() && !state.loading
    ) {
      Text(if (state.loading) "搜索中..." else "搜索")
    }

    if (state.error.isNotBlank()) {
      Text(
        text = state.error,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error
      )
    }
    if (state.notice.isNotBlank()) {
      Text(
        text = state.notice,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary
      )
    }

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      items(state.results, key = { it.name }) { show ->
        Card(modifier = Modifier.fillMaxWidth()) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Text(show.name, style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
              TextButton(onClick = { onAddFavorite(show) }) {
                Text("收藏")
              }
            }
            Text(
              text = listOf(show.type, show.year, show.remarks).filter { it.isNotBlank() }.joinToString(" · "),
              style = MaterialTheme.typography.bodySmall
            )
            val expanded = expandedEpisodes[show.name] == true
            val visibleEpisodes = if (expanded) show.episodes else show.episodes.take(12)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              visibleEpisodes.forEach { ep ->
                OutlinedButton(
                  onClick = {
                    onEpisodeClick(show.name, ep.name, ep.playUrl, ep.altUrls)
                  },
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Text(ep.name)
                }
              }
              if (show.episodes.size > 12) {
                TextButton(
                  onClick = { expandedEpisodes[show.name] = !expanded },
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Text(
                    if (expanded) "收起剧集"
                    else "展开全部剧集（${show.episodes.size}）"
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}
