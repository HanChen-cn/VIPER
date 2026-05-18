# VIP 视频智能搜索

Chrome/Edge 浏览器扩展 — 输入电视剧/动漫名称，自动搜索全网可播放资源，一键播放。

仓库同时包含 `android-app/` 原生 Android 客户端（搜索、播放、历史、收藏），已对齐 Web 端核心能力：预热测速选最快可播源、下一集、剧集列表、换源。
Android 版本支持 `android-v*` tag 自动构建，构建成功后会把 `APK` 同时上传到 `GitHub Release` 与 `Actions Artifacts`。

妈妈再也不用充会员、到处找资源了。

告别满屏广告。直接搜索剧名，看就完事！

## 功能

- **智能搜索** — 输入名称，并发搜索多个影视资源站
- **35 个解析接口** — popup 打开时自动预热检测，选最快的用
- **换源机制** — 播放失败时一键换源，不用重新搜索
- **搜索历史** — 自动记录搜索关键词，点击快速重搜
- **播放历史** — 记录看过的剧集，一键继续观看
- **收藏夹** — 收藏常看的剧，随时回来搜索
- **新 tab 播放** — 点击剧集直接在新标签页播放
- **浮动播放器** — 可在当前页面固定播放，拖拽/缩放/最小化，支持换源
- **下一集** — 播放页自动检测下一集，一键切换
- **剧集列表** — 播放页查看全部剧集，点击任意集数播放
- **复制链接** — 一键复制当前集播放地址

## 安装

### 方式一：从 Release 下载（推荐）

1. 前往 [Releases](../../releases) 页面，下载最新版本的 `VIPER-vX.X.X.zip`
2. 解压 zip 文件，得到 `VIPER-vX.X.X` 文件夹
3. 打开 `chrome://extensions/`（Edge 浏览器用 `edge://extensions/`）
4. 开启"开发者模式"
5. 点击"加载已解压的扩展程序"，选择解压后的 `VIPER-vX.X.X` 文件夹

### 方式二：克隆源码

1. `git clone https://github.com/HanChen-cn/VIPER.git`
2. 打开 `chrome://extensions/`（Edge 浏览器用 `edge://extensions/`）
3. 开启"开发者模式"
4. 点击"加载已解压的扩展程序"，选择项目中的 `extension` 文件夹

> 详细图文教程请打开 `安装教程.html`

## 使用

1. 点击浏览器工具栏中的紫色「6」图标
2. 等待顶部状态显示接口就绪
3. 输入电视剧/动漫名称，点搜索
4. 点击剧集按钮播放；看不了点「▼」换源
5. 播放页右上角：下一集、剧集列表、复制链接、换源

## 技术架构

```
popup (搜索/历史/收藏) ←→ service-worker (后台) ←→ CMS API + 解析接口
                                    ↕
                          chrome.storage.local (历史, 收藏)
                          chrome.storage.session (播放数据, 固定模式)
                                    ↕
content script (浮动播放器)  ←  注入到任意页面 (Shadow DOM 隔离)
player (新标签页播放器)      ←  从 session storage 读取播放数据
```

- Chrome Extension Manifest V3
- 原生 JavaScript / HTML / CSS，零依赖
- CMS 采集站标准 API（5 个资源站并发搜索）
- 35 个内置解析接口，预热检测 + 智能排序
- 浮动播放器：可拖拽、可缩放、可最小化，支持一键换源

### Android 端

- Kotlin + Jetpack Compose + Media3 ExoPlayer
- 预热测速：App 启动时并发 HEAD 测速所有解析接口，按延迟排序，搜索和播放均使用最快接口
- 播放页：竖屏 16:9 视口 + 沉浸式横屏全屏（右上角全屏按钮，避开视频控制栏）
- 播放页功能：下一集、剧集选择列表（FlowRow 网格）、换源（延迟加载，点击时才搜 CMS 备源）
- 搜索缓存：播放页剧集列表从搜索缓存秒取，免除重复 CMS 请求
- 换源对齐 Web 端：`mergeOnly` 保留同集多线路，`getEpisodeList` 完整填充 altUrls
- ExoPlayer 直接播放 `.m3u8` / `.mp4`；其余 URL 统一走 WebView（CSS 注入全屏 + 隐藏广告）
- WebView 错误回调仅主帧触发换源，子资源失败不误判
- 视频加载时显示转圈指示器替代纯黑屏
- 复制链接：播放页一键复制当前播放 URL 到剪贴板
- 播放历史：切集/下一集时自动记录，历史页可一键继续观看
- 自动检查 GitHub Release 更新（启动时），提示下载新版 APK
