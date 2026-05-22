package com.vipsearch.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vipsearch.app.dlna.PlaybackInfo
import com.vipsearch.app.ui.theme.AppColors

@Composable
fun CastRemoteView(
  deviceName: String,
  showName: String,
  episodeName: String,
  playbackInfo: PlaybackInfo,
  hasNext: Boolean,
  hasPrev: Boolean,
  onPlay: () -> Unit,
  onPause: () -> Unit,
  onSeek: (Long) -> Unit,
  onVolumeChange: (Int) -> Unit,
  onPrevEpisode: () -> Unit,
  onNextEpisode: () -> Unit,
  onDisconnect: () -> Unit
) {
  val isSeeking = remember { mutableStateOf(false) }
  val seekPosition = remember { mutableFloatStateOf(0f) }
  val volume = remember { mutableFloatStateOf(50f) }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .aspectRatio(16f / 9f)
      .background(Color.Black)
      .padding(16.dp),
    verticalArrangement = Arrangement.SpaceBetween
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          Icons.Default.CastConnected,
          contentDescription = null,
          tint = AppColors.ActionBlue,
          modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
          Text("正在投屏", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
          Text(
            deviceName,
            color = Color.White,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 180.dp)
          )
        }
      }
      TextButton(onClick = onDisconnect) {
        Text("断开", color = AppColors.ErrorRed, fontSize = 13.sp)
      }
    }

    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(
        showName,
        color = Color.White,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        episodeName,
        color = Color.White.copy(alpha = 0.6f),
        fontSize = 13.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
      val progress = if (isSeeking.value) {
        seekPosition.floatValue
      } else {
        if (playbackInfo.duration > 0) {
          playbackInfo.position.toFloat() / playbackInfo.duration.toFloat()
        } else {
          0f
        }
      }

      Slider(
        value = progress,
        onValueChange = {
          isSeeking.value = true
          seekPosition.floatValue = it
        },
        onValueChangeFinished = {
          isSeeking.value = false
          val targetMs = (seekPosition.floatValue * playbackInfo.duration).toLong()
          onSeek(targetMs)
        },
        modifier = Modifier.fillMaxWidth(),
        colors = SliderDefaults.colors(
          thumbColor = AppColors.ActionBlue,
          activeTrackColor = AppColors.ActionBlue,
          inactiveTrackColor = Color.White.copy(alpha = 0.2f)
        )
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          formatDuration(playbackInfo.position),
          color = Color.White.copy(alpha = 0.6f),
          fontSize = 11.sp
        )
        Text(
          formatDuration(playbackInfo.duration),
          color = Color.White.copy(alpha = 0.6f),
          fontSize = 11.sp
        )
      }

      Spacer(modifier = Modifier.height(4.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(onClick = onPrevEpisode, enabled = hasPrev) {
          Icon(
            Icons.Default.SkipPrevious,
            contentDescription = "上一集",
            tint = if (hasPrev) Color.White else Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(28.dp)
          )
        }
        IconButton(onClick = { onSeek((playbackInfo.position - 15000).coerceAtLeast(0)) }) {
          Icon(
            Icons.Default.Replay10,
            contentDescription = "快退15秒",
            tint = Color.White,
            modifier = Modifier.size(28.dp)
          )
        }
        IconButton(
          onClick = { if (playbackInfo.isPlaying) onPause() else onPlay() },
          modifier = Modifier.size(52.dp)
        ) {
          Icon(
            if (playbackInfo.isPlaying) Icons.Default.PauseCircleFilled
            else Icons.Default.PlayCircleFilled,
            contentDescription = if (playbackInfo.isPlaying) "暂停" else "播放",
            tint = Color.White,
            modifier = Modifier.size(48.dp)
          )
        }
        IconButton(onClick = { onSeek(playbackInfo.position + 15000) }) {
          Icon(
            Icons.Default.Forward10,
            contentDescription = "快进15秒",
            tint = Color.White,
            modifier = Modifier.size(28.dp)
          )
        }
        IconButton(onClick = onNextEpisode, enabled = hasNext) {
          Icon(
            Icons.Default.SkipNext,
            contentDescription = "下一集",
            tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(28.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(4.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
      ) {
        Icon(
          Icons.Default.VolumeDown,
          contentDescription = null,
          tint = Color.White.copy(alpha = 0.5f),
          modifier = Modifier.size(18.dp)
        )
        Slider(
          value = volume.floatValue,
          onValueChange = { volume.floatValue = it },
          onValueChangeFinished = { onVolumeChange(volume.floatValue.toInt()) },
          valueRange = 0f..100f,
          modifier = Modifier.width(160.dp),
          colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = Color.White.copy(alpha = 0.6f),
            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
          )
        )
        Icon(
          Icons.Default.VolumeUp,
          contentDescription = null,
          tint = Color.White.copy(alpha = 0.5f),
          modifier = Modifier.size(18.dp)
        )
      }
    }
  }
}

private fun formatDuration(ms: Long): String {
  val totalSeconds = ms / 1000
  val h = totalSeconds / 3600
  val m = (totalSeconds % 3600) / 60
  val s = totalSeconds % 60
  return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
