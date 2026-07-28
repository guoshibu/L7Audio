# L7Audio 音频工具

> 为银河 L7 车型量身打造的 Android 音频工具箱，集音乐播放、麦克风放大、文字转语音（TTS）、悬浮窗控制于一体。

***

## 目录

- [项目简介](#项目简介)
- [功能特性](#功能特性)
- [技术架构](#技术架构)
- [项目结构](#项目结构)
- [核心模块说明](#核心模块说明)
- [快速开始](#快速开始)
- [构建与安装](#构建与安装)
- [版本历史](#版本历史)
- [常见问题](#常见问题)
- [注意事项](#注意事项)
- [联系方式](#联系方式)

***

## 项目简介

L7Audio 是一款运行于 Android 系统的音频处理应用，专为吉利银河 L7 车型设计。应用提供车内 / 车外双输出通道的音乐播放、麦克风实时放大（车外喊话）、文字转语音播报等功能，并通过悬浮窗实现快捷操作，满足车主在不同场景下的音频需求。

**开发背景**：基于 Trae AI 辅助开发，全程代码以 AI 生成与优化为主，持续迭代功能与稳定性。

**适用车型**：

- 银河 L7 2023 款
- 银河 L7 2024 款

> 💡 23 / 24 款银河 L7 设置页面的车外输出设备填 **9、15、22** 均可，但音乐模块的车外功能仅支持填 **9**，否则会报错。默认已配置为 9。

***

## 功能特性

### 🎵 音乐播放器

- **本地音乐播放**：支持 MP3、FLAC、WAV、M4A、AAC、OGG、WMA、AMR 等 8 种格式
- **内置文件浏览器**：
  - 「扫描音乐」：弹窗选择文件夹，递归扫描目录下所有音频文件，支持多选
  - 「添加音乐」：弹窗选择音频文件，支持多选
  - 完全使用 File API，不依赖 MediaStore 或 SAF，实时性更强
  - 零缓存复制：全部操作真实文件路径，无额外存储占用
  - 多存储设备支持：自动检测内部存储、SD卡、U盘等外接存储
  - 显示优化：两种模式均显示全部文件，不可选项灰色半透明区分
- **格式感知元数据提取**：
  - **WAV**：直接自解析 RIFF INFO / id3 RIFF 块 + 头计算时长，零无用 IO
  - **FLAC**：直接自解析 STREAMINFO（时长）+ Vorbis Comment（标题/艺术家）
  - **M4A/AAC**：直接自解析 mvhd（时长）+ ilst（标题/艺术家）
  - **MP3/其他**：MediaMetadataRetriever 提取
  - **文件名兜底**：以上都失败时，使用去扩展名的文件名原样作为标题，绝不解析"艺术家 - 标题"格式，artist 留空
- **路径规范化去重**：getCanonicalPath() + 统一大小写 + 统一分隔符，彻底避免重复添加
- **播放列表管理**：支持手动添加、批量扫描、单首删除、清空列表
- **多种循环模式**：
  - 全部循环：按顺序循环播放整个列表
  - 随机播放：随机选择下一首歌曲
  - 单曲循环：重复播放当前歌曲
  - 单曲播放：当前歌曲播放完毕后自动停止
- **进度记忆**：自动保存播放进度，下次启动时可从上次位置继续
- **歌词显示**：支持同目录 .lrc 格式歌词文件，播放时自动加载并滚动
- **后台播放**：配合前台服务，支持后台持续播放与通知栏控制
- **双输出通道**：车内扬声器 / 车外扬声器一键切换

### 🎤 麦克风放大器（车外喊话）

- **实时麦克风采集**：低延迟音频采集与播放
- **多级放大增益**：可调节放大级别，适应不同喊话距离
- **智能降噪处理**（管线模式 Pipeline Pattern）：
  - HPF @80Hz：一阶 IIR 滤除 DC 和低频噪声
  - AFC NLMS：256 阶自适应滤波消除声反馈，HW AEC 启用时仍串联消残余
  - Gain：可调增益放大倍数
  - SpectralNR：512 FFT 谱减法自学习噪声轮廓
  - HowlingNotch：FFT 峰值检测 + IIR 窄带陷波，最多 3 频点
  - AGC：目标 RMS 自动增益，MAX\_GAIN=2.0 限幅防正反馈
  - 硬件 3A 可用时自动禁用对应软件处理器
- **车外喊话模式**：一键开启车外喊话，自动切换输出设备
- **防抖保护**：屏蔽快速连续触发（500-2000ms可配置），避免麦克风频繁启停导致啸叫和硬件损伤
- **闲置自动关闭**：无声音输入超时后自动关闭（5-300秒可配置），防止忘记关闭
- **状态同步**：悬浮窗和麦克风页面状态实时同步
- **第三方按键支持**：支持 Intent 和广播两种方式触发车外喊话（触发后开启，再次触发关闭）
  - Intent（推荐）：`com.aug32.l7audio.ACTION_TOGGLE_MIC`，通过 `startActivity` 调用，更可靠
  - 广播：`com.aug32.l7audio.OUTSIDE_MIC_TOGGLE`，通过 `sendBroadcast` 调用

#### 🔌 第三方调用详细说明

**方式一：Intent 调用（推荐）**

通过 `startActivity` 触发，最可靠的方式，确保应用进程完整初始化。

**第三方 APP 代码调用**：

```java
Intent intent = new Intent("com.aug32.l7audio.ACTION_TOGGLE_MIC");
intent.setPackage("com.aug32.l7audio");
intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
context.startActivity(intent);
```

**EVCC 配置步骤**：

1. 打开 EVCC → 点击 自动化 → 新建脚本 → 编辑规则 → 映射的物理按键 → 点击`动作（按顺序执行）`的`添加`按钮
2. 弹窗的类型选择 `启动app` → 包名填 `com.aug32.17audio/.ui.activity.MicToggleActivity` → 确定
3. 点击保存

**Key Mapper 配置步骤**：

1. 打开 Key Mapper → 点击底部 `+` 号 → 选择要映射的物理按键
2. 点击 `Add action` → 选择 `Activity` → 点击 `Select activity`
3. 搜索并选择 `L7Audio` → 选择 `MicToggleActivity`
4. 或手动输入：Action=`com.aug32.l7audio.ACTION_TOGGLE_MIC`，Target=`Activity`，Package=`com.aug32.l7audio`

**Tasker 配置步骤**：

1. 新建任务 → 添加动作 → `System` → `Send Intent`
2. 配置：Action=`com.aug32.l7audio.ACTION_TOGGLE_MIC`，Package=`com.aug32.l7audio`，Target=`Activity`
3. 新建配置文件 → `Event` → `Hardware` → 选择物理按键 → 关联任务

**ADB 测试命令**：

```bash
adb shell am start -a com.aug32.l7audio.ACTION_TOGGLE_MIC -n com.aug32.l7audio/.ui.activity.MicToggleActivity
```

**方式二：广播调用（兼容）**

通过 `sendBroadcast` 触发，适合部分老旧按键映射工具。

**第三方 APP 代码调用**：

```java
Intent intent = new Intent("com.aug32.l7audio.OUTSIDE_MIC_TOGGLE");
intent.setPackage("com.aug32.l7audio");
context.sendBroadcast(intent);
```

**EVCC 配置步骤**：

1. 打开 EVCC → 点击 自动化 → 新建脚本 → 编辑规则 → 映射的物理按键 → 点击`动作（按顺序执行）`的`添加`按钮
2. 弹窗的类型选择 `启动app` → 包名填 `com.aug32.17audio/.ui.activity.MicToggleActivity` → 确定
3. 点击保存

**Key Mapper 配置步骤**：

1. 打开 Key Mapper → 点击底部 `+` 号 → 选择要映射的物理按键
2. 点击 `Add action` → 选择 `Broadcast`
3. 配置：Action=`com.aug32.l7audio.OUTSIDE_MIC_TOGGLE`，Package=`com.aug32.l7audio`

**Tasker 配置步骤**：

1. 新建任务 → 添加动作 → `System` → `Send Intent`
2. 配置：Action=`com.aug32.l7audio.OUTSIDE_MIC_TOGGLE`，Package=`com.aug32.l7audio`，Target=`Broadcast Receiver`
3. 新建配置文件 → `Event` → `Hardware` → 选择物理按键 → 关联任务

**ADB 测试命令**：

```bash
adb shell am broadcast -a com.aug32.l7audio.OUTSIDE_MIC_TOGGLE -p com.aug32.l7audio
```

**两种方式对比**：

| 对比项  | Intent 方式        | 广播方式               |
| ---- | ---------------- | ------------------ |
| 可靠性  | ⭐⭐⭐ 最可靠          | ⭐⭐ Android 8+ 后台受限 |
| 启动速度 | 稍慢（需创建 Activity） | 更快（无 UI）           |
| 兼容性  | 所有按键映射工具         | 部分工具不支持            |
| 推荐度  | ✅ 推荐             | 仅兼容老旧工具            |

**使用注意事项**：

- 首次使用需授予录音权限（`RECORD_AUDIO`）
- 如需显示悬浮窗状态，需授予悬浮窗权限（`SYSTEM_ALERT_WINDOW`）
- 默认 800ms 防抖间隔，短时间内连续按只会触发一次
- 默认开启静音检测，无声音输入 30 秒后自动关闭

### 📢 文字转语音（TTS）

- **文本转语音播报**：输入文字即可合成语音输出
- **TTS 列表管理**：支持添加、删除、自定义条目标题
- **持久化存储**：TTS 列表通过 Gson 序列化保存，重启不丢失
- **快速播报**：悬浮窗中可直接选择预设条目一键播报

### 🎯 悬浮窗控制

- **全局悬浮球**：始终显示在其他应用之上，一键展开 / 收起
- **快捷功能面板**：
  - TTS 快速列表选择与播报
  - 车外喊话一键切换
  - 主题切换（浅色 / 深色）
  - **智能自动收起**：无操作可配置时长自动收起（默认 10 秒，范围 5-30 秒，车外喊话中保持展开）
  - **拖动交互**：支持拖动悬浮球调整位置，自动贴边

### ⚙️ 设置与其他

- **音频输出设备**：自定义车内 / 车外输出设备编号
- **主题切换**：支持浅色 / 深色主题，跟随系统或手动切换
- **开机自启**：可配置开机自动启动应用
- **横屏 / 竖屏自适应**：通过 configChanges 避免页面重建

***

## 技术架构

### 技术栈

| 类别     | 技术                         | 说明               |
| ------ | -------------------------- | ---------------- |
| 开发语言   | Java                       | 主体代码采用 Java 11   |
| 最低 SDK | API 30 (Android 11)        | 适配车机系统           |
| 目标 SDK | API 30                     | 保证车机兼容性          |
| 编译 SDK | API 36                     | 使用最新 SDK 编译      |
| 构建工具   | Gradle (KTS)               | build.gradle.kts |
| UI 框架  | AndroidX + Material Design | 兼容低版本系统          |

### 核心依赖

| 依赖库                         | 版本     | 用途                                             |
| --------------------------- | ------ | ---------------------------------------------- |
| **Media3 ExoPlayer**        | 1.9.2  | 音乐播放核心引擎                                       |
| **Android MediaSession**    | -      | 系统媒体中心会话（原生 API，无额外依赖）                         |
| **AndroidX Media**          | 1.7.0  | MediaStyle 通知样式（NotificationCompat.MediaStyle） |
| **Gson**                    | 2.10.1 | JSON 序列化 / 反序列化（TTS 列表、配置持久化）                  |
| **Lifecycle**               | 2.8.7  | ViewModel / LiveData，TTS 页面数据驱动                |
| **Appcompat / Material**    | -      | UI 组件与主题                                       |
| **RecyclerView / CardView** | -      | 列表展示                                           |

### 架构设计

```
┌─────────────────────────────────────────────────────────┐
│                        UI 层                              │
│  MainActivity / MusicPlayerFragment / TTSFragment / ...  │
└───────────────────┬─────────────────────────────────────┘
                    │ 调用
┌───────────────────▼─────────────────────────────────────┐
│                     Domain 层                             │
│  MusicPlayerManager / TTSManager / MicrophoneManager     │
│  AudioFocusManager / AudioOutputManager / PlaylistManager│
│  MediaSessionManager（媒体中心会话）                      │
│  MicOutputController（车外喊话统一管理）                  │
│  AudioPipeline → HPF → AFC(NLMS) → Gain → SpectralNR →   │
│  HowlingNotch → AGC（管线模式音频处理）                  │
└───────────────────┬─────────────────────────────────────┘
                    │ 依赖
┌───────────────────▼─────────────────────────────────────┐
│                      Data 层                             │
│  AppConfig (SharedPreferences) / TTSRepository           │
└───────────────────┬─────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────┐
│                    Service 层                            │
│  AudioForegroundService / FloatingWindowService          │
└─────────────────────────────────────────────────────────┘
```

**设计原则**：

- **单例管理**：核心管理器（MusicPlayerManager、AudioFocusManager 等）通过 `AudioServiceLocator` 统一管理，采用 DCL 双重检查锁实现懒加载单例
- **关注点分离**：播放控制（PlaybackController）与播放列表（PlaylistManager）职责分离
- **线程安全**：PlaylistManager 所有操作使用 `synchronized` 保证多线程安全

***

## 项目结构

```
L7Audio/
├── app/
│   ├── src/main/
│   │   ├── java/com/aug32/l7audio/
│   │   │   ├── L7AudioApp.java              # Application 入口
│   │   │   ├── base/                        # 基类
│   │   │   │   ├── BaseActivity.java
│   │   │   │   └── BaseFragment.java
│   │   │   ├── domain/audio/                # 音频核心领域层
│   │   │   │   ├── AudioServiceLocator.java # 服务定位器（单例管理）
│   │   │   │   ├── AudioFocusManager.java   # 音频焦点管理
│   │   │   │   ├── AudioVisualizerView.java # 音频可视化视图
│   │   │   │   ├── micoutput/               # 车外喊话模块
│   │   │   │   │   ├── MicOutputController.java   # 喊话统一管理（防抖、静音检测、状态同步）
│   │   │   │   │   ├── MicrophoneManager.java     # 麦克风管理（协调者）
│   │   │   │   │   ├── AudioOutputManager.java    # 输出设备管理
│   │   │   │   │   ├── AudioPipeline.java         # 音频处理管线编排
│   │   │   │   │   ├── AudioProcessor.java        # 音频处理器接口（管线模式）
│   │   │   │   │   └── processor/                 # 音频处理器实现
│   │   │   │   │       ├── HighPassFilterProcessor.java                # 80Hz 高通滤波
│   │   │   │   │       ├── AdaptiveFeedbackCancellationProcessor.java  # NLMS 自适应反馈消除
│   │   │   │   │       ├── GainLimiterProcessor.java                   # 增益+软限幅
│   │   │   │   │       ├── SpectralNoiseReductionProcessor.java        # 512 FFT 谱减法降噪
│   │   │   │   │       ├── HowlingNotchFilterProcessor.java            # FFT+IIR 啸叫陷波
│   │   │   │   │       └── AutomaticGainControlProcessor.java          # 目标 RMS AGC
│   │   │   │   ├── player/                  # 音乐播放模块
│   │   │   │   │   ├── MusicPlayerManager.java  # 音乐播放管理（门面）
│   │   │   │   │   ├── PlaybackController.java  # ExoPlayer 播放控制
│   │   │   │   │   ├── PlaybackCallback.java    # 播放回调接口
│   │   │   │   │   ├── PlaylistManager.java     # 播放列表管理
│   │   │   │   │   ├── MediaSessionManager.java # 媒体会话管理
│   │   │   │   │   ├── PlaybackState.java       # 播放状态
│   │   │   │   │   ├── MusicItem.java           # 音乐条目模型
│   │   │   │   │   └── LrcParser.java           # 歌词解析器
│   │   │   │   └── tts/                    # TTS 语音播报模块
│   │   │   │       └── TTSManager.java          # TTS 管理
│   │   │   ├── data/                        # 数据层
│   │   │   │   ├── local/
│   │   │   │   │   ├── AppConfig.java       # 配置持久化（总入口）
│   │   │   │   │   └── config/              # 配置分类
│   │   │   │   │       ├── AudioConfig.java
│   │   │   │   │       ├── ThemeConfig.java
│   │   │   │   │       ├── micoutput/MicOutputConfig.java
│   │   │   │   │       ├── player/MusicConfig.java
│   │   │   │   │       ├── tts/TTSConfig.java
│   │   │   │   │       └── floating/FloatingWindowConfig.java
│   │   │   │   ├── model/
│   │   │   │   │   └── TTSItem.java         # TTS 数据模型
│   │   │   │   └── repository/
│   │   │   │       └── TTSRepository.java   # TTS 数据仓库
│   │   │   ├── ui/                          # UI 层
│   │   │   │   ├── activity/
│   │   │   │   │   ├── MainActivity.java    # 主 Activity
│   │   │   │   │   └── MicToggleActivity.java # 第三方 Intent 触发入口（透明 Activity）
│   │   │   │   ├── fragment/
│   │   │   │   │   ├── micoutput/MicOutputFragment.java  # 麦克风放大页面
│   │   │   │   │   ├── tts/TTSFragment.java              # TTS 页面
│   │   │   │   │   ├── player/
│   │   │   │   │   │   ├── MusicPlayerFragment.java      # 音乐播放页面
│   │   │   │   │   │   └── FileBrowserFragment.java      # 文件浏览器
│   │   │   │   │   ├── settings/SettingsFragment.java    # 设置页面
│   │   │   │   │   └── about/AboutFragment.java          # 关于页面
│   │   │   │   ├── viewmodel/
│   │   │   │   │   └── TTSViewModel.java    # TTS 页面 ViewModel
│   │   │   │   ├── adapter/
│   │   │   │   │   ├── MusicPlaylistAdapter.java
│   │   │   │   │   └── FileBrowserAdapter.java
│   │   │   │   └── model/
│   │   │   │       └── FileItem.java        # 文件浏览器数据模型
│   │   │   ├── service/                     # 服务层
│   │   │   │   ├── player/AudioForegroundService.java    # 音频前台服务
│   │   │   │   └── floating/FloatingWindowService.java   # 悬浮窗服务
│   │   │   ├── receiver/                    # 广播接收器
│   │   │   │   ├── micoutput/MicOutputReceiver.java  # 车外喊话广播接收
│   │   │   │   └── boot/BootReceiver.java            # 开机自启
│   │   │   └── utils/                       # 工具类
│   │   │       ├── AppLog.java              # 日志工具
│   │   │       ├── AppExecutors.java        # 线程池
│   │   │       ├── FileUtils.java           # 文件工具
│   │   │       ├── ServiceCompat.java       # 服务兼容工具
│   │   │       ├── AlbumArtCache.java       # 专辑封面缓存
│   │   │       ├── AudioMetadataReader.java # 音频元数据读取（总入口）
│   │   │       ├── WavMetadataReader.java   # WAV 元数据解析
│   │   │       ├── FlacMetadataReader.java  # FLAC 元数据解析
│   │   │       └── M4aMetadataReader.java   # M4A/AAC 元数据解析
│   │   ├── res/                             # 资源文件
│   │   │   ├── layout/                      # 布局（竖屏）
│   │   │   ├── layout-land/                 # 布局（横屏）
│   │   │   ├── drawable/                    # 图片 / 形状
│   │   │   ├── values/                      # 字符串 / 颜色 / 主题
│   │   │   ├── values-night/                # 深色主题
│   │   │   ├── color/                       # 颜色选择器
│   │   │   └── menu/                        # 菜单
│   │   └── AndroidManifest.xml              # 应用清单
│   ├── build.gradle.kts                     # 应用级构建配置
│   └── proguard-rules.pro                   # 混淆规则
├── CHANGELOG.md                             # 改动记录
├── 开发需求文档.md                           # 开发需求文档
├── README.md                                # 本文件
├── settings.gradle.kts                      # 项目设置
├── gradle.properties                        # Gradle 属性
├── gradlew / gradlew.bat                    # Gradle 包装器
├── gradle/                                  # Gradle 配置
│   ├── wrapper/                             # Wrapper 文件
│   │   ├── gradle-wrapper.jar               # Wrapper 核心 JAR
│   │   └── gradle-wrapper.properties        # Wrapper 配置
│   ├── libs.versions.toml                   # 版本目录（依赖版本管理）
│   └── gradle-daemon-jvm.properties         # Daemon JVM 配置
```

***

## 核心模块说明

### 1. AudioServiceLocator — 服务定位器

**文件**：`domain/audio/AudioServiceLocator.java`

**职责**：统一管理所有音频相关管理器的单例实例，采用 DCL（Double-Checked Locking）双重检查锁实现懒加载。

**特点**：

- 全局唯一入口，避免静态单例滥用
- 初始化时传入 Application Context，避免内存泄漏
- 按需创建，启动时不占用过多资源

### 2. MusicPlayerManager — 音乐播放管理器

**文件**：`domain/audio/MusicPlayerManager.java`

**职责**：音乐播放的顶层入口，封装播放控制、列表管理、状态保存等核心逻辑。

**核心方法**：

- `togglePlayPause()` — 播放 / 暂停切换
- `playAt(int index)` — 播放指定位置歌曲
- `seekTo(long positionMs)` — 跳转到指定播放位置
- `next() / previous()` — 下一曲 / 上一曲
- `addMusicFiles / addMusicFromScan()` — 添加音乐（手动 / 扫描）
- `saveState / restoreState()` — 状态持久化

### 3. PlaybackController — 播放控制器

**文件**：`domain/audio/player/PlaybackController.java`

**职责**：封装 ExoPlayer 的底层操作，与业务逻辑解耦。

**关键点**：

- ExoPlayer 实例创建与生命周期管理
- 音频属性配置（`setAudioAttributes` 第二参数为 `false`，禁用内部焦点管理，避免与自定义 `AudioFocusManager` 冲突）
- 播放回调分发（通过 `PlaybackCallback` 接口）
- 音频使用场景切换（音乐 / TTS / 车外喊话）

### 4. PlaylistManager — 播放列表管理器

**文件**：`domain/audio/playlist/PlaylistManager.java`

**职责**：播放列表数据管理与循环模式逻辑。

**循环模式**：

| 模式   | 常量                    | 行为         |
| ---- | --------------------- | ---------- |
| 全部循环 | `REPEAT_MODE_ALL`     | 列表末尾自动回到开头 |
| 随机播放 | `REPEAT_MODE_SHUFFLE` | 随机选择下一首    |
| 单曲循环 | `REPEAT_MODE_ONE`     | 重复播放当前歌曲   |
| 单曲播放 | `REPEAT_MODE_OFF`     | 当前歌曲结束后停止  |

**线程安全**：所有修改列表的操作均使用 `synchronized` 保护。

### 5. AudioFocusManager — 音频焦点管理器

**文件**：`domain/audio/AudioFocusManager.java`

**职责**：统一管理应用内的音频焦点请求与释放，避免内部冲突。

**焦点类型**：

- **永久焦点**（`AUDIOFOCUS_GAIN`）：音乐播放使用
- **瞬时焦点**（`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`）：TTS / 车外喊话使用，支持闪避

**特点**：

- 同应用内切换时手动调度焦点事件（瞬时焦点 → 播放焦点）
- 焦点被外部应用抢占时自动暂停音乐，归还时自动恢复

### 6. AudioOutputManager — 音频输出管理器

**文件**：`domain/audio/AudioOutputManager.java`

**职责**：管理音频输出设备（车内 / 车外扬声器）的切换。

### 7. MediaSessionManager — 媒体会话管理器

**文件**：`domain/audio/MediaSessionManager.java`

**职责**：管理 Android MediaSession，实现与系统媒体中心的交互。

**核心功能**：

- 创建并管理 MediaSession 生命周期
- 同步播放状态（播放/暂停/位置）到系统媒体中心
- 同步歌曲元数据（标题、艺术家、专辑封面）供第三方读取
- 接收媒体按键事件（播放/暂停、上一曲、下一曲）

**接入能力**：

- 车机方向盘/中控媒体按键控制
- 第三方音乐应用读取当前播放歌曲信息
- 通知栏显示当前歌曲信息和控制按钮

### 8. FloatingWindowService — 悬浮窗服务

**文件**：`service/FloatingWindowService.java`

**职责**：全局悬浮窗的显示、交互、自动收起逻辑。

**核心特性**：

- 悬浮球 + 展开面板双形态
- 无操作可配置时长自动收起（默认 10 秒，范围 5-30 秒，车外喊话中暂停计时）
- TTS 列表快速选择，点击后直接跳转 TTS 模块
- 主题切换按钮，支持浅色 / 深色模式

### 9. AppConfig — 应用配置

**文件**：`data/local/AppConfig.java`

**职责**：基于 SharedPreferences 的配置持久化，统一管理所有用户设置项。

**配置项包括**：主题模式、音频输出设备、循环模式、开机自启、悬浮窗开关、TTS 列表、播放进度等。

### 10. MicOutputController — 车外喊话统一管理

**文件**：`domain/audio/micoutput/MicOutputController.java`

**职责**：集中处理车外喊话的状态切换、防抖、静音检测、焦点管理和状态通知。

**核心功能**：

- **状态管理**：统一管理 `isAnnouncing` 状态，支持多个入口（悬浮窗、麦克风页面、第三方按键）
- **防抖处理**：记录上次触发时间，过滤短时间内的连续触发请求（默认 800ms，可配置 500-2000ms）
- **静音检测**：通过 RMS 音量检测判断是否有声音输入，超时后自动关闭（默认 30 秒，可配置 5-300 秒）
- **焦点管理**：申请短暂独占焦点暂停音乐，结束后释放焦点恢复音乐
- **观察者模式**：`MicOutputListener` 接口实现悬浮窗和麦克风页面状态同步

**设计特点**：

- DCL 双重检查锁懒加载单例，确保全局唯一
- 支持第三方 APP 通过 Intent 和广播两种方式触发控制：
  - Intent（推荐）：`com.aug32.l7audio.ACTION_TOGGLE_MIC`，通过 `startActivity` 调用，更可靠
  - 广播：`com.aug32.l7audio.OUTSIDE_MIC_TOGGLE`，通过 `sendBroadcast` 调用
- Toast 提示增强：开启/关闭/自动关闭均显示提示

### 11. AudioProcessor / AudioPipeline — 音频处理管线

**文件**：`domain/audio/AudioProcessor.java`、`domain/audio/AudioPipeline.java`

**职责**：采用管线模式（Pipeline Pattern）将音频处理拆分为独立的处理器，由管线按注册顺序串联执行。

**核心功能**：

- **AudioProcessor**：定义音频处理器的统一契约（`process`、`reset`、`isEnabled`、`setEnabled`）
- **AudioPipeline**：按注册顺序执行处理器链，支持运行时启用/禁用，统一重置所有处理器状态

**设计特点**：

- 单一职责：每个处理器只做一种音频处理，可独立测试和替换
- 处理顺序：`HPF → AFC NLMS → Gain → SpectralNR → HowlingNotch → AGC`
- 线程安全：在录制线程中单线程调用，无需加锁

### 12. HighPassFilterProcessor — 高通滤波器

**文件**：`domain/audio/processor/HighPassFilterProcessor.java`

**职责**：80Hz 一阶 IIR 高通滤波，滤除 DC 偏移和低频噪声（呼吸喷麦、空调等）。

**核心参数**：B1=0.969（适配 16000Hz 采样率）。

### 13. AdaptiveFeedbackCancellationProcessor — NLMS 自适应反馈消除

**文件**：`domain/audio/processor/AdaptiveFeedbackCancellationProcessor.java`

**职责**：以输出信号为参考，使用归一化最小均方（NLMS）自适应滤波器消除声反馈。

**核心参数**：

- **滤波器阶数**：256 阶
- **MU 步长**：0.3（收敛速度与稳定性平衡）
- **DT\_THRESHOLD**：1.5（双讲检测阈值，防止发散）
- **LEAKAGE**：0.001（防止系数漂移）

**设计特点**：

- 硬件 AEC 启用时仍串联运行，消除 HW AEC 后的残余回声
- `xnorm` 计算优化为 O(1)，降低每帧计算量
- 提供 `getLastErleDb()` 供日志输出回波抑制比

### 14. GainLimiterProcessor — 增益+软限幅处理器

**文件**：`domain/audio/processor/GainLimiterProcessor.java`

**职责**：对音频采样进行增益放大，并用 tanh 软限幅防止溢出。

**设计特点**：

- 使用 tanh 函数替代 clamp 硬限幅，减少削波失真
- 增益倍数实时可调，响应录制过程中的配置变化

### 15. SpectralNoiseReductionProcessor — 谱减法降噪

**文件**：`domain/audio/processor/SpectralNoiseReductionProcessor.java`

**职责**：使用 512 点 FFT + 正弦窗 + 50% overlap-add 的谱减法实时降低背景噪声。

**核心参数**：

- **FFT 长度**：512 点（256 频段）
- **alpha**：1.3（过减系数，保留语音谐波）
- **beta**：0.01（频谱下限，保留底噪自然度）
- **学习机制**：前 10 帧静音期建立初始噪声谱，后持续自学习更新

### 16. HowlingNotchFilterProcessor — FFT 啸叫陷波器

**文件**：`domain/audio/processor/HowlingNotchFilterProcessor.java`

**职责**：通过 FFT 频谱峰值检测啸叫频率点，使用 IIR 窄带陷波滤波器进行抑制。

**核心参数**：

- **FFT 长度**：512 点
- **陷波 Q 值**：30（窄带，不伤人声）
- **抑制深度**：-12dB
- **最多同时抑制**：3 个啸叫频点

### 17. AutomaticGainControlProcessor — 自动增益控制

**文件**：`domain/audio/processor/AutomaticGainControlProcessor.java`

**职责**：以目标 RMS 为导向的自动增益控制，将输出音量稳定在目标水平。

**核心参数**：

- **目标 RMS**：0.3
- **MAX\_GAIN**：2.0（限制最大增益，防止 AGC+AFC 正反馈发散）
- **GAIN\_CHANGE\_LIMIT**：0.02（每帧最大增益变化 ±2%，确保 AFC 能跟踪）
- **限幅**：tanh 软限幅替代 clamp 硬限幅

**设计特点**：

- 用户可开关（麦克风页面第 4 个 Switch，持久化到配置）
- 增益变化率严格限制，避免 AFC 跟不上导致发散
- 放在管线末尾，参考信号经过 AGC 后才保存给下一帧 AFC 使用

***

## 快速开始

### 环境要求

- JDK 11+
- Android Studio Hedgehog 或更高版本
- Android SDK 30+
- Gradle 8.x（随项目包装器自动下载）

### 导入项目

1. 克隆或下载项目代码到本地
2. 使用 Android Studio 选择「Open an existing project」，选择项目根目录
3. 等待 Gradle 同步完成
4. 连接 Android 设备或启动模拟器
5. 点击「Run」按钮安装并运行

### 调试技巧

- **日志标签**：全局日志使用 `AppLog` 工具类，标签统一为 `L7Audio`，可通过 `adb logcat -s L7Audio` 过滤
- **Debug 构建**：Debug 模式下日志完整输出，Release 模式自动禁用日志
- **悬浮窗权限**：首次使用悬浮窗需授权「悬浮窗 / 显示在其他应用之上」权限

***

## 构建与安装

### 构建命令

```powershell
# Debug 构建（开发调试）
.\gradlew.bat assembleDebug --no-daemon

# Release 构建（带签名）
.\gradlew.bat assembleRelease --no-daemon

# 同时构建 Debug 和 Release
.\gradlew.bat assemble --no-daemon

# 清理构建产物
.\gradlew.bat clean
```

> 💡 建议使用 `--no-daemon` 参数避免 Gradle 守护进程导致的内存占用问题。

### 构建产物

| 类型          | 路径                                                                         |
| ----------- | -------------------------------------------------------------------------- |
| Debug APK   | `app/build/outputs/apk/debug/L7音频工具-versionName-versionCode-debug.apk`     |
| Release APK | `app/build/outputs/apk/release/L7音频工具-versionName-versionCode-release.apk` |

### Release 签名配置

签名信息已内置在 `app/build.gradle.kts` 中：

| 配置项         | 值                     |
| ----------- | --------------------- |
| Keystore 文件 | `../release.keystore` |
| Keystore 密码 | `password123`         |
| Key 别名      | `l7audio`             |
| Key 密码      | `password123`         |

> ⚠️ 生产环境请妥善保管密钥文件和密码，避免泄露。

### 安装到设备

```powershell
# 安装 Debug 包
adb install app/build/outputs/apk/debug/L7音频工具-versionName-versionCode-debug.apk

# 安装 Release 包
adb install app/build/outputs/apk/release/L7音频工具-versionName-versionCode-release.apk
```

也可以运行根目录下方的“构建脚本.bat”，按提示进行即可。

***

## 版本历史

### v1.5.9 (versionCode: 105)

- ✨ **UI字体增大优化**：全界面字体统一增大 3sp，提升可读性
  - 悬浮窗列表：标题 26→29sp，按钮 20→23sp，功能按钮 22→25sp
  - 音乐播放器：歌曲标题 24→27sp，艺术家 19→22sp，按钮 21→24sp
  - 麦克风放大器：状态 22→25sp，选项 19→22sp，开始按钮 28→31sp
  - TTS 界面：输入框/按钮 19→22sp
  - TTS 列表项：播放 18→21sp，删除 16→19sp
- 🚀 **性能优化**（v1.5.8 累积）：内存泄漏修复、对象复用、线程安全优化、资源管理、缓存优化

### v1.5.8 (versionCode: 96)

- 🐛 **修复静音检测误判问题**：`MicrophoneManager.getCurrentRms()` 改为返回最近 10 帧的滑动窗口平均值，避免采样到瞬时静音帧导致误判
- ✨ **增强静音检测日志**：添加宽限期状态、RMS 与阈值对比、计时进度等详细日志
- 🐛 **修复输出模式偏好被错误修改**：移除 `MicOutputController.toggle()` 中 `setPreferExternalMode(true)` 调用，`forceExternal` 仅控制本次输出模式，不修改用户偏好
- 🐛 **修复反射调用异常**：移除 `MicrophoneManager.getAudioRecordErrorCode()` 方法，车机 Android 版本不支持该方法
- 🐛 **修复通知更新过于频繁**：`AudioForegroundService.updateNotification()` 添加 200ms 防抖间隔
- 🐛 **修复车外喊话停止后输出模式未恢复**：`MicOutputController.stopAnnouncement()` 恢复 `savedOutputMode`
- ✨ **悬浮窗按钮边框增强**：三个按钮样式文件添加 2dp 边框，增强可视性
- ✨ **麦克风初始化日志增强**：增加动态缓冲区获取、详细初始化日志、调用堆栈日志

### v1.5.7 (versionCode: 91)

- 🐛 **修复悬浮窗深色模式显示问题**：深色模式下悬浮窗列表背景颜色调整为 `#464646ff`，不再发白
- 🐛 **修复自动收起时长标签颜色**：`tv_auto_hide_label` 添加 `applyTextTheme()` 调用，与其他标签颜色一致
- ✨ **新增第三方 Intent 调用方式**：新增 `MicToggleActivity` 透明 Activity，支持通过 `startActivity` 触发车外喊话
  - Intent Action：`com.aug32.l7audio.ACTION_TOGGLE_MIC`
  - 相比广播方式更可靠，保证完整初始化音频管线
- 🐛 **修复广播触发静默失败**：`MicOutputReceiver` 添加 `AudioServiceLocator.init()` 调用，确保各 Manager 正确初始化
- 🔧 **更新设置页面第三方调用说明**：标注 Intent 为推荐方式

### v1.5.6 (versionCode: 70)

- 🐛 **修复静音检测失效**：MicOutputController 改用 pre-RMS（getCurrentRms），替代被 AGC 拉平的 postProcessRms
- 🐛 **修复录制线程崩溃不释放资源**：MicrophoneManager catch 中主动调用 releaseResources()
- 🐛 **修复 AudioRecord 阻塞导致僵尸线程**：stop() 中先 stop AudioRecord 强制解除 read 阻塞
- 🐛 **修复悬浮窗服务泄漏**：FloatingWindowService onDestroy 未注销 MicOutputListener
- 🐛 **修复 TTS 监听无限重试**：retrySetupTTSRunnable 加最多 10 次重试上限
- 🐛 **修复 TTS 模拟进度空转**：删除 progressHandler/progressRunnable/currentProgress 及所有 onTTSProgress 调用
- 🚀 **性能优化**：AlbumArtCache 去掉 MD5 cacheKey 改用 hashCode；LruCache 淘汰时主动 recycle() Bitmap；PlaylistManager saveToStorage 防抖 1s 窗口
- 🔧 **TTS 悬浮窗持久化迁移至 uid 方案**：TTSItem 新增 uid(UUID) + transient isPlaying；删 TTSFragment 内部类，改用 model；悬浮窗编辑器按 uid 匹配
- 🔧 versionCode 69 → 70

### v1.5.5 (versionCode: 66)

- 🐛 **修复 Buffer 脏数据导致的人声失真**：MicrophoneManager `samples`数组大小动态匹配 `readSize/2`，消除三抑制模块同时开启时的失真
- 🚀 **性能优化**：PlaylistManager 去深拷贝、MicrophoneManager 合并循环、AFC 4096→1024+MU 0.3→0.05、AGC 降频更新、SpectralAndNotchProcessor 合并新旧两个处理器文件
- ✨ **悬浮窗自动收起时长滑动条**：5-30秒可调，替代硬编码10秒
- ✨ **悬浮球按钮增大**：88dp×77dp → 100dp×90dp
- ✨ **OutputModeListener**：MainActivity 注册/注销输出模式监听
- 🔧 versionCode 65 → 66

### v1.5.4 (versionCode: 65)

- 🐛 修复 Toast 模式恢复：MicOutputController 新增 `preferExternal` 持久化偏好
- 🐛 修复 TTS 播报通道：SettingsFragment 改为 `getCarAudioUsage()`
- 🐛 修复枚举设备地址缺失：SettingsFragment 增加 `device.getAddress()` 输出
- 🐛 修复采样率劣化：MicrophoneManager / HowlingNotchFilterProcessor 16000Hz → 48000Hz
- 🚀 扫描性能大幅优化：
  - WAV/FLAC/M4A 跳过 MediaMetadataRetriever，格式感知自解析（WAV 只读 44 字节头）
  - WavMetadataReader 新增 `id3 `  RIFF 块支持（Mp3tag 格式）
  - FlacMetadataReader / M4aMetadataReader 新增 durationMs 自解析
- 🔧 文件名显示策略调整：无元数据时 title = 文件名去扩展名（原样），artist 留空，不做任何智能猜解
- 🔧 versionCode 63 → 65

### v1.5.3 (versionCode: 61)

- 🔧 包结构按功能模块重组：domain/audio/、ui/fragment/、service/、receiver/、data/local/config/ 均按功能拆分子包
- 🔧 4 个类重命名：AnnouncementController → MicOutputController、AnnouncementReceiver → MicOutputReceiver、MicAmplifierFragment → MicOutputFragment、MicConfig → MicOutputConfig

### v1.5.2 (versionCode: 60)

- 🐛 修复 TTSFragment 播放车外 TTS 默认跟随车内模式的问题
- 🐛 修复 SettingsFragment 反馈 TTS（"已保存"提示音）错误使用车内音频通道
- 🐛 修复音乐播放器启动时未初始化为配置的音频输出模式
- 🔧 音频输出通道统一通过 `AudioOutputManager` 集中管理，清理所有散落的直接配置读取
- 🔧 PlaybackController 移除硬编码 USAGE\_MEDIA，完全交由 `updateAudioOutputUsage()` 负责

### v1.5.1 (versionCode: 59)

- 🚀 **音频处理管线全面升级**（汇总 50\~59 所有迭代）
- 🚀 **新管线顺序**：
  `HPF(@80Hz) → AFC(NLMS 256阶) → Gain → SpectralNR(512 FFT) → HowlingNotch(FFT+IIR) → AGC(MAX_GAIN=2.0)`
- 🚀 **AFC 自适应反馈消除**：MU=0.3，DT\_THRESHOLD=1.5，LEAKAGE=0.001，与 HW AEC 串联消残余
- 🚀 **SpectralNR 谱减法降噪**：512 FFT + Sine 窗 + 50% overlap-add，alpha=1.3 保留语音谐波
- 🚀 **HowlingNotch FFT 啸叫陷波器**：IIR 窄带陷波 Q=30 -12dB，最多同时抑制 3 频点
- 🚀 **AGC 自动增益控制**：目标 RMS=0.3，MAX\_GAIN=2.0，硬限幅 ±2%/帧，tanh 软限幅
- 🚀 **HPF 高通滤波器 @80Hz**：一阶 IIR B1=0.969，滤除 DC 和低频噪声
- 🚀 **Android 原生 3A 自适应**：硬件可用时自动禁用对应软件处理器
- 🚀 **AGC 用户开关**：麦克风页面第 4 个 Switch，持久化到配置
- 🐛 修复 HowlingNotch 数组越界、AFC 发散、SpectralNR 人声过减
- 🗑️ 删除旧 AudioSuppressionProcessor.java　（能量交叉相关回声 + 宽带啸叫衰减）
- 详细改动见 [CHANGELOG.md](CHANGELOG.md)

### v1.4.9 (versionCode: 49)

- 🚀 扫描时不再提取专辑封面，延迟到播放时按需提取（`PlaylistManager` → `MusicPlayerManager.start()`）
  - 扫描性能提升：200 首歌省 200 次文件 IO + 200 次磁盘缓存写入
  - 磁盘缓存只存播放过的歌的封面

### v1.4.8 (versionCode: 48)

- 🐛 修复 Release 构建日志未禁用问题（AppLog.debugEnabled 改用 BuildConfig.DEBUG）
- 🐛 修复封面全尺寸解码 OOM 风险（默认 512px 采样解码）
- 🐛 修复磁盘缓存无淘汰策略（上限 200 文件，超出删除最旧）
- 🐛 修复 AnnouncementController 部分初始化 NPE 路径
- 🐛 修复 FileBrowserAdapter 废弃的 getAdapterPosition()
- 🐛 修复 TTSFragment 废弃的 getResources().getColor()
- 🔧 TTSManager Handler 添加显式 Looper
- 🔧 FloatingWindowService Gson 实例复用 + 移除 TYPE\_PHONE 死代码
- 🔧 删除 MainActivity 空 onPause() 方法
- 🔧 AnnouncementController CopyOnWriteArrayList → ArrayList+synchronized
- 🔧 MusicPlaylistAdapter 颜色初始化改用 boolean 标记

### v1.4.7 (versionCode: 47)

- 🗑️ 死代码深度清理（第二轮）：
  - 删除 KeepAliveManager/Worker、MusicSource、ScannedMusicInfo 共 4 个文件
  - 删除 26 个未使用的 string 资源
  - 移除 work-runtime Gradle 依赖、WAKE\_LOCK 权限
  - 清理 18 个未使用的 Java 方法
  - 修复上轮误删 AppExecutors import 导致的编译错误

### v1.4.6 (versionCode: 46)

- 🔧 封面图片存储与解码全面优化：
  - `albumArt`/`lyrics` 加 `transient` 不再参与 Gson 序列化，SharedPreferences 存储量减少 90%+
  - 新增 `AlbumArtCache`（LRU 内存缓存 10MB + 文件缓存 + 采样压缩）
  - Fragment/MediaSession/Notification 三处封面解码合为统一入口，消除重复解码
  - 封面按 240dp 采样解码，大图内存占用显著降低
  - 首次加载在工作线程解码，不卡主线程
- 🔧 播放列表 RecyclerView 性能优化：
  - DiffUtil 增量刷新替代 `notifyDataSetChanged`
  - 颜色值缓存至 int 字段，避免 `onBindViewHolder` 中重复 `getColor()`
  - `setHasStableIds(true)` 稳定 ID，优化动画性能

### v1.4.5 (versionCode: 45)

- 🐛 修复扫描/添加大量音乐后播放列表不刷新问题：文件浏览器关闭与计算线程回调存在竞态条件，onResume 时补充刷新播放列表
- 🗑️ 全面死代码清理：删除 AudioUtils.java、BaseService.java、3个工具类、4个 Domain 层类、2个未使用布局、3个未使用颜色、6个未使用 Gradle 依赖，FileUtils 从 389 行精简至 57 行
- ✨ 设置页面新增「恢复默认设置」按钮：一键清除所有配置并重新初始化

### v1.4.4 (versionCode: 44)

- 🐛 修复麦克风放大按钮失效：AnnouncementController 未在 L7AudioApp 初始化导致 toggle() 直接 return
- 🐛 修复 GainLimiterProcessor tanh 过度压缩：仅在溢出时使用 tanh 限幅，正常范围直通

### v1.4.3 (versionCode: 43)

- 🐛 修复关闭主界面后悬浮窗车外喊话不可用问题（MainActivity.onDestroy 不再注销音频管理器）
- 🐛 修复悬浮窗按钮文字显示不全问题（移除内边距，设置 includeFontPadding=false）
- ✨ 新增车外喊话统一管理（AnnouncementController）：
  - DCL 单例模式，集中处理状态切换、防抖、静音检测和焦点管理
  - 观察者模式实现悬浮窗和麦克风页面状态同步
- ✨ 新增第三方 APP 按键控制支持：接收广播 `com.aug32.l7audio.OUTSIDE_MIC_TOGGLE`
- ✨ 新增防抖保护（500-2000ms可配置），避免麦克风频繁启停啸叫和硬件损伤
- ✨ 新增闲置自动关闭功能（静音检测 5-300秒可配置），防止忘记关闭
- ✨ 设置页面新增车外喊话配置项：防抖间隔、静音检测开关、静音超时、静音阈值（0.03-0.3）
- ✨ Toast 提示增强：开启/关闭/自动关闭均显示提示（仅第三方广播调用时显示）
- ✨ 保存结果显示位置修复：车外喊话设置卡片内新增独立状态显示
- ✨ 防抖间隔添加解释文字 + 第三方调用说明
- 🔧 MicrophoneManager 重构为管线模式（Pipeline Pattern）：
  - 新增 AudioProcessor 接口 + AudioPipeline 管线编排类
  - 新增 GainLimiterProcessor（tanh 软限幅替代 clamp 硬限幅）
  - 新增 AudioSuppressionProcessor（噪声门+回声消除+啸叫抑制三合一）
  - 修复状态泄漏：reset() 统一清零所有累积检测状态
  - 移除死代码：detectSteadyNoise 计算未使用
  - 处理顺序优化：增益→限幅→噪声门→回声→啸叫
  - 噪声抑制算法升级：从能量阈值法改为噪声门（Noise Gate）
  - 对外接口零变化

### v1.4.2 (versionCode: 42)

- 🗑️ 删除 TTS 语速/音调设置功能（UI 滑块 + 配置方法 + 持久化）
  - SettingsFragment 移除 TTS 语速/音调调节界面
  - TTSConfig / TTSManager / AppConfig / TTSRepository / TTSViewModel 移除 speed/pitch 相关方法
  - TTS 播报使用默认语速和音调（1.0f），简化功能
- 🔧 重构 MediaSession 为 Android 原生实现：
  - 使用 `android.media.session.MediaSession` 替代 media3-session
  - DCL 双重检查锁懒加载单例，确保全局唯一
  - 支持车机方向盘/中控媒体按键控制（播放/暂停、上一曲、下一曲）
  - 同步歌曲元数据到系统媒体中心（歌名、艺术家、专辑、时长、封面）
  - 第三方 APP 可读取当前播放歌曲信息
- ✨ 前台服务通知升级为 MediaStyle 标准样式：
  - 引入 `androidx.media:media:1.7.0` 依赖
  - 使用 `NotificationCompat.MediaStyle` 系统原生媒体通知布局
  - 通知栏显示媒体控制按钮（上一首、播放/暂停、下一首）
  - 显示专辑封面、歌曲名、艺术家
  - 封面 Bitmap 自动回收，防止内存泄漏
- 🐛 修复通知栏状态不联动问题：
  - 音乐模块内播放/暂停操作后通知栏同步更新状态
  - MusicPlayerManager 状态变化时调用 AudioForegroundService.notifyUpdate()
- 🐛 修复底部导航选中效果丢失问题：
  - loadFunctionPage() 中添加 updateFunctionButtons() 调用
  - 切换页面后底部导航按钮正确显示选中高亮
- 🔧 枚举设备显示优化：
  - 设置页枚举设备结果区域添加 ScrollView
  - maxHeight=300dp，支持垂直滚动，解决内容显示不全问题

### v1.4.1 (versionCode: 41)

- 🔧 接入 Android 媒体中心（MediaSession）：
  - 新增 MediaSessionManager，管理 MediaSession 生命周期
  - 同步播放状态到系统媒体中心（供车机按键、第三方APP读取）
  - 通知栏添加媒体控制按钮：上一首、播放/暂停、下一首，显示专辑封面
  - 接入能力：车机方向盘/中控媒体按键控制、第三方APP读取歌曲信息

### v1.3.12 (versionCode: 35)

- 🔧 设置页枚举按钮重构与显示优化：
  - 三个枚举按钮（枚举麦克风、枚举输出设备、枚举车内输出设备）统一调用共用方法，减少代码冗余
  - 车内/车外输出设备枚举共用同一逻辑，均显示"输出设备列表"
  - 麦克风与扬声器分开显示，保持当前 UI 结构不变
  - 简洁显示：编号 + 设备名 + 设备类型中文名 + 类型ID，让用户清楚知道车机有几个麦克风/扬声器
- 🔧 「显示音频路由」按钮内容增强：
  - 新增基本音频信息（音频模式、铃声模式、蓝牙SCO状态）
  - 新增音量设置（音乐流、铃声流、通话流、闹钟流）
  - 新增系统音频设备列表（所有输入/输出设备的详细信息）
  - 新增系统信息（Android版本、设备品牌/型号/厂商）
- 🔧 添加重复音乐吐司提示优化：根据成功、重复、失败数量动态生成提示信息
- 🔧 全项目 import 语句规范化整理：
  - 统一分组顺序：Android系统包 → AndroidX/第三方 → JDK标准库 → 项目本地包
  - 组内按字母顺序排序，组间空行分隔
  - 移除未使用的 import（共清理 28 个冗余导入）
- 🔧 Fragment 生命周期规范：为 SettingsFragment、TTSFragment、FileBrowserFragment 添加 onDestroyView 方法
  - 置空所有 View 引用，防止 Fragment 视图销毁后内存泄漏
  - 清理 TTSProgressListener 等回调监听器

### v1.3.11 (versionCode: 34)

- 🐛 修复文件浏览器目录层级导航问题：进入存储设备根目录后无"返回上级目录"项
  - 统一导航逻辑：移除父目录可读检查，始终显示".."项
  - 点击".."时判断父目录是否可读：可读则进入上级目录，不可读则返回存储设备选择页

### v1.3.10 (versionCode: 33)

- 🐛 修复扫描音乐完成后播放列表不自动刷新的问题（音乐文件多时等待时间长）
- 🐛 修复TTS语音测试（车外）声音从车内喇叭发出的问题（使用车外音频输出模式）
- 🐛 修复设置页枚举设备内容与1.1.1旧版不一致的问题
  - 重新实现 `enumMicrophones()` / `enumOutputDevices()` / `enumCarOutputDevices()`，使用反射调用 AudioManager.getDevices() 遍历 AudioDeviceInfo
  - 新增 `getDeviceTypeName()` 工具方法映射22种设备类型中文名
- 🔧 文件选择器视觉效果优化：目录保持正常显示，只有非音频文件才显示半透明
- 🔧 彻底移除状态栏管理：
  - themes.xml 移除全部状态栏/导航栏属性
  - BaseActivity.setupStatusBar() 改为空方法
  - MainActivity 移除 setupStatusBarWithTheme() 调用
  - Fragment 布局添加 fitsSystemWindows 属性
- 🐛 修复歌曲封面无法显示的问题
  - 新增 MusicItem.albumArt 字段存储封面字节数组
  - 在添加音乐时通过 MediaMetadataRetriever 提取内嵌封面
  - 在播放时将封面加载到 ImageView
- 🔧 文件浏览器存储设备页添加"返回上级目录"按钮（点击返回主页）

### v1.3.9 (versionCode: 32)

- 🆕 文件浏览器支持多存储设备：内部存储、SD卡、U盘等外接存储自动检测
- 🔧 文件浏览器显示优化：两种模式均显示全部文件，不可选项灰色半透明
  - 目录模式：只有目录可选中，文件灰色不可选
  - 文件模式：只有音频文件可选中，其他文件灰色不可选
- 🔧 修复切换目录后多选模式未重置导致的无法进入子目录问题
- 🔧 全选按钮只选中当前模式下可选的项目
- 🐛 修复横竖屏切换后音乐模块布局不刷新问题（onConfigurationChanged 重新加载 Fragment）

### v1.3.8 (versionCode: 31)

- 🆕 内置文件浏览器：「扫描音乐」选择文件夹、「添加音乐」选择文件，均支持多选
- 🔧 完全使用 File API，不依赖 MediaStore 或 SAF，实时性更强
- 🔧 零缓存复制：全部操作真实文件路径，无额外存储占用
- 🔧 路径规范化去重：getCanonicalPath() + 统一大小写 + 统一分隔符，彻底解决重复添加
- 🔧 目录递归扫描：自动跳过隐藏文件和 .nomedia 目录

### v1.3.7 (versionCode: 30)

- 🔧 PlaylistManager 新增路径规范化去重机制
- 🔧 优化音乐添加逻辑，避免重复条目

### v1.3.6 (versionCode: 29)

- 🔧 优化侧边栏菜单按钮尺寸（图标 32dp，文字 20sp，垂直间距 24dp），提升车机触控体验
- 🐛 修复 WAV 格式中文元数据乱码问题：智能编码检测，GBK 优先，兼容 UTF-8
- 🔧 新增 WAV 文件 ID3v2 尾部标签解析支持（部分 WAV 使用 ID3v2 而非 RIFF INFO）
- 🐛 修复"添加音乐"按钮无法添加歌曲的问题：Android 11+ 上 content:// URI 无法获取文件路径时直接使用 content URI 添加

### v1.3.5 (versionCode: 28)

- 🐛 修复 WAV / FLAC / M4A 格式歌曲元数据读取失败问题（显示 `<unknown>`）
- 🔧 新增三级元数据 fallback 机制：系统 API → 自解析二进制文件头 → 文件名兜底
- 🔧 新增 WAV RIFF INFO 块自解析器
- 🔧 新增 FLAC Vorbis Comment 自解析器
- 🔧 新增 M4A / AAC MP4 ilst 元数据自解析器
- ⚠️ 自解析失败时仅显示文件名，绝不"智能"拆分艺术家/标题，避免搞反

### v1.3.4 (versionCode: 27)

- 🐛 修复车内扬声器无声问题（ExoPlayer 内部音频焦点与自定义 AudioFocusManager 冲突）
- 🐛 修复车外扬声器切换时报错（非 MEDIA/GAME audio usage 导致 IllegalArgumentException）
- 🔄 循环模式优化：「不循环」改为「单曲播放」（当前歌曲结束后停止）
- 🔄 「列表循环」改名为「全部循环」
- 📝 全项目代码注释规范化（Javadoc 补全）

### v1.3.3 (versionCode: 26)

- TTS 删除按钮样式统一（与音乐列表删除按钮一致）
- 悬浮窗「关闭」改「收起」
- 代码清理与健壮性优化

### v1.3.2 (versionCode: 25)

- 悬浮窗 TTS 列表跳转优化（直接跳转，去除 Intent 中间层）
- 500ms 延迟逻辑清理
- 车外喊话时悬浮窗不自动收起

### v1.3.1 (versionCode: 24)

- TTS 列表数据模型修复（List<String> → List<TTSItem>）
- 悬浮窗 UI 优化

### v1.3.0 (versionCode: 23)

- 首次点击播放修复（ExoPlayer 音频焦点配置）
- 切后台状态丢失修复（MusicPlayerManager 单例化）
- 进度条显示与 seek 优化

### v1.2.x

- 音乐播放器核心功能迭代
- 悬浮窗功能初版
- 音频焦点管理重构

### v1.1.x

- 基础框架搭建
- 音乐、TTS、麦克风三大核心功能实现

> 详细改动记录请参考 [CHANGELOG.md](CHANGELOG.md)

***

## 常见问题

### Q1：音乐模块车外播放报错？

**A**：请在设置页将车外输出设备编号改为 **9**。23 / 24 款银河 L7 音乐模块仅支持设备 9 作为车外输出。

### Q2：TTS 列表全部删除后又出现默认条目？

**A**：这是正常设计。当列表为空时会自动恢复默认条目，避免功能不可用。

### Q3：悬浮窗不显示？

**A**：请检查以下几点：

1. 是否已授予「悬浮窗 / 显示在其他应用之上」权限
2. 设置页中「悬浮窗开关」是否开启
3. 系统是否限制了应用后台弹出窗口权限

### Q4：应用在后台容易被杀？

**A**：

1. 请确保已开启「开机自启」和「前台服务」
2. 在系统设置中将应用加入「后台运行白名单」
3. 关闭电池优化

### Q5：横屏切换时页面重启？

**A**：已通过 `AndroidManifest` 中 `configChanges` 配置避免了旋转和屏幕大小变化导致的 Activity 重建。如遇到其他配置变化导致的重启，请检查 `configChanges` 属性。

### Q6：歌词不显示？

**A**：歌词文件需满足以下条件：

1. 与音乐文件同名（如 `song.mp3` 对应 `song.lrc`）
2. 放在同一目录下
3. 格式为标准 .lrc 格式（`[mm:ss.xx] 歌词内容`）

***

## 注意事项

### 使用约束

1. **车外喊话音量**：使用车外喊话功能时请遵守当地法律法规，避免扰民
2. **驾驶安全**：驾驶过程中请勿操作复杂功能，确保行车安全
3. **版权声明**：请确保播放的音乐拥有合法版权

### 开发约束

1. **不修改业务逻辑**：重构 / 优化时必须保证原有业务逻辑 100% 不变
2. **单例模式**：核心管理器必须通过 `AudioServiceLocator` 获取，禁止直接 `new`
3. **线程安全**：播放列表等共享数据操作必须加锁
4. **内存泄漏**：
   - Fragment 中在 `onDestroyView` 置空 View 引用
   - 监听器 / 回调及时移除
   - 不使用 Activity Context 注册长生命周期对象
5. **ExoPlayer 音频焦点**：`setAudioAttributes` 第二参数必须为 `false`，由自定义 `AudioFocusManager` 统一管理焦点
6. **备份机制**：每批次修改前必须备份，支持随时回滚

### 安全提示

- ⚠️ `release.keystore` 和签名密码仅用于开发 / 测试环境，生产环境请使用独立的安全密钥
- ⚠️ 不要将密钥文件和密码提交到公开的代码仓库

***

## 联系方式

- **QQ 群**：159045907
- **项目地址**：[GitHub](https://github.com/guoshibu/L7Audio)

***

**享受您的车外音频体验！** 🎧
