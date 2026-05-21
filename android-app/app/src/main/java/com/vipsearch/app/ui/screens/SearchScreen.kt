package com.vipsearch.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vipsearch.app.data.model.Episode
import com.vipsearch.app.data.model.Show
import com.vipsearch.app.ui.state.PlaybackSessionStore
import com.vipsearch.app.ui.theme.AppColors
import com.vipsearch.app.ui.theme.CardShape
import com.vipsearch.app.ui.theme.PillShape
import com.vipsearch.app.ui.theme.ThemeMode
import com.vipsearch.app.ui.theme.ThemePreference
import com.vipsearch.app.ui.viewmodel.SearchUiState

private const val EPISODE_COLUMNS = 2
private const val MAX_EPISODE_ROWS = 4
private const val MAX_VISIBLE_EPISODES = EPISODE_COLUMNS * MAX_EPISODE_ROWS

@Composable
fun SearchScreen(
  state: SearchUiState,
  themeMode: MutableState<ThemeMode>,
  onKeywordChange: (String) -> Unit,
  onSearch: () -> Unit,
  onAddFavorite: (show: Show) -> Unit,
  onEpisodeClick: (showName: String, episodeName: String, playUrl: String, altUrls: List<String>) -> Unit
) {
  val expandedEpisodes = remember { mutableStateMapOf<String, Boolean>() }
  val playbackSession = PlaybackSessionStore.session
  val context = LocalContext.current

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(AppColors.Canvas)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "VIP 视频搜索",
        color = AppColors.TextPrimary,
        fontSize = 28.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.28).sp
      )
      IconButton(onClick = {
        val next = when (themeMode.value) {
          ThemeMode.SYSTEM -> ThemeMode.LIGHT
          ThemeMode.LIGHT -> ThemeMode.DARK
          ThemeMode.DARK -> ThemeMode.SYSTEM
        }
        themeMode.value = next
        ThemePreference.set(context, next)
      }) {
        Icon(
          imageVector = when (themeMode.value) {
            ThemeMode.SYSTEM -> Icons.Outlined.Settings
            ThemeMode.LIGHT -> Icons.Outlined.LightMode
            ThemeMode.DARK -> Icons.Outlined.DarkMode
          },
          contentDescription = when (themeMode.value) {
            ThemeMode.SYSTEM -> "跟随系统"
            ThemeMode.LIGHT -> "浅色模式"
            ThemeMode.DARK -> "深色模式"
          },
          tint = AppColors.TextMuted,
          modifier = Modifier.size(22.dp)
        )
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      BasicTextField(
        value = state.keyword,
        onValueChange = onKeywordChange,
        singleLine = true,
        textStyle = TextStyle(
          color = AppColors.TextPrimary,
          fontSize = 17.sp,
          fontWeight = FontWeight.Normal
        ),
        cursorBrush = SolidColor(AppColors.ActionBlue),
        modifier = Modifier
          .weight(1f)
          .clip(PillShape)
          .background(AppColors.CardSurface)
          .border(1.dp, AppColors.InputBorder, PillShape)
          .padding(horizontal = 20.dp, vertical = 12.dp),
        decorationBox = { innerTextField ->
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
          ) {
            Icon(
              imageVector = Icons.Default.Search,
              contentDescription = null,
              tint = AppColors.TextMuted,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
              if (state.keyword.isEmpty()) {
                Text(
                  text = "输入剧名 / 动漫名",
                  color = AppColors.TextMuted.copy(alpha = 0.7f),
                  fontSize = 17.sp
                )
              }
              innerTextField()
            }
            if (state.keyword.isNotEmpty()) {
              IconButton(
                onClick = { onKeywordChange("") },
                modifier = Modifier.size(24.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = "清除",
                  tint = AppColors.TextMuted,
                  modifier = Modifier.size(18.dp)
                )
              }
            }
          }
        }
      )

      Button(
        onClick = onSearch,
        enabled = state.keyword.isNotBlank() && !state.loading,
        shape = PillShape,
        colors = ButtonDefaults.buttonColors(
          containerColor = AppColors.ActionBlue,
          contentColor = AppColors.TextPrimary,
          disabledContainerColor = AppColors.ActionBlue.copy(alpha = 0.4f),
          disabledContentColor = AppColors.TextPrimary.copy(alpha = 0.6f)
        ),
        modifier = Modifier.height(44.dp)
      ) {
        if (state.loading) {
          CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            color = AppColors.TextPrimary,
            strokeWidth = 2.dp
          )
        } else {
          Text(
            text = "搜索",
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal
          )
        }
      }
    }

    if (state.error.isNotBlank()) {
      if (state.results.isEmpty() && state.error == "未找到资源") {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Icon(
            imageVector = Icons.Outlined.SearchOff,
            contentDescription = null,
            tint = AppColors.TextMuted.copy(alpha = 0.5f),
            modifier = Modifier.size(48.dp)
          )
          Text(
            text = state.error,
            color = AppColors.TextMuted.copy(alpha = 0.6f),
            fontSize = 14.sp
          )
        }
      } else {
        Text(
          text = state.error,
          color = AppColors.ErrorRed,
          fontSize = 14.sp
        )
      }
    }
    if (state.notice.isNotBlank()) {
      Text(
        text = state.notice,
        color = AppColors.SkyBlue,
        fontSize = 14.sp
      )
    }

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      items(state.results, key = { it.name }) { show ->
        ShowResultCard(
          show = show,
          expanded = expandedEpisodes[show.name] == true,
          currentShowName = playbackSession.showName,
          currentEpisodeName = playbackSession.episodeName,
          onToggleExpand = { expandedEpisodes[show.name] = expandedEpisodes[show.name] != true },
          onAddFavorite = { onAddFavorite(show) },
          onEpisodeClick = onEpisodeClick
        )
      }
    }
  }
}

