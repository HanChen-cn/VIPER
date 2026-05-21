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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.vipsearch.app.ui.theme.AppColors
import com.vipsearch.app.ui.theme.PillShape
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
  val isBuffering = remember { mutableStateOf(false) }
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
        WindowInsetsControllerCompat(window, window.decorView).let { ctrl ->
          ctrl.hide(WindowInsetsCompat.Type.systemBars())
          ctrl.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
      } else {
        act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        WindowInsetsControllerCompat(window, window.decorView)
          .show(WindowInsetsCompat.Type.systemBars())
      }
    }
  }

  fun moveTo(url: String) {
    if (url == currentUrl.value) return
    currentUrl.value = url
    isVideoLoading.value = true
    isBuffering.value = false
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

  LaunchedEffect(session.primaryUrl) {
    if (session.primaryUrl.isNotBlank()) {
      val combined = (session.altUrls + uiState.extraSources).distinct().filter { it.isNotBlank() }
      switchController.setSources(primary = session.primaryUrl, alternatives = combined)
      moveTo(switchController.current().orEmpty())
      statusHint.value = ""
    }
  }

  LaunchedEffect(session.altUrls, uiState.extraSources) {
    if (currentUrl.value.isNotBlank()) {
      val combined = (session.altUrls + uiState.extraSources).distinct().filter { it.isNotBlank() }
      switchController.updateAlternatives(session.primaryUrl, combined)
    }
  }

  // Manage ExoPlayer lifecycle — reuse player instance when possible
  DisposableEffect(target.value) {
    val playbackTarget = target.value
    if (playbackTarget is PlaybackTarget.Exo) {
      val url = playbackTarget.mediaUrl
      if (exoPlayerUrl.value != url) {
        val existing = exoPlayer.value
        if (existing != null) {
          playbackPositions[exoPlayerUrl.value] = existing.currentPosition
          existing.setMediaItem(MediaItem.fromUri(url))
          existing.seekTo(playbackPositions[url] ?: 0L)
          existing.prepare()
          existing.playWhenReady = true
        } else {
          val player = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            seekTo(playbackPositions[url] ?: 0L)
            prepare()
            playWhenReady = true
          }
          exoPlayer.value = player
        }
        exoPlayerUrl.value = url
      }
    } else {
      exoPlayer.value?.let {
        playbackPositions[exoPlayerUrl.value] = it.currentPosition
        it.release()
      }
      exoPlayer.value = null
      exoPlayerUrl.value = ""
    }
    onDispose { }
  }

  // Save position and release ExoPlayer on final dispose
  DisposableEffect(Unit) {
    activity?.let {
      WindowInsetsControllerCompat(it.window, it.window.decorView)
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
          WindowInsetsControllerCompat(act.window, act.window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
        }
      }
    }
  }

  if (isVideoFullscreen.value) {
    BackHandler { exitFullscreen() }
  }

  Column(modifier = Modifier.fillMaxSize().background(AppColors.Canvas)) {
    // Top bar — hidden in fullscreen
    if (!isVideoFullscreen.value) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(AppColors.CardSurface)
          .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(onClick = onBack) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = AppColors.TextPrimary)
        }
        Text(
          text = "${session.showName} · ${session.episodeName}",
          color = AppColors.TextPrimary,
          fontSize = 16.sp,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.weight(1f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }

    // Video container — aspectRatio in portrait, fillMaxSize in fullscreen
    val videoBg = if (target.value != null) Color.Black else AppColors.Canvas
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
          .background(videoBg)
      }
    ) {
      when (val playbackTarget = target.value) {
        is PlaybackTarget.Exo -> {
          val player = exoPlayer.value
          if (player != null) {
            HoistedExoPlayerView(
              exoPlayer = player,
              onPlaybackReady = {
                isVideoLoading.value = false
                isBuffering.value = false
              },
              onBuffering = { isBuffering.value = true },
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
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                  Icons.Default.Fullscreen,
                  contentDescription = null,
                  tint = Color.White.copy(alpha = 0.4f),
                  modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("请先从搜索页选择剧集", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
              }
            }
          }
        }
      }

      if ((isVideoLoading.value || isBuffering.value) && target.value != null) {
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

      // Compose fullscreen toggle button (bottom-right corner)
      if (target.value != null) {
        IconButton(
          onClick = {
            if (isVideoFullscreen.value) exitFullscreen() else enterFullscreen()
          },
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(8.dp)
            .zIndex(2f)
        ) {
          Icon(
            imageVector = if (isVideoFullscreen.value) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
            contentDescription = if (isVideoFullscreen.value) "退出全屏" else "全屏",
            tint = Color.White,
            modifier = Modifier.size(32.dp)
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
            shape = PillShape,
            colors = ButtonDefaults.buttonColors(
              containerColor = AppColors.ActionBlue,
              contentColor = Color.White
            ),
            modifier = Modifier.weight(1f).height(40.dp)
          ) {
            Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("下一集", fontSize = 14.sp)
          }
        }

        Button(
          onClick = {
            val expanding = !showSourceList.value
            showSourceList.value = expanding
            if (expanding) onRequestExtraSources()
          },
          shape = PillShape,
          colors = ButtonDefaults.buttonColors(
            containerColor = AppColors.CardSurface,
            contentColor = AppColors.TextMuted
          ),
          modifier = Modifier.weight(1f).height(40.dp)
        ) {
          Text(
            if (showSourceList.value) "收起换源 ▲" else "换源 ▼",
            fontSize = 14.sp
          )
        }

        Button(
          onClick = {
            val url = currentUrl.value
            if (url.isNotBlank()) {
              val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              clipboard.setPrimaryClip(ClipData.newPlainText("播放链接", url))
              statusHint.value = "链接已复制"
            }
          },
          shape = PillShape,
          colors = ButtonDefaults.buttonColors(
            containerColor = AppColors.CardSurface,
            contentColor = AppColors.TextMuted
          ),
          modifier = Modifier.height(40.dp)
        ) {
          Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("复制", fontSize = 14.sp)
        }
      }

      if (statusHint.value.isNotBlank()) {
        val hintColor = if (statusHint.value == "链接已复制") AppColors.SuccessGreen else AppColors.ErrorRed
        Text(
          text = statusHint.value,
          color = hintColor,
          fontSize = 13.sp,
          modifier = Modifier.padding(horizontal = 12.dp)
        )
      }
      if (uiState.error.isNotBlank()) {
        Text(
          text = uiState.error,
          color = AppColors.ErrorRed,
          fontSize = 13.sp,
          modifier = Modifier.padding(horizontal = 12.dp)
        )
      }

      if (showSourceList.value) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            "可用源：",
            color = AppColors.TextMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 4.dp)
          )
          if (uiState.loadingExtraSources) {
            Text("搜索其他源中...", color = AppColors.TextMuted, fontSize = 13.sp)
          }
          switchController.all().forEachIndexed { index, sourceUrl ->
            val selected = sourceUrl == currentUrl.value
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(PillShape)
                .then(
                  if (selected) Modifier.background(AppColors.ActionBlue)
                  else Modifier.border(1.dp, AppColors.PillBorder, PillShape)
                )
                .clickable(enabled = !selected) {
                  val switched = switchController.switchTo(index)
                  if (!switched.isNullOrBlank()) {
                    moveTo(switched)
                    statusHint.value = ""
                  }
                },
              contentAlignment = Alignment.Center
            ) {
              Text(
                if (selected) "当前源 ${index + 1}" else "源 ${index + 1}",
                color = if (selected) Color.White else AppColors.TextMuted,
                fontSize = 13.sp
              )
            }
          }
        }
      }

      val episodeExpanded = remember { mutableStateOf(false) }
      val maxVisibleEpisodes = 8

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
          Text("剧集列表", color = AppColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
          if (uiState.loadingEpisodes) {
            Text("加载中...", color = AppColors.TextMuted, fontSize = 13.sp)
          }
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.episodes.isNotEmpty()) {
          val visibleEpisodes = if (episodeExpanded.value) uiState.episodes
            else uiState.episodes.take(maxVisibleEpisodes)

          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            visibleEpisodes.chunked(2).forEach { rowEpisodes ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                rowEpisodes.forEachIndexed { _, ep ->
                  val epIndex = uiState.episodes.indexOf(ep)
                  val isCurrent = epIndex == uiState.currentEpisodeIndex
                  Box(
                    modifier = Modifier
                      .weight(1f)
                      .height(36.dp)
                      .clip(PillShape)
                      .then(
                        if (isCurrent) Modifier.background(AppColors.ActionBlue)
                        else Modifier.border(1.dp, AppColors.PillBorder, PillShape)
                      )
                      .clickable { if (!isCurrent) onSwitchEpisode(epIndex) },
                    contentAlignment = Alignment.Center
                  ) {
                    Text(
                      ep.name,
                      color = if (isCurrent) Color.White else AppColors.TextMuted,
                      fontSize = 13.sp,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      modifier = Modifier.padding(horizontal = 12.dp)
                    )
                  }
                }
                if (rowEpisodes.size < 2) {
                  Spacer(modifier = Modifier.weight(1f))
                }
              }
            }
          }

          if (uiState.episodes.size > maxVisibleEpisodes) {
            androidx.compose.material3.TextButton(
              onClick = { episodeExpanded.value = !episodeExpanded.value },
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = if (episodeExpanded.value) "收起剧集"
                  else "展开全部（${uiState.episodes.size} 集）",
                color = AppColors.SkyBlue,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
              )
            }
          }
        } else if (!uiState.loadingEpisodes) {
          Text("暂无剧集信息", color = AppColors.TextMuted, fontSize = 13.sp)
        }
      }
    }
  }
}

