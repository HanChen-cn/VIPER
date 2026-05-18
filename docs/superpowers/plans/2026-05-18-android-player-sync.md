# Android 播放页对齐 Web 端 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Android 端播放页对齐 Web 端能力：预热测速选最快源、下一集、剧集选择、换源逻辑、视口优化（竖屏 16:9 + 横屏全屏）

**Architecture:** 新建 WarmupManager 单例管理预热测速结果，App 启动时触发。扩展 PlayerViewModel 加入 episodeList / nextEpisode / sourceSwitch / fullscreen 状态。PlayerScreen 从固定 220dp 改为 16:9 竖屏 + 沉浸式横屏全屏双模式。

**Tech Stack:** Kotlin, Jetpack Compose, Media3 ExoPlayer, OkHttp, Coroutines

**验证方式:** 本项目无测试套件。所有改动通过 `chrome://extensions/` 式手动加载 + 真机/模拟器运行验证。

---

## 文件变更清单

| 操作 | 文件路径 | 职责 |
|------|---------|------|
| **新建** | `app/src/main/java/com/vipsearch/app/data/remote/WarmupManager.kt` | 预热测速单例，全局共享排序后的 API 列表 |
| **修改** | `app/src/main/java/com/vipsearch/app/AppContainer.kt` | 接入 WarmupManager 初始化 |
| **修改** | `app/src/main/java/com/vipsearch/app/MainActivity.kt` | App 启动时触发预热 |
| **修改** | `app/src/main/java/com/vipsearch/app/data/repository/SearchRepository.kt` | 用预热结果选最快 API + 新增 getEpisodeList() |
| **修改** | `app/src/main/java/com/vipsearch/app/ui/state/PlaybackSessionStore.kt` | PlaybackSession 新增 showName 传递 |
| **修改** | `app/src/main/java/com/vipsearch/app/ui/viewmodel/PlayerViewModel.kt` | 扩展状态：episodes / currentIndex / fullscreen / sourceSwitch |
| **重写** | `app/src/main/java/com/vipsearch/app/ui/screens/PlayerScreen.kt` | 竖屏 16:9 + 横屏全屏 + 剧集列表 + 下一集 + 换源 UI |
| **修改** | `app/src/main/java/com/vipsearch/app/ui/navigation/AppNavGraph.kt` | 传 SearchRepository 给 PlayerViewModel |

所有路径相对于 `android-app/` 根目录。

---

### Task 1: WarmupManager 单例

**Files:**
- Create: `app/src/main/java/com/vipsearch/app/data/remote/WarmupManager.kt`

- [ ] **Step 1: 创建 WarmupManager**

```kotlin
package com.vipsearch.app.data.remote

import com.vipsearch.app.data.model.ParseApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object WarmupManager {
  private val mutex = Mutex()
  private var rankedApis: List<ParseApiHealth> = emptyList()
  private var warmupDeferred: Deferred<List<ParseApiHealth>>? = null
  private var done = false

  fun start(
    scope: CoroutineScope,
    parserApiClient: ParserApiClient,
    parseApis: List<ParseApi>
  ) {
    if (done || warmupDeferred != null) return
    val testUrl = "https://v.qq.com/x/page/test.html"
    warmupDeferred = scope.async(Dispatchers.IO) {
      parserApiClient.warmup(parseApis, testUrl)
    }
  }

  suspend fun awaitResult(): List<ParseApiHealth> {
    mutex.withLock {
      if (done) return rankedApis
      warmupDeferred?.let {
        rankedApis = runCatching { it.await() }.getOrDefault(emptyList())
        done = true
      }
    }
    return rankedApis
  }

  fun bestApi(fallback: ParseApi?): ParseApi? =
    rankedApis.firstOrNull()?.api ?: fallback

  fun rankedApiList(): List<ParseApi> =
    if (rankedApis.isNotEmpty()) rankedApis.map { it.api }
    else emptyList()

  fun isReady(): Boolean = done

  fun statusText(): String {
    if (!done) return "⏳ 检测解析接口中..."
    if (rankedApis.isEmpty()) return "⚠ 接口检测失败，将使用默认接口"
    val fastest = rankedApis.first()
    return "✓ ${rankedApis.size} 个接口可用，最快: ${fastest.api.name} (${fastest.latencyMs}ms)"
  }
}
```