@Composable
private fun ShowResultCard(
  show: Show,
  expanded: Boolean,
  currentShowName: String,
  currentEpisodeName: String,
  onToggleExpand: () -> Unit,
  onAddFavorite: () -> Unit,
  onEpisodeClick: (showName: String, episodeName: String, playUrl: String, altUrls: List<String>) -> Unit
) {
  val visibleEpisodes = if (expanded) show.episodes else show.episodes.take(MAX_VISIBLE_EPISODES)
  val hasMore = show.episodes.size > MAX_VISIBLE_EPISODES

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(CardShape)
      .background(AppColors.CardSurface)
      .border(1.dp, AppColors.CardBorder, CardShape)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.Top
    ) {
      Text(
        text = show.name,
        modifier = Modifier.weight(1f),
        color = AppColors.TextPrimary,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )
      TextButton(onClick = onAddFavorite) {
        Text(
          text = "收藏",
          color = AppColors.SkyBlue,
          fontSize = 14.sp
        )
      }
    }

    val meta = listOf(show.type, show.year, show.remarks).filter { it.isNotBlank() }.joinToString(" · ")
    if (meta.isNotBlank()) {
      Text(
        text = meta,
        color = AppColors.TextMuted,
        fontSize = 14.sp,
        lineHeight = 20.sp
      )
    }

    EpisodeGrid(
      episodes = visibleEpisodes,
      showName = show.name,
      currentShowName = currentShowName,
      currentEpisodeName = currentEpisodeName,
      onEpisodeClick = onEpisodeClick
    )

    if (hasMore) {
      TextButton(
        onClick = onToggleExpand,
        modifier = Modifier.fillMaxWidth()
      ) {
        Text(
          text = if (expanded) "收起剧集" else "展开全部（${show.episodes.size} 集）",
          color = AppColors.SkyBlue,
          fontSize = 14.sp,
          modifier = Modifier.fillMaxWidth(),
          textAlign = TextAlign.Center
        )
      }
    }
  }
}

@Composable
private fun EpisodeGrid(
  episodes: List<Episode>,
  showName: String,
  currentShowName: String,
  currentEpisodeName: String,
  onEpisodeClick: (showName: String, episodeName: String, playUrl: String, altUrls: List<String>) -> Unit
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    episodes.chunked(EPISODE_COLUMNS).forEach { rowEpisodes ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        rowEpisodes.forEach { ep ->
          val isCurrent = showName == currentShowName &&
            ep.name.trim() == currentEpisodeName.trim()
          EpisodePillButton(
            episode = ep,
            isCurrent = isCurrent,
            modifier = Modifier.weight(1f),
            onClick = {
              onEpisodeClick(showName, ep.name, ep.playUrl, ep.altUrls)
            }
          )
        }
        if (rowEpisodes.size < EPISODE_COLUMNS) {
          Spacer(modifier = Modifier.weight(1f))
        }
      }
    }
  }
}

@Composable
private fun EpisodePillButton(
  episode: Episode,
  isCurrent: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  Box(
    modifier = modifier
      .height(36.dp)
      .clip(PillShape)
      .then(
        if (isCurrent) Modifier.background(AppColors.ActionBlue)
        else Modifier.border(1.dp, AppColors.PillBorder, PillShape)
      )
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = episode.name,
      color = if (isCurrent) AppColors.TextPrimary else AppColors.TextMuted,
      fontSize = 13.sp,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(horizontal = 12.dp)
    )
  }
}
