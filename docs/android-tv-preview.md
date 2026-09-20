# Animeko Android TV 测试版

本版本提供独立的遥控器界面，复用 Animeko 的番剧、账号、资源订阅和播放服务。适用 Android 8.1（API 27）及以上、支持安装 APK 的 ARM Android 电视或盒子。Android 8.0（API 26）不在安装范围内。

## 安装

交付文件位于 `output/android-tv/`：

| 文件 | 适用设备 |
| --- | --- |
| `Animeko-TV-preview-20260920-universal.apk` | 首选，包含 ARM64 和 ARM32，系统自动选择 |
| `Animeko-TV-preview-20260920-arm64-v8a.apk` | 已确认使用 64 位 Android 系统的电视 |
| `Animeko-TV-preview-20260920-armeabi-v7a.apk` | 使用 32 位 Android 系统的电视 |

电视芯片支持 64 位不代表电视系统为 64 位；不确定时使用通用包。三种 APK 是同一个应用，选择一个安装即可。

1. 将通用 APK 复制到 U 盘，连接电视。
2. 在电视文件管理器打开 APK，按系统提示允许该文件管理器安装应用。
3. 安装后，在电视应用列表打开 **Animeko TV Preview**。首次启动需要联网加载番剧和默认资源订阅，部分资源解析可能需要等待。
4. 先测试搜索、选集、播放和返回；登录不是浏览和播放的前提。

已经配置 ADB 的设备，也可使用：

```sh
adb install -r Animeko-TV-preview-20260920-universal.apk
adb shell am start -n me.him188.ani.tvpreview/me.him188.ani.android.activity.MainActivity
```

应用包名固定为 `me.him188.ani.tvpreview`，可与原版共存，账号状态、设置和本机记录独立。后续同包名、同签名安装包可覆盖更新并保留数据。版本为 `4.9.0-dev`，版本号 `50406`。当前交付为 debug 测试包。

## 遥控器操作

| 场景 | 操作 |
| --- | --- |
| 页面导航 | 方向键移动焦点，确认键选择，返回键返回 |
| 搜索、邮箱、订阅输入 | 移动到输入框后按确认打开电视输入法；返回关闭键盘，方向键继续导航 |
| 播放器菜单隐藏时 | 确认键打开控制菜单；在菜单中选择播放或暂停 |
| 调整播放进度 | 菜单隐藏时按左右进入预览，短按每次 10 秒、长按加速；确认才真正跳转，返回取消 |
| 播放器功能 | 菜单提供进度、选集、下一集、资源、字幕、音轨、倍速、弹幕和重试 |
| 播放器返回 | 先关闭子菜单，再隐藏控制层，再退出播放页 |
| 媒体键 | 系统播放、暂停、快进、快退和下一集由媒体会话处理 |
| Home | 返回电视桌面后暂停，重新进入保持暂停并显示控制菜单 |

设置包括订阅网址和更新、数据源启停、播放选项、视频缓存、账号和界面模式。标准 Android TV 自动进入电视界面；部分厂商电视未声明标准电视特征时可选择电视模式。

## 已验证范围

实际运行环境为 Android TV API 34（Android 14）ARM64 模拟器，1920×1080、320 dpi。安装及覆盖更新成功；验证了遥控器导航、电视输入法、真实联网搜索、详情选集、网页资源解析、H.264 视频实际解码与弹幕、媒体暂停、进度预览与提交、Home 暂停及返回保持暂停、详情返回搜索时恢复焦点、本机观看历史与续播定位恢复至 6:25、设置与邮箱输入界面。测试截图和媒体会话状态保存在交付目录的 `evidence/`。

自动化测试共 45 项：14 项 TV 单元测试、4 项设备模式测试、27 项 Android TV 仪器测试。覆盖按键状态机、焦点恢复、分页、内容保护、输入框导航及真实 MediaController 与播放器桥接。Android 的 `assertScreenshot` 帮助函数当前不执行截图断言；视觉证据来自实际模拟器截图检查。

已知实测问题：某网页资源在重新加载并恢复到 6:25 后持续缓冲，尚未确认原因；定位恢复成功不等于该位置已经连续解码。首次连续播放、暂停及进度保存已验证。遇到类似情况可在播放菜单尝试其他资源，并记录所选来源供后续排查。

以下项目需要在目标电视继续验证：

- ARM32 系统、Android 8.1 老系统、厂商固件、中文输入法及遥控器差异。
- 电视硬件解码、HEVC/HDR、多音轨、外部字幕、蓝牙音频和长时间播放。模拟器以无音频输出模式运行，未验证听感。
- 真实邮箱验证码登录、登录后收藏同步和会话过期流程。当前已接入现有服务，但没有使用用户账号完成登录验收。
- BT 持续下载及低存储场景、低内存回收、发布版性能。当前未做全仓 `check` 或手机/桌面全量回归。

首期登录入口为邮箱验证码。二维码授权入口未开放。需要交互式网页验证的资源会提示换源；资源可用性受各来源服务和电视网络影响。

## 电视测试反馈

反馈时提供电视品牌型号、Android 版本、是否能安装和打开、出问题的按键或页面；播放问题附番剧、集数、所选资源及大致播放进度。安装失败可附系统提示。具备 ADB 时可读取：

```sh
adb shell getprop ro.build.version.release
adb shell getprop ro.product.cpu.abilist
adb logcat -b crash -d
```

## 维护与构建

模块依赖为 `app/android → app/shared/ui-tv → app/shared`。`ui-tv` 负责 TV 路由、焦点、输入框和播放控制；共享层继续负责数据与播放会话。Android 的 `TvMediaSessionBridge` 将现有 Mediamp 播放器接到系统 MediaSession，不创建第二个播放器。

使用 JBR 21、项目 Gradle 9.3.1、Android SDK 37。在 `local.properties` 中配置：

```properties
sdk.dir=/absolute/path/to/android-sdk
ani.android.debug.applicationIdSuffix=.tvpreview
ani.android.debug.appName=Animeko TV Preview
ani.android.abis=arm64-v8a,armeabi-v7a
```

```sh
./gradlew :app:android:assembleDefaultDebug \
  :app:shared:ui-tv:testDebugUnitTest \
  :app:shared:ui-tv:connectedDebugAndroidTest \
  --no-configuration-cache --parallel --max-workers=6
./gradlew :app:shared:app-platform:testAndroidHostTest \
  --tests 'me.him188.ani.app.platform.DeviceUiModeTest' \
  --no-configuration-cache
```

仪器测试需要正在运行的兼容 ARM Android 模拟器或设备。APK 输出为 `app/android/build/outputs/apk/default/debug/android-default-*-debug.apk`。本机构建工具及缓存保存在原仓库的 `.tv-build/`，未纳入版本控制。

交付包的 SHA-256 位于 `output/android-tv/SHA256SUMS`。签名证书 SHA-256 为 `fad6b4417e67a8742a332d1e15ce2dc79a2f33b45b3c853058380cf08da94d06`。后续构建需继续使用同一个本机 debug keystore；更换机器生成的新签名无法直接覆盖安装。签名私钥不随交付目录分发。