@Composable
private fun HoistedExoPlayerView(
  exoPlayer: ExoPlayer,
  onPlaybackReady: () -> Unit,
  onBuffering: () -> Unit,
  onPlaybackError: (String) -> Unit
) {
  val currentOnReady = rememberUpdatedState(onPlaybackReady)
  val currentOnBuffering = rememberUpdatedState(onBuffering)
  val currentOnError = rememberUpdatedState(onPlaybackError)

  DisposableEffect(exoPlayer) {
    val listener = object : Player.Listener {
      override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
          Player.STATE_READY -> currentOnReady.value.invoke()
          Player.STATE_BUFFERING -> {
            if (exoPlayer.playWhenReady) currentOnBuffering.value.invoke()
          }
        }
      }
      override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) currentOnReady.value.invoke()
      }
      override fun onPlayerError(error: PlaybackException) {
        currentOnError.value.invoke(error.message ?: "播放器错误")
      }
    }
    exoPlayer.addListener(listener)
    onDispose { exoPlayer.removeListener(listener) }
  }

  AndroidView(
    modifier = Modifier.fillMaxSize(),
    factory = { ctx ->
      (LayoutInflater.from(ctx).inflate(R.layout.exo_player_view, null) as PlayerView).apply {
        controllerShowTimeoutMs = 3000
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
      s.textContent='html,body{margin:0!important;padding:0!important;overflow:hidden!important;width:100%!important;height:100%!important;background:#000!important}video,iframe,.player,.dplayer,.video-js,.art-video-player,[class*=player]{width:100%!important;height:100%!important;max-width:100%!important;max-height:100%!important;object-fit:contain!important;background:#000!important}header,footer,nav,.ad,.ads,.advertisement,[class*=header],[class*=footer],[class*=nav]{display:none!important}video::-webkit-media-controls-fullscreen-button,.dplayer-full-in-icon,.dplayer-full-icon,.dplayer-setting-fullscreen,.art-control-fullscreen,.art-control-fullscreenWeb,.vjs-fullscreen-control,.xgplayer-fullscreen,.xgplayer-cssfullscreen,.prism-fullscreen-btn,.mfp-fullscreen{display:none!important;width:0!important;height:0!important;overflow:hidden!important}';
      document.head.appendChild(s);
    })()
  """.trimIndent()

  // Create WebView once, reuse for URL changes
  DisposableEffect(Unit) {
    if (hoistedWebView.value == null) {
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
          override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {}
          override fun onHideCustomView() {}
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
      }
      hoistedWebView.value = webView
    }
    onDispose { }
  }

  val webView = hoistedWebView.value
  if (webView != null) {
    AndroidView(
      modifier = Modifier.fillMaxSize(),
      factory = {
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView
      },
      update = { view ->
        if (hoistedWebViewUrl.value != url) {
          view.stopLoading()
          view.loadUrl(url)
          hoistedWebViewUrl.value = url
        }
      }
    )
  }
}
