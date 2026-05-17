# 面板固定浮层功能设计

## 概述

为 VIPER 扩展添加「固定播放」模式：在 popup 搜索结果中激活后，点击剧集按钮将在当前页面以浮层形式播放视频，而非打开新标签页。

## 方案选择

**方案 A：Shadow DOM + iframe（选定）**

通过 content script 注入 Shadow DOM 隔离的浮动面板，内嵌 iframe 加载视频。

理由：
- 直接复用现有 iframe 解析流程，无需额外提取视频直链
- Shadow DOM 样式隔离，不受宿主页面 CSS 影响
- 可完全自定义 UI（换源、拖拽、最小化等）

舍弃方案：
- 方案 B（无 Shadow DOM）：样式可能被宿主页面干扰
- 方案 C（浏览器原生 PiP）：需要 `<video>` 元素，与现有 iframe 架构不兼容；无法自定义 UI

## 文件结构

```
content/
  content-script.js    ← 新增：入口，监听消息，管理浮层生命周期
  floating-panel.js    ← 新增：Shadow DOM 浮层 UI 组件
popup/
  popup.js             ← 修改：添加固定模式切换按钮及逻辑
  popup.html           ← 修改：添加固定模式按钮 DOM
  popup.css            ← 修改：添加固定模式按钮样式
manifest.json          ← 修改：添加 content_scripts 声明
background/
  service-worker.js    ← 不修改：复用现有 getAltSources 消息
lib/                   ← 不修改
player/                ← 不修改
```

## 架构与数据流

```
popup
  ├─ 点击「固定播放」→ chrome.storage.session.set({pinMode: true})
  └─ 点击剧集按钮 → 检查 pinMode
       ├─ false → chrome.runtime.sendMessage({type:'play', ...})  [现有逻辑]
       └─ true  → chrome.tabs.sendMessage(activeTabId, {type:'pinPlay', url, name, episode})

content-script.js (注入到当前网页)
  ├─ 收到 pinPlay 消息
  ├─ 首次：创建 Shadow DOM 容器 + 浮层
  └─ 后续：更新 iframe src + 标题

floating-panel.js
  ├─ 拖拽、缩放、最小化、关闭
  └─ 换源 → chrome.runtime.sendMessage({type:'getAltSources'}) → service-worker
```

## manifest.json 变更

添加 `tabs` 权限和 content_scripts 声明：

```json
"permissions": [
  "storage",
  "tabs",
  "scripting"
],
"content_scripts": [{
  "matches": ["<all_urls>"],
  "js": ["content/content-script.js"],
  "run_at": "document_idle"
}]
```

## Popup 交互设计

### UI

搜索结果列表顶部增加唯一的全局切换按钮：

```
┌─────────────────────────────────────┐
│  🔍 [输入框] [搜索]                │
│  [搜索] [历史] [收藏]              │
├─────────────────────────────────────┤
│  [📌 固定播放]  ← 全局切换按钮      │
│                                     │
│  剧名A · 类型 · 年份               │
│  [第1集] [第2集] [第3集] ...        │
│                                     │
│  剧名B · 类型 · 年份               │
│  [第1集] [第2集] [第3集] ...        │
└─────────────────────────────────────┘
```

### 交互逻辑

1. 默认状态：按钮灰色未激活，剧集点击 → 新标签页播放（现有行为不变）
2. 点击「固定播放」→ 按钮变为高亮激活态
3. 激活态下点击任意剧集按钮 → 浮层播放
4. 再次点击「固定播放」→ 取消激活，恢复新标签页行为
5. 状态存储在 `chrome.storage.session`，popup 关闭后保持

### 消息发送

激活固定模式后点击剧集，popup 需先获取当前活动标签页 ID：

```js
const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
chrome.tabs.sendMessage(tab.id, {
  type: 'pinPlay',
  url: episodeUrl,      // 解析后的视频 URL
  name: showName,       // 剧名
  episode: episodeName  // 集名
});
```

注意：`tabs` 权限需要在 manifest.json 的 permissions 中添加。

## Content Script 浮层设计

### Shadow DOM 结构

```
<div id="viper-pin-root">              ← 宿主页面上的唯一节点
  #shadow-root                         ← Shadow DOM 隔离
    <div class="viper-panel">          ← 可拖拽、可缩放的容器
      <div class="viper-header">       ← 标题栏（拖拽区域）
        <span class="viper-title">剧名 - 第1集</span>
        <div class="viper-controls">
          <button class="viper-min">─</button>
          <button class="viper-close">✕</button>
        </div>
      </div>
      <div class="viper-body">
        <iframe src="视频URL" allowfullscreen />
      </div>
      <div class="viper-source-bar">   ← 换源栏
        <button class="viper-source-toggle">换源 ▼</button>
        <div class="viper-source-list hidden">...</div>
      </div>
      <div class="viper-resize-handle"></div>  ← 右下角缩放手柄
    </div>
</div>
```

### 默认样式

- 面板默认位置：右下角
- 默认尺寸：400×225（16:9）
- 最小尺寸：320×180
- 最大尺寸：视口 80%
- z-index: 2147483647（最大值）
- 圆角、阴影、深色背景

### 拖拽

- 拖拽区域：header 区域
- `mousedown` → 记录初始偏移
- `mousemove` → `transform: translate(x, y)`
- `mouseup` → 结束拖拽
- 边界限制：不超出视口

### 缩放

- 缩放手柄：右下角
- 拖拽时按 16:9 锁定比例
- 最小 320×180，最大不超过视口 80%
- 更新面板 `width` + `height`

### 最小化

- 点击 ─ → 面板收起为右下角小图标（带剧名 tooltip）
- 点击小图标 → 恢复面板

### 关闭

- 点击 ✕ → 移除整个浮层 DOM（`#viper-pin-root`）
- 清理所有事件监听器

### 换源

- 换源栏默认隐藏，鼠标 hover 面板时显示（移动端则常驻显示换源按钮）
- 点击「换源 ▼」→ 展开源列表
- 调用 `chrome.runtime.sendMessage({type:'getAltSources', ...})`
- 选择源 → 更新 iframe src

## 异常处理

### 视频加载失败

- iframe 加载超时（10秒）→ 浮层内显示「加载失败，点击换源」提示
- 换源时自动尝试下一个可用源

### CSP 限制

- 少数网站 Content-Security-Policy 可能阻止 iframe 加载
- 浮层内显示提示「该页面安全策略限制了视频加载，请使用新标签页播放」

### 页面导航

- 用户在当前页面跳转 → 浮层随页面卸载自动销毁
- 这是预期行为

### 全屏

- iframe 标记 `allowfullscreen`
- 视频全屏时占据整个屏幕，浮层在后面
- 退出全屏回到浮层正常播放

### 内存管理

- 关闭浮层时移除所有事件监听器
- Shadow DOM 容器从页面移除
- 无持久化浮层状态

## 边界情况

- **多标签页**：每个标签页独立 content script 实例，浮层互不影响
- **重复 pinPlay**：同一标签页收到多次 pinPlay → 更新已有浮层的 iframe src 和标题，不重复创建
- **content script 未注入**：popup 发送消息失败时，先用 `chrome.scripting.executeScript` 注入 content script，再发送消息
