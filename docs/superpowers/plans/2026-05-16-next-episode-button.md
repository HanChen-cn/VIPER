# 下一集按钮 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在新标签页播放器中新增「下一集」按钮，空闲时自动获取剧集列表，换源加载完毕后显示。

**Architecture:** 播放器 iframe load 后，先加载当前集换源（已有），再异步请求剧集列表（新增），定位下一集并构造 playUrl，显示按钮。点击切换后重复此流程。

**Tech Stack:** 原生 JS，Chrome Extension APIs（sendMessage, storage.session），无依赖。

---

### Task 1: 新增 `getEpisodeList()` 方法

**Files:**
- Modify: `lib/searcher.js:131` — 在文件末尾新增方法

- [ ] **Step 1: 在 `lib/searcher.js` 末尾添加 `getEpisodeList` 方法**

在 `getAltSources` 函数之后追加：

```js
/**
 * 获取剧集列表（轻量搜索，只返回匹配剧名的剧集列表）
 */
export async function getEpisodeList(showName) {
  if (!showName) return [];

  const enabledSources = API_SOURCES.filter(s => s.enabled);

  const searchResults = await Promise.allSettled(
    enabledSources.map(source => fetchSource(source, showName))
  );

  const allResults = [];
  for (const result of searchResults) {
    if (result.status === 'fulfilled' && result.value.length > 0) {
      allResults.push(result.value);
    }
  }

  if (allResults.length === 0) return [];

  const merged = mergeResults(allResults);
  const show = merged.find(item => item.name.trim() === showName.trim());
  if (!show) return [];

  const bestApi = pickBestApi(rankedApis);

  return deduplicateEpisodes(show.episodes).map(ep => {
    if (needsParsing(ep.url)) {
      return { name: ep.name, playUrl: bestApi.url + encodeURIComponent(ep.url) };
    }
    return { name: ep.name, playUrl: ep.url };
  });
}
```

- [ ] **Step 2: 验证语法无误**

在浏览器扩展管理页重新加载扩展，打开任意页面确认 service-worker 无报错（`chrome://extensions/` → 检查错误）。

- [ ] **Step 3: Commit**

```bash
git add lib/searcher.js
git commit -m "feat: 新增 getEpisodeList 方法获取剧集列表"
```

---

### Task 2: 新增 service-worker 消息处理

**Files:**
- Modify: `background/service-worker.js:1` — import 新方法
- Modify: `background/service-worker.js:69-74` — 新增消息类型

- [ ] **Step 1: 更新 import 语句**

将第 1 行：
```js
import { search, validateEpisodes, setWarmupResult, getAltSources } from '../lib/searcher.js';
```
改为：
```js
import { search, validateEpisodes, setWarmupResult, getAltSources, getEpisodeList } from '../lib/searcher.js';
```

- [ ] **Step 2: 在 `getAltSources` 消息处理块之后添加新消息处理**

在第 74 行（`getAltSources` 处理块的 `}`）之后追加：

```js
  if (message.type === 'getEpisodeList') {
    getEpisodeList(message.showName)
      .then(episodes => sendResponse({ success: true, data: episodes }))
      .catch(err => sendResponse({ success: false, error: err.message }));
    return true;
  }
```

- [ ] **Step 3: 验证**

重新加载扩展，确认 service-worker 无报错。

- [ ] **Step 4: Commit**

```bash
git add background/service-worker.js
git commit -m "feat: 新增 getEpisodeList 消息处理"
```

---

### Task 3: 添加下一集按钮 HTML

**Files:**
- Modify: `player/player.html:14-15` — 在 source-panel 内添加按钮

- [ ] **Step 1: 在 `toggleBtn` 之前添加下一集按钮**

将第 14-15 行：
```html
  <div id="sourcePanel" class="source-panel">
    <button id="toggleBtn" class="toggle-btn" title="换源">换源 ▼</button>
```
改为：
```html
  <div id="sourcePanel" class="source-panel">
    <button id="nextEpisodeBtn" class="next-episode-btn hidden">下一集 ▶</button>
    <button id="toggleBtn" class="toggle-btn" title="换源">换源 ▼</button>
```

- [ ] **Step 2: Commit**

```bash
git add player/player.html
git commit -m "feat: 添加下一集按钮 HTML"
```

---

### Task 4: 添加下一集按钮样式

