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
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
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

  Column(modifier = Modifier.fillMaxSize().background(Color(0xFF1D1D1F))) {
    // Top bar — hidden in fullscreen
    if (!isVideoFullscreen.value) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color(0xFF272729))
          .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(onClick = onBack) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
        }
        Text(
          text = "${session.showName} · ${session.episodeName}",
          color = Color.White,
          fontSize = 16.sp,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.weight(1f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
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
            shape = RoundedCornerShape(9999.dp),
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
          shape = RoundedCornerShape(9999.dp),
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
          shape = RoundedCornerShape(9999.dp),
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
        Text(
          text = statusHint.value,
          color = AppColors.ErrorRed,
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
          verticalArrangement = Arrangement.spacedBy(4.dp)
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
            if (selected) {
              Button(
                onClick = {},
                modifier = Modifier.fillMaxWidth().height(36.dp),
                shape = RoundedCornerShape(9999.dp),
                colors = ButtonDefaults.buttonColors(
                  containerColor = AppColors.ActionBlue,
                  contentColor = Color.White
                )
              ) {
                Text("当前源 ${index + 1}", fontSize = 13.sp)
              }
            } else {
              Button(
                onClick = {
                  val switched = switchController.switchTo(index)
                  if (!switched.isNullOrBlank()) {
                    moveTo(switched)
                    statusHint.value = ""
                  }
                },
                modifier = Modifier
                  .fillMaxWidth()
                  .height(36.dp)
                  .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(9999.dp)),
                shape = RoundedCornerShape(9999.dp),
                colors = ButtonDefaults.buttonColors(
                  containerColor = Color.Transparent,
                  contentColor = AppColors.TextMuted
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
              ) {
                Text("源 ${index + 1}", fontSize = 13.sp)
              }
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

          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            visibleEpisodes.chunked(2).forEach { rowEpisodes ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                rowEpisodes.forEachIndexed { _, ep ->
                  val epIndex = uiState.episodes.indexOf(ep)
                  val isCurrent = epIndex == uiState.currentEpisodeIndex
                  if (isCurrent) {
                    Button(
                      onClick = {},
                      modifier = Modifier.weight(1f).height(40.dp),
                      shape = RoundedCornerShape(9999.dp),
                      colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.ActionBlue,
                        contentColor = Color.White
                      )
                    ) {
                      Text(ep.name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                  } else {
                    Button(
                      onClick = { onSwitchEpisode(epIndex) },
                      modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(9999.dp)),
                      shape = RoundedCornerShape(9999.dp),
                      colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = AppColors.TextMuted
                      ),
                      elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                      Text(ep.name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
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
  onPlaybackError: (String) -> Unit
) {
  val currentOnReady = rememberUpdatedState(onPlaybackReady)
  val currentOnError = rememberUpdatedState(onPlaybackError)

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

  AndroidView(
    modifier = Modifier.fillMaxSize(),
    factory = { ctx ->
      (LayoutInflater.from(ctx).inflate(R.layout.exo_player_view, null) as PlayerView).apply {
        controllerShowTimeoutMs = 3000
        player = exoPlayer
        setFullscreenButtonClickListener(null)
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
