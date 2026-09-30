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
- **硬件加速解码**：ExoPlayer 请求音频硬件 offload（aDSP 解码）+ 硬解优先
- **智能缓冲控制**：针对 ECARX 车机（8155）使用自定义 DefaultLoadControl（minBufferMs=60s、maxBufferMs=90s），减少 CPU 唤醒次数
- **全局字体缩放**：设置页可调节字体大小（0.7×–1.5×），实时预览、松手即时全局生效

### 🎤 麦克风放大器（车外喊话）

- **实时麦克风采集**：低延迟音频采集与播放
- **多级放大增益**：可调节放大级别（钳制 1~50），适应不同喊话距离
- **智能降噪处理**（管线模式 Pipeline Pattern）：
  - HPF @100Hz：二阶 Butterworth IIR 滤除 DC 和低频胎噪（截止频率可配置）
  - LPF @4000Hz：二阶 Butterworth IIR 砍掉高频啸叫/嘶声/齿音（截止频率可配置）
  - AFC NLMS：256 阶自适应滤波消除声反馈，HW AEC 启用时仍串联消残余
  - Gain：可调增益放大倍数
  - SpectralNR：256 FFT 谱减法自学习噪声轮廓
  - HowlingNotch：FFT 峰值检测 + IIR 窄带陷波，最多 3 频点
  - AGC：目标 RMS 自动增益，MAX_GAIN=2.0 限幅防正反馈
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
2. 弹窗的类型选择 `启动app` → 包名填 `com.aug32.l7audio/.ui.activity.MicToggleActivity` → 确定
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
2. 弹窗的类型选择 `启动app` → 包名填 `com.aug32.l7audio/.ui.activity.MicToggleActivity` → 确定
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
- **悬浮窗编辑**：悬浮窗"添加"按钮点击后跳转 TTS 模块并自动打开编辑悬浮窗列表对话框

### 🎯 悬浮窗控制

- **全局悬浮球**：始终显示在其他应用之上，一键展开 / 收起
- **快捷功能面板**：
  - TTS 快速列表选择与播报
  - 车外喊话一键切换
  - 主题切换（浅色 / 深色）
  - **智能自动收起**：无操作可配置时长自动收起（默认 10 秒，范围 5-30 秒，车外喊话中保持展开）
  - **自动收起时长实时显示**：悬浮窗设置面板中实时显示当前配置的秒数
  - **拖动交互**：支持拖动悬浮球调整位置，自动贴边

### ⚙️ 设置与其他

- **音频输出设备**：自定义车内 / 车外输出设备编号
- **主题切换**：支持浅色 / 深色主题，跟随系统或手动切换
- **开机自启**：可配置开机自动启动应用
- **横屏 / 竖屏自适应**：通过 configChanges 避免页面重建
- **字体缩放**：全局字体大小调节（0.7×–1.5×），实时预览
- **usage 路由探测工具**：遍历 usage 0–100 创建静音 AudioTrack，探测实际路由设备/总线/地址
- **音频路由详细信息**：显示基本音频信息、音量设置、系统音频设备列表、系统信息

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
| **LocalBroadcastManager**   | 1.1.0  | 本地广播（通知更新、模块间通信）                              |
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
│  AudioPipeline → HPF → LPF → AFC(NLMS) → Gain →         │
│  SpectralNR → HowlingNotch → AGC（管线模式音频处理）     │
└───────────────────┬─────────────────────────────────────┘
                    │ 依赖
┌───────────────────▼─────────────────────────────────────┐
│                      Data 层                             │
│  AppConfig (SharedPreferences) / TTSRepository           │
│  TTSConfig / FloatingWindowConfig / AudioConfig          │
│  ThemeConfig / MicOutputConfig / MusicConfig             │
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
- **进程级前后台感知**：通过 `ProcessLifecycleOwner` 监听全局前后台状态，优化后台资源占用
- **本地广播通信**：模块间通过 `LocalBroadcastManager` 发送广播通知数据变更，避免频繁 IPC

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
│   │   │   │   │       ├── HighPassFilterProcessor.java                # 二阶 Butterworth 高通（默认 100Hz）
│   │   │   │   │       ├── LowPassFilterProcessor.java                 # 二阶 Butterworth 低通（默认 4000Hz）
│   │   │   │   │       ├── AdaptiveFeedbackCancellationProcessor.java  # NLMS 自适应反馈消除
│   │   │   │   │       ├── GainLimiterProcessor.java                   # 增益+软限幅
│   │   │   │   │       ├── SpectralAndNotchProcessor.java              # 256 FFT 工具类，内含谱减法降噪 + 啸叫陷波两个处理器
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
│   │   │   │   │   ├── TTSItem.java         # TTS 数据模型
│   │   │   │   │   └── FileItem.java        # 文件浏览器数据模型
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
├── README2.md                               # 增强版文档（本文件）
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

