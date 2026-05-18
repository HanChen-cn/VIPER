# Android App 骨架说明

## 当前状态

已完成第 1 - 2 批可运行骨架：

- Android 工程结构（`app` 模块）
- 领域模型与源配置迁移（`api_sources.json`）
- CMS / 解析客户端基础封装
- `SearchRepository` 合并去重逻辑
- `UrlClassifier`、`PlayerCoordinator`、`SourceSwitchController`
- `Room` 数据表与 DAO
- Compose 基础导航与 4 个页面骨架
- `SearchViewModel` / `PlayerViewModel` 与状态管理
- 当前集备源后台拉取（通过 `GetAltSourcesUseCase` + 缓存）
- `PlayerScreen` 已接入真实 `ExoPlayer` 与 `WebView` 双路组件
- 播放器支持点击源切换、失败自动尝试下一源、基础进度记忆恢复
- 历史 / 收藏已接 `Room`，`HistoryScreen` 与 `FavoritesScreen` 为真实数据页
- 单测文件：`UrlClassifierTest`、`SearchRepositoryTest`

## 你接下来怎么跑

1. 用 Android Studio 打开 `android-app`
2. 让 IDE 自动同步并生成 `Gradle Wrapper`
3. 运行 `app`（Debug）

## 已知限制

- 当前环境没有 `gradle` 命令，也没有 `gradlew`，所以无法在 CLI 里直接跑测试
- `PlayerScreen` 已可切换真实播放组件，但尚未做完整播放器控制条与进度恢复
- 真机联调前建议先补齐 `Gradle Wrapper` 并在 Android Studio 运行一次单测