- [ ] **Step 2: 验证编译**

Run: 在 Android Studio 中 Build > Make Project，确认无编译错误。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/vipsearch/app/data/remote/WarmupManager.kt
git commit -m "feat(android): add WarmupManager singleton for parse API speed testing"
```

---

### Task 2: App 启动时触发预热

**Files:**
- Modify: `app/src/main/java/com/vipsearch/app/AppContainer.kt`
- Modify: `app/src/main/java/com/vipsearch/app/MainActivity.kt`

- [ ] **Step 1: AppContainer 添加 warmup 启动方法**

在 `AppContainer.kt` 中添加：

```kotlin
// 在类末尾、右花括号之前添加
fun startWarmup(scope: CoroutineScope) {
  scope.launch(Dispatchers.IO) {
    val bundle = apiSourcesProvider.load()
    val mobileApis = bundle.parseApis.filter { it.mobile }
    val apis = if (mobileApis.isNotEmpty()) mobileApis else bundle.parseApis
    WarmupManager.start(scope, parserApiClient, apis)
  }
}
```

需要在文件顶部添加导入：

```kotlin
import com.vipsearch.app.data.remote.WarmupManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
```

- [ ] **Step 2: MainActivity 在 onCreate 中调用预热**

修改 `MainActivity.kt`：

```kotlin
package com.vipsearch.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.vipsearch.app.ui.navigation.AppNavGraph

class MainActivity : ComponentActivity() {
  private val appContainer by lazy { AppContainer(this) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    appContainer.startWarmup(lifecycleScope)
    setContent {
      AppNavGraph(appContainer)
    }
  }
}
```

- [ ] **Step 3: 验证编译**

Run: Build > Make Project

- [ ] **Step 4: 手动验证**

启动 App，检查 Logcat 中是否有网络请求（HEAD 请求到解析接口）。
可在 `WarmupManager.start` 中临时加 `Log.d("Warmup", "Starting warmup with ${parseApis.size} apis")` 验证。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/vipsearch/app/AppContainer.kt app/src/main/java/com/vipsearch/app/MainActivity.kt
git commit -m "feat(android): trigger warmup on app launch"
```

---

### Task 3: SearchRepository 接入预热 + 新增 getEpisodeList

**Files:**
- Modify: `app/src/main/java/com/vipsearch/app/data/repository/SearchRepository.kt`

- [ ] **Step 1: 抽取 searchAndMerge 共享方法**

将 `search()` 和 `getAltSources()` 中重复的搜索+合并逻辑抽取为私有方法：

在 `SearchRepository` 类中添加：

```kotlin
private suspend fun searchAndMerge(keyword: String): List<Show> {
  val bundle = apiSourcesProvider.load()
  val enabledSources = bundle.cmsSources.filter { it.enabled }
  if (enabledSources.isEmpty()) return emptyList()

  val sourceResults = coroutineScope {
    enabledSources.map { source ->
      async { fetchSourceShows(source, keyword) }
    }.awaitAll()
  }.filter { it.isNotEmpty() }

  if (sourceResults.isEmpty()) return emptyList()
  return mergeAndDedupe(sourceResults)
}
```

- [ ] **Step 2: 修改 search() 使用预热结果**

替换 `search()` 方法：

```kotlin
suspend fun search(keyword: String): List<Show> {
  if (keyword.isBlank()) return emptyList()

  val bundle = apiSourcesProvider.load()
  val merged = searchAndMerge(keyword)
  if (merged.isEmpty()) return emptyList()

  val warmupApis = WarmupManager.awaitResult()
  val mobileApis = bundle.parseApis.filter { it.mobile }
  val fallbackApis = if (mobileApis.isNotEmpty()) mobileApis else bundle.parseApis

  val rankedApis = if (warmupApis.isNotEmpty()) warmupApis.map { it.api } else fallbackApis
  val bestApi = rankedApis.firstOrNull()

  return merged.map { show ->
    show.copy(
      episodes = show.episodes.map { ep ->
        val encoded = urlEncode(ep.playUrl)
        val alt = rankedApis.take(8).map { api -> "${api.url}$encoded" }
        if (needsParsing(ep.playUrl) && bestApi != null) {
          ep.copy(playUrl = parserApiClient.buildParseUrl(bestApi, encoded), altUrls = alt)
        } else {
          ep.copy(playUrl = ep.playUrl, altUrls = alt)
        }
      }
    )
  }
}
```

