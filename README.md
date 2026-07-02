# L7Audio 音频工具

> 为银河 L7 车型量身打造的 Android 音频工具箱，集音乐播放、麦克风放大、文字转语音（TTS）、悬浮窗控制于一体。

---

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

---

## 项目简介

L7Audio 是一款运行于 Android 系统的音频处理应用，专为吉利银河 L7 车型设计。应用提供车内 / 车外双输出通道的音乐播放、麦克风实时放大（车外喊话）、文字转语音播报等功能，并通过悬浮窗实现快捷操作，满足车主在不同场景下的音频需求。

**开发背景**：基于 Trae AI 辅助开发，全程代码以 AI 生成与优化为主，持续迭代功能与稳定性。

**适用车型**：
- 银河 L7 2023 款
- 银河 L7 2024 款

> 💡 23 / 24 款银河 L7 设置页面的车外输出设备填 **9、15、22** 均可，但音乐模块的车外功能仅支持填 **9**，否则会报错。默认已配置为 9。

---

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
- **三级元数据 fallback 机制**：
  1. **系统 API 优先**：使用 MediaStore / MediaMetadataRetriever 提取元数据
  2. **自解析 fallback**：WAV / FLAC / M4A 格式系统读取失败时，自行解析二进制文件头（RIFF INFO / Vorbis Comment / MP4 ilst）
  3. **文件名兜底**：以上都失败时，使用去扩展名的文件名作为标题，绝不"智能"猜测艺术家，避免搞反
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
  - 噪声门（Noise Gate）：静音低能量帧，保留语音
  - 回声消除（AEC）：能量比检测 + 动态衰减，硬件 AEC 可用时自动切换
  - 啸叫抑制（Howling Suppression）：动态衰减根据啸叫强度自适应调整
  - tanh 软限幅：替代 clamp 硬限幅，减少削波失真
- **车外喊话模式**：一键开启车外喊话，自动切换输出设备
- **防抖保护**：屏蔽快速连续触发（500-2000ms可配置），避免麦克风频繁启停导致啸叫和硬件损伤
- **闲置自动关闭**：无声音输入超时后自动关闭（5-300秒可配置），防止忘记关闭
- **状态同步**：悬浮窗和麦克风页面状态实时同步
- **第三方按键支持**：接收广播 `com.aug32.l7audio.OUTSIDE_MIC_TOGGLE` 触发车外喊话（触发后开启，再次触发关闭）

### 📢 文字转语音（TTS）

- **文本转语音播报**：输入文字即可合成语音输出
- **TTS 列表管理**：支持添加、删除、自定义条目标题
- **持久化存储**：TTS 列表通过 Gson 序列化保存，重启不丢失
- **快速播报**：悬浮窗中可直接选择预设条目一键播报

### 🎯 悬浮窗控制

- **全局悬浮球**：始终显示在其他应用之上，一键展开 / 收起
- **快捷功能面板**：
  - 音乐播放控制（播放 / 暂停、上一曲 / 下一曲）
  - TTS 快速列表选择与播报
  - 车外喊话一键切换
  - 主题切换（浅色 / 深色）
- **智能自动收起**：10 秒无操作自动收起（车外喊话中保持展开）
- **拖动交互**：支持拖动悬浮球调整位置，自动贴边

### ⚙️ 设置与其他

- **音频输出设备**：自定义车内 / 车外输出设备编号
- **主题切换**：支持浅色 / 深色主题，跟随系统或手动切换
- **开机自启**：可配置开机自动启动应用
- **前台服务保活**：基于 WorkManager 的保活机制，降低后台被系统杀死概率
- **横屏 / 竖屏自适应**：通过 configChanges 避免页面重建

---

## 技术架构

### 技术栈

| 类别 | 技术 | 说明 |
|------|------|------|
| 开发语言 | Java | 主体代码采用 Java 11 |
| 最低 SDK | API 30 (Android 11) | 适配车机系统 |
| 目标 SDK | API 30 | 保证车机兼容性 |
| 编译 SDK | API 36 | 使用最新 SDK 编译 |
| 构建工具 | Gradle (KTS) | build.gradle.kts |
| UI 框架 | AndroidX + Material Design | 兼容低版本系统 |

