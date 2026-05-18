# Android 播放页对齐 Web 端 — Design Spec

## 背景

Android 端播放页与 Web 端（`extension/player/player.js`）存在显著功能差距：

| 能力 | Web 端 | Android 端（改动前） |
|------|--------|---------------------|
| 预热测速选最快源 | `warmupParseApis()` HEAD 并发测延迟排序 | 代码存在（`ParserApiClient.warmup()`）但未接入，搜索用 `firstOrNull()` |
| 下一集 | 播放页"下一集"按钮 | 无 |
| 剧集选择列表 | 播放页"剧集"下拉 | 无 |
| 换源逻辑 | `getAltSources` 动态重搜 CMS | 仅用搜索时带过来的 `altUrls` + Room 缓存 |
| 播放视口 | 全屏 iframe | 固定 220dp + 16dp padding + 大量调试文字 |
| ExoPlayer 黑区 | N/A | PlayerView 控制栏在 220dp 内挤占底部空间 |

## 设计决策

### 1. 架构方案：WarmupManager 单例 + 扩展 PlayerViewModel

**选定理由：** 最接近 Web 端架构（service-worker 全局预热结果 → searcher 引用），预热结果全局可复用。

- `WarmupManager` 对象单例持有排序后的 `ParseApiHealth` 列表
- App 启动时通过 `AppContainer.startWarmup()` 触发
- `SearchRepository.search()` 和 `getEpisodeList()` 等待预热完成后取排序结果
- `PlayerViewModel` 扩展 `episodes` / `currentEpisodeIndex` / `hasNextEpisode` / `isFullscreen` 状态

### 2. 播放视口：竖屏 16:9 + 沉浸式横屏全屏

- **竖屏模式：** `aspectRatio(16f/9f)` 替代固定 `height(220.dp)`，视频区域自适应屏幕宽度
- **横屏全屏：** 点击"全屏"按钮 → `SENSOR_LANDSCAPE` + `WindowInsetsControllerCompat` 隐藏系统栏
- **退出全屏：** 返回键或"退出全屏"按钮 → 恢复竖屏 + 显示系统栏

### 3. 竖屏模式下方内容

去掉调试信息（路由类型、URL），仅保留用户需要的：

- **顶栏：** 返回按钮 + 剧名·集名 + 全屏按钮
- **操作行：** 下一集按钮（有下一集时显示）+ 换源按钮
- **换源列表：** 可展开/收起，显示所有可用源
- **剧集列表：** `FlowRow` 网格布局，当前集高亮

### 4. 预热时机：App 启动时

- `MainActivity.onCreate()` → `appContainer.startWarmup(lifecycleScope)`
- 对齐 Web 端 popup 打开即预热的行为
- 搜索前如果预热未完成则等待

### 5. 换源逻辑：完全对齐 Web 端

- 播放页加载后并发调用：
  1. `getEpisodeList(showName)` → 获取完整剧集列表（含 warmup 后最优 playUrl）
  2. `getAltSources(showName, episodeName)` → 获取当前集的 CMS 备源
- 切集时重新调用 `getAltSources` 获取新集的备源

### 6. 剧集数据来源：混合模式

- **从搜索进入：** 传入 `showName` + `episodeName`，播放页异步调 `getEpisodeList` 获取完整列表
- **从历史进入：** 同理，用 `showName` 异步拉取
- 避免重复搜索：`searchAndMerge()` 抽取为共享私有方法

## 文件变更

| 操作 | 文件 | 说明 |
|------|------|------|
| 新建 | `data/remote/WarmupManager.kt` | 预热测速单例 |
| 修改 | `AppContainer.kt` | 接入 WarmupManager 初始化 |
| 修改 | `MainActivity.kt` | 启动时触发预热 |
| 修改 | `data/repository/SearchRepository.kt` | 用预热结果 + 新增 `getEpisodeList()` + 抽取 `searchAndMerge()` |
| 修改 | `ui/viewmodel/PlayerViewModel.kt` | 扩展状态和方法 |
| 重写 | `ui/screens/PlayerScreen.kt` | 全新 UI |
| 修改 | `ui/navigation/AppNavGraph.kt` | 适配新接口 |
| 修改 | `build.gradle.kts` | 添加 `material-icons-extended` |
| 修改 | `README.md` | 更新 Android 端介绍 |

## 被拒绝的方案

- **方案 A（仅扩展 ViewModel）：** 预热结果无法全局共享，搜索和播放各自独立预热造成浪费
- **方案 C（独立 PlayerActivity）：** 改动过大，需要新建 Activity + 在 Manifest 注册 + 数据传递改 Intent，收益不大
- **自动横屏模式：** 进入即横屏的体验过于突兀，用户可能在竖屏浏览时不希望被强制旋转
