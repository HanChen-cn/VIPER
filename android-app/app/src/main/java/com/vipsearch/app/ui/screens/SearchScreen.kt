package com.vipsearch.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vipsearch.app.data.model.Episode
import com.vipsearch.app.data.model.Show
import com.vipsearch.app.ui.state.PlaybackSessionStore
import com.vipsearch.app.ui.viewmodel.SearchUiState

private val BgCanvas = Color(0xFF1D1D1F)
private val CardSurface = Color(0xFF272729)
private val ActionBlue = Color(0xFF0066CC)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextMuted = Color(0xFFCCCCCC)
private val ErrorRed = Color(0xFFFF6B6B)
private val NoticeBlue = Color(0xFF2997FF)

private val PillShape = RoundedCornerShape(9999.dp)
private val CardShape = RoundedCornerShape(18.dp)

private const val EPISODE_COLUMNS = 2
private const val MAX_EPISODE_ROWS = 4
private const val MAX_VISIBLE_EPISODES = EPISODE_COLUMNS * MAX_EPISODE_ROWS

@Composable
fun SearchScreen(
  state: SearchUiState,
  onKeywordChange: (String) -> Unit,
  onSearch: () -> Unit,
  onAddFavorite: (show: Show) -> Unit,
  onEpisodeClick: (showName: String, episodeName: String, playUrl: String, altUrls: List<String>) -> Unit
) {
  val expandedEpisodes = remember { mutableStateMapOf<String, Boolean>() }
  val playbackSession = PlaybackSessionStore.session

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(BgCanvas)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Text(
      text = "VIP 视频搜索",
      color = TextPrimary,
      fontSize = 28.sp,
      fontWeight = FontWeight.SemiBold,
      letterSpacing = (-0.28).sp
    )

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
          color = TextPrimary,
          fontSize = 17.sp,
          fontWeight = FontWeight.Normal
        ),
        cursorBrush = SolidColor(ActionBlue),
        modifier = Modifier
          .weight(1f)
          .clip(PillShape)
          .background(CardSurface)
          .border(1.dp, Color.White.copy(alpha = 0.08f), PillShape)
          .padding(horizontal = 20.dp, vertical = 12.dp),
        decorationBox = { inner ->
          Box {
            if (state.keyword.isEmpty()) {
              Text(
                text = "输入剧名 / 动漫名",
                color = TextMuted.copy(alpha = 0.7f),
                fontSize = 17.sp
              )
            }
            inner()
          }
        }
      )

      Button(
        onClick = onSearch,
        enabled = state.keyword.isNotBlank() && !state.loading,
        shape = PillShape,
        colors = ButtonDefaults.buttonColors(
          containerColor = ActionBlue,
          contentColor = TextPrimary,
          disabledContainerColor = ActionBlue.copy(alpha = 0.4f),
          disabledContentColor = TextPrimary.copy(alpha = 0.6f)
        ),
        modifier = Modifier.height(44.dp)
      ) {
        if (state.loading) {
          CircularProgressIndicator(
            modifier = Modifier.height(18.dp),
            color = TextPrimary,
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
      Text(
        text = state.error,
        color = ErrorRed,
        fontSize = 14.sp
      )
    }
    if (state.notice.isNotBlank()) {
      Text(
        text = state.notice,
        color = NoticeBlue,
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
      .background(CardSurface)
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
        color = TextPrimary,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )
      TextButton(onClick = onAddFavorite) {
        Text(
          text = "收藏",
          color = NoticeBlue,
          fontSize = 14.sp
        )
      }
    }

    val meta = listOf(show.type, show.year, show.remarks).filter { it.isNotBlank() }.joinToString(" · ")
    if (meta.isNotBlank()) {
      Text(
        text = meta,
        color = TextMuted,
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
          color = NoticeBlue,
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
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    episodes.chunked(EPISODE_COLUMNS).forEach { rowEpisodes ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
  if (isCurrent) {
    Button(
      onClick = onClick,
      modifier = modifier.height(40.dp),
      shape = PillShape,
      colors = ButtonDefaults.buttonColors(
        containerColor = ActionBlue,
        contentColor = TextPrimary
      ),
      contentPadding = ButtonDefaults.ContentPadding
    ) {
      Text(
        text = episode.name,
        fontSize = 13.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  } else {
    Button(
      onClick = onClick,
      modifier = modifier
        .height(40.dp)
        .border(1.dp, Color.White.copy(alpha = 0.35f), PillShape),
      shape = PillShape,
      colors = ButtonDefaults.buttonColors(
        containerColor = Color.Transparent,
        contentColor = TextMuted
      ),
      elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
      contentPadding = ButtonDefaults.ContentPadding
    ) {
      Text(
        text = episode.name,
        fontSize = 13.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}