### 核心依赖

| 依赖库 | 版本 | 用途 |
|--------|------|------|
| **Media3 ExoPlayer** | 1.9.2 | 音乐播放核心引擎 |
| **Android MediaSession** | - | 系统媒体中心会话（原生 API，无额外依赖） |
| **AndroidX Media** | 1.7.0 | MediaStyle 通知样式（NotificationCompat.MediaStyle） |
| **Gson** | 2.10.1 | JSON 序列化 / 反序列化（TTS 列表、配置持久化） |
| **WorkManager** | 2.9.0 | 后台保活任务调度 |
| **Lifecycle** | 2.8.7 | ViewModel / LiveData，TTS 页面数据驱动 |
| **Appcompat / Material** | - | UI 组件与主题 |
| **RecyclerView / CardView** | - | 列表展示 |

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
│  AnnouncementController（车外喊话统一管理）               │
│  AudioPipeline → GainLimiterProcessor →                  │
│  AudioSuppressionProcessor（管线模式音频处理）            │
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
│  KeepAliveManager / KeepAliveWorker                      │
└─────────────────────────────────────────────────────────┘
```

**设计原则**：
- **单例管理**：核心管理器（MusicPlayerManager、AudioFocusManager 等）通过 `AudioServiceLocator` 统一管理，采用 DCL 双重检查锁实现懒加载单例
- **关注点分离**：播放控制（PlaybackController）与播放列表（PlaylistManager）职责分离
- **接口抽象**：`MusicSource` 接口预留扩展点，支持未来接入在线音乐等更多音乐源
- **线程安全**：PlaylistManager 所有操作使用 `synchronized` 保证多线程安全

---

## 项目结构

```
L7Audio/
├── app/
│   ├── src/main/
│   │   ├── java/com/aug32/l7audio/
│   │   │   ├── L7AudioApp.java              # Application 入口
│   │   │   ├── base/                        # 基类
│   │   │   │   ├── BaseActivity.java
│   │   │   │   ├── BaseFragment.java
│   │   │   │   └── BaseService.java
│   │   │   ├── domain/audio/                # 音频核心领域层
│   │   │   │   ├── AudioServiceLocator.java # 服务定位器（单例管理）
│   │   │   │   ├── AudioFocusManager.java   # 音频焦点管理
│   │   │   │   ├── AudioOutputManager.java  # 输出设备管理
│   │   │   │   ├── AnnouncementController.java # 车外喊话统一管理（防抖、静音检测、状态同步）
│   │   │   │   ├── MusicPlayerManager.java  # 音乐播放管理
│   │   │   │   ├── MediaSessionManager.java # 媒体会话管理（Android 媒体中心）
│   │   │   │   ├── PlaybackState.java       # 播放状态
│   │   │   │   ├── MusicItem.java           # 音乐条目模型
│   │   │   │   ├── LrcParser.java           # 歌词解析器
│   │   │   │   ├── AudioVisualizerView.java # 音频可视化视图
│   │   │   │   ├── MicrophoneManager.java   # 麦克风管理（协调者）
│   │   │   │   ├── TTSManager.java          # TTS 管理
│   │   │   │   ├── AudioProcessor.java      # 音频处理器接口（管线模式）
│   │   │   │   ├── AudioPipeline.java       # 音频处理管线编排
│   │   │   │   └── processor/               # 音频处理器实现
│   │   │   │       ├── GainLimiterProcessor.java      # 增益+软限幅
│   │   │   │       └── AudioSuppressionProcessor.java  # 噪声门+回声+啸叫抑制
│   │   │   │   ├── player/                  # 播放控制器
│   │   │   │   │   ├── PlaybackController.java
│   │   │   │   │   └── PlaybackCallback.java
│   │   │   │   └── playlist/                # 播放列表
│   │   │   │       ├── PlaylistManager.java
│   │   │   │       ├── MusicSource.java     # 音乐源接口（扩展点）
│   │   │   │       └── ScannedMusicInfo.java
│   │   │   ├── data/                        # 数据层
│   │   │   │   ├── local/
│   │   │   │   │   ├── AppConfig.java       # 配置持久化（总入口）
│   │   │   │   │   └── config/              # 配置分类
│   │   │   │   │       ├── AudioConfig.java
│   │   │   │   │       ├── MusicConfig.java
│   │   │   │   │       ├── MicConfig.java
│   │   │   │   │       ├── TTSConfig.java
│   │   │   │   │       ├── FloatingWindowConfig.java
│   │   │   │   │       └── ThemeConfig.java
│   │   │   │   ├── model/
│   │   │   │   │   └── TTSItem.java         # TTS 数据模型
│   │   │   │   └── repository/
│   │   │   │       └── TTSRepository.java   # TTS 数据仓库
│   │   │   ├── ui/                          # UI 层
│   │   │   │   ├── activity/
│   │   │   │   │   └── MainActivity.java    # 主 Activity
│   │   │   │   ├── fragment/
│   │   │   │   │   ├── MusicPlayerFragment.java
│   │   │   │   │   ├── MicAmplifierFragment.java
│   │   │   │   │   ├── TTSFragment.java
│   │   │   │   │   ├── SettingsFragment.java
│   │   │   │   │   ├── FileBrowserFragment.java
│   │   │   │   │   └── AboutFragment.java
│   │   │   │   ├── viewmodel/
│   │   │   │   │   └── TTSViewModel.java    # TTS 页面 ViewModel
│   │   │   │   ├── adapter/
│   │   │   │   │   ├── MusicPlaylistAdapter.java
│   │   │   │   │   └── FileBrowserAdapter.java
│   │   │   │   └── model/
│   │   │   │       └── FileItem.java        # 文件浏览器数据模型
│   │   │   ├── service/                     # 服务层
│   │   │   │   ├── AudioForegroundService.java
│   │   │   │   ├── FloatingWindowService.java
│   │   │   │   ├── KeepAliveManager.java
│   │   │   │   └── KeepAliveWorker.java
│   │   │   ├── receiver/                    # 广播接收器
│   │   │   │   ├── BootReceiver.java        # 开机自启
│   │   │   │   └── AnnouncementReceiver.java # 车外喊话广播接收（第三方按键控制）
│   │   │   └── utils/                       # 工具类
│   │   │       ├── AppLog.java              # 日志工具
│   │   │       ├── AppExecutors.java        # 线程池
│   │   │       ├── AudioUtils.java          # 音频工具
│   │   │       ├── FileUtils.java           # 文件工具
│   │   │       ├── ServiceCompat.java       # 服务兼容工具
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

