package com.vipsearch.app.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import android.widget.FrameLayout
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
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
import com.vipsearch.app.ui.viewmodel.PlayerUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(
  uiState: PlayerUiState,
  onBack: () -> Unit,
  onSwitchEpisode: (Int) -> Unit,
  onNextEpisode: () -> Unit,
  onRequestExtraSources: () -> Unit
) {
  val session = uiState.session
  val coordinator = remember { PlayerCoordinator() }
  val switchController = remember { SourceSwitchController() }
  val currentUrl = remember { mutableStateOf("") }
  val target = remember { mutableStateOf<PlaybackTarget?>(null) }
  val statusHint = remember { mutableStateOf("") }
  val playbackPositions = remember { mutableStateMapOf<String, Long>() }
  val showSourceList = remember { mutableStateOf(false) }
  val isVideoLoading = remember { mutableStateOf(true) }
  val isVideoFullscreen = remember { mutableStateOf(false) }
  val fullscreenView = remember { mutableStateOf<View?>(null) }
  val fullscreenCallback = remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

  val context = LocalContext.current
  val activity = context as? Activity

  fun enterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
    fullscreenView.value = view
    fullscreenCallback.value = callback
    isVideoFullscreen.value = true
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

  fun exitFullscreen() {
    fullscreenCallback.value?.onCustomViewHidden()
    fullscreenView.value = null
    fullscreenCallback.value = null
    isVideoFullscreen.value = false
    activity?.let {
      it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
      val window = it.window
      WindowCompat.setDecorFitsSystemWindows(window, true)
      WindowInsetsControllerCompat(window, window.decorView)
        .show(WindowInsetsCompat.Type.systemBars())
    }
  }

  fun moveTo(url: String) {
    currentUrl.value = url
    isVideoLoading.value = true
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

  DisposableEffect(Unit) {
    onDispose {
      if (isVideoFullscreen.value) exitFullscreen()
    }
  }

  if (isVideoFullscreen.value) {
    BackHandler { exitFullscreen() }

    val customView = fullscreenView.value
    if (customView != null) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black)
      ) {
        AndroidView(
          modifier = Modifier.fillMaxSize(),
          factory = {
            FrameLayout(it).apply {
              addView(
                customView,
                FrameLayout.LayoutParams(
                  ViewGroup.LayoutParams.MATCH_PARENT,
                  ViewGroup.LayoutParams.MATCH_PARENT
                )
              )
            }
          },
          update = {}
        )
      }
    } else {
      Box(
        modifier = Modifier
          .fillMaxSize()
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
            onWebError = { errorMsg -> autoFallback(errorMsg) },
            onShowCustomView = ::enterFullscreen,
            onHideCustomView = { exitFullscreen() }
          )
          null -> {}
        }
      }
    }
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
          onPlaybackError = { errorMsg -> autoFallback(errorMsg) },
          onPlaybackReady = { isVideoLoading.value = false }
        )
        is PlaybackTarget.Web -> WebViewPane(
          url = playbackTarget.pageUrl,
          onWebError = { errorMsg -> autoFallback(errorMsg) },
          onShowCustomView = ::enterFullscreen,
          onHideCustomView = { exitFullscreen() },
          onPageLoaded = { isVideoLoading.value = false }
        )
        null -> Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center
        ) {
          isVideoLoading.value = false
          Text("请先从搜索页选择剧集", color = Color.White)
        }
      }

      if (isVideoLoading.value && target.value != null) {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center
        ) {
          CircularProgressIndicator(
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(36.dp),
            strokeWidth = 3.dp
          )
        }
      }

      IconButton(
        onClick = {
          isVideoFullscreen.value = true
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
        },
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(8.dp)
          .size(40.dp)
      ) {
        Icon(
          Icons.Default.Fullscreen,
          contentDescription = "全屏",
          tint = Color.White.copy(alpha = 0.85f),
          modifier = Modifier.size(28.dp)
        )
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
        onClick = {
          val expanding = !showSourceList.value
          showSourceList.value = expanding
          if (expanding) onRequestExtraSources()
        },
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
private fun ExoPlayerPane(
  url: String,
  startPositionMs: Long,
  onSavePosition: (Long) -> Unit,
  onPlaybackError: (String) -> Unit,
  onPlaybackReady: (() -> Unit)? = null
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
      override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY) onPlaybackReady?.invoke()
      }
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
private fun WebViewPane(
  url: String,
  onWebError: (String) -> Unit,
  onShowCustomView: ((View, WebChromeClient.CustomViewCallback) -> Unit)? = null,
  onHideCustomView: (() -> Unit)? = null,
  onPageLoaded: (() -> Unit)? = null
) {
  val cookieManager = CookieManager.getInstance()
  cookieManager.setAcceptCookie(true)

  val fullscreenCss = """
    javascript:(function(){
      var s=document.createElement('style');
      s.textContent='html,body{margin:0!important;padding:0!important;overflow:hidden!important;width:100%!important;height:100%!important;background:#000!important}video,iframe,.player,.dplayer,.video-js,.art-video-player,[class*=player]{width:100%!important;height:100%!important;max-width:100%!important;max-height:100%!important;position:fixed!important;top:0!important;left:0!important;z-index:9999!important}header,footer,nav,.ad,.ads,.advertisement,[class*=header],[class*=footer],[class*=nav]{display:none!important}';
      document.head.appendChild(s);
    })()
  """.trimIndent()

  AndroidView(
    modifier = Modifier.fillMaxSize(),
    factory = { ctx ->
      WebView(ctx).apply {
        cookieManager.setAcceptThirdPartyCookies(this, true)
        setBackgroundColor(android.graphics.Color.BLACK)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.userAgentString = settings.userAgentString
          .replace(Regex("\\s*wv\\b"), "")
          .replace("; ${android.os.Build.MODEL}", "")
        webChromeClient = object : WebChromeClient() {
          override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
            if (view != null && callback != null) {
              onShowCustomView?.invoke(view, callback)
            }
          }
          override fun onHideCustomView() {
            onHideCustomView?.invoke()
          }
        }
        webViewClient = object : WebViewClient() {
          override fun onPageFinished(view: WebView?, pageUrl: String?) {
            view?.evaluateJavascript(fullscreenCss, null)
            onPageLoaded?.invoke()
          }

          override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
          ) {
            if (request?.isForMainFrame == true) {
              onWebError(error?.description?.toString() ?: "WebView 加载失败")
            }
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
