package com.vipsearch.app.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.vipsearch.app.player.PlayerCoordinator
import com.vipsearch.app.player.PlaybackTarget
import com.vipsearch.app.player.SourceSwitchController
import com.vipsearch.app.ui.state.PlaybackSession
import com.vipsearch.app.ui.viewmodel.PlayerUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(
  uiState: PlayerUiState,
  onBack: () -> Unit,
  onSwitchEpisode: (Int) -> Unit,
  onNextEpisode: () -> Unit,
  onToggleFullscreen: () -> Unit
) {
  val session = uiState.session
  val coordinator = remember { PlayerCoordinator() }
  val switchController = remember { SourceSwitchController() }
  val currentUrl = remember { mutableStateOf("") }
  val target = remember { mutableStateOf<PlaybackTarget?>(null) }
  val statusHint = remember { mutableStateOf("") }
  val playbackPositions = remember { mutableStateMapOf<String, Long>() }
  val showSourceList = remember { mutableStateOf(false) }

  fun moveTo(url: String) {
    currentUrl.value = url
    target.value = coordinator.resolve(url)
  }

  fun autoFallback(reason: String) {
    val next = switchController.nextOnFailure()
    if (!next.isNullOrBlank()) {
      moveTo(next)
      statusHint.value = "当前源失败，已自动切换"
    } else {
      statusHint.value = "播放失败且无更多备源：$reason"
    }
  }

  LaunchedEffect(session.primaryUrl, session.altUrls, uiState.extraSources) {
    if (session.primaryUrl.isNotBlank()) {
      val combined = (session.altUrls + uiState.extraSources).distinct().filter { it.isNotBlank() }
      switchController.setSources(primary = session.primaryUrl, alternatives = combined)
      moveTo(switchController.current().orEmpty())
      statusHint.value = ""
    }
  }

  val context = LocalContext.current
  val activity = context as? Activity

  if (uiState.isFullscreen) {
    BackHandler { onToggleFullscreen() }

    LaunchedEffect(Unit) {
      activity?.let {
        it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val window = it.window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { ctrl ->
          ctrl.hide(WindowInsetsCompat.Type.systemBars())
          ctrl.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
      }
    }

    DisposableEffect(Unit) {
      onDispose {
        activity?.let {
          it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
          val window = it.window
          WindowCompat.setDecorFitsSystemWindows(window, true)
          WindowInsetsControllerCompat(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
        }
      }
    }

    FullscreenPlayer(
      target = target.value,
      playbackPositions = playbackPositions,
      session = session,
      hasNextEpisode = uiState.hasNextEpisode,
      onPlaybackError = ::autoFallback,
      onSavePosition = { url, pos -> playbackPositions[url] = pos },
      onNextEpisode = onNextEpisode,
      onExitFullscreen = onToggleFullscreen
    )
    return
  }

  // --- 竖屏模式 ---
  Column(modifier = Modifier.fillMaxSize()) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.surface)
        .padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
      }
      Text(
        text = "${session.showName} · ${session.episodeName}",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.weight(1f),
        maxLines = 1
      )
      IconButton(onClick = onToggleFullscreen) {
        Icon(Icons.Default.Fullscreen, contentDescription = "全屏")
      }
    }

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(16f / 9f)
        .background(Color.Black)
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
        null -> Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center
        ) {
          Text("请先从搜索页选择剧集", color = Color.White)
        }
      }
    }

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      if (uiState.hasNextEpisode) {
        Button(
          onClick = onNextEpisode,
          modifier = Modifier.weight(1f)
        ) {
          Icon(Icons.Default.SkipNext, contentDescription = null)
          Spacer(modifier = Modifier.width(4.dp))
          Text("下一集")
        }
      }

      FilledTonalButton(
        onClick = { showSourceList.value = !showSourceList.value },
        modifier = Modifier.weight(1f)
      ) {
        Text(if (showSourceList.value) "收起换源 ▲" else "换源 ▼")
      }
    }

    if (statusHint.value.isNotBlank()) {
      Text(
        text = statusHint.value,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = 12.dp)
      )
    }
    if (uiState.error.isNotBlank()) {
      Text(
        text = uiState.error,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = 12.dp)
      )
    }

    if (showSourceList.value) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Text(
          "可用源：",
          style = MaterialTheme.typography.labelMedium,
          modifier = Modifier.padding(bottom = 4.dp)
        )
        if (uiState.loadingExtraSources) {
          Text("搜索其他源中...", style = MaterialTheme.typography.bodySmall)
        }
        switchController.all().forEachIndexed { index, sourceUrl ->
          val selected = sourceUrl == currentUrl.value
          if (selected) {
            Button(
              onClick = {},
              modifier = Modifier.fillMaxWidth(),
              colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
              )
            ) {
              Text("当前源 ${index + 1}")
            }
          } else {
            OutlinedButton(
              onClick = {
                val switched = switchController.switchTo(index)
                if (!switched.isNullOrBlank()) {
                  moveTo(switched)
                  statusHint.value = ""
                }
              },
              modifier = Modifier.fillMaxWidth()
            ) {
              Text("源 ${index + 1}")
            }
          }
        }
      }
    }

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .verticalScroll(rememberScrollState())
        .padding(12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("剧集列表", style = MaterialTheme.typography.titleSmall)
        if (uiState.loadingEpisodes) {
          Text("加载中...", style = MaterialTheme.typography.bodySmall)
        }
      }
      Spacer(modifier = Modifier.height(8.dp))

      if (uiState.episodes.isNotEmpty()) {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          uiState.episodes.forEachIndexed { index, ep ->
            val isCurrent = index == uiState.currentEpisodeIndex
            if (isCurrent) {
              Button(
                onClick = {},
                colors = ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.primary
                )
              ) {
                Text(ep.name)
              }
            } else {
              OutlinedButton(onClick = { onSwitchEpisode(index) }) {
                Text(ep.name)
              }
            }
          }
        }
      } else if (!uiState.loadingEpisodes) {
        Text("暂无剧集信息", style = MaterialTheme.typography.bodySmall)
      }
    }
  }
}

