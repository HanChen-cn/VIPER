package com.vipsearch.app.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.vipsearch.app.player.PlayerCoordinator
import com.vipsearch.app.player.PlaybackTarget
import com.vipsearch.app.player.SourceSwitchController
import com.vipsearch.app.ui.state.PlaybackSession

@Composable
fun PlayerScreen(
  session: PlaybackSession,
  extraSources: List<String>,
  loadingExtraSources: Boolean,
  error: String
) {
  val coordinator = remember { PlayerCoordinator() }
  val switchController = remember { SourceSwitchController() }
  val currentUrl = remember { mutableStateOf("") }
  val routeText = remember { mutableStateOf("未初始化") }
  val target = remember { mutableStateOf<PlaybackTarget?>(null) }
  val statusHint = remember { mutableStateOf("") }
  val playbackPositions = remember { mutableStateMapOf<String, Long>() }

  fun moveTo(url: String) {
    currentUrl.value = url
    target.value = coordinator.resolve(url)
    routeText.value = when (target.value) {
      is PlaybackTarget.Exo -> "ExoPlayer"
      is PlaybackTarget.Web -> "WebView"
      null -> "未初始化"
    }
  }

  fun autoFallback(reason: String) {
    val next = switchController.nextOnFailure()
    if (!next.isNullOrBlank()) {
      moveTo(next)
      statusHint.value = "当前源失败，已自动切换：$reason"
    } else {
      statusHint.value = "播放失败且无更多备源：$reason"
    }
  }

  LaunchedEffect(session.primaryUrl, session.altUrls, extraSources) {
    if (session.primaryUrl.isNotBlank()) {
      val combined = (session.altUrls + extraSources).distinct().filter { it.isNotBlank() }
      switchController.setSources(primary = session.primaryUrl, alternatives = combined)
      moveTo(switchController.current().orEmpty())
      statusHint.value = ""
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Text("播放器", style = MaterialTheme.typography.headlineSmall)
    Text("剧名：${session.showName.ifBlank { "-" }}")
    Text("剧集：${session.episodeName.ifBlank { "-" }}")
    Text("当前播放路由：${routeText.value}")
    Text("当前 URL：${currentUrl.value.ifBlank { "-" }}")

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(220.dp)
    ) {
      when (val playbackTarget = target.value) {
        is PlaybackTarget.Exo -> ExoPlayerPane(
          url = playbackTarget.mediaUrl,
          startPositionMs = playbackPositions[playbackTarget.mediaUrl] ?: 0L,
          onSavePosition = { pos -> playbackPositions[playbackTarget.mediaUrl] = pos },
          onPlaybackError = { errorMsg -> autoFallback(errorMsg) }
        )
        is PlaybackTarget.Web -> WebViewPane(
          url = playbackTarget.pageUrl,
          onWebError = { errorMsg -> autoFallback(errorMsg) }
        )
        null -> Text("请先从搜索页选择剧集。")
      }
    }

    if (loadingExtraSources) {
      Text("后台正在拉取当前集其他源...")
    }
    if (error.isNotBlank()) {
      Text(text = error, color = MaterialTheme.colorScheme.error)
    }
    if (statusHint.value.isNotBlank()) {
      Text(text = statusHint.value, color = MaterialTheme.colorScheme.error)
    }

    Button(
      onClick = {
        val next = switchController.nextOnFailure()
        if (!next.isNullOrBlank()) {
          moveTo(next)
          statusHint.value = ""
        } else {
          statusHint.value = "没有更多备源可切换"
        }
      },
      modifier = Modifier.fillMaxWidth()
    ) {
      Text("切到下一个备源")
    }

    Text("可用源列表：")
    LazyColumn(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f, fill = false),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      itemsIndexed(switchController.all()) { index, sourceUrl ->
        val selected = sourceUrl == currentUrl.value
        Text(
          text = "${index + 1}. $sourceUrl",
          color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
          modifier = Modifier
            .fillMaxWidth()
            .clickable {
              val switched = switchController.switchTo(index)
              if (!switched.isNullOrBlank()) {
                moveTo(switched)
                statusHint.value = ""
              }
            }
            .padding(vertical = 4.dp)
        )
      }
    }
  }
}

@Composable
private fun ExoPlayerPane(
  url: String,
  startPositionMs: Long,
  onSavePosition: (Long) -> Unit,
  onPlaybackError: (String) -> Unit
) {
  val context = LocalContext.current
  val exoPlayer = remember(url) {
    ExoPlayer.Builder(context).build().apply {
      setMediaItem(MediaItem.fromUri(url))
      seekTo(startPositionMs)
      prepare()
      playWhenReady = true
    }
  }

  DisposableEffect(exoPlayer) {
    val listener = object : Player.Listener {
      override fun onPlayerError(error: PlaybackException) {
        onPlaybackError(error.message ?: "播放器错误")
      }
    }
    exoPlayer.addListener(listener)
    onDispose {
      exoPlayer.removeListener(listener)
    }
  }

  DisposableEffect(exoPlayer) {
    onDispose {
      onSavePosition(exoPlayer.currentPosition)
      exoPlayer.release()
    }
  }

  AndroidView(
    modifier = Modifier.fillMaxSize(),
    factory = { ctx ->
      PlayerView(ctx).apply {
        useController = true
        player = exoPlayer
      }
    },
    update = { view ->
      view.player = exoPlayer
    }
  )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebViewPane(url: String, onWebError: (String) -> Unit) {
  AndroidView(
    modifier = Modifier.fillMaxSize(),
    factory = { ctx ->
      WebView(ctx).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        webChromeClient = WebChromeClient()
        webViewClient = object : WebViewClient() {
          @Deprecated("Deprecated in Java")
          override fun onReceivedError(
            view: WebView?,
            errorCode: Int,
            description: String?,
            failingUrl: String?
          ) {
            onWebError(description ?: "WebView 加载失败")
          }
        }
        loadUrl(url)
      }
    },
    update = { webView ->
      if (webView.url != url) {
        webView.loadUrl(url)
      }
    }
  )
}
