# 剧集列表 + 复制链接 + 自动隐藏 — 设计文档

## 概述

在新标签页播放器中新增三个功能：
1. 剧集列表按钮 — 下拉面板展示所有集数，点击切换播放
2. 复制链接按钮 — 复制当前集播放地址到剪贴板
3. 操作按钮自动隐藏 — 鼠标静止 3 秒后自动隐藏操作栏，移动鼠标恢复

## 范围

- 仅新标签页播放器（`player/`）
- 不涉及浮动播放器（`content/`）
- 不改动 popup 或 service-worker

## 功能一：剧集列表

### UI 结构

在 `source-panel` 中新增「剧集」按钮和下拉面板，与换源面板风格一致：

```html
<button id="episodeListBtn" class="episode-list-btn">剧集 ▼</button>
<div id="episodeDropdown" class="episode-dropdown hidden">
  <div class="dropdown-header">
    <span class="episode-info">全部剧集</span>
    <button id="closeEpisodeDropdown" class="close-dropdown">✕</button>
  </div>
  <div id="episodeList" class="episode-list"></div>
</div>
```

### 数据流

```
播放器打开
  ↓
iframe load → loadAltSources() + loadNextEpisode()
  ↓
loadNextEpisode 内调用 getEpisodeList → 返回 episodes 数组
  ↓
缓存到变量 cachedEpisodes（供剧集列表和下一集共用）
  ↓
点击「剧集」按钮 → 用缓存渲染列表（无需重复请求）
```

### 缓存机制

- `loadNextEpisode()` 获取到 episodes 后，存入 `cachedEpisodes` 变量
- 点击「剧集」按钮时优先使用缓存
- 切换集数后清空缓存，下次 load 时重新获取
- 缓存为空时按钮显示 loading 状态

### 点击某一集

复用下一集的切换逻辑，提取公共函数 `switchToEpisode(ep)`：

```
switchToEpisode(ep)
  ↓
更新 currentData（url, episode）
  ↓
保存到 chrome.storage.session
  ↓
更新标题显示
  ↓
切换 iframe src
  ↓
iframe load → loadAltSources() + loadNextEpisode()（重新获取缓存）
```

### 列表样式

- 与换源下拉面板（`.source-dropdown`）完全一致的圆角、阴影、背景色
- 每个集数项与 `.source-item` 样式一致
- 当前集高亮 + 「当前」标记
- 列表可滚动（max-height 限制）

## 功能二：复制链接

### UI

在 source-panel 中新增按钮：

```html
<button id="copyUrlBtn" class="copy-url-btn" title="复制播放地址">复制链接</button>
```

### 逻辑

1. 点击 → `navigator.clipboard.writeText(currentData.url)`
2. 成功 → 按钮文字变「已复制 ✓」，2 秒后恢复「复制链接」
3. 失败 → 按钮文字变「复制失败」，2 秒后恢复

### 样式

与换源按钮风格一致，但用透明底 + 边框（稍低调）：

```css
.copy-url-btn {
  padding: 10px 16px;
  border: 1px solid #667eea;
  border-radius: 8px;
  background: transparent;
  color: #667eea;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
  opacity: 0.85;
}
.copy-url-btn:hover {
  background: rgba(102, 126, 234, 0.15);
  opacity: 1;
}
```

## 功能三：自动隐藏

### 行为

- 鼠标静止 3 秒 → source-panel 整体隐藏（`opacity: 0` + `pointer-events: none`）
- 鼠标移动 → 立即显示
- 当下拉面板（换源或剧集）展开时，不触发隐藏

### 实现

```js
let autoHideTimer = null;

function setupAutoHide() {
  document.addEventListener('mousemove', () => {
    sourcePanel.style.opacity = '1';
    sourcePanel.style.pointerEvents = 'auto';
    clearTimeout(autoHideTimer);
    if (!sourceDropdown.classList.contains('hidden') || !episodeDropdown.classList.contains('hidden')) return;
    autoHideTimer = setTimeout(() => {
      sourcePanel.style.opacity = '0';
      sourcePanel.style.pointerEvents = 'none';
    }, 3000);
  });
}
```

### CSS

```css
.source-panel {
  transition: opacity 0.3s;
}
```

## 涉及文件

| 文件 | 改动 |
|------|------|
| `player/player.html` | 新增剧集按钮、剧集下拉面板、复制按钮 |
| `player/player.css` | 新增剧集列表样式、复制按钮样式、自动隐藏过渡 |
| `player/player.js` | 剧集列表逻辑、复制逻辑、自动隐藏逻辑、switchToEpisode 提取 |