**文件**：`domain/audio/player/MusicPlayerManager.java`

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
- 硬件加速解码：请求音频硬件 offload（aDSP 解码）+ 硬解优先
- 智能缓冲控制：针对 ECARX 车机（8155）使用自定义 DefaultLoadControl（minBufferMs=60s、maxBufferMs=90s），减少 CPU 唤醒次数

### 4. PlaylistManager — 播放列表管理器

**文件**：`domain/audio/player/PlaylistManager.java`

**职责**：播放列表数据管理与循环模式逻辑。

**循环模式**：

| 模式   | 常量                    | 行为         |
| ---- | --------------------- | ---------- |
| 全部循环 | `REPEAT_MODE_ALL`     | 列表末尾自动回到开头 |
| 随机播放 | `REPEAT_MODE_SHUFFLE` | 随机选择下一首    |
| 单曲循环 | `REPEAT_MODE_ONE`     | 重复播放当前歌曲   |
| 单曲播放 | `REPEAT_MODE_OFF`     | 当前歌曲结束后停止  |

**线程安全**：所有修改列表的操作均使用 `synchronized` 保护。

**性能优化**：

- `existingPaths` HashSet 增量维护，O(1) 查重替代 O(N) 重建
- 防抖持久化（1s 窗口），合并多次增删为一次序列化
- 序列化在 IO 线程执行，避免阻塞主线程

### 5. AudioFocusManager — 音频焦点管理器

**文件**：`domain/audio/AudioFocusManager.java`

**职责**：统一管理应用内的音频焦点请求与释放，避免内部冲突。

**焦点类型**：

- **永久焦点**（`AUDIOFOCUS_GAIN`）：音乐播放使用
- **瞬时焦点**（`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`）：TTS / 车外喊话使用，支持闪避

**特点**：

- 同应用内切换时手动调度焦点事件（瞬时焦点 → 播放焦点）
- 焦点被外部应用抢占时自动暂停音乐，归还时自动恢复
- 所有 dispatch 通过 `mainHandler.post` 切主线程，避免 Binder 线程回调 ExoPlayer

### 6. AudioOutputManager — 音频输出管理器

**文件**：`domain/audio/micoutput/AudioOutputManager.java`

**职责**：管理音频输出设备（车内 / 车外扬声器）的切换。

**核心方法**：

- `setOutputMode(int mode)` — 设置输出模式（车内=0 / 车外=1）
- `getOutputMode()` — 获取当前输出模式
- `getAudioUsage()` — 获取当前 AudioAttributes.Usage 值
- `getCarAudioUsage() / getExternalAudioUsage()` — 获取指定模式的 Usage 值

### 7. MediaSessionManager — 媒体会话管理器

**文件**：`domain/audio/player/MediaSessionManager.java`

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

**文件**：`service/floating/FloatingWindowService.java`

**职责**：全局悬浮窗的显示、交互、自动收起逻辑。

**核心特性**：

- 悬浮球 + 展开面板双形态
- 无操作可配置时长自动收起（默认 10 秒，范围 5-30 秒，车外喊话中暂停计时）
- 自动收起时长实时显示（"自动收起时长：X 秒"）
- TTS 列表快速选择，点击"添加"按钮跳转 TTS 模块并自动打开编辑对话框
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

**文件**：`domain/audio/micoutput/AudioProcessor.java`、`domain/audio/micoutput/AudioPipeline.java`

**职责**：采用管线模式（Pipeline Pattern）将音频处理拆分为独立的处理器，由管线按注册顺序串联执行。

