# 剧集列表 + 复制链接 + 自动隐藏 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在新标签页播放器中新增剧集列表下拉面板、复制链接按钮、操作栏自动隐藏。

**Architecture:** 提取公共 `switchToEpisode` 函数供下一集和剧集列表共用；`loadNextEpisode` 获取到的 episodes 缓存到变量供剧集列表渲染；新增自动隐藏逻辑控制 source-panel 显隐。

**Tech Stack:** 原生 JS，Chrome Extension APIs（clipboard, storage.session），无依赖。

---

### Task 1: 提取 switchToEpisode 公共函数 + 缓存 episodes

**Files:**
- Modify: `player/player.js`

- [ ] **Step 1: 添加 cachedEpisodes 变量**

在第 11 行（`let nextEpisodeLoadHandler = null;`）之后追加：

```js
let cachedEpisodes = null;
```

- [ ] **Step 2: 修改 loadNextEpisode 缓存 episodes**

将 `loadNextEpisode` 函数（第 61-83 行）改为：

```js
function loadNextEpisode() {
  if (!currentData || !currentData.name) return;

  // 闲时请求：延迟 500ms，不阻塞播放
  setTimeout(() => {
    chrome.runtime.sendMessage({
      type: 'getEpisodeList',
      showName: currentData.name
    }, (res) => {
      if (!res || !res.success || !res.data || res.data.length === 0) return;

      cachedEpisodes = res.data;
      const currentIndex = cachedEpisodes.findIndex(ep => ep.name === currentData.episode);
      if (currentIndex < 0 || currentIndex >= cachedEpisodes.length - 1) return;

      const nextEp = cachedEpisodes[currentIndex + 1];
      currentData.nextPlayUrl = nextEp.playUrl;
      currentData.nextEpisode = nextEp.name;

      nextEpisodeBtn.classList.remove('hidden');
    });
  }, 500);
}
```

- [ ] **Step 3: 提取 switchToEpisode 函数**

在 `loadNextEpisode` 函数之后添加：

```js
function switchToEpisode(url, episodeName) {
  currentData.url = url;
  currentData.episode = episodeName;
  delete currentData.nextPlayUrl;
  delete currentData.nextEpisode;
  cachedEpisodes = null;

  chrome.storage.session.set({ playerData: currentData });

  topTitle.textContent = `${currentData.name} - ${currentData.episode}`;
  showInfo.textContent = `${currentData.name} · ${currentData.episode}`;
  document.title = `${currentData.name} - ${currentData.episode}`;

  nextEpisodeBtn.classList.add('hidden');

  if (nextEpisodeLoadHandler) {
    playerFrame.removeEventListener('load', nextEpisodeLoadHandler);
  }
  nextEpisodeLoadHandler = () => {
    loadAltSources();
    loadNextEpisode();
  };
  playerFrame.src = currentData.url;
  playerFrame.addEventListener('load', nextEpisodeLoadHandler, { once: true });
}
```

- [ ] **Step 4: 重写下一集点击事件使用 switchToEpisode**

将下一集点击事件（第 157-182 行）替换为：

```js
nextEpisodeBtn.addEventListener('click', () => {
  if (!currentData || !currentData.nextPlayUrl) return;
  switchToEpisode(currentData.nextPlayUrl, currentData.nextEpisode);
});
```

- [ ] **Step 5: 验证**

重新加载扩展，搜索一部剧播放，确认下一集功能仍然正常。

- [ ] **Step 6: Commit**

```bash
git add player/player.js
git commit -m "refactor: 提取 switchToEpisode 公共函数，缓存 episodes"
```

---

### Task 2: 添加剧集列表 HTML 和 CSS

**Files:**
- Modify: `player/player.html` — 新增剧集按钮和下拉面板
- Modify: `player/player.css` — 新增剧集列表样式

- [ ] **Step 1: 在 player.html 的 source-panel 中添加剧集按钮和面板**