添加顶部导入：

```kotlin
import com.vipsearch.app.data.remote.WarmupManager
```

- [ ] **Step 3: 修改 getAltSources() 复用 searchAndMerge**

替换 `getAltSources()` 方法：

```kotlin
suspend fun getAltSources(
  showName: String,
  episodeName: String,
  currentUrl: String
): List<String> {
  if (showName.isBlank() || episodeName.isBlank()) return emptyList()

  val merged = searchAndMerge(showName)
  val targetShow = merged.firstOrNull { it.name.trim() == showName.trim() } ?: return emptyList()

  val seen = linkedSetOf<String>()
  targetShow.episodes.forEach { ep ->
    if (ep.name.trim() == episodeName.trim() && ep.playUrl != currentUrl) {
      seen += ep.playUrl
    }
  }
  return seen.toList()
}
```

- [ ] **Step 4: 新增 getEpisodeList()**

在 `SearchRepository` 类中添加：

```kotlin
suspend fun getEpisodeList(showName: String): List<Episode> {
  if (showName.isBlank()) return emptyList()

  val merged = searchAndMerge(showName)
  val show = merged.firstOrNull { it.name.trim() == showName.trim() } ?: return emptyList()

  val warmupApis = WarmupManager.awaitResult()
  val bundle = apiSourcesProvider.load()
  val mobileApis = bundle.parseApis.filter { it.mobile }
  val fallbackApis = if (mobileApis.isNotEmpty()) mobileApis else bundle.parseApis
  val bestApi = (if (warmupApis.isNotEmpty()) warmupApis.first().api else fallbackApis.firstOrNull())
    ?: return deduplicateEpisodes(show.episodes)

  return deduplicateEpisodes(show.episodes).map { ep ->
    if (needsParsing(ep.playUrl)) {
      ep.copy(playUrl = parserApiClient.buildParseUrl(bestApi, urlEncode(ep.playUrl)))
    } else ep
  }
}

private fun deduplicateEpisodes(episodes: List<Episode>): List<Episode> =
  episodes.groupBy { it.name.trim() }
    .mapNotNull { (_, list) -> list.firstOrNull() }
```

- [ ] **Step 5: 验证编译**

Run: Build > Make Project

- [ ] **Step 6: 手动验证**

