# L7Audio 改动记录

> 日期：2026-06-30
> 版本：v1.4.2 (versionCode: 42)

---

## 一、修改文件总览

| 类型 | 数量 | 文件 |
|------|------|------|
| 🗑️ 删除 | 1 | TTS 语速/音调设置 UI 及相关代码 |
| ✨ 新增 | 1 | androidx.media:media:1.7.0 依赖（MediaStyle 通知） |
| 🔧 优化 | 10 | TTSConfig.java、TTSManager.java、AppConfig.java、TTSRepository.java、TTSViewModel.java、SettingsFragment.java、MediaSessionManager.java、AudioForegroundService.java、MusicPlayerManager.java、MainActivity.java |
| 🐛 修复 | 3 | 通知栏状态不联动、底部导航选中效果丢失、枚举设备显示不全 |
| 📝 文档 | 2 | README.md、CHANGELOG.md |
| 🔢 版本 | 1 | build.gradle.kts |

---

## 二、功能变更

### 1️⃣ 删除 TTS 语速/音调设置功能

**变更内容**：移除 TTS 语速/音调调节的 UI 和相关配置代码，简化 TTS 功能。

**变更方案**：

| 修改文件 | 改动内容 |
|----------|----------|
| [SettingsFragment.java](app/src/main/java/com/aug32/l7audio/ui/fragment/SettingsFragment.java) | 删除 TTS 语速/音调滑块、测试按钮、保存按钮及相关事件监听 |
| [fragment_settings.xml](app/src/main/res/layout/fragment_settings.xml) | 删除 TTS 语速/音调设置 CardView，仅保留 TTS 诊断部分 |
| [TTSConfig.java](app/src/main/java/com/aug32/l7audio/data/local/config/TTSConfig.java) | 删除 `getTTSSpeed()` / `setTTSSpeed()` / `getTTSPitch()` / `setTTSPitch()` 方法及相关常量 |
| [TTSManager.java](app/src/main/java/com/aug32/l7audio/domain/audio/TTSManager.java) | 删除 `setSpeechRate()` / `setPitch()` 方法，语速和音调固定为默认值 1.0f |
| [AppConfig.java](app/src/main/java/com/aug32/l7audio/data/local/AppConfig.java) | 删除 TTS speed/pitch 代理方法 |
| [TTSRepository.java](app/src/main/java/com/aug32/l7audio/data/repository/TTSRepository.java) | 删除 speed/pitch 相关方法 |
| [TTSViewModel.java](app/src/main/java/com/aug32/l7audio/ui/viewmodel/TTSViewModel.java) | 删除 speed/pitch 相关 LiveData 和方法 |

**说明**：TTS 核心播报功能、列表管理、悬浮窗快速播报等功能均不受影响。

---

### 2️⃣ 重构 MediaSession 为 Android 原生实现

**优化内容**：使用 Android 原生 `android.media.session.MediaSession` 替代 media3-session 依赖，实现与系统媒体中心的标准接入。

**优化方案**：

| 修改文件 | 改动内容 |
|----------|----------|
| [MediaSessionManager.java](app/src/main/java/com/aug32/l7audio/domain/audio/MediaSessionManager.java) | 重构为使用原生 `MediaSession`，DCL 单例模式，管理 MediaSession 生命周期 |

**核心功能**：
- 同步播放状态（播放/暂停、进度）到系统媒体中心
- 同步歌曲元数据（歌名、艺术家、专辑、时长、封面）到系统媒体中心
- 接收系统媒体按键事件（播放/暂停、上一曲、下一曲）并转发给播放器
- 支持车机方向盘/中控媒体按键控制
- 支持第三方音乐 APP 读取当前播放歌曲信息

**技术要点**：
- 使用 `MediaSession.Callback` 处理媒体按键回调
- 使用 `PlaybackState.Builder` 同步播放状态
- 使用 `MediaMetadata.Builder` 同步歌曲元数据
- DCL 双重检查锁懒加载单例，确保全局唯一

---

### 3️⃣ 前台服务通知升级为 MediaStyle 标准样式

**优化内容**：引入 `androidx.media:media:1.7.0` 依赖，使用 `NotificationCompat.MediaStyle` 实现标准 Android 媒体通知样式。