---

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
| 模式 | 常量 | 行为 |
|------|------|------|
| 全部循环 | `REPEAT_MODE_ALL` | 列表末尾自动回到开头 |
| 随机播放 | `REPEAT_MODE_SHUFFLE` | 随机选择下一首 |
| 单曲循环 | `REPEAT_MODE_ONE` | 重复播放当前歌曲 |
| 单曲播放 | `REPEAT_MODE_OFF` | 当前歌曲结束后停止 |

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
- 10 秒无操作自动收起（车外喊话中暂停计时）
- TTS 列表快速选择，点击后直接跳转 TTS 模块
- 主题切换按钮，支持浅色 / 深色模式

### 9. AppConfig — 应用配置

**文件**：`data/local/AppConfig.java`

**职责**：基于 SharedPreferences 的配置持久化，统一管理所有用户设置项。

**配置项包括**：主题模式、音频输出设备、循环模式、开机自启、悬浮窗开关、TTS 列表、播放进度等。

### 10. AnnouncementController — 车外喊话统一管理

**文件**：`domain/audio/AnnouncementController.java`

**职责**：集中处理车外喊话的状态切换、防抖、静音检测、焦点管理和状态通知。

**核心功能**：
- **状态管理**：统一管理 `isAnnouncing` 状态，支持多个入口（悬浮窗、麦克风页面、第三方按键）
- **防抖处理**：记录上次触发时间，过滤短时间内的连续触发请求（默认 800ms，可配置 500-2000ms）
- **静音检测**：通过 RMS 音量检测判断是否有声音输入，超时后自动关闭（默认 30 秒，可配置 5-300 秒）
- **焦点管理**：申请短暂独占焦点暂停音乐，结束后释放焦点恢复音乐
- **观察者模式**：`AnnouncementListener` 接口实现悬浮窗和麦克风页面状态同步

