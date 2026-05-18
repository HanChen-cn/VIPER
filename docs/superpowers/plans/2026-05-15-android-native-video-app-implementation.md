# Android 原生视频应用（全直连）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development（recommended）or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `android-app` 中实现 Android 原生视频应用，复刻现有扩展的搜索、播放、历史、收藏与当前集换源能力。

**Architecture:** 采用 `Kotlin + Compose + Repository` 分层。客户端直连 CMS 与解析接口，当前集备源异步拉取并缓存，播放路径由 `ExoPlayer` 与 `WebView` 双通道兜底。

**Tech Stack:** Kotlin、Jetpack Compose、Coroutines、OkHttp、Room、ExoPlayer、WebView。

---

## File Structure（目标）

### Create

- `android-app/settings.gradle.kts`
- `android-app/build.gradle.kts`
- `android-app/gradle.properties`
- `android-app/app/build.gradle.kts`
- `android-app/app/src/main/AndroidManifest.xml`
- `android-app/app/src/main/java/com/vipsearch/app/MainActivity.kt`
- `android-app/app/src/main/java/com/vipsearch/app/ui/navigation/AppNavGraph.kt`
- `android-app/app/src/main/java/com/vipsearch/app/ui/screens/SearchScreen.kt`
- `android-app/app/src/main/java/com/vipsearch/app/ui/screens/HistoryScreen.kt`
- `android-app/app/src/main/java/com/vipsearch/app/ui/screens/FavoritesScreen.kt`
- `android-app/app/src/main/java/com/vipsearch/app/ui/screens/PlayerScreen.kt`
- `android-app/app/src/main/java/com/vipsearch/app/data/model/*.kt`
- `android-app/app/src/main/java/com/vipsearch/app/data/local/*.kt`
- `android-app/app/src/main/java/com/vipsearch/app/data/remote/*.kt`
- `android-app/app/src/main/java/com/vipsearch/app/data/repository/*.kt`
- `android-app/app/src/main/java/com/vipsearch/app/domain/usecase/*.kt`
- `android-app/app/src/main/java/com/vipsearch/app/player/UrlClassifier.kt`
- `android-app/app/src/main/java/com/vipsearch/app/player/PlayerCoordinator.kt`
- `android-app/app/src/main/java/com/vipsearch/app/player/SourceSwitchController.kt`
- `android-app/app/src/main/res/raw/api_sources.json`
- `android-app/app/src/test/java/com/vipsearch/app/UrlClassifierTest.kt`
- `android-app/app/src/test/java/com/vipsearch/app/SearchRepositoryTest.kt`

---

### Task 1: 初始化 Android 工程骨架

**Files:**
- Create: `android-app/settings.gradle.kts`
- Create: `android-app/build.gradle.kts`
- Create: `android-app/app/build.gradle.kts`
- Create: `android-app/app/src/main/AndroidManifest.xml`

- [ ] **Step 1: 写基础 Gradle 配置**

```kotlin
// android-app/settings.gradle.kts
rootProject.name = "vip-search-android"
include(":app")
```

- [ ] **Step 2: 配置 app 依赖（Compose / OkHttp / Room / ExoPlayer）**

```kotlin
// android-app/app/build.gradle.kts（片段）
dependencies {
  implementation("androidx.core:core-ktx:1.15.0")
  implementation("androidx.activity:activity-compose:1.10.1")
  implementation("androidx.compose.material3:material3:1.3.1")
  implementation("com.squareup.okhttp3:okhttp:4.12.0")
  implementation("androidx.room:room-runtime:2.6.1")
  kapt("androidx.room:room-compiler:2.6.1")
  implementation("androidx.room:room-ktx:2.6.1")
  implementation("androidx.media3:media3-exoplayer:1.5.1")
  implementation("androidx.media3:media3-ui:1.5.1")
}
```

- [ ] **Step 3: 构建验证**