**Files:**
- Modify: `player/player.css:229` — 末尾追加样式

- [ ] **Step 1: 在 `player.css` 末尾追加按钮样式**

```css
.next-episode-btn {
  padding: 10px 20px;
  border: none;
  border-radius: 8px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(102, 126, 234, 0.4);
  transition: all 0.2s;
  opacity: 0.85;
  margin-right: 8px;
}

.next-episode-btn:hover {
  opacity: 1;
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.6);
}

.next-episode-btn.hidden {
  display: none;
}
```

- [ ] **Step 2: Commit**

```bash
git add player/player.css
git commit -m "feat: 添加下一集按钮样式"
```

---

### Task 5: 实现下一集获取与切换逻辑

**Files:**
- Modify: `player/player.js` — 新增下一集相关函数和事件绑定

- [ ] **Step 1: 在 `player.js` 顶部添加 DOM 引用**

在第 7 行（`const topTitle = ...`）之后追加：

```js
const nextEpisodeBtn = document.getElementById('nextEpisodeBtn');
```

- [ ] **Step 2: 在 `loadAltSources()` 调用之后添加下一集加载**

将第 28-30 行：
```js
  playerFrame.addEventListener('load', () => {
    loadAltSources();
  }, { once: true });
```
改为：
```js
  playerFrame.addEventListener('load', () => {
    loadAltSources();
    loadNextEpisode();
  }, { once: true });
```

- [ ] **Step 3: 在 `loadAltSources` 函数之后添加 `loadNextEpisode` 函数**

在第 56 行（`loadAltSources` 函数的 `}`）之后追加：

```js
function loadNextEpisode() {
  if (!currentData || !currentData.name) return;

  setTimeout(() => {
    chrome.runtime.sendMessage({
      type: 'getEpisodeList',
      showName: currentData.name
    }, (res) => {
      if (!res || !res.success || !res.data || res.data.length === 0) return;

      const episodes = res.data;
      const currentIndex = episodes.findIndex(ep => ep.name === currentData.episode);
      if (currentIndex < 0 || currentIndex >= episodes.length - 1) return;

      const nextEp = episodes[currentIndex + 1];
      currentData.nextPlayUrl = nextEp.playUrl;
      currentData.nextEpisode = nextEp.name;

      nextEpisodeBtn.classList.remove('hidden');
    });
  }, 500);
}
```

- [ ] **Step 4: 在文件末尾添加下一集按钮点击事件**

在文件最后追加：

```js
nextEpisodeBtn.addEventListener('click', () => {
  if (!currentData || !currentData.nextPlayUrl) return;

  currentData.url = currentData.nextPlayUrl;
  currentData.episode = currentData.nextEpisode;
  delete currentData.nextPlayUrl;
  delete currentData.nextEpisode;

  chrome.storage.session.set({ playerData: currentData });

  topTitle.textContent = `${currentData.name} - ${currentData.episode}`;
  showInfo.textContent = `${currentData.name} · ${currentData.episode}`;
  document.title = `${currentData.name} - ${currentData.episode}`;

  nextEpisodeBtn.classList.add('hidden');
  playerFrame.src = currentData.url;

  playerFrame.addEventListener('load', () => {
    loadAltSources();
    loadNextEpisode();
  }, { once: true });
});
```

- [ ] **Step 5: 验证功能**

1. 重新加载扩展
2. 搜索一部多集的剧（如"海贼王"）
3. 点击某一集播放
4. 等待换源加载完毕后，确认「下一集」按钮出现
5. 点击按钮，确认切换到下一集并自动加载换源
6. 播放到最后一集，确认按钮隐藏

- [ ] **Step 6: Commit**

```bash
git add player/player.js
git commit -m "feat: 实现下一集获取与切换逻辑"
```

---

### Task 6: 整体验证与最终提交

- [ ] **Step 1: 完整流程测试**

1. 重新加载扩展
2. 搜索一部剧，点击播放
3. 确认：换源正常加载 → 下一集按钮出现
4. 点击下一集 → 确认切换成功 → 换源重新加载 → 新的下一集按钮出现
5. 播放到最后一集 → 按钮消失
6. 测试浮动播放器不受影响

- [ ] **Step 2: 如有问题，修复后提交**

```bash
git add -A
git commit -m "fix: 修复下一集功能的边界问题"
```