**设计特点**：
- DCL 双重检查锁懒加载单例，确保全局唯一
- 支持第三方 APP 通过广播 `com.aug32.l7audio.OUTSIDE_MIC_TOGGLE` 触发控制
- Toast 提示增强：开启/关闭/自动关闭均显示提示

### 11. AudioProcessor / AudioPipeline — 音频处理管线

**文件**：`domain/audio/AudioProcessor.java`、`domain/audio/AudioPipeline.java`

**职责**：采用管线模式（Pipeline Pattern）将音频处理拆分为独立的处理器，由管线按注册顺序串联执行。

**核心功能**：
- **AudioProcessor**：定义音频处理器的统一契约（`process`、`reset`、`isEnabled`、`setEnabled`）
- **AudioPipeline**：按注册顺序执行处理器链，支持运行时启用/禁用，统一重置所有处理器状态

**设计特点**：
- 单一职责：每个处理器只做一种音频处理，可独立测试和替换
- 处理顺序：增益→限幅→噪声门→回声消除→啸叫抑制
- 线程安全：在录制线程中单线程调用，无需加锁

### 12. GainLimiterProcessor — 增益+软限幅处理器

**文件**：`domain/audio/processor/GainLimiterProcessor.java`

**职责**：对音频采样进行增益放大，并用 tanh 软限幅防止溢出。

**设计特点**：
- 使用 tanh 函数替代 clamp 硬限幅，减少削波失真
- 增益倍数实时可调，响应录制过程中的配置变化

### 13. AudioSuppressionProcessor — 三合一抑制处理器

**文件**：`domain/audio/processor/AudioSuppressionProcessor.java`

**职责**：统一管理噪声门、回声消除、啸叫抑制三种算法，共享 reset() 消除状态泄漏。

**核心功能**：
- **噪声门**：RMS < 0.02 完全静音，0.02~0.05 软衰减，> 0.05 不处理
- **回声消除**：互相关法检测能量比在 0.5~2.0 之间的帧，动态衰减
- **啸叫抑制**：连续 3 帧能量 > 历史 1.8 倍判定为啸叫，衰减强度根据啸叫强度动态调整

**设计特点**：
- 三种抑制共享 `reset()`，一次清零所有累积状态，彻底解决状态泄漏
- 每个抑制算法独立开关，互不影响
- 硬件 AEC 可用时自动禁用软件回声消除，避免冲突

---

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

---

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

| 类型 | 路径 |
|------|------|
| Debug APK | `app/build/outputs/apk/debug/L7音频工具-versionName-versionCode-debug.apk` |
| Release APK | `app/build/outputs/apk/release/L7音频工具-versionName-versionCode-release.apk` |

### Release 签名配置

签名信息已内置在 `app/build.gradle.kts` 中：

| 配置项 | 值 |
|--------|-----|
| Keystore 文件 | `../release.keystore` |
| Keystore 密码 | `password123` |
| Key 别名 | `l7audio` |
| Key 密码 | `password123` |

> ⚠️ 生产环境请妥善保管密钥文件和密码，避免泄露。

### 安装到设备

```powershell
# 安装 Debug 包
adb install app/build/outputs/apk/debug/L7音频工具-versionName-versionCode-debug.apk

# 安装 Release 包
adb install app/build/outputs/apk/release/L7音频工具-versionName-versionCode-release.apk
```
也可以运行根目录下方的“构建脚本.bat”，按提示进行即可。

---

## 版本历史

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

---

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

---

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

---

## 联系方式

- **QQ 群**：159045907
- **项目地址**：[GitHub](https://github.com/guoshibu/L7Audio)

---

**享受您的车外音频体验！** 🎧