Run: `cd android-app && ./gradlew :app:assembleDebug`  
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add android-app
git commit -m "chore: scaffold android app project structure"
```

---

### Task 2: 迁移核心数据模型与源配置

**Files:**
- Create: `android-app/app/src/main/java/com/vipsearch/app/data/model/VideoModels.kt`
- Create: `android-app/app/src/main/res/raw/api_sources.json`
- Create: `android-app/app/src/main/java/com/vipsearch/app/data/remote/ApiSourcesProvider.kt`

- [ ] **Step 1: 建立数据模型**

```kotlin
data class Episode(
  val name: String,
  val playUrl: String,
  val source: String,
  val altUrls: List<String> = emptyList()
)
```

- [ ] **Step 2: 写入源配置 JSON（由现有 `lib/api-sources.js` 转换）**
- [ ] **Step 3: 实现 `ApiSourcesProvider` 读取 raw JSON**
- [ ] **Step 4: 单测验证 JSON 可解析**

Run: `cd android-app && ./gradlew :app:testDebugUnitTest --tests "*ApiSourcesProvider*"`  
Expected: `PASSED`

- [ ] **Step 5: Commit**

```bash
git add android-app/app/src/main/java/com/vipsearch/app/data/model android-app/app/src/main/res/raw/api_sources.json android-app/app/src/main/java/com/vipsearch/app/data/remote/ApiSourcesProvider.kt
git commit -m "feat: add source config and core video models"
```

---

### Task 3: 实现远程数据层（CMS / 解析）

**Files:**
- Create: `android-app/app/src/main/java/com/vipsearch/app/data/remote/CmsApiClient.kt`
- Create: `android-app/app/src/main/java/com/vipsearch/app/data/remote/ParserApiClient.kt`
- Create: `android-app/app/src/main/java/com/vipsearch/app/data/remote/NetworkConfig.kt`

- [ ] **Step 1: 封装统一 OkHttp 客户端（超时、重试、UA）**
- [ ] **Step 2: 实现 CMS 搜索请求与解析**
- [ ] **Step 3: 实现解析接口调用**
- [ ] **Step 4: 为网络层写失败场景单测（超时 / 空响应）**

Run: `cd android-app && ./gradlew :app:testDebugUnitTest --tests "*remote*"`  
Expected: `PASSED`

- [ ] **Step 5: Commit**

```bash
git add android-app/app/src/main/java/com/vipsearch/app/data/remote
git commit -m "feat: implement cms and parser remote clients"
```

---

### Task 4: 实现 SearchRepository（并发搜索 + 去重 + 标准化）

**Files:**
- Create: `android-app/app/src/main/java/com/vipsearch/app/data/repository/SearchRepository.kt`
- Create: `android-app/app/src/main/java/com/vipsearch/app/domain/usecase/SearchUseCase.kt`
- Test: `android-app/app/src/test/java/com/vipsearch/app/SearchRepositoryTest.kt`

- [ ] **Step 1: 先写失败单测（多源合并与去重）**

```kotlin
@Test
fun `merge multi-source results and dedupe by show name`() { /* ... */ }
```

- [ ] **Step 2: 运行单测并确认失败**

Run: `cd android-app && ./gradlew :app:testDebugUnitTest --tests "*SearchRepositoryTest*"`  
Expected: `FAILED`

- [ ] **Step 3: 写最小实现使测试通过**
- [ ] **Step 4: 重跑测试确认通过**
- [ ] **Step 5: Commit**

```bash
git add android-app/app/src/main/java/com/vipsearch/app/data/repository/SearchRepository.kt android-app/app/src/main/java/com/vipsearch/app/domain/usecase/SearchUseCase.kt android-app/app/src/test/java/com/vipsearch/app/SearchRepositoryTest.kt
git commit -m "feat: add search repository with merge and dedupe"
```

---

### Task 5: 实现本地存储（历史 / 收藏 / 当前集备源缓存）

**Files:**
- Create: `android-app/app/src/main/java/com/vipsearch/app/data/local/AppDatabase.kt`
- Create: `android-app/app/src/main/java/com/vipsearch/app/data/local/HistoryDao.kt`
- Create: `android-app/app/src/main/java/com/vipsearch/app/data/local/FavoriteDao.kt`
- Create: `android-app/app/src/main/java/com/vipsearch/app/data/local/AltSourceCacheDao.kt`
- Create: `android-app/app/src/main/java/com/vipsearch/app/data/repository/AltSourceRepository.kt`

- [ ] **Step 1: 建表与 DAO**
- [ ] **Step 2: 实现 `AltSourceRepository`（当前集 key、TTL = 24 h）**
- [ ] **Step 3: 单测验证缓存命中 / 过期刷新逻辑**

Run: `cd android-app && ./gradlew :app:testDebugUnitTest --tests "*AltSource*"`  
Expected: `PASSED`

- [ ] **Step 4: Commit**

```bash
git add android-app/app/src/main/java/com/vipsearch/app/data/local android-app/app/src/main/java/com/vipsearch/app/data/repository/AltSourceRepository.kt
git commit -m "feat: add room storage for history favorites and alt source cache"
```

---

### Task 6: 实现播放器内核（ExoPlayer + WebView）

**Files:**
- Create: `android-app/app/src/main/java/com/vipsearch/app/player/UrlClassifier.kt`
- Create: `android-app/app/src/main/java/com/vipsearch/app/player/PlayerCoordinator.kt`
- Create: `android-app/app/src/main/java/com/vipsearch/app/player/SourceSwitchController.kt`
- Test: `android-app/app/src/test/java/com/vipsearch/app/UrlClassifierTest.kt`

- [ ] **Step 1: 先写 `UrlClassifier` 单测**

```kotlin
@Test
fun `m3u8 and mp4 should use exoplayer`() { /* ... */ }
```

- [ ] **Step 2: 实现分类器与播放器协调器**
- [ ] **Step 3: 实现自动换源（失败最多 3 次）**
- [ ] **Step 4: 单测与本地真机验证**

Run: `cd android-app && ./gradlew :app:testDebugUnitTest --tests "*UrlClassifierTest*"`  
Expected: `PASSED`

- [ ] **Step 5: Commit**

```bash
git add android-app/app/src/main/java/com/vipsearch/app/player android-app/app/src/test/java/com/vipsearch/app/UrlClassifierTest.kt
git commit -m "feat: implement player coordinator with exoplayer and webview fallback"
```

---

### Task 7: 实现 UI 页面与交互

**Files:**
- Create: `android-app/app/src/main/java/com/vipsearch/app/MainActivity.kt`
- Create: `android-app/app/src/main/java/com/vipsearch/app/ui/navigation/AppNavGraph.kt`
- Create: `android-app/app/src/main/java/com/vipsearch/app/ui/screens/SearchScreen.kt`
- Create: `android-app/app/src/main/java/com/vipsearch/app/ui/screens/HistoryScreen.kt`
- Create: `android-app/app/src/main/java/com/vipsearch/app/ui/screens/FavoritesScreen.kt`
- Create: `android-app/app/src/main/java/com/vipsearch/app/ui/screens/PlayerScreen.kt`

- [ ] **Step 1: 搭建底部导航（搜索 / 历史 / 收藏）**
- [ ] **Step 2: 接入搜索结果列表与选集点击**
- [ ] **Step 3: 接入播放器页与换源抽屉**
- [ ] **Step 4: 手工回归主流程（搜索、播放、换源）**

Run: `cd android-app && ./gradlew :app:assembleDebug`  
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add android-app/app/src/main/java/com/vipsearch/app
git commit -m "feat: build compose screens and playback interactions"
```