搜索一个关键词，确认搜索结果仍正常返回，并且日志中可以看到预热结果被使用。

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/vipsearch/app/data/repository/SearchRepository.kt
git commit -m "feat(android): integrate warmup into search + add getEpisodeList"
```

---

### Task 4: PlayerViewModel 扩展

**Files:**
- Modify: `app/src/main/java/com/vipsearch/app/ui/viewmodel/PlayerViewModel.kt`
- Modify: `app/src/main/java/com/vipsearch/app/ui/state/PlaybackSessionStore.kt`

- [ ] **Step 1: PlaybackSession 保持不变但确认数据传递**

`PlaybackSession` 已有 `showName`、`episodeName`、`primaryUrl`、`altUrls`，无需修改。

- [ ] **Step 2: 扩展 PlayerUiState**

修改 `PlayerViewModel.kt` 中的 `PlayerUiState`：

```kotlin
data class PlayerUiState(
  val session: PlaybackSession = PlaybackSession(),
  val loadingExtraSources: Boolean = false,
  val extraSources: List<String> = emptyList(),
  val error: String = "",
  val episodes: List<Episode> = emptyList(),
  val currentEpisodeIndex: Int = -1,
  val hasNextEpisode: Boolean = false,
  val loadingEpisodes: Boolean = false,
  val isFullscreen: Boolean = false
)
```

添加导入：

```kotlin
import com.vipsearch.app.data.model.Episode
```

- [ ] **Step 3: 扩展 PlayerViewModel 方法**

替换整个 `PlayerViewModel` 类：

```kotlin
class PlayerViewModel(
  private val getAltSourcesUseCase: GetAltSourcesUseCase,
  private val searchRepository: SearchRepository
) : ViewModel() {
  var state by mutableStateOf(PlayerUiState())
    private set

  fun bindSession(session: PlaybackSession) {
    state = state.copy(session = session, error = "")
    if (session.primaryUrl.isBlank() || session.showName.isBlank() || session.episodeName.isBlank()) {
      return
    }
    loadExtraSources(session)
    loadEpisodeList(session.showName, session.episodeName)
  }

  private fun loadExtraSources(session: PlaybackSession) {
    viewModelScope.launch {
      state = state.copy(loadingExtraSources = true)
      val result = runCatching {
        getAltSourcesUseCase(
          showName = session.showName,
          episodeName = session.episodeName,
          primaryUrl = session.primaryUrl
        ) {
          searchRepository.getAltSources(
            showName = session.showName,
            episodeName = session.episodeName,
            currentUrl = session.primaryUrl
          )
        }
      }
      state = result.fold(
        onSuccess = { extra ->
          state.copy(loadingExtraSources = false, extraSources = extra, error = "")
        },
        onFailure = {
          state.copy(loadingExtraSources = false, extraSources = emptyList(), error = it.message ?: "备源加载失败")
        }
      )
    }
  }

  private fun loadEpisodeList(showName: String, currentEpisodeName: String) {
    viewModelScope.launch {
      state = state.copy(loadingEpisodes = true)
      val result = runCatching { searchRepository.getEpisodeList(showName) }
      state = result.fold(
        onSuccess = { episodes ->
          val index = episodes.indexOfFirst { it.name.trim() == currentEpisodeName.trim() }
          state.copy(
            loadingEpisodes = false,
            episodes = episodes,
            currentEpisodeIndex = index,
            hasNextEpisode = index >= 0 && index < episodes.size - 1
          )
        },
        onFailure = {
          state.copy(loadingEpisodes = false)
        }
      )
    }
  }

  fun switchEpisode(index: Int) {
    val ep = state.episodes.getOrNull(index) ?: return
    val newSession = PlaybackSession(
      showName = state.session.showName,
      episodeName = ep.name,
      primaryUrl = ep.playUrl,
      altUrls = ep.altUrls
    )
    PlaybackSessionStore.update(
      showName = newSession.showName,
      episodeName = newSession.episodeName,
      primaryUrl = newSession.primaryUrl,
      altUrls = newSession.altUrls
    )
    state = state.copy(
      session = newSession,
      currentEpisodeIndex = index,
      hasNextEpisode = index < state.episodes.size - 1,
      extraSources = emptyList(),
      error = ""
    )
    loadExtraSources(newSession)
  }

  fun nextEpisode() {
    if (state.hasNextEpisode) {
      switchEpisode(state.currentEpisodeIndex + 1)
    }
  }

  fun toggleFullscreen() {
    state = state.copy(isFullscreen = !state.isFullscreen)
  }
}
```

添加导入：

```kotlin
import com.vipsearch.app.ui.state.PlaybackSessionStore
```

- [ ] **Step 4: 验证编译**

Run: Build > Make Project

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/vipsearch/app/ui/viewmodel/PlayerViewModel.kt
git commit -m "feat(android): extend PlayerViewModel with episodes, next, fullscreen"
```

---

### Task 5: PlayerScreen UI 重构

**Files:**
- Rewrite: `app/src/main/java/com/vipsearch/app/ui/screens/PlayerScreen.kt`

这是最大的改动。分两步：先竖屏模式，再横屏全屏。

- [ ] **Step 1: 完整重写 PlayerScreen.kt**

