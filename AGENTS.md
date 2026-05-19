# AGENTS.md

## 项目简介

Chrome/Edge Manifest V3 扩展 — 搜索 CMS 影视资源站，通过解析接口播放。纯原生 JS/HTML/CSS，零依赖，无构建步骤，无 package.json。

## 加载测试

1. 打开 `chrome://extensions/`（Edge 用 `edge://extensions/`）
2. 开启"开发者模式"
3. "加载已解压的扩展程序" → 选择 `extension` 文件夹
4. 点击工具栏扩展图标测试 popup

无构建、无编译、无 `npm install`。改文件 → 在 `chrome://extensions/` 重新加载 → 测试。

## 架构

```
extension/popup/popup.html + popup.js  ←→  extension/background/service-worker.js  ←→  CMS API + 解析接口
      (UI, 搜索/历史/收藏)                   (消息路由, 预热)                          (5 资源站, 35 解析接口)
                                                 ↕
                                          chrome.storage.local (历史, 收藏)
                                          chrome.storage.session (播放数据, 固定模式)

extension/content/floating-panel.js + content-script.js  →  Shadow DOM 浮动播放器 (注入任意页面)
extension/player/player.html + player.js                   →  新标签页全屏播放器
```

- `extension/lib/` 是 service-worker 的共享代码（ES module，通过 `type: "module"` 导入）
- `extension/popup/popup.js` 通过 `chrome.runtime.sendMessage` 与 service-worker 通信；它本身不是 ES module（虽然用 `<script type="module">` 加载，但不从 lib/ 导入）
- Content script 注入到所有页面（`<all_urls>`），用 Shadow DOM 隔离样式

## 核心模式

- **消息传递**：popup↔background 全部通过 `chrome.runtime.sendMessage` + `{type: "..."}` 通信。Service-worker 的 `addListener` 回调返回 `true` 以支持异步响应。
- **预热机制**：popup 打开 → 立即发 `warmup` → service-worker 并发 HEAD 35 个解析接口 → 按延迟排序。搜索会等待预热完成。
- **换源**：每集返回 `playUrl`（最佳解析接口）+ `altUrls`（前 8 个备选）。`getAltSources` 重新搜索所有 CMS 源找同名剧集的其他流地址。
- **浮动面板**：`content-script.js` 收到 `pinPlay` 消息 → 创建 `FloatingPanel`（Shadow DOM）。popup.js 通过 `ensureContentScript()` 按需注入（chrome.scripting.executeScript）。
- **播放数据传递**：service-worker 写 `{url, name, episode}` 到 `chrome.storage.session`，再打开 `player/player.html` 新标签页。Player 从 session storage 读取。

## 文件编辑指南

- `extension/lib/api-sources.js` — CMS 资源站 URL 和解析接口列表。增删资源站改这里。
- `extension/lib/searcher.js` — 搜索编排、剧集去重、换源查询。
- `extension/lib/parser.js` — CMS API 响应标准化。CMS 格式：`vod_play_from` 按 `$$$` 分割，`vod_play_url` 按 `$$$` → `#` → `$`（名称$url）。
- `extension/lib/validator.js` — URL HEAD 验证（带超时）。`needsParsing()` 判断 URL 是否来自需解析的平台（爱奇艺、优酷、腾讯等）。
- `extension/lib/storage.js` — Chrome storage 封装。搜索历史最多 20 条，播放历史最多 50 条。
- `extension/popup/popup.js` — popup 所有 UI 逻辑。三个 tab：搜索/历史/收藏。
- `extension/content/floating-panel.js` — `FloatingPanel` 类。可拖拽、可缩放、可最小化。样式在 `getStyles()` 内联。
- `extension/player/player.js` — 全屏播放器。从 session storage 读取数据，构建换源列表。

## 注意事项

- `extension/manifest.json` 的 `host_permissions: ["<all_urls>"]` 是 CMS API 请求和 content script 注入的必要权限。
- Service-worker 使用 `type: "module"` — 可以 ES module 导入 `extension/lib/` 下的文件。
- Content script 加载顺序很重要：先 `floating-panel.js`（定义 `FloatingPanel` 类），再 `content-script.js`（使用它）。
- 解析接口预热用 `mode: "no-cors"` HEAD 请求 — 不透明响应也算成功。
- `ensureContentScript()` 先 ping 再注入，防止重复注入。
- 项目无测试套件、无 lint、无类型检查。改动后在浏览器中手动验证。

## Android 版本号规则

推送安卓代码并打 tag 触发 CI 构建时，**必须**同步更新 `android-app/app/build.gradle.kts` 中的 `versionCode` 和 `versionName`，确保 APK 内显示的版本号与 git tag 一致。

- tag 格式：`android-v{versionName}`（如 `android-v0.3.6`）
- `versionCode` 每次递增
- `versionName` 与 tag 中的版本号相同

## Git Commit 规范

- 所有 commit message **必须使用中文**
- 专业术语、代码标识符（如类名、函数名、文件名）可保留英文
- 格式示例：`fix(android): 修复 PlayerView 全屏按钮点击后控件无响应`

## 文档更新规则

提交代码前，若满足以下任一条件，必须先更新 `README.md` 的项目介绍部分：

- 代码改动超过 **68 行**
- 改动涉及超过 **16 个文件**
- 涉及架构变动（新增/删除模块、改变通信方式、调整存储策略等）