---

### Task 8: 稳定性收敛与发布产物

**Files:**
- Modify: `android-app/app/src/main/java/com/vipsearch/app/player/SourceSwitchController.kt`
- Modify: `android-app/app/src/main/java/com/vipsearch/app/data/repository/AltSourceRepository.kt`
- Create: `android-app/README.md`

- [ ] **Step 1: 增加弱网与超时兜底提示**
- [ ] **Step 2: 增加调试日志开关（仅 debug 构建）**
- [ ] **Step 3: 编写运行说明与打包命令**

```bash
cd android-app
./gradlew :app:assembleDebug
```

- [ ] **Step 4: 最终回归检查**
  - 搜索成功
  - 当前集备源可拉取
  - 手动换源可用
  - 主源失败自动切源可触发
  - 直链失败可降级到 WebView

- [ ] **Step 5: Commit**

```bash
git add android-app
git commit -m "chore: polish stability and add android app usage docs"
```

---

## Self-Review Checklist

- [ ] 与设计文档所有范围一致（无后端、全直连、当前集备源）
- [ ] 无占位词（`TODO` / `TBD`）
- [ ] 路径与命名在各任务间一致
- [ ] 每个任务都可独立执行并验收

## Execution Handoff

计划文件已完成并保存到：  
`docs/superpowers/plans/2026-05-15-android-native-video-app-implementation.md`

后续执行有 2 个选项：

1. **Subagent-Driven（推荐）**：逐任务分发子代理执行与复核  
2. **Inline Execution**：在当前会话按任务批次执行