**优化方案**：

| 修改文件 | 改动内容 |
|----------|----------|
| [AudioForegroundService.java](app/src/main/java/com/aug32/l7audio/service/AudioForegroundService.java) | 使用 MediaStyle 通知样式，显示专辑封面、歌曲名、艺术家，3 个媒体控制按钮 |
| [build.gradle.kts](app/build.gradle.kts) | 添加 `androidx.media:media:1.7.0` 依赖 |
| [libs.versions.toml](gradle/libs.versions.toml) | 添加 media 版本配置（1.7.0） |

**通知特性**：
- 系统原生媒体通知布局，与系统媒体中心联动
- 显示当前播放歌曲名和艺术家
- 显示专辑封面（LargeIcon）
- 3 个媒体控制按钮：上一首、播放/暂停、下一首
- 播放中通知为常驻（ongoing），暂停时自动取消常驻
- 封面 Bitmap 自动回收，防止内存泄漏

---

### 4️⃣ 通知栏状态同步修复

**问题描述**：在音乐模块内点击播放/暂停按钮后，通知栏状态未同步更新，只有点击通知栏按钮才能正常更新。

**原因分析**：MusicPlayerManager 的状态变化回调中没有调用通知更新方法。

**修复方案**：

| 修改文件 | 改动内容 |
|----------|----------|
| [MusicPlayerManager.java](app/src/main/java/com/aug32/l7audio/domain/audio/MusicPlayerManager.java) | 在播放开始、暂停、停止、切歌等状态变化时调用 `AudioForegroundService.notifyUpdate()` |

---

### 5️⃣ 底部导航选中效果修复

**问题描述**：打开音乐模块后，底部三大模块（音乐、麦克风、TTS）的选中高亮效果丢失。

**原因分析**：`loadFunctionPage()` 方法中缺少 `updateFunctionButtons()` 调用。

**修复方案**：

| 修改文件 | 改动内容 |
|----------|----------|
| [MainActivity.java](app/src/main/java/com/aug32/l7audio/ui/activity/MainActivity.java) | 在 `loadFunctionPage()` 方法末尾添加 `updateFunctionButtons()` 调用 |

---

### 6️⃣ 枚举设备显示优化（添加滚动）

**问题描述**：枚举麦克风/输出设备后，内容过多显示不全，无法查看完整列表。

**修复方案**：

| 修改文件 | 改动内容 |
|----------|----------|
| [fragment_settings.xml](app/src/main/res/layout/fragment_settings.xml) | 为枚举设备状态显示区域添加 ScrollView，maxHeight=300dp，支持垂直滚动 |

---

## 三、版本历史摘要

详细版本历史请参考 [README.md](README.md)

---

## 四、旧版本记录

> 日期：2026-06-29
> 版本：v1.4.1 (versionCode: 41)

---

## 一、修改文件总览

| 类型 | 数量 | 文件 |
|------|------|------|
| ✨ 新增 | 1 | MediaSessionManager.java |
| 🔧 优化 | 4 | AudioServiceLocator.java、MusicPlayerManager.java、AudioForegroundService.java、build.gradle.kts |
| 📦 新增资源 | 4 | ic_media_play.xml、ic_media_pause.xml、ic_media_previous.xml、ic_media_next.xml |

---

## 二、功能优化

### 1️⃣ 接入 Android 媒体中心（MediaSession）

**优化内容**：接入 Android 系统媒体中心，支持车机方向盘/中控媒体按键控制、第三方APP读取歌曲信息、锁屏界面媒体控制。

**优化方案**：

