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
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vipsearch.app.ui.theme.AppColors
import com.vipsearch.app.ui.theme.CardShape
import com.vipsearch.app.ui.theme.PillShape
import com.vipsearch.app.ui.viewmodel.FavoritesUiState

@Composable
fun FavoritesScreen(
  state: FavoritesUiState,
  onReload: () -> Unit,
  onRemove: (String) -> Unit,
  onSearchFavorite: (String) -> Unit
) {
  LaunchedEffect(Unit) { onReload() }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(AppColors.Canvas)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Text(
      text = "收藏",
      color = AppColors.TextPrimary,
      fontSize = 28.sp,
      fontWeight = FontWeight.SemiBold,
      letterSpacing = (-0.28).sp
    )

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      if (state.favorites.isEmpty()) {
        item {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
          ) {
            Icon(
              imageVector = Icons.Outlined.FavoriteBorder,
              contentDescription = null,
              modifier = Modifier.size(48.dp),
              tint = AppColors.TextMuted.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(8.dp))
            Text("暂无收藏", color = AppColors.TextMuted.copy(alpha = 0.6f), fontSize = 14.sp)
          }
        }
      } else {
        items(state.favorites) { item ->
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clip(CardShape)
              .background(AppColors.CardSurface)
              .border(1.dp, AppColors.CardBorder, CardShape)
              .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Text(
              text = item.showName,
              color = AppColors.TextPrimary,
              fontSize = 17.sp,
              fontWeight = FontWeight.SemiBold,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis
            )
            val meta = listOf(item.type, item.year, item.remarks)
              .filter { it.isNotBlank() }
              .joinToString(" · ")
            if (meta.isNotBlank()) {
              Text(
                text = meta,
                color = AppColors.TextMuted,
                fontSize = 14.sp
              )
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Button(
                onClick = { onSearchFavorite(item.showName) },
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(
                  containerColor = AppColors.ActionBlue,
                  contentColor = AppColors.TextPrimary
                ),
                modifier = Modifier.weight(1f).height(40.dp)
              ) {
                Text("立即搜索", fontSize = 14.sp)
              }
              Button(
                onClick = { onRemove(item.showName) },
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(
                  containerColor = Color.Transparent,
                  contentColor = AppColors.TextMuted
                ),
                modifier = Modifier
                  .weight(1f)
                  .height(40.dp)
                  .border(1.dp, AppColors.PillBorder, PillShape)
              ) {
                Text("取消收藏", fontSize = 14.sp)
              }
            }
          }
        }
      }
    }
  }
}