将第 14-24 行：
```html
  <div id="sourcePanel" class="source-panel">
    <button id="nextEpisodeBtn" class="next-episode-btn hidden">下一集 ▶</button>
    <button id="toggleBtn" class="toggle-btn" title="换源">换源 ▼</button>
    <div id="sourceDropdown" class="source-dropdown hidden">
      <div class="dropdown-header">
        <span id="showInfo" class="show-info"></span>
        <button id="closeDropdown" class="close-dropdown">✕</button>
      </div>
      <div id="sourceList" class="source-list"></div>
    </div>
  </div>
```
改为：
```html
  <div id="sourcePanel" class="source-panel">
    <button id="nextEpisodeBtn" class="next-episode-btn hidden">下一集 ▶</button>
    <button id="episodeListBtn" class="episode-list-btn">剧集 ▼</button>
    <button id="copyUrlBtn" class="copy-url-btn" title="复制播放地址">复制链接</button>
    <button id="toggleBtn" class="toggle-btn" title="换源">换源 ▼</button>
    <div id="sourceDropdown" class="source-dropdown hidden">
      <div class="dropdown-header">
        <span id="showInfo" class="show-info"></span>
        <button id="closeDropdown" class="close-dropdown">✕</button>
      </div>
      <div id="sourceList" class="source-list"></div>
    </div>
    <div id="episodeDropdown" class="episode-dropdown hidden">
      <div class="dropdown-header">
        <span class="episode-info">全部剧集</span>
        <button id="closeEpisodeDropdown" class="close-dropdown">✕</button>
      </div>
      <div id="episodeList" class="episode-list"></div>
    </div>
  </div>
```

- [ ] **Step 2: 在 player.css 末尾追加剧集列表和复制按钮样式**

```css
.episode-list-btn {
  padding: 10px 16px;
  border: 1px solid #667eea;
  border-radius: 8px;
  background: transparent;
  color: #667eea;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
  opacity: 0.85;
  margin-right: 8px;
}

.episode-list-btn:hover {
  background: rgba(102, 126, 234, 0.15);
  opacity: 1;
}

.episode-dropdown {
  position: absolute;
  top: 50px;
  right: 0;
  width: 280px;
  max-height: 400px;
  background: #1a1a2e;
  border: 1px solid #667eea;
  border-radius: 10px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.6);
  overflow-y: overlay;
}

.episode-dropdown::-webkit-scrollbar {
  width: 6px;
}

.episode-dropdown::-webkit-scrollbar-track {
  background: #16213e;
}

.episode-dropdown::-webkit-scrollbar-thumb {
  background: #3a3a5a;
  border-radius: 4px;
}

.episode-dropdown::-webkit-scrollbar-thumb:hover {
  background: #667eea;
}

.episode-dropdown.hidden {
  display: none;
}

.episode-list {
  padding: 8px;
}

.episode-item {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 8px 14px;
  margin: 3px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s;
  color: #ccc;
  font-size: 13px;
  border: 1px solid #2a2a4a;
}

.episode-item:hover {
  background: rgba(102, 126, 234, 0.15);
  color: #fff;
  border-color: #667eea;
}

.episode-item.active {
  background: rgba(102, 126, 234, 0.25);
  color: #667eea;
  font-weight: 500;
  border-color: #667eea;
}

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
  margin-right: 8px;
}

.copy-url-btn:hover {
  background: rgba(102, 126, 234, 0.15);
  opacity: 1;
}

.source-panel {
  transition: opacity 0.3s;
}
```

注意：`.source-panel` 的 `transition` 属性已存在（第 53-58 行），需要合并到现有规则中，而不是重复定义。

- [ ] **Step 3: Commit**

```bash
git add player/player.html player/player.css
git commit -m "feat: 添加剧集列表和复制按钮 HTML/CSS"
```

---

### Task 3: 实现剧集列表逻辑

**Files:**
- Modify: `player/player.js`

- [ ] **Step 1: 添加 DOM 引用**

在第 8 行（`const nextEpisodeBtn = ...`）之后追加：

```js
const episodeListBtn = document.getElementById('episodeListBtn');
const episodeDropdown = document.getElementById('episodeDropdown');
const closeEpisodeDropdown = document.getElementById('closeEpisodeDropdown');
const episodeList = document.getElementById('episodeList');
```

- [ ] **Step 2: 添加 renderEpisodeList 函数**

在 `switchToEpisode` 函数之后添加：

