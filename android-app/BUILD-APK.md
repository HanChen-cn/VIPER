# Android APK 打包指南（当前项目专用）

## 0. 前置条件

- 使用 Android Studio（建议最新稳定版）
- 已安装 Android SDK（`compileSdk = 35`）
- JDK `17`

## 1. 打 Debug APK（最快）

### 方式 A：Android Studio 图形界面（推荐）

1. 打开目录：`android-app`
2. 等待项目同步完成
3. 菜单：`Build -> Build Bundle(s) / APK(s) -> Build APK(s)`
4. 构建完成后点击右下角提示中的 `locate`

输出文件：

- `app/build/outputs/apk/debug/app-debug.apk`

### 方式 B：CLI（需要先有 Gradle Wrapper）

```powershell
cd android-app
.\gradlew.bat :app:assembleDebug
```

## 2. 打 Release APK（可分发）

### Android Studio 图形界面

1. 菜单：`Build -> Generate Signed Bundle / APK...`
2. 选择：`APK`
3. 选择或创建 `keystore`
4. 选择构建类型：`release`
5. 完成后打开输出目录

输出文件：

- `app/build/outputs/apk/release/app-release.apk`

## 3. 生成 Gradle Wrapper（你当前仓库还没有）

当前 `android-app` 目录没有 `gradlew`，建议在 Android Studio 里生成：

1. 打开 `Gradle` 工具窗口
2. 找到任务：`Tasks -> build setup -> wrapper`
3. 执行该任务
4. 执行后应出现：
   - `android-app/gradlew`
   - `android-app/gradlew.bat`
   - `android-app/gradle/wrapper/*`

随后可用 CLI 命令构建：

```powershell
cd android-app
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

## 4. 安装到 Android 手机

### 方式 A：ADB

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### 方式 B：手动安装

1. 把 APK 发送到手机
2. 开启“允许安装未知应用”
3. 点击 APK 安装

## 5. 常见问题

### 5.1 `gradlew.bat` 不存在

- 先按第 3 节在 Android Studio 里执行 `wrapper` 任务

### 5.2 构建失败提示 SDK/JDK 版本不匹配

- 确认 Android Studio 的 JDK 为 `17`
- 确认已安装 `Android API 35`

### 5.3 安装时报“应用未安装”

- 尝试先卸载旧包再装
- 检查签名是否一致（debug 包与 release 包签名不同）

## 6. 一句话流程（最快）

`Open android-app -> Build APK(s) -> Locate -> 安装`
