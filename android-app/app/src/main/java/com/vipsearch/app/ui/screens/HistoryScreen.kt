package com.vipsearch.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vipsearch.app.ui.theme.AppColors
import com.vipsearch.app.ui.theme.CardShape
import com.vipsearch.app.ui.theme.PillShape
import com.vipsearch.app.ui.viewmodel.HistoryUiState

@Composable
fun HistoryScreen(
  state: HistoryUiState,
  onReload: () -> Unit,
  onClearSearchHistory: () -> Unit,
  onSearchKeyword: (String) -> Unit,
  onPlayHistory: (showName: String, episodeName: String, url: String) -> Unit
) {
  LaunchedEffect(Unit) { onReload() }

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
        text = "历史",
        color = AppColors.TextPrimary,
        fontSize = 28.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.28).sp
      )
      TextButton(onClick = onClearSearchHistory) {
        Text("清空搜索历史", color = AppColors.ErrorRed, fontSize = 14.sp)
      }
    }

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      item {
        Text(
          text = "搜索历史",
          color = AppColors.TextMuted,
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
        )
      }

      if (state.searchHistory.isEmpty()) {
        item {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
          ) {
            Icon(
              imageVector = Icons.Outlined.History,
              contentDescription = null,
              modifier = Modifier.size(48.dp),
              tint = AppColors.TextMuted.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(8.dp))
            Text("暂无搜索历史", color = AppColors.TextMuted.copy(alpha = 0.6f), fontSize = 14.sp)
          }
        }
      } else {
        items(state.searchHistory) { item ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(CardShape)
              .background(AppColors.CardSurface)
              .border(1.dp, AppColors.CardBorder, CardShape)
              .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = item.keyword,
              color = AppColors.TextPrimary,
              fontSize = 16.sp,
              modifier = Modifier.weight(1f),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Button(
              onClick = { onSearchKeyword(item.keyword) },
              shape = PillShape,
              colors = ButtonDefaults.buttonColors(
                containerColor = AppColors.ActionBlue,
                contentColor = AppColors.TextPrimary
              ),
              modifier = Modifier.height(36.dp)
            ) {
              Text("搜索", fontSize = 13.sp)
            }
          }
        }
      }

      item {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = "播放历史",
          color = AppColors.TextMuted,
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.padding(bottom = 4.dp)
        )
      }

      if (state.playHistory.isEmpty()) {
        item {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
          ) {
            Icon(
              imageVector = Icons.Outlined.PlayCircleOutline,
              contentDescription = null,
              modifier = Modifier.size(48.dp),
              tint = AppColors.TextMuted.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(8.dp))
            Text("暂无播放历史", color = AppColors.TextMuted.copy(alpha = 0.6f), fontSize = 14.sp)
          }
        }
      } else {
        items(state.playHistory) { item ->
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clip(CardShape)
              .background(AppColors.CardSurface)
              .border(1.dp, AppColors.CardBorder, CardShape)
              .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Text(
              text = item.showName,
              color = AppColors.TextPrimary,
              fontSize = 16.sp,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = item.episodeName,
              color = AppColors.TextMuted,
              fontSize = 14.sp
            )
            Button(
              onClick = { onPlayHistory(item.showName, item.episodeName, item.url) },
              shape = PillShape,
              colors = ButtonDefaults.buttonColors(
                containerColor = AppColors.ActionBlue,
                contentColor = AppColors.TextPrimary
              ),
              modifier = Modifier.fillMaxWidth().height(40.dp)
            ) {
              Text("继续播放", fontSize = 14.sp)
            }
          }
        }
      }
    }
  }
}
