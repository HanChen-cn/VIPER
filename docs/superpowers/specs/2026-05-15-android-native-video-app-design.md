# Android 原生视频应用设计（全直连）

## 1. 目标

构建一个 Android 原生 App，在不依赖浏览器插件机制的前提下，实现当前扩展的核心能力：

- 影视搜索
- 解析与播放
- 当前集备源获取与随时换源
- 历史与收藏管理

## 2. 范围与非目标

### 范围（MVP）

- 分发方式：小范围分发（非应用商店首发）
- 技术路线：`Kotlin + Jetpack Compose + ExoPlayer + WebView`
- 架构深度：无后端，全直连
- 维护方式：通过发布新 APK 更新接口配置
- 播放能力：仅全屏播放
- 换源能力：支持当前集的备源拉取与手动切换，播放失败时自动切换

### 非目标（本阶段不做）

- iOS 端
- 云端账号体系
- 云同步历史 / 收藏
- 悬浮窗播放、PiP
- 应用商店上架合规打包

## 3. 约束

- 不依赖 `chrome.*` 扩展 API
- 网络请求由 App 直接访问 CMS 与解析接口
- 备源预抓取仅针对当前播放集
- 不引入服务端转发或代理

## 4. 架构概览

```text
UI（Compose Screens）
    ↓
UseCases（Search / Play / AltSources）
    ↓
Repository（统一业务编排）
    ↓
Remote DataSource（CMS API / 解析 API）
    ↓
PlayerCoordinator（ExoPlayer 或 WebView）
```

本架构保持单向依赖，UI 不直接操作网络与播放细节，便于后续替换接口或升级播放器策略。

## 5. 模块设计

### 5.1 UI 层

- `SearchScreen`：搜索输入、结果列表、选集入口
- `HistoryScreen`：搜索历史与播放历史
- `FavoritesScreen`：收藏列表与回放入口
- `PlayerScreen`：播放器主体、换源抽屉、错误提示

### 5.2 Domain 层

- `SearchUseCase`：按关键词聚合多源搜索
- `ResolvePlayUseCase`：选择播放地址并交给播放器
- `GetAltSourcesUseCase`：获取当前集备源
- `RecordHistoryUseCase`：统一历史写入策略

### 5.3 Data 层

- `ApiSourcesProvider`：读取内置源配置（随 APK 发布）
- `CmsRemoteDataSource`：CMS 搜索请求
- `ParserRemoteDataSource`：解析接口调用
- `SearchRepository`：搜索、去重、结果标准化
- `AltSourceRepository`：当前集备源获取与缓存

### 5.4 Player 层

- `UrlClassifier`：判断直链媒体或网页解析场景
- `PlayerCoordinator`：选择 `ExoPlayer` 或 `WebView` 播放路径
- `SourceSwitchController`：手动换源与自动回退

## 6. 数据模型

### 6.1 本地存储（Room）

- `search_history`：
  - `id`
  - `keyword`
  - `timestamp`

- `play_history`：
  - `id`
  - `showName`
  - `episodeName`
  - `url`
  - `timestamp`

- `favorites`：
  - `id`
  - `showName`
  - `pic`
  - `type`
  - `year`
  - `remarks`

- `alt_source_cache`：
  - `id`
  - `showName`
  - `episodeName`
  - `primaryUrlHash`
  - `altUrlsJson`
  - `fetchedAt`
  - `expiresAt`
  - `status`（`fetching` / `ready` / `failed`）

## 7. 关键流程

### 7.1 搜索流程

1. 用户输入关键词
2. `SearchUseCase` 调用 `SearchRepository`
3. 多源并发请求并标准化
4. 去重后回传 UI 展示

### 7.2 播放流程

1. 用户选择某一集
2. `UrlClassifier` 判断 URL 类型
3. 直链走 `ExoPlayer`；非直链走 `WebView`
4. 记录播放历史

### 7.3 当前集备源流程

1. 开始播放后，后台异步触发 `GetAltSourcesUseCase`
2. 仅请求当前集的备源
3. 写入 `alt_source_cache`
4. 用户打开换源抽屉时优先读缓存

### 7.4 换源与回退流程

1. 用户手动选择备源，立即切源
2. 若当前源播放失败，自动尝试下一个备源
3. 若直链全部失败，降级到 `WebView` 播放
4. 若仍失败，提示外部打开或复制地址

## 8. 异常处理

- 网络超时与重试（有限次数）
- 源无可用结果时的 UI 提示
- 缓存失效自动刷新
- 播放失败时的分层回退（主源 -> 备源 -> WebView）

## 9. 可观测性与调试

- 本地记录最近错误事件（用于小范围分发排查）
- 记录源成功率与延迟（用于后续优化排序）
- 在调试页面展示最近播放错误与切源轨迹

## 10. 验收标准

满足以下条件即可视为 MVP 达成：

- 搜索结果可返回且可播放
- 当前集备源可在播放中随时切换
- 主源失败可自动切换至少 1 次
- 历史与收藏可正常保存与读取
- 在弱网下有可理解的错误反馈

## 11. 风险与应对

- 接口变化频繁：通过快速发版应对
- 解析成功率波动：增加备源缓存与自动切源
- 设备兼容性差异：建立主流机型回归清单

