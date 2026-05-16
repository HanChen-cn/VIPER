# 下一集按钮 — 设计文档

## 概述

在新标签页播放器中新增「下一集」按钮。播放器在空闲时自动获取完整剧集列表，定位当前集索引，预构造下一集播放 URL。换源加载完毕后显示按钮，点击直接切换播放。

## 范围

- 仅新标签页播放器（`player/`）
- 不涉及浮动播放器（`content/`）
- 不改动 popup 或 service-worker 的现有数据传递逻辑

## 数据流

```
播放器打开
  ↓
读取 playerData {url, name, episode}
  ↓
加载当前集换源列表（已有逻辑 loadAltSources）
  ↓
【新增】空闲时：getEpisodeList(showName)
  ↓
CMS 搜索 → 合并去重 → 返回剧集列表 [{name, playUrl, url}, ...]
  ↓
定位当前集索引（按 episode name 匹配）
  ↓
确定下一集（index + 1）
  ↓
用最佳解析接口拼出下一集 playUrl
  ↓
显示「下一集」按钮
```

点击「下一集」后：

```
更新 currentData（url, episode, episodeIndex）
  ↓
切换 iframe src
  ↓
隐藏按钮
  ↓
iframe load → loadAltSources（已有逻辑）
  ↓
空闲时重新 getEpisodeList → 获取新一集的下一集 → 重新显示按钮
```

## 新增接口

### lib/searcher.js

```js
export async function getEpisodeList(showName)
```

- 并发搜索所有启用的 CMS 源（复用 `fetchSource`）
- 合并去重（复用 `mergeResults` + `deduplicateEpisodes`）
- 从结果中找到匹配 `showName` 的剧集
- 返回其 episodes 数组 `[{name, playUrl, url}]`
- 未找到返回空数组

### service-worker.js

新增消息类型 `getEpisodeList`：

```js
if (message.type === 'getEpisodeList') {
  getEpisodeList(message.showName).then(sendResponse);
  return true;
}
```

## UI 变更

### player.html

在 `source-panel` 中、`toggleBtn` 之前添加按钮：

```html
<button id="nextEpisodeBtn" class="next-episode-btn hidden">下一集 ▶</button>
```

### player.css

新增样式，与换源按钮风格一致：

```css
.next-episode-btn {
  background: rgba(102, 126, 234, 0.15);
  border: 1px solid #667eea;
  color: #667eea;
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
}
.next-episode-btn:hover {
  background: rgba(102, 126, 234, 0.3);
}
.next-episode-btn.hidden {
  display: none;
}
```

### player.js 逻辑变更

新增函数：

1. `loadNextEpisode()` — 空闲时获取剧集列表，定位下一集，显示按钮
2. `findEpisodeIndex(episodes, episodeName)` — 按 name 匹配当前集索引
3. `buildNextPlayUrl(nextEpisode)` — 用最佳解析接口拼 playUrl

按钮点击事件：

- 更新 `currentData.url`、`currentData.episode`
- 更新标题显示
- 切换 `playerFrame.src`
- 保存到 `chrome.storage.session`
- 隐藏按钮
- iframe load 后重新 `loadAltSources()` + 空闲时重新 `loadNextEpisode()`

## 边界情况

- **最后一集**：按钮保持隐藏
- **剧集列表获取失败**：按钮保持隐藏，不影响正常播放
- **集名匹配失败**：找不到当前集在列表中的位置，按钮保持隐藏
- **URL 需解析**：用最佳解析接口拼接（复用 `pickBestApi`）
- **URL 不需解析**：直接使用原始 URL（如已是直链）

## 涉及文件

| 文件 | 改动 |
|------|------|
| `lib/searcher.js` | 新增 `getEpisodeList()` |
| `background/service-worker.js` | 新增 `getEpisodeList` 消息处理 |
| `player/player.js` | 新增下一集获取/显示/切换逻辑 |
| `player/player.html` | 新增按钮元素 |
| `player/player.css` | 新增按钮样式 |