| 修改文件 | 改动内容 |
|----------|----------|
| [MediaSessionManager.java](app/src/main/java/com/aug32/l7audio/domain/audio/MediaSessionManager.java) | 新增文件：DCL 单例，管理 MediaSession 生命周期，同步播放状态到系统媒体中心 |
| [AudioServiceLocator.java](app/src/main/java/com/aug32/l7audio/domain/audio/AudioServiceLocator.java) | 添加 MediaSessionManager 初始化和绑定逻辑 |
| [MusicPlayerManager.java](app/src/main/java/com/aug32/l7audio/domain/audio/MusicPlayerManager.java) | 播放开始/暂停/停止时同步通知 MediaSession |
| [AudioForegroundService.java](app/src/main/java/com/aug32/l7audio/service/AudioForegroundService.java) | 升级为支持媒体控制的通知：播放控制按钮、专辑封面显示 |
| [build.gradle.kts](app/build.gradle.kts) | 添加 media3-session 依赖 |
| [res/drawable/*](app/src/main/res/drawable/) | 新增 4 个媒体控制图标：ic_media_play、ic_media_pause、ic_media_previous、ic_media_next |

**接入能力**：
- 车机方向盘/中控媒体按键控制（上/下一曲、播放/暂停）
- 第三方音乐APP读取当前播放歌曲信息
- 通知栏媒体控制（显示歌曲信息和控制按钮）

---

## 三、版本历史摘要

详细版本历史请参考 [README.md](README.md)

---

## 四、旧版本记录

> 日期：2026-06-29
> 版本：v1.3.12 (versionCode: 35)

**优化内容**：设置页的「枚举麦克风」、「枚举输出设备」、「枚举车内输出设备」三个按钮，让用户能清楚知道车机的麦克风和扬声器数量。

**优化方案**：

| 修改文件 | 改动内容 |
|----------|----------|
| [SettingsFragment.java](app/src/main/java/com/aug32/l7audio/ui/fragment/SettingsFragment.java) | 1. 三个枚举按钮统一调用 `enumAudioDevices(boolean isInput, String title)` 共用方法，减少代码冗余<br>2. 车内/车外输出设备枚举共用同一逻辑（均调用 `getDevices(GET_DEVICES_OUTPUTS)`），仅标题不同<br>3. 麦克风与扬声器分开显示，保持当前 UI 结构不变<br>4. 简洁显示：编号 + 设备名 + 设备类型中文名 + 类型ID |

---

### 2️⃣ 显示音频路由按钮内容增强

**优化内容**：「显示音频路由」按钮展示更详细的音频路由信息，方便调试和排查问题。

**优化方案**：

| 修改文件 | 改动内容 |
|----------|----------|
| [SettingsFragment.java](app/src/main/java/com/aug32/l7audio/ui/fragment/SettingsFragment.java) | 重构 `displayAudioRoutes()` 方法，新增以下内容：<br>1. 基本音频信息：音频模式、铃声模式、蓝牙SCO状态<br>2. 音量设置：音乐流、铃声流、通话流、闹钟流的当前音量/最大音量<br>3. 系统音频设备列表：所有输入/输出设备的详细信息（名称、类型、ID、地址、方向）<br>4. 系统信息：Android版本、API Level、设备品牌/型号/厂商 |

---

### 3️⃣ 添加重复音乐吐司提示优化

**优化内容**：添加音乐时，如果有重复或失败的歌曲，吐司提示会显示详细的统计信息。

**优化方案**：

| 修改文件 | 改动内容 |
|----------|----------|
| [MusicPlayerFragment.java](app/src/main/java/com/aug32/l7audio/ui/fragment/MusicPlayerFragment.java) | 优化 `onAddComplete()` 中的吐司提示，根据成功数量、重复数量、失败数量动态生成提示信息 |

---

### 4️⃣ 全项目 import 语句规范化整理

**优化内容**：统一所有 Java 文件的 import 语句格式，提升代码可读性和规范性。

**优化方案**：

| 修改文件 | 改动内容 |
|----------|----------|
| 全部 40 个 Java 文件 | 1. 统一分组顺序：Android系统包（android.*）→ AndroidX/第三方（androidx.* 等）→ JDK标准库（java.*）→ 项目本地包（com.aug32.l7audio.*）<br>2. 组内按字母顺序严格排序<br>3. 组与组之间空一行分隔<br>4. 移除未使用的 import（共清理 28 个冗余导入） |

---

### 5️⃣ Fragment 生命周期规范（onDestroyView 内存泄漏防护）

**优化内容**：为缺失 onDestroyView 方法的 Fragment 补充生命周期清理，防止 View 引用泄漏。

**问题原因**：SettingsFragment、TTSFragment、FileBrowserFragment 未实现 onDestroyView，
Fragment 视图销毁后 View 引用和回调监听器未置空，存在内存泄漏风险。

**优化方案**：

| 修改文件 | 改动内容 |
|----------|----------|
| [SettingsFragment.java](app/src/main/java/com/aug32/l7audio/ui/fragment/SettingsFragment.java) | 添加 onDestroyView，置空所有 View 引用（30+ 个变量） |
| [TTSFragment.java](app/src/main/java/com/aug32/l7audio/ui/fragment/TTSFragment.java) | 添加 onDestroyView，清理 TTSProgressListener + View + 数据引用 |
| [FileBrowserFragment.java](app/src/main/java/com/aug32/l7audio/ui/fragment/FileBrowserFragment.java) | 添加 onDestroyView，置空 View、Adapter、回调引用 |

> 说明：onDestroyView 仅清理 View 层引用，不影响音乐模块后台播放逻辑。
> MusicPlayerManager 由 AudioServiceLocator DCL 单例管理，与 Fragment 生命周期无关。

---

## 三、版本历史摘要

详细版本历史请参考 [README.md](README.md)

---

## 四、旧版本记录

> 日期：2026-06-29
> 版本：v1.3.11 (versionCode: 34)

---

## 一、修改文件总览（v1.3.11）

| 类型 | 数量 | 文件 |
|------|------|------|
| 🐛 修复 | 7 | 见下方详细清单 |
| 🔧 优化 | 4 | 见下方详细清单 |

---

## 二、核心问题修复（v1.3.11）

### 1️⃣ 扫描音乐完成后播放列表不自动刷新

**问题描述**：音乐文件较多时，等待时间长，扫描完列表不会自动刷新，要重新点开音乐页面才能看到歌曲。

**原因**：`addFilesToPlaylist` 回调中没有调用 `refreshPlaylist()` 方法。

**修复方案**：

| 修改文件 | 改动内容 |
|----------|----------|
| [MusicPlayerFragment.java](app/src/main/java/com/aug32/l7audio/ui/fragment/MusicPlayerFragment.java) | 在 `onAddComplete` 回调中添加 `refreshPlaylist()` 调用 |

---

### 2️⃣ TTS语音测试（车外）声音从车内喇叭发出

**问题描述**：设置页的"测试TTS发声（车外）"按钮，声音依旧从车内喇叭发出。

**原因**：`testTTS()` 方法使用的是默认音频输出模式，而非车外模式。

**修复方案**：

| 修改文件 | 改动内容 |
|----------|----------|
| [SettingsFragment.java](app/src/main/java/com/aug32/l7audio/ui/fragment/SettingsFragment.java) | 使用 `speakWithUsage(testMessage, externalUsage)` 传入车外音频 usage |

---

### 3️⃣ 设置页枚举设备、查看音频路由与旧版不一致

**问题描述**：设置页中的"枚举麦克风"、"枚举输出设备"、"枚举车内输出设备"按钮，点开后的内容与1.1.1旧版不一致。

**原因**：重构时只保留了基础的状态显示，未实现反射调用 `AudioManager.getDevices()` 的完整实现。

**修复方案**：

| 修改文件 | 改动内容 |
|----------|----------|
| [SettingsFragment.java](app/src/main/java/com/aug32/l7audio/ui/fragment/SettingsFragment.java) | 按1.1.1旧版重新实现 `enumMicrophones()` / `enumOutputDevices()` / `enumCarOutputDevices()`，使用反射调用 `getDevices()` 方法遍历 `AudioDeviceInfo[]`，并新增 `getDeviceTypeName()` 工具方法映射22种设备类型中文名 |

---

### 4️⃣ 歌曲封面无法显示

**问题描述**：歌曲封面读取不出来。

**原因**：`MusicItem` 类缺少封面字段，`MusicPlayerFragment` 无封面加载逻辑。

**修复方案**：

| 修改文件 | 改动内容 |
|----------|----------|
| [MusicItem.java](app/src/main/java/com/aug32/l7audio/domain/audio/MusicItem.java) | 新增 `albumArt` 字段存储封面字节数组 |
| [PlaylistManager.java](app/src/main/java/com/aug32/l7audio/domain/audio/playlist/PlaylistManager.java) | 在 `createItemFromFile` 中通过 `retriever.getEmbeddedPicture()` 提取封面 |
| [MusicPlayerFragment.java](app/src/main/java/com/aug32/l7audio/ui/fragment/MusicPlayerFragment.java) | 新增 `loadAlbumArt()` 方法，在 `updateCurrentSongInfo()` 中调用加载封面 |

---

### 5️⃣ 文件选择器文件夹视觉效果问题

**问题描述**：点击添加音乐打开文件选择窗口，文件夹是灰色但可以点击进入。

**原因**：目录在文件选择模式下被设为不可选并设置了半透明。

**修复方案**：

| 修改文件 | 改动内容 |
|----------|----------|
| [FileBrowserAdapter.java](app/src/main/java/com/aug32/l7audio/ui/adapter/FileBrowserAdapter.java) | 目录保持正常显示，只对非音频文件设置半透明 |

---

### 6️⃣ 横竖屏顶部多余空行/状态栏

**问题描述**：横屏模式和竖屏模式的最上方多一个空行，应用不应该管理状态栏。

**原因**： themes.xml 中设置了状态栏颜色，BaseActivity 和 MainActivity 中主动管理状态栏。

**修复方案**（彻底移除状态栏管理）：

| 修改文件 | 改动内容 |
|----------|----------|
| [themes.xml](app/src/main/res/values/themes.xml) | 移除全部状态栏/导航栏属性（statusBarColor, navigationBarColor, windowTranslucentStatus, windowLightStatusBar 等） |
| [BaseActivity.java](app/src/main/java/com/aug32/l7audio/base/BaseActivity.java) | `setupStatusBar()` 改为空方法，不再调用 setStatusBarColor/setDecorFitsSystemWindows/WindowInsetsController |
| [MainActivity.java](app/src/main/java/com/aug32/l7audio/ui/activity/MainActivity.java) | 移除 `setupStatusBarWithTheme()` 调用 |
| fragment_music_player.xml | 添加 `android:fitsSystemWindows="true"` |
| fragment_settings.xml | 添加 `android:fitsSystemWindows="true"` |
| fragment_file_browser.xml | 添加 `android:fitsSystemWindows="true"` |

---

### 7️⃣ 文件浏览器存储设备页无返回上级目录按钮

**问题描述**：文件浏览器的内部存储、U盘等页面没有返回上级目录按钮。

**原因**：存储设备选择页是根级别，没有".."虚拟项。

**修复方案**：

| 修改文件 | 改动内容 |
|----------|----------|
| [FileBrowserFragment.java](app/src/main/java/com/aug32/l7audio/ui/fragment/FileBrowserFragment.java) | 在 `loadStorageList()` 中添加".."虚拟项，点击返回主页 |

---

### 8️⃣ 文件浏览器目录层级导航统一优化

**问题描述**：进入存储设备根目录（如 `/storage/emulated/0`）后没有"返回上级目录"项，只有进入子目录后才显示。

**原因**：`loadDirectory()` 中添加".."项时检查了 `parentDir.canRead()`，而存储设备根目录的父目录通常不可读，导致不显示。

**修复方案**（统一导航逻辑）：

| 修改文件 | 改动内容 |
|----------|----------|
| [FileBrowserFragment.java](app/src/main/java/com/aug32/l7audio/ui/fragment/FileBrowserFragment.java) | 1. 移除父目录可读检查，始终显示".."项；2. 点击时判断父目录是否可读：可读则进入上级目录，不可读则返回存储设备选择页 |

---

## 三、版本历史摘要

详细版本历史请参考 [README.md](README.md)

---

## 四、旧版本记录

> 日期：2026-06-11
> 版本：v1.1.4 (versionCode: 7)

---

## 一、修改文件总览

| 类型 | 数量 | 文件 |
|------|------|------|
| 🔧 修改 | 22 | 见下方详细清单 |
| ➕ 新增 | 3 | `LrcParser.java`、`gradle.properties`、本文件 |

---

## 二、核心问题修复

### 1️⃣ 悬浮窗 TTS 选择按钮点击不自动跳转

**问题描述**：在悬浮窗的 TTS 列表中点击「选择」按钮后，主界面的 TTS 模块不自动跳转。

**原因**：`MainActivity` 已在运行时（Activity 栈中存在），`Intent` 通过 `onCreate()` 处理，后续启动 Activity 不触发 `onCreate()`，导致跳转逻辑不执行。

**修复方案**：

| 修改文件 | 修改位置 | 改动内容 |
|----------|----------|----------|
| [FloatingWindowService.java](app/src/main/java/com/aug32/l7audio/service/FloatingWindowService.java) | `openMainActivityToSelectTTS()` | 添加 `FLAG_ACTIVITY_NEW_TASK \| FLAG_ACTIVITY_CLEAR_TOP \| FLAG_ACTIVITY_SINGLE_TOP`，确保主界面被唤起 |
| [MainActivity.java](app/src/main/java/com/aug32/l7audio/MainActivity.java) | `onNewIntent()` | 新增覆盖方法，在 Activity 已运行时处理新 Intent 并切换到 TTS Fragment |
| [MainActivity.java](app/src/main/java/com/aug32/l7audio/MainActivity.java) | `handleFloatingWindowIntent()` | 重构，确保无论在 onCreate 还是 onNewIntent 中都能正确处理跳转 |

---

### 2️⃣ 音乐模块「添加音乐」与「扫描音乐」列表重复

**问题描述**：同一首歌通过「添加音乐」（文件选择器）和「扫描音乐」（扫描磁盘）会以不同条目出现，显示为两首不同的歌。

**原因**：
- Android Scoped Storage 导致两种方式获取的文件路径不一致；
- `getRealPathFromURI()` 仅处理 `type=primary`，模拟器共享目录（MuMuShared 等）无法正确转换；
- 缺少基于元数据（标题+艺术家+时长）的去重机制。

**修复方案**：

| 修改文件 | 修改位置 | 改动内容 |
|----------|----------|----------|
| [MusicPlayerFragment.java](app/src/main/java/com/aug32/l7audio/MusicPlayerFragment.java) | `getRealPathFromURI()` | 不再限制 `type=primary`，尝试多种路径候选（`/storage/emulated/0/`、`/sdcard/`、`/mnt/sdcard/`、`/storage/{type}/`）|
| [MusicPlayerFragment.java](app/src/main/java/com/aug32/l7audio/MusicPlayerFragment.java) | 文件选择回调 | 同时保存 `filePath` 和原始 `contentUri`，传给 MusicPlayerManager |
| [MusicPlayerManager.java](app/src/main/java/com/aug32/l7audio/audio/MusicPlayerManager.java) | `MusicItem` 类 | 新增 `contentUri` 字段 |
| [MusicPlayerManager.java](app/src/main/java/com/aug32/l7audio/audio/MusicPlayerManager.java) | `extractMetadata()` | 当文件路径读取失败时，使用 content URI 作为 fallback |
| [MusicPlayerManager.java](app/src/main/java/com/aug32/l7audio/audio/MusicPlayerManager.java) | `addMusicFilesWithUris()` | **三重去重**：先比对路径规范化、再比对 contentUri、最后比对元数据（标题+艺术家+时长），确保同一首歌只保留一条 |
| [MusicPlayerManager.java](app/src/main/java/com/aug32/l7audio/audio/MusicPlayerManager.java) | 播放逻辑 | `buildMediaItem()` 优先使用 content URI，文件路径作为备选 |

---

### 3️⃣ Release APK 日志输出未禁用

**问题描述**：Release 包中 `AppLog.d/i/w/e()` 仍会输出 logcat 并写入文件日志，可能泄露调试信息。

**修复方案**：

| 修改文件 | 修改位置 | 改动内容 |
|----------|----------|----------|
| [AppLog.java](app/src/main/java/com/aug32/l7audio/AppLog.java) | 所有日志方法 | 开头加 `if (!BuildConfig.DEBUG) return;`，release 模式直接跳过 |
| [proguard-rules.pro](app/proguard-rules.pro) | `-assumenosideeffects` | 通过 ProGuard 进一步移除 release 下的日志调用 |
| [build.gradle.kts](app/build.gradle.kts) | `buildFeatures` | 新增 `buildConfig = true`，确保 `BuildConfig` 类在 AGP 9.0 下生成 |

---

## 三、构建配置变更

### build.gradle.kts

| 配置项 | 原值 | 新值 | 说明 |
|--------|------|------|------|
| `targetSdk` | 36 | **30** | 降低 targetSdk 以保证在车机/模拟器上的兼容性 |
| `versionCode` | 5 | **7** | 版本号递增 |
| `versionName` | 1.1.1 | **1.1.4** | 版本名递增 |
| `isMinifyEnabled` | — | **false** | 关闭代码混淆，避免运行时找不到类（如需开启，需精细配置 proguard 规则）|
| `buildFeatures.buildConfig` | — | **true** | AGP 9.0 默认不生成 BuildConfig，显式开启 |
| `implementation media3-session` | 存在 | **移除** | 代码中未使用 |
| `implementation okhttp` | 存在 | **移除** | 代码中未使用 |

### gradle.properties（新增文件）

```properties
org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m
org.gradle.daemon.performance.disable-logging=true
```

> 解决 Gradle 构建时「JVM garbage collector is thrashing」导致的 OOM 崩溃。

---

## 四、ProGuard 规则强化

[proguard-rules.pro](app/proguard-rules.pro) 扩展内容：

- `-keepattributes Signature, *Annotation*`：保留泛型签名和注解（Gson 需要）
- `-keep class com.aug32.l7audio.** { *; }`：保留应用所有类、字段和方法
- `-keep class androidx.media3.** { *; }` + `-keep interface androidx.media3.** { *; }`：保留 Media3 播放库
- `-keep class com.google.android.material.** { *; }`：保留 Material Design 组件
- `-keep class androidx.work.** { *; }`：保留 WorkManager 保活服务
- `-keep public class * extends android.app.Activity / Service / BroadcastReceiver`：系统组件类名保留
- `-keep public class * extends androidx.work.Worker / ListenableWorker`：Worker 类名保留

---

## 五、其他功能增强

### 5.1 悬浮窗车外喊话 & 设置面板

- 新增车外喊话按钮（`isAnnouncing` 状态机）
- TTS 播放期间自动暂停音乐，结束后自动恢复（`ttsPausedMusic` 标志）
- 悬浮窗内新增设置面板，支持主题切换
- `onConfigurationChanged()` 跟随系统主题
- `onDestroy()` 兜底清理：强制停止 TTS / 车外喊话、释放音频焦点

### 5.2 音频焦点管理重构

- `AudioFocusManager` 区分**永久焦点**（音乐）和**瞬时焦点**（TTS/车外喊话）
- 瞬时焦点请求使用 `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`
- 焦点被抢占时自动暂停音乐，焦点归还时恢复
- TTS Manager / MicrophoneManager / MusicPlayerManager 统一使用 AudioFocusManager

### 5.3 输出设备管理

- `AudioOutputManager` 支持在音乐、TTS、车外喊话模式下自动切换输出设备
- 设置页新增输出设备测试功能

### 5.4 TTS 列表增强

- 支持自定义条目名称（Gson 持久化到 SharedPreferences）
- 选中状态持久化
- 通过悬浮窗选择后自动在主界面跳转并高亮选中项

### 5.5 音乐播放器 UI 优化

- 横屏/竖屏布局同步更新
- 歌曲信息（标题、艺术家、时长）显示优化
- 歌单列表滚动体验优化
- 增加歌词解析器 `LrcParser.java`（预留功能）

---

## 六、构建命令

```powershell
# Debug 构建（开发调试）
.\gradlew.bat assembleDebug --no-daemon

# Release 构建（带签名）
.\gradlew.bat assembleRelease --no-daemon
```

构建产物位置：

| 类型 | 路径 |
|------|------|
| Debug | `app/build/outputs/apk/debug/app-debug.apk` |
| Release | `app/build/outputs/apk/release/app-release.apk` |

Release 签名配置（已内置）：

- Keystore 文件：`../release.keystore`
- Keystore 密码：`password123`
- Key 别名：`l7audio`
- Key 密码：`password123`

---

## 七、APK 体积对比

| 版本 | 体积 | 说明 |
|------|------|------|
| 云端原版（v1.1.1） | ~21 MB | 开启混淆，含 media3-session、okhttp |
| 本次（v1.1.4） | ~17.8 MB | 关闭混淆，移除未使用依赖，日志禁用 |

体积优化方向（可选）：
- 图片资源转 WebP（`qr_code_qq_group.png` 415 KB → WebP ≈ 150 KB）
- 精细配置 ProGuard 后重新开启混淆（预计 ~10 MB）

---

## 八、注意事项

1. **代码混淆目前为关闭状态**：如果后续需要开启 `isMinifyEnabled = true`，需要逐一验证各功能是否正常，特别是 Gson 序列化、WorkManager、Media3。当前 ProGuard 规则已较为完整，但建议在真机上充分测试后再开启。
2. **`targetSdk` 从 36 降到 30**：这是为了避免高版本 targetSdk 在某些老车机/模拟器上出现的兼容性问题。后续如果要上架 Google Play，需要升到 33+（Google Play 要求）。
3. **日志输出**：Release 构建中，`AppLog.*()` 会被 `BuildConfig.DEBUG` 判断拦截，不会输出任何日志。Debug 构建仍正常输出，方便调试。
4. **临时日志文件**：项目根目录下的 `*.txt`（如 `build_log.txt`、`l7audio_logcat.txt` 等）均为调试时产生的临时文件，**不要提交到 Git**。

---

## 九、完整修改文件列表

### 修改的源文件（22）

1. `app/build.gradle.kts` — 构建配置（依赖移除、版本号、BuildConfig）
2. `app/proguard-rules.pro` — 混淆规则扩展
3. `app/src/main/java/com/aug32/l7audio/MainActivity.java` — 主界面（onNewIntent、悬浮窗跳转）
4. `app/src/main/java/com/aug32/l7audio/AppLog.java` — 日志工具（release 禁用）
5. `app/src/main/java/com/aug32/l7audio/AppConfig.java` — 配置类（主题模式扩展）
6. `app/src/main/java/com/aug32/l7audio/BootReceiver.java` — 开机广播优化
7. `app/src/main/java/com/aug32/l7audio/AboutFragment.java` — 关于页微调
8. `app/src/main/java/com/aug32/l7audio/SettingsFragment.java` — 设置页（主题、输出设备测试）
9. `app/src/main/java/com/aug32/l7audio/TTSFragment.java` — TTS 页（列表持久化、自定义名称）
10. `app/src/main/java/com/aug32/l7audio/MusicPlayerFragment.java` — 音乐页（路径转换、去重接入、UI 优化）
11. `app/src/main/java/com/aug32/l7audio/MusicPlaylistAdapter.java` — 歌单适配器
12. `app/src/main/java/com/aug32/l7audio/MicAmplifierFragment.java` — 麦克风功放页
13. `app/src/main/java/com/aug32/l7audio/audio/MusicPlayerManager.java` — 音乐播放核心（三重去重、元数据提取）
14. `app/src/main/java/com/aug32/l7audio/audio/AudioFocusManager.java` — 音频焦点管理（永久/瞬时焦点区分）
15. `app/src/main/java/com/aug32/l7audio/audio/AudioOutputManager.java` — 输出设备管理
16. `app/src/main/java/com/aug32/l7audio/audio/AudioVisualizerView.java` — 音频可视化
17. `app/src/main/java/com/aug32/l7audio/audio/MicrophoneManager.java` — 麦克风采集（车外喊话）
18. `app/src/main/java/com/aug32/l7audio/audio/TTSManager.java` — TTS 播放管理
19. `app/src/main/java/com/aug32/l7audio/service/FloatingWindowService.java` — 悬浮窗（跳转、车外喊话、设置面板、主题跟随）
20. `app/src/main/java/com/aug32/l7audio/service/AudioForegroundService.java` — 音频前台服务
21. `app/src/main/java/com/aug32/l7audio/service/KeepAliveManager.java` — 保活管理
22. `app/src/main/java/com/aug32/l7audio/service/KeepAliveWorker.java` — 保活 Worker

### 新增文件（3）

23. `gradle.properties` — Gradle JVM 内存配置（2048m）
24. `app/src/main/java/com/aug32/l7audio/audio/LrcParser.java` — 歌词解析器
25. `CHANGELOG.md` — 本文件（改动记录）