```kotlin
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
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.SkipNext
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
import com.vipsearch.app.data.model.Episode
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

  // 横屏全屏处理
  if (uiState.isFullscreen) {
    BackHandler { onToggleFullscreen() }

    LaunchedEffect(Unit) {
      activity?.let {
        it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val window = it.window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { ctrl ->
          ctrl.hide(WindowInsetsCompat.Type.systemBars())
          ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
      }
    }

    DisposableEffect(Unit) {
      onDispose {
        activity?.let {
          it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
          val window = it.window
          WindowCompat.setDecorFitsSystemWindows(window, true)
          WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        }
      }
    }

    FullscreenPlayer(
      target = target.value,
      currentUrl = currentUrl.value,
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

  // 竖屏模式
  Column(modifier = Modifier.fillMaxSize()) {
    // 顶栏
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

    // 播放区域 — 16:9
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

    // 操作按钮行
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

    // 错误/状态提示
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

    // 换源列表（展开时）
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

    // 剧集列表
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
  currentUrl: String,
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

    // 全屏覆盖控制层
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

    // 标题
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
```

- [ ] **Step 2: 验证编译**

Run: Build > Make Project  
⚠️ 需要确认 `material-icons-extended` 依赖已添加。如果未添加，在 `build.gradle.kts` 中加入：
```
implementation("androidx.compose.material:material-icons-extended")
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/vipsearch/app/ui/screens/PlayerScreen.kt
git commit -m "feat(android): rewrite PlayerScreen with 16:9 portrait + fullscreen + episodes + source switch"
```

---

### Task 6: AppNavGraph 适配新 PlayerScreen 接口

**Files:**
- Modify: `app/src/main/java/com/vipsearch/app/ui/navigation/AppNavGraph.kt`

- [ ] **Step 1: 更新 player composable 调用**

在 `AppNavGraph.kt` 中，将 `composable("player")` 块替换为：

```kotlin
composable("player") {
  val vm: PlayerViewModel = viewModel(
    factory = PlayerViewModelFactory(
      getAltSourcesUseCase = appContainer.getAltSourcesUseCase,
      searchRepository = appContainer.searchRepository
    )
  )
  LaunchedEffect(PlaybackSessionStore.session) {
    vm.bindSession(PlaybackSessionStore.session)
  }
  PlayerScreen(
    uiState = vm.state,
    onBack = { navController.popBackStack() },
    onSwitchEpisode = vm::switchEpisode,
    onNextEpisode = vm::nextEpisode,
    onToggleFullscreen = vm::toggleFullscreen
  )
}
```

- [ ] **Step 2: 验证编译**

Run: Build > Make Project

- [ ] **Step 3: 手动端到端测试**

1. 启动 App → 观察预热状态
2. 搜索一个剧名 → 点击某集 → 进入播放页
3. 验证竖屏模式：16:9 视口、剧集列表、下一集按钮、换源按钮
4. 点击全屏 → 验证横屏沉浸模式
5. 按返回键 → 退出全屏回竖屏
6. 点击剧集列表中其他集 → 验证切集
7. 点击换源 → 验证源列表展开/切换
8. 从历史进入播放 → 验证剧集列表异步加载

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/vipsearch/app/ui/navigation/AppNavGraph.kt
git commit -m "feat(android): wire new PlayerScreen interface in navigation"
```

---

### Task 7: build.gradle.kts 依赖检查

**Files:**
- Modify (if needed): `app/build.gradle.kts`

- [ ] **Step 1: 确认 material-icons-extended 依赖**

检查 `build.gradle.kts` 是否包含 `material-icons-extended`。如果没有，添加：

```kotlin
implementation("androidx.compose.material:material-icons-extended")
```

这是 `Icons.Default.Fullscreen` / `Icons.Default.SkipNext` 等图标所需的依赖。

- [ ] **Step 2: 确认 activity-compose 依赖**

`BackHandler` 需要 `activity-compose`。检查是否已有：

```kotlin
implementation("androidx.activity:activity-compose")
```

- [ ] **Step 3: Sync & Build**

Run: Gradle Sync → Build > Make Project

- [ ] **Step 4: Commit (if changes made)**

```bash
git add app/build.gradle.kts
git commit -m "chore(android): add material-icons-extended dependency"
```