**核心功能**：

- **AudioProcessor**：定义音频处理器的统一契约（`process`、`reset`、`isEnabled`、`setEnabled`）
- **AudioPipeline**：按注册顺序执行处理器链，支持运行时启用/禁用，统一重置所有处理器状态；单个处理器连续异常超过阈值（5 次）后自动降级禁用，重置时恢复

**设计特点**：

- 单一职责：每个处理器只做一种音频处理，可独立测试和替换
- 处理顺序：`HPF → LPF → AFC NLMS → Gain → SpectralNR → HowlingNotch → AGC`
- 线程安全：在录制线程中单线程调用，无需加锁

### 12. HighPassFilterProcessor — 高通滤波器

**文件**：`domain/audio/micoutput/processor/HighPassFilterProcessor.java`

**职责**：二阶 Butterworth IIR 高通滤波，抑制 DC 偏移、低频胎噪和车身共振。

**核心参数**：

- **采样率**：48000Hz
- **默认截止频率**：100Hz（可配置，clamp 到 [50, 2000]）
- 运行时可通过 `setCutoffFrequency` 修改截止频率并即时生效，换系数后立即 `reset` 清空历史状态防止爆音
- 与低通滤波器配合组成人声带通（约 100~4000Hz）

### 13. LowPassFilterProcessor — 低通滤波器

**文件**：`domain/audio/micoutput/processor/LowPassFilterProcessor.java`

**职责**：二阶 Butterworth IIR 低通滤波，砍掉高频段的啸叫、嘶声与齿音，让车外喊话更清晰、更不易反馈啸叫。

**核心参数**：

- **采样率**：48000Hz
- **默认截止频率**：4000Hz（可配置，clamp 到 [200, 8000]）
- 与高通差分方程结构相同，仅系数公式不同（高通 `(1+cosw0)/2`，低通 `(1-cosw0)/2`）
- 运行时可修改截止频率并即时生效，换系数后立即 `reset` 防止爆音

### 14. AdaptiveFeedbackCancellationProcessor — NLMS 自适应反馈消除

**文件**：`domain/audio/micoutput/processor/AdaptiveFeedbackCancellationProcessor.java`

**职责**：以输出信号为参考，使用归一化最小均方（NLMS）自适应滤波器消除声反馈。

**核心参数**：

- **滤波器阶数**：256 阶
- **MU 步长**：0.3（收敛速度与稳定性平衡）
- **DT_THRESHOLD**：1.5（双讲检测阈值，防止发散）
- **LEAKAGE**：0.001（防止系数漂移）

**设计特点**：

- 硬件 AEC 启用时仍串联运行，消除 HW AEC 后的残余回声
- `xnorm` 计算优化为 O(1)，降低每帧计算量
- 提供 `getLastErleDb()` 供日志输出回波抑制比

### 15. GainLimiterProcessor — 增益+软限幅处理器

**文件**：`domain/audio/micoutput/processor/GainLimiterProcessor.java`

**职责**：对音频采样进行增益放大，并用 tanh 软限幅防止溢出。

**设计特点**：

- 使用 tanh 函数替代 clamp 硬限幅，减少削波失真
- 增益倍数实时可调，响应录制过程中的配置变化

### 16. SpectralAndNotchProcessor — 谱减法降噪 + 啸叫陷波（共享 FFT 工具类）

**文件**：`domain/audio/micoutput/processor/SpectralAndNotchProcessor.java`

**职责**：作为共享 FFT 工具类，内部包含两个实现 `AudioProcessor` 的静态嵌套类，复用同一套 256 点 FFT 实现：

- **SpectralNoiseReduction（谱减法降噪）**：256 点 FFT + 窗函数 + overlap-add 的谱减法实时降低背景噪声
  - **FFT 长度**：256 点（128 频段），从 512 降低以平衡频率分辨率和 8155 CPU 性能
  - **alpha**：过减系数，保留语音谐波
  - **beta**：频谱下限，保留底噪自然度
  - **学习机制**：静音期建立初始噪声谱，后持续自学习更新
