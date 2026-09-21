# Animeko Android TV 测试版

本版本提供独立的遥控器界面，复用 Animeko 的番剧、账号、资源订阅和播放服务。适用 Android 8.1（API 27）及以上、支持安装 APK 的 ARM Android 电视或盒子。Android 8.0（API 26）不在安装范围内。

## 安装

交付文件位于 `output/android-tv/release-20260921/`，也可从 [GitHub Release](https://github.com/N3urda/animeko/releases/tag/android-tv-preview-20260921) 下载：

| 文件 | 适用设备 |
| --- | --- |
| `Animeko-TV-preview-20260921-universal.apk` | 首选，包含 ARM64 和 ARM32，系统自动选择 |
| `Animeko-TV-preview-20260921-arm64-v8a.apk` | 已确认使用 64 位 Android 系统的电视 |
| `Animeko-TV-preview-20260921-armeabi-v7a.apk` | 使用 32 位 Android 系统的电视 |

电视芯片支持 64 位不代表电视系统为 64 位；不确定时使用通用包。三种 APK 是同一个应用，选择一个安装即可。

1. 将通用 APK 复制到 U 盘，连接电视。
2. 在电视文件管理器打开 APK，按系统提示允许该文件管理器安装应用。
3. 安装后，在电视应用列表打开 **Animeko TV Preview**。首次启动需要联网加载番剧和默认资源订阅，部分资源解析可能需要等待。
4. 先测试搜索、选集、播放和返回；登录不是浏览和播放的前提。

已经配置 ADB 的设备，也可使用：

```sh
adb install -r Animeko-TV-preview-20260921-universal.apk
adb shell am start -n me.him188.ani.tvpreview/me.him188.ani.android.activity.MainActivity
```

应用包名固定为 `me.him188.ani.tvpreview`，可与原版共存，账号状态、设置和本机记录独立。后续同包名、同签名安装包可覆盖更新并保留数据。版本为 `4.9.0-dev`，版本号 `50406`。当前交付为 debug 测试包。

## 遥控器操作

| 场景 | 操作 |
| --- | --- |
| 页面导航 | 方向键移动焦点，确认键选择，返回键返回 |
| 推荐 | 首页选择「推荐」进入推荐区，左右浏览，确认打开详情；返回恢复原条目 |
| 搜索筛选 | 选择「筛选 · 类型 / 设定」；左侧选分类，右键进入条件，左键返回分类，底部应用或取消；类型、设定可多选，无需输入名称 |
| Bangumi 登录 | 首页「账号」→「Bangumi 登录」→获取二维码，用手机扫码登录并授权；账号设置也提供入口 |
| 搜索、邮箱、订阅输入 | 移动到输入框后按确认打开电视输入法；返回关闭键盘，方向键继续导航 |
| 播放器菜单隐藏时 | 确认键直接暂停 / 继续一次，播放结束时从头重播；上下键或菜单键打开控制菜单 |
| 调整播放进度 | 已知时长时，菜单隐藏按左右进入预览，短按每次 10 秒、长按加速；确认提交，返回取消。时长未知时仅打开菜单 |
| 播放器功能 | 常用栏提供进度、选集、下一集、换源；「更多」提供字幕、音轨、倍速、弹幕、重新加载和退出 |
| 播放器返回 | 字幕 / 音轨 / 倍速先回「更多」，再回常用栏；继续返回隐藏控制层，再退出播放页 |
| 媒体键 | 系统播放、暂停、快进、快退和下一集由媒体会话处理 |
| Home | 已加载视频在返回电视桌面后暂停；回到同一播放会话时保持暂停并显示控制菜单 |

首页提供热门、继续追番和真实接口推荐；推荐支持分页、加载失败重试与空结果刷新。首页导航集中在同一行；海报使用明确焦点边框；低高度页面采用左海报、右标题的横向卡片。搜索、详情和历史保留业务条目的返回位置；详情按每组最多 24 集浏览，支持定位当前剧集，未播出剧集不能播放。长简介通过弹窗上下滚动阅读，关闭后回到原按钮。历史显示已看时间和总时长。

筛选面板复用原版类型、设定、角色等标签，支持年份、季度和排序。选择仅修改草稿，「应用筛选」提交查询；返回或取消保留原条件。「清空筛选」保留输入的名称，应用后重新搜索；名称与条件均为空时显示搜索提示。

Bangumi 二维码在电视本地生成，手机打开现有服务的授权链接，电视等待服务确认后建立账号会话。可刷新二维码、取消或使用电视浏览器；两分钟未完成会显示超时并允许重试。刷新和离开页面取消当前等待。

播放中区分查询、解析、缓冲、暂停、结束和失败；同一等待状态持续约 15 秒时，控制栏显示换源和重试入口。选集、字幕和音轨侧栏会定位当前选项。

设置左侧上下选择分类，确认或右键进入内容，非编辑状态左键回分类；编辑网址时左右移动光标，先返回关闭输入法再导航。每类保留最近选项。开关显示明确的开启 / 关闭状态，删除和退出登录默认聚焦取消。设置包括订阅网址和更新、数据源启停、播放选项、视频缓存、账号和界面模式。标准 Android TV 自动进入电视界面；部分厂商电视未声明标准电视特征时可选择电视模式。

## 已验证范围

实际运行环境为 Android TV API 34（Android 14）ARM64 模拟器，标准 1920×1080 和紧凑 1440×810、均为 320 dpi，对应 960×540 dp 和 720×405 dp。安装及覆盖更新成功；验证了遥控器导航、电视输入法、真实联网搜索、详情选集、网页资源解析、H.264 视频实际解码与弹幕、媒体暂停、进度预览与提交、已加载视频的 Home 暂停及返回保持暂停、详情返回搜索时恢复焦点、本机观看历史与续播定位恢复至 6:25、设置与邮箱输入界面。测试截图和媒体会话状态保存在交付目录的 `evidence/`。

自动化测试共 124 项：37 项 TV 单元测试、4 项设备模式测试、83 项 Android TV 仪器测试，均通过。覆盖按键状态机、焦点恢复、分页、推荐入口与追加失败重试、筛选应用/取消/清空与年份季度联动、授权刷新/取消/超时/账号身份、二维码解码、内容保护、输入框导航、小高度卡片完整边界、键盘压缩后输入框可见性及真实 MediaController 与播放器桥接。Android 的 `assertScreenshot` 帮助函数当前不执行截图断言；视觉证据来自实际模拟器截图检查。

2026-09-21 功能验收使用同一 TV 模拟器：真实推荐显示「孤独摇滚！」与「命运石之门」等接口内容，从热门区经导航进入推荐并打开详情、返回原条目成功；无关键词的「科幻＋超能力」查询返回真实结果，取消草稿与清空查询行为正确。两个视口下的推荐、搜索筛选和授权布局均经截图检查。Bangumi 首次与刷新后的实际二维码均可解码为 `https://bgm.tv/oauth/authorize`，刷新产生不同请求；取消回到登录页。模拟器没有可用浏览器，系统显示提示且二维码保留。此次截图保存在本机 `output/android-tv/evidence-20260921/`，授权二维码截图不作为 Release 附件。

网页资源的解析速度、续播和稳定性取决于具体来源。本次已验证从 6:25 恢复后持续播放至 6:59，并成功跳转到 7:09；这不代表所有来源的持续缓冲问题均已解决。遇到长等待可在播放菜单重试或选择其他资源，并记录所选来源。

资源仍在解析时按 Home、解析完成前回到播放页，解析成功后仍会按正常加载流程自动播放。系统回收进程后重建播放会话也不保留原会话的暂停状态。

以下项目需要在目标电视继续验证：

- ARM32 系统、Android 8.1 老系统、厂商固件、中文输入法及遥控器差异。
- 电视硬件解码、HEVC/HDR、多音轨、外部字幕、蓝牙音频和长时间播放。模拟器以无音频输出模式运行，未验证听感。
- 真实邮箱验证码和 Bangumi 扫码完成登录、登录后收藏同步和会话过期流程。当前已接入现有服务，但没有使用用户账号完成登录验收。
- BT 持续下载及低存储场景、低内存回收、发布版性能。当前未做全仓 `check` 或手机/桌面全量回归。

登录支持邮箱验证码与 Bangumi 手机扫码授权。需要交互式网页验证的资源会提示换源；资源可用性受各来源服务和电视网络影响。

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

交付包的 SHA-256 位于 `output/android-tv/release-20260921/SHA256SUMS`。签名证书 SHA-256 为 `fad6b4417e67a8742a332d1e15ce2dc79a2f33b45b3c853058380cf08da94d06`。后续构建需继续使用同一个本机 debug keystore；更换机器生成的新签名无法直接覆盖安装。签名私钥不随交付目录分发。