```js
function renderEpisodeList() {
  episodeList.innerHTML = '';
  if (!cachedEpisodes || cachedEpisodes.length === 0) {
    episodeList.innerHTML = '<div class="source-empty">暂无剧集信息</div>';
    return;
  }

  for (const ep of cachedEpisodes) {
    const item = document.createElement('div');
    item.className = 'episode-item' + (ep.name === currentData.episode ? ' active' : '');
    item.textContent = ep.name;
    item.addEventListener('click', () => {
      episodeDropdown.classList.add('hidden');
      switchToEpisode(ep.playUrl, ep.name);
    });
    episodeList.appendChild(item);
  }
}
```

- [ ] **Step 3: 添加剧集按钮事件**

在下一集点击事件之后添加：

```js
episodeListBtn.addEventListener('click', (e) => {
  e.stopPropagation();
  if (!episodeDropdown.classList.contains('hidden')) {
    episodeDropdown.classList.add('hidden');
    return;
  }
  renderEpisodeList();
  episodeDropdown.classList.remove('hidden');
});

closeEpisodeDropdown.addEventListener('click', () => {
  episodeDropdown.classList.add('hidden');
});
```

- [ ] **Step 4: 更新 document click 事件关闭所有下拉面板**

将现有的 document click 事件（第 151-155 行）：
```js
document.addEventListener('click', (e) => {
  if (!e.target.closest('.source-panel')) {
    sourceDropdown.classList.add('hidden');
  }
});
```
改为：
```js
document.addEventListener('click', (e) => {
  if (!e.target.closest('.source-panel')) {
    sourceDropdown.classList.add('hidden');
    episodeDropdown.classList.add('hidden');
  }
});
```

- [ ] **Step 5: 验证**

重新加载扩展，搜索一部多集的剧播放，点击「剧集」按钮确认列表显示，点击某一集确认切换正常。

- [ ] **Step 6: Commit**

```bash
git add player/player.js
git commit -m "feat: 实现剧集列表渲染和切换逻辑"
```

---

### Task 4: 实现复制链接逻辑

**Files:**
- Modify: `player/player.js`

- [ ] **Step 1: 添加 DOM 引用**

在剧集列表 DOM 引用之后追加：

```js
const copyUrlBtn = document.getElementById('copyUrlBtn');
```

- [ ] **Step 2: 添加复制按钮事件**

在剧集按钮事件之后添加：

```js
copyUrlBtn.addEventListener('click', () => {
  if (!currentData || !currentData.url) return;

  navigator.clipboard.writeText(currentData.url).then(() => {
    copyUrlBtn.textContent = '已复制 ✓';
    setTimeout(() => { copyUrlBtn.textContent = '复制链接'; }, 2000);
  }).catch(() => {
    copyUrlBtn.textContent = '复制失败';
    setTimeout(() => { copyUrlBtn.textContent = '复制链接'; }, 2000);
  });
});
```

- [ ] **Step 3: Commit**

```bash
git add player/player.js
git commit -m "feat: 实现复制播放地址功能"
```

---

### Task 5: 实现自动隐藏逻辑

**Files:**
- Modify: `player/player.js`

- [ ] **Step 1: 添加自动隐藏函数**

在文件末尾追加：

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

setupAutoHide();
```

- [ ] **Step 2: 添加 sourcePanel DOM 引用**

在第 1 行之前追加：

```js
const sourcePanel = document.getElementById('sourcePanel');
```

- [ ] **Step 3: 验证**

重新加载扩展，播放视频，等待 3 秒不移动鼠标确认操作栏隐藏，移动鼠标确认恢复。

- [ ] **Step 4: Commit**

```bash
git add player/player.js
git commit -m "feat: 实现操作栏自动隐藏（3秒无操作）"
```

---

### Task 6: 整体验证与最终提交

- [ ] **Step 1: 完整流程测试**

1. 重新加载扩展
2. 搜索一部多集的剧，点击播放
3. 确认：换源正常 → 下一集按钮出现 → 剧集按钮可点击
4. 点击「剧集」→ 确认列表显示，当前集高亮
5. 点击另一集 → 确认切换播放 → 换源重新加载 → 剧集列表更新
6. 点击「复制链接」→ 确认按钮变「已复制 ✓」→ 2 秒后恢复
7. 等待 3 秒不移动鼠标 → 确认操作栏隐藏
8. 移动鼠标 → 确认操作栏恢复
9. 展开下拉面板时等待 → 确认不触发隐藏

- [ ] **Step 2: 如有问题，修复后提交**

```bash
git add -A
git commit -m "fix: 修复剧集列表/复制/自动隐藏的边界问题"
```