- **HowlingNotchFilter（啸叫陷波）**：FFT 频谱峰值检测啸叫频率点，使用 IIR 窄带陷波滤波器抑制
  - **FFT 长度**：256 点
  - **陷波 Q 值**：30（窄带，不伤人声）
  - **抑制深度**：-12dB
  - **最多同时抑制**：3 个啸叫频点

### 17. AutomaticGainControlProcessor — 自动增益控制

**文件**：`domain/audio/micoutput/processor/AutomaticGainControlProcessor.java`

**职责**：以目标 RMS 为导向的自动增益控制，将输出音量稳定在目标水平。

**核心参数**：

- **目标 RMS**：0.3
- **MAX_GAIN**：2.0（限制最大增益，防止 AGC+AFC 正反馈发散）
- **GAIN_CHANGE_LIMIT**：0.02（每帧最大增益变化 ±2%，确保 AFC 能跟踪）
- **限幅**：tanh 软限幅替代 clamp 硬限幅

**设计特点**：

- 用户可开关（麦克风页面第 4 个 Switch，持久化到配置）
- 增益变化率严格限制，避免 AFC 跟不上导致发散
- 放在管线末尾，参考信号经过 AGC 后才保存给下一帧 AFC 使用

### 18. AlbumArtCache — 专辑封面缓存

**文件**：`utils/AlbumArtCache.java`

**职责**：统一管理专辑封面的内存缓存，避免重复解码。

**核心特性**：

- LRU 内存缓存（10MB），存储 512px 采样 Bitmap
- `Entry` 包装类固化 `size`，确保 `sizeOf()` 恒定
- `put()` 同步 decode+存入，双重检查避免并发重复 put
- `get()` 纯读无锁，返回缓存 Bitmap
- `decodeForSize()` 供 Fragment 非标准尺寸一次性解码（不入缓存）
- 仅缓存真正淘汰时回收 Bitmap，外部绝不持有 Entry.bitmap 引用

### 19. AudioMetadataReader — 音频元数据读取

**文件**：`utils/AudioMetadataReader.java`、`utils/WavMetadataReader.java`、`utils/FlacMetadataReader.java`、`utils/M4aMetadataReader.java`

**职责**：格式感知的音频元数据提取，优先使用自解析避免 MediaMetadataRetriever 开销。

**支持格式**：

- **WAV**：RIFF INFO 块 + id3 RIFF 块 + 头计算时长
- **FLAC**：STREAMINFO（时长）+ Vorbis Comment（标题/艺术家）
- **M4A/AAC**：mvhd（时长）+ ilst（标题/艺术家）
- **MP3/其他**：MediaMetadataRetriever 提取

### 20. TTSRepository — TTS 数据仓库

**文件**：`data/repository/TTSRepository.java`

**职责**：TTS 列表的持久化加载/保存（JSON ↔ List<TTSItem>）。

**核心特性**：

- 单例模式，全局唯一数据访问入口
- 内存缓存（cachedItems）减少磁盘读取次数
- LiveData 实现数据驱动的 UI 更新
- 构造函数仅读取，不写入；`initializeIfNeeded()` 显式初始化，首次使用前显式调用
- 同步写入保证并发场景下数据不丢失

### 21. TTSConfig — TTS 配置持久化

**文件**：`data/local/config/tts/TTSConfig.java`

**职责**：管理 TTS 预设文本列表的持久化存储。

**核心方法**：

- `getTTSItems()` — 获取 TTS 列表 JSON
- `setTTSItems(String json)` — 保存 TTS 列表 JSON

### 22. FloatingWindowConfig — 悬浮窗配置持久化

**文件**：`data/local/config/floating/FloatingWindowConfig.java`

**职责**：管理悬浮窗的开关状态、显示位置、透明度、宽度以及 TTS 选择状态等配置。

**配置项**：

- 悬浮窗开关、位置、透明度、宽度
- TTS 选中 UID 列表（`floating_window_tts_uids`）
- TTS 自定义名称映射（`floating_window_tts_names_by_uid`）
- 自动收起时长（5-30 秒）

### 23. FileUtils — 文件工具类

**文件**：`utils/FileUtils.java`

**职责**：提供文件相关工具方法。

**核心功能**：

- 支持的音频格式扩展名列表
- 文件路径规范化
- 文件名提取

### 24. AppExecutors — 线程池工具