@Composable
private fun FullscreenPlayer(
  target: PlaybackTarget?,
  playbackPositions: Map<String, Long>,
  session: PlaybackSession,
  hasNextEpisode: Boolean,
  onPlaybackError: (String) -> Unit,
  onSavePosition: (String, Long) -> Unit,
  onNextEpisode: () -> Unit,
  onExitFullscreen: () -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black)
  ) {
    when (val playbackTarget = target) {
      is PlaybackTarget.Exo -> ExoPlayerPane(
        url = playbackTarget.mediaUrl,
        startPositionMs = playbackPositions[playbackTarget.mediaUrl] ?: 0L,
        onSavePosition = { pos -> onSavePosition(playbackTarget.mediaUrl, pos) },
        onPlaybackError = onPlaybackError
      )
      is PlaybackTarget.Web -> WebViewPane(
        url = playbackTarget.pageUrl,
        onWebError = onPlaybackError
      )
      null -> {}
    }

    Column(
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(16.dp),
      horizontalAlignment = Alignment.End,
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      if (hasNextEpisode) {
        FilledTonalButton(onClick = onNextEpisode) {
          Icon(Icons.Default.SkipNext, contentDescription = null)
          Spacer(modifier = Modifier.width(4.dp))
          Text("下一集")
        }
      }
      FilledTonalButton(onClick = onExitFullscreen) {
        Icon(Icons.Default.FullscreenExit, contentDescription = null)
        Spacer(modifier = Modifier.width(4.dp))
        Text("退出全屏")
      }
    }

    Text(
      text = "${session.showName} · ${session.episodeName}",
      color = Color.White.copy(alpha = 0.8f),
      style = MaterialTheme.typography.bodySmall,
      modifier = Modifier
        .align(Alignment.TopStart)
        .padding(16.dp)
    )
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
  val cookieManager = CookieManager.getInstance()
  cookieManager.setAcceptCookie(true)

  AndroidView(
    modifier = Modifier.fillMaxSize(),
    factory = { ctx ->
      WebView(ctx).apply {
        cookieManager.setAcceptThirdPartyCookies(this, true)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
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
