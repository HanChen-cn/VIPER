# Android 安装排错清单（APK）

## 1. 提示“应用未安装”

常见原因：
- 手机已有同包名旧版本，且签名不同（`debug` 与 `release` 混装）
- APK 文件损坏

处理：
1. 卸载旧版本 App
2. 重新安装新 APK
3. 若仍失败，重新打包并校验文件大小是否异常

## 2. 提示“解析软件包时出现问题”

常见原因：
- APK 下载不完整
- 文件被中转平台改名或损坏

处理：
1. 重新传输 APK（优先用 USB / ADB）
2. 不要通过会重打包的渠道中转

## 3. 提示“与当前 Android 版本不兼容”

当前项目配置：
- `minSdk = 26`（Android 8.0）

处理：
1. 检查手机系统版本是否 `>= Android 8.0`
2. 老设备需降低 `minSdk` 后重新构建

## 4. 安装后闪退

常见原因：
- 网络权限或 WebView 环境异常
- 机型对播放器能力兼容性差

处理：
1. 确认网络可用
2. 升级系统 WebView（Google Play 商店）
3. 先用 `debug APK` 在 Android Studio 连接设备看 Logcat

## 5. ADB 安装失败

命令：

```powershell
adb install -r app-debug.apk
```

常见报错与处理：
- `device unauthorized`：手机上允许 USB 调试授权
- `device offline`：重插 USB，执行 `adb kill-server && adb start-server`
- `INSTALL_FAILED_VERSION_DOWNGRADE`：卸载旧包或提升 `versionCode`

## 6. 一次性自检（推荐）

安装失败时按顺序检查：
1. 包名是否一致  
2. 签名是否一致（debug / release）  
3. Android 版本是否满足 `minSdk`  
4. APK 是否完整  
5. 是否开启“允许安装未知应用”  
6. 是否能用 `adb install -r` 成功

---

如果你愿意，我下一步可以再给你一份“发布给他人安装”的 `release 签名与版本号策略`。