**文件**：`utils/AppExecutors.java`

**职责**：提供全局线程池，避免重复创建。

**核心功能**：

- 计算线程池（CPU 密集型任务）
- IO 线程池（磁盘/网络 IO 任务）
- 主线程 Handler（UI 操作）

### 25. ServiceCompat — 服务兼容工具

**文件**：`utils/ServiceCompat.java`

**职责**：提供 Service 相关兼容性处理。

### 26. LrcParser — 歌词解析器

**文件**：`domain/audio/player/LrcParser.java`

**职责**：解析 .lrc 格式歌词文件，提取时间戳和歌词文本。

### 27. PlaybackState — 播放状态模型

**文件**：`domain/audio/player/PlaybackState.java`

**职责**：封装播放状态信息（状态、当前位置、时长、当前歌曲、错误信息）。

### 28. PlaybackCallback — 播放回调接口

**文件**：`domain/audio/player/PlaybackCallback.java`

**职责**：定义播放状态、进度、错误、歌曲完成等回调接口。

### 29. TTSItem — TTS 数据模型

**文件**：`data/model/TTSItem.java`

**职责**：TTS 条目数据模型。

**字段**：

- `uid` — UUID 唯一标识
- `text` — TTS 文本内容
- `customName` — 自定义名称
- `isPlaying` — 播放状态（transient，不参与序列化）

### 30. FileItem — 文件浏览器数据模型

**文件**：`ui/model/FileItem.java`

**职责**：文件浏览器列表项数据模型。

**字段**：

- `path` — 文件路径
- `name` — 文件名
- `isDirectory` — 是否目录
- `size` — 文件大小
- `childCount` — 目录子项数（后台线程预计算）
- `isSelected` — 是否选中

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

也可以运行根目录下方的"构建脚本.bat"，按提示进行即可。

***

## 版本历史

### v1.5.11 (versionCode: 131)

> 自 v1.5.6 以来的累积改动,涵盖 v1.5.7→v1.5.10 全部内容。

### 🚀 性能
- 加大 ExoPlayer 播放缓冲(DefaultLoadControl,maxBuffer 90s、minBuffer 60s),降低 CPU 软解场景下线程唤醒频率
- ExoPlayer 请求音频硬件 offload + 硬解优先 + AudioAttributes(USAGE_MEDIA/CONTENT_TYPE_MUSIC)
- 后台进度更新降频至 1s、播放位置落盘节流至 5s,暂停时兜底落盘
- 引入 ProcessLifecycleOwner 全局前后台感知,App 退后台时自动降频
- AudioVisualizerView 缓存渐变 Shader,去掉 onDraw 逐帧对象分配

### ✨ 新功能
- 新增低通滤波器 LowPassFilterProcessor(二阶 Butterworth IIR),与高通组成人声带通(默认 100~4000Hz),管线顺序 HPF → LPF → AFC → Gain → SpectralNR → HowlingNotch → AGC
- HPF/LPF 截止频率运行时可配(高通 50~2000Hz、低通 200~8000Hz),改系数后立即 reset 防爆音
- 麦克风增益上下限可配(0.1~50 双限幅裁剪)
- 全局字体缩放(0.7×–1.5×),BaseActivity.attachBaseContext 覆写 fontScale
- 设置页"探测 usage 路由",遍历 usage 值看系统实际路由到哪条 bus
- 关于页合规内容(免责声明/隐私/权限/使用须知/开源许可)
- ExoPlayer DefaultRenderersFactory 关闭软解扩展,优先高通硬解码器

### 🐛 修复
- 放大进行中切换车内外,停止后被打回放大前方向
- 主页主动切换车内外未对外通知一致性问题
- 全新安装首次点"放大"路由从车内切到车外(preferExternal 默认值与界面矛盾)
- 侧边抽屉菜单高亮与实际页面错位
- 无悬浮窗权限时闪退:新增 SettingsFragment + MainActivity + FloatingWindowService 三层防御
- 无权限时只 catch BadTokenException 的漏网,放宽为 Exception + safeRemoveView
- 字体缩放后松手跳回上一个页面
- 全新安装扫描音乐后列表不刷新
- TTS 页退后台后可视化动画未停导致持续 CPU 占用
- 从后台返回音乐页播放列表不定位到当前曲目

