package com.vipsearch.app.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.view.LayoutInflater
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
import com.vipsearch.app.R
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
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
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

  val context = LocalContext.current
  val activity = context as? Activity

  // Hoist ExoPlayer — created once per URL, survives fullscreen toggle
  val exoPlayer = remember { mutableStateOf<ExoPlayer?>(null) }
  val exoPlayerUrl = remember { mutableStateOf("") }

  // Hoist WebView — created once, survives fullscreen toggle
  val hoistedWebView = remember { mutableStateOf<WebView?>(null) }
  val hoistedWebViewUrl = remember { mutableStateOf("") }

  fun enterFullscreen() {
    isVideoFullscreen.value = true
  }

  fun exitFullscreen() {
    isVideoFullscreen.value = false
  }

  LaunchedEffect(isVideoFullscreen.value) {
    kotlinx.coroutines.delay(150)
    activity?.let { act ->
      val window = act.window
      if (isVideoFullscreen.value) {
        act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { ctrl ->
          ctrl.hide(WindowInsetsCompat.Type.systemBars())
          ctrl.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
      } else {
        act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView)
          .show(WindowInsetsCompat.Type.systemBars())
      }
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

  // Manage ExoPlayer lifecycle — create/release when URL changes
  DisposableEffect(target.value) {
    val playbackTarget = target.value
    if (playbackTarget is PlaybackTarget.Exo) {
      val url = playbackTarget.mediaUrl
      if (exoPlayerUrl.value != url) {
        exoPlayer.value?.release()
        val player = ExoPlayer.Builder(context).build().apply {
          setMediaItem(MediaItem.fromUri(url))
          seekTo(playbackPositions[url] ?: 0L)
          prepare()
          playWhenReady = true
        }
        exoPlayer.value = player
        exoPlayerUrl.value = url
      }
    } else {
      exoPlayer.value?.release()
      exoPlayer.value = null
      exoPlayerUrl.value = ""
    }
    onDispose { }
  }

  // Save position and release ExoPlayer on final dispose
  DisposableEffect(Unit) {
    // Ensure correct window state on entry
    activity?.let {
      val window = it.window
      WindowCompat.setDecorFitsSystemWindows(window, true)
      WindowInsetsControllerCompat(window, window.decorView)
        .show(WindowInsetsCompat.Type.systemBars())
    }
    onDispose {
      exoPlayer.value?.let {
        playbackPositions[exoPlayerUrl.value] = it.currentPosition
        it.release()
      }
      exoPlayer.value = null
      hoistedWebView.value?.destroy()
      hoistedWebView.value = null
      if (isVideoFullscreen.value) {
        isVideoFullscreen.value = false
        activity?.let { act ->
          act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
          val window = act.window
          WindowCompat.setDecorFitsSystemWindows(window, true)
          WindowInsetsControllerCompat(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
        }
      }
    }
  }

  if (isVideoFullscreen.value) {
    BackHandler { exitFullscreen() }
  }

  Column(modifier = Modifier.fillMaxSize()) {
    // Top bar — hidden in fullscreen
    if (!isVideoFullscreen.value) {
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
    }

    // Video container — aspectRatio in portrait, fillMaxSize in fullscreen
    Box(
      modifier = if (isVideoFullscreen.value) {
        Modifier
          .fillMaxWidth()
          .weight(1f)
          .background(Color.Black)
      } else {
        Modifier
          .fillMaxWidth()
          .aspectRatio(16f / 9f)
          .background(Color.Black)
      }
    ) {
      when (val playbackTarget = target.value) {
        is PlaybackTarget.Exo -> {
          val player = exoPlayer.value
          if (player != null) {
            HoistedExoPlayerView(
              exoPlayer = player,
              isFullscreen = isVideoFullscreen.value,
              onEnterFullscreen = ::enterFullscreen,
              onExitFullscreen = ::exitFullscreen,
              onPlaybackReady = { isVideoLoading.value = false },
              onPlaybackError = { errorMsg -> autoFallback(errorMsg) }
            )
          }
        }
        is PlaybackTarget.Web -> {
          HoistedWebView(
            url = playbackTarget.pageUrl,
            hoistedWebView = hoistedWebView,
            hoistedWebViewUrl = hoistedWebViewUrl,
            onWebError = { errorMsg -> autoFallback(errorMsg) },
            onPageLoaded = { isVideoLoading.value = false }
          )
        }
        null -> {
          if (!isVideoFullscreen.value) {
            Box(
              modifier = Modifier.fillMaxSize(),
              contentAlignment = Alignment.Center
            ) {
              isVideoLoading.value = false
              Text("请先从搜索页选择剧集", color = Color.White)
            }
          }
        }
      }

      if (isVideoLoading.value && target.value != null) {
        Box(
          modifier = Modifier.fillMaxSize().zIndex(1f),
          contentAlignment = Alignment.Center
        ) {
          CircularProgressIndicator(
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(36.dp),
            strokeWidth = 3.dp
          )
        }
      }

    }

    // Bottom controls — hidden in fullscreen
    if (!isVideoFullscreen.value) {
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

        OutlinedButton(
          onClick = {
            val url = currentUrl.value
            if (url.isNotBlank()) {
              val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              clipboard.setPrimaryClip(ClipData.newPlainText("播放链接", url))
              statusHint.value = "链接已复制"
            }
          }
        ) {
          Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("复制")
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
}

@Composable
private fun HoistedExoPlayerView(
  exoPlayer: ExoPlayer,
  isFullscreen: Boolean,
  onEnterFullscreen: () -> Unit,
  onExitFullscreen: () -> Unit,
  onPlaybackReady: () -> Unit,
  onPlaybackError: (String) -> Unit
) {
  val currentOnReady = rememberUpdatedState(onPlaybackReady)
  val currentOnError = rememberUpdatedState(onPlaybackError)
  val currentOnEnterFs = rememberUpdatedState(onEnterFullscreen)
  val currentOnExitFs = rememberUpdatedState(onExitFullscreen)
  val playerViewRef = remember { mutableStateOf<PlayerView?>(null) }

  DisposableEffect(exoPlayer) {
    val listener = object : Player.Listener {
      override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY) currentOnReady.value.invoke()
      }
      override fun onPlayerError(error: PlaybackException) {
        currentOnError.value.invoke(error.message ?: "播放器错误")
      }
    }
    exoPlayer.addListener(listener)
    onDispose { exoPlayer.removeListener(listener) }
  }

  LaunchedEffect(isFullscreen) {
    kotlinx.coroutines.delay(400)
    playerViewRef.value?.post {
      playerViewRef.value?.requestLayout()
    }
  }

  AndroidView(
    modifier = Modifier.fillMaxSize(),
    factory = { ctx ->
      (LayoutInflater.from(ctx).inflate(R.layout.exo_player_view, null) as PlayerView).apply {
        layoutParams = FrameLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT
        )
        controllerShowTimeoutMs = 3000
        player = exoPlayer
        playerViewRef.value = this
      }
    },
    update = { view ->
      view.player = exoPlayer
      view.controllerShowTimeoutMs = if (isFullscreen) 5000 else 3000
      view.setFullscreenButtonClickListener { isEnteringFs ->
        view.post {
          if (isEnteringFs) currentOnEnterFs.value.invoke()
          else currentOnExitFs.value.invoke()
        }
      }
    }
  )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HoistedWebView(
  url: String,
  hoistedWebView: androidx.compose.runtime.MutableState<WebView?>,
  hoistedWebViewUrl: androidx.compose.runtime.MutableState<String>,
  onWebError: (String) -> Unit,
  onPageLoaded: () -> Unit
) {
  val context = LocalContext.current
  val currentOnError = rememberUpdatedState(onWebError)
  val currentOnPageLoaded = rememberUpdatedState(onPageLoaded)

  val fullscreenCss = """
    javascript:(function(){
      var s=document.createElement('style');
      s.textContent='html,body{margin:0!important;padding:0!important;overflow:hidden!important;width:100%!important;height:100%!important;background:#000!important}video,iframe,.player,.dplayer,.video-js,.art-video-player,[class*=player]{width:100%!important;height:100%!important;max-width:100%!important;max-height:100%!important;object-fit:contain!important;background:#000!important}header,footer,nav,.ad,.ads,.advertisement,[class*=header],[class*=footer],[class*=nav]{display:none!important}';
      document.head.appendChild(s);
    })()
  """.trimIndent()

  // Create WebView once
  DisposableEffect(url) {
    val existing = hoistedWebView.value
    if (existing != null && hoistedWebViewUrl.value == url) {
      // WebView already exists for this URL, just reload if needed
      if (existing.url != url) {
        existing.loadUrl(url)
      }
    } else {
      // Destroy old WebView if URL changed
      existing?.destroy()

      val cookieManager = CookieManager.getInstance()
      cookieManager.setAcceptCookie(true)

      val webView = WebView(context).apply {
        layoutParams = FrameLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT
        )
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
            // Not used in this simplified flow
          }
          override fun onHideCustomView() {
            // Not used in this simplified flow
          }
        }
        webViewClient = object : WebViewClient() {
          override fun onPageFinished(view: WebView?, pageUrl: String?) {
            view?.evaluateJavascript(fullscreenCss, null)
            currentOnPageLoaded.value.invoke()
          }
          override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
          ) {
            if (request?.isForMainFrame == true) {
              currentOnError.value.invoke(error?.description?.toString() ?: "WebView 加载失败")
            }
          }
        }
        loadUrl(url)
      }
      hoistedWebView.value = webView
      hoistedWebViewUrl.value = url
    }
    onDispose { }
  }

  val webView = hoistedWebView.value
  if (webView != null) {
    AndroidView(
      modifier = Modifier.fillMaxSize(),
      factory = { webView },
      update = { view ->
        if (view.url != url) {
          view.loadUrl(url)
        }
      }
    )
  }
}