### 🔧 其他
- 统一音频输出模式整数编码,AudioConfig 常量对齐 AudioOutputManager
- 全局字体缩放涉及的布局集中到 dimens.xml
- 主界面顶栏/底部按钮、文件浏览页工具栏高度统一改为 wrap_content + minHeight
- 关于页/设置页字体大小也集中化,超字号 URL 按字符断行
- AudioForegroundService 删除过时注释

### v1.5.6 (versionCode: 88)

- 🐛 **修复 AlbumArtCache 并发 put 竞态**：`get()` check-then-put 加 `synchronized` 双重检查锁，消除 `sizeOf inconsistent` Crash
- 🐛 **修复 TTSRepository 读路径写磁盘副作用**：构造函数仅读取，新增 `initializeIfNeeded()` 显式初始化
- 🐛 **修复 MicOutputController.init() 非线程安全**：加 `synchronized`，双重检查幂等
- 🐛 **修复 PlaylistManager existingPaths 重复计算**：类成员增量维护，O(1) 查重
- 🐛 **修复 AudioForegroundService notifyUpdate 频繁 IPC**：改为 `LocalBroadcastManager` 本地广播
- ✨ **悬浮窗自动收起时长实时显示**：SeekBar 旁实时显示"自动收起时长：X 秒"
- ✨ **悬浮窗"添加"按钮**：文案改为"添加"，点击跳转 TTS 模块并自动打开编辑对话框
- 🔧 versionCode 86 → 88

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
  - WavMetadataReader 新增 `id3 ` RIFF 块支持（Mp3tag 格式）
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
- 🔧 PlaybackController 移除硬编码 USAGE_MEDIA，完全交由 `updateAudioOutputUsage()` 负责

### v1.5.1 (versionCode: 59)

- 🚀 **音频处理管线全面升级**（汇总 50~59 所有迭代）
- 🚀 **新管线顺序**：
  `HPF(@80Hz) → AFC(NLMS 256阶) → Gain → SpectralNR(512 FFT) → HowlingNotch(FFT+IIR) → AGC(MAX_GAIN=2.0)`
- 🚀 **AFC 自适应反馈消除**：MU=0.3，DT_THRESHOLD=1.5，LEAKAGE=0.001，与 HW AEC 串联消残余
- 🚀 **SpectralNR 谱减法降噪**：512 FFT + Sine 窗 + 50% overlap-add，alpha=1.3 保留语音谐波
- 🚀 **HowlingNotch FFT 啸叫陷波器**：IIR 窄带陷波 Q=30 -12dB，最多同时抑制 3 频点
- 🚀 **AGC 自动增益控制**：目标 RMS=0.3，MAX_GAIN=2.0，硬限幅 ±2%/帧，tanh 软限幅
- 🚀 **HPF 高通滤波器 @80Hz**：一阶 IIR B1=0.969，滤除 DC 和低频噪声
- 🚀 **Android 原生 3A 自适应**：硬件可用时自动禁用对应软件处理器
- 🚀 **AGC 用户开关**：麦克风页面第 4 个 Switch，持久化到配置
- 🐛 修复 HowlingNotch 数组越界、AFC 发散、SpectralNR 人声过减
- 🗑️ 删除旧 AudioSuppressionProcessor.java（能量交叉相关回声 + 宽带啸叫衰减）
- 详细改动见 [CHANGELOG.md](CHANGELOG.md)

### v1.4.x

- 封面缓存、MediaSession、文件浏览器、死代码清理等
- 详细改动见 [CHANGELOG.md](CHANGELOG.md)

### v1.3.x

- 包结构重组、WAV/FLAC/M4A 自解析、路径规范化去重等
- 详细改动见 [CHANGELOG.md](CHANGELOG.md)

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

### Q7：悬浮窗"添加"按钮点击后没有自动打开编辑对话框？

**A**：请确保：

1. 应用已升级到最新版本（v1.5.6 versionCode: 88+）
2. TTS 列表不为空（编辑对话框需要至少一个 TTS 项才能打开）
3. 如仍有问题，请检查日志中是否有 `open_floating_editor` 相关错误

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
