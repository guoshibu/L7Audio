# L7Audio CHANGELOG

> 日期：2026-11-03
> 版本：v1.5.12 (versionCode: 149)

---

## v1.5.12 代码审查修复与稳定性 (versionCode: 132→149)

> 自 v1.5.11（versionCode 131）以来的累积改动，涵盖 versionCode 132→149（132→144 为代码审查批次，145→148 为真车测试批次，149 为路径回正批次，另含 2026-11-02 补记项）。
> 其中 132→144 由代码审查驱动：修复 5 个 P0 真实故障、6 个 P1 竞态问题、5 项 P2 整洁与兼容问题，并新增 JVM 单元测试骨架；145→148 为实车日志驱动的修复与显示调整。

### 变更概览

**🐛 修复（P0 用户可感知故障）**

- 开机自启动开关关闭无效：`BootReceiver.enable()` 会把配置强制写回 true，用户关不掉
- 第二次车外喊话没有降噪/AGC：硬件效果器释放后未恢复软件处理器；硬件 AGC 还无视用户开关
- 喊话启停阻塞主线程：`MicrophoneManager.stop()` 持锁 `join(1000)`、`toggle()` 在主线程全量初始化 AudioRecord/AudioTrack
- Activity 重建替换 TTSManager：悬浮窗 TTS 监听永久失效、播报中销毁不回调
- TTS 与喊话共用瞬态焦点互踩：改为引用计数，全部释放后才让出焦点

**🐛 修复（P1 竞态与稳定性）**

- 静音超时/录制异常自动关闭缺 `isAnnouncing` 校验，`stopAnnouncement` 未加锁
- 麦克风启动失败回滚输出模式后未通知 UI
- AGC/谱减 `enabled` 非 `volatile`，开关可能延迟生效
- 通知栏异步封面加载未校验路径，切歌串图且污染封面缓存
- `PlaylistManager.removeItems` 锁内写 SharedPreferences
- AFC 输出缺少限幅，滤波器收敛前整数回绕爆音

**🐛 修复（2026-10-24 真车测试批次）**

- 正常说话被静音检测误判为静音、5 秒后自动关闭喊话：改为峰值 RMS 保持，默认阈值 `0.05 → 0.003`，历史默认值一次性迁移（版本 3）
- 音乐“车内/车外”切换无效：ExoPlayer 线程校验抛异常被静默吞掉，`PlaybackController.updateAudioUsage()` 非主线程改为主线程转发
- 车外喊话在应用界面之外失败且完全静默：两个前台服务补 `microphone` 类型与权限，启动失败降级为 `mediaPlayback` 并 Toast 提示
- 悬浮窗用过一次车外喊话后不再自动收起：喊话结束/自动关闭时重新武装自动收起计时器
- 喊话设置保存失败提示写到错误卡片：由音频设备状态位改为“车外喊话设置”状态位

**🚀 性能**

- 喊话启停移出主线程：Controller 内部单线程串行执行器 + 输出模式切换走 IO 线程，`stop()` 改为锁外 join
- `AudioOutputManager.getAudioUsage()` 去锁，消除建流热点路径上的锁内 SP 读取
- 删除 `MicOutputFragment` 5 处重复配置落盘、TTS 监听重试脚手架、仅打日志的 try-catch 等冗余

**🔧 其他**

- P2 整洁：配置读侧 clamp、`Locale.ROOT` 与音频格式判断合并、死代码删除、20 条 deprecation 清零（`LocalBroadcastManager` / `getColor` / `stopForeground` / `startActivityForResult` 等）
- 新增 JVM 单元测试骨架（JUnit 4.13.2，2 个测试类 10 个用例）
- 移除无引用的 `localbroadcastmanager` 依赖
- 关于页版本号追加 versionCode 显示；设置页静音阈值范围提示改为“推荐 0.003~0.01”、输入框 `maxLength` 5→6
- 两个文件移回原路径，消除源码目录与包名不一致（撤销 10-24 被撤回重构的路径残留），并删除空目录 `ui/view/`
- 注释掉无调用方的 `AudioVisualizerView.updateTheme()`（主题切换走 Activity 重建）

### 详细批次记录（versionCode 132→149，按批次倒序排列）

### 🔧 其他（2026-11-02 批次十八：路径回正 + 死代码注释，versionCode 149）

- 🔧 两个文件移回原路径，消除“源码目录与包名不一致”并撤销 10-24 被撤回重构残留的路径：`data/local/MicOutputConfig.java` → `data/local/config/micoutput/MicOutputConfig.java`，`ui/view/AudioVisualizerView.java` → `domain/audio/AudioVisualizerView.java`；`package` 与 import 均未改动，并删除空目录 `ui/view/`
- 🔧 注释掉无调用方的 `AudioVisualizerView.updateTheme()`：主题切换走 Activity 重启重建、视图每次新建，均无调用点；按约定保留原实现便于恢复

### 🔧 其他（2026-11-02 补记：遗漏项补录，versionCode 148 不变）

> 补记说明：以下两项在 132→148 的开发过程中已落地，但此前未写入 CHANGELOG。本次仅补文档，不改代码、不递增 versionCode、不改 versionName。

- 🚀 性能：**音乐页列表刷新双触发**——`MusicPlayerFragment` 添加歌曲完成回调原先无条件 `refreshPlaylist()`，而前台（RESUMED）时反应式 `onPlaylistChanged` 已会刷新，导致同一份列表的 O(N) 拷贝 + DiffUtil 计算跑两遍。改为仅当页面非 RESUMED（反应式回调会被静默丢弃）时才主动刷新
- 🔧 其他：`MicrophoneManager.stop()` 的锁外 `join(1000)` 被中断时补恢复 `Thread.currentThread().interrupt()` 标记，避免中断状态被吞掉后上层无法感知（与既有的"锁外 join、监听器多槽位"修复同一批次）

### 🔧 其他（2026-10-24 批次十七：关于页显示 versionCode，versionCode 148）

- 🔧 关于页版本号增加 versionCode 显示：`版本 1.5.12` → `版本 1.5.12 (148)`，便于车机上区分同一 versionName 下的多次日常构建（`AboutFragment.initViews()` 改用 `PackageInfo.getLongVersionCode()`）

### 🔧 其他（2026-10-24 真车测试批次十六：设置页静音阈值显示修正，versionCode 147）

- 🔧 修复：**喊话设置保存失败时提示打到错误卡片**——`saveAnnouncementSettings()` 的 `NumberFormatException` 分支原先把提示写到 `tvAudioDeviceStatus`（音频设备设置卡片，位于页面上方），用户在"车外喊话设置"卡片里看不到任何反馈。改为写 `tvAnnouncementStatus`
- 🔧 显示：阈值右侧范围由 `0.001-0.3`（合法区间）改为 `推荐 0.003~0.01`，避免用户误以为 0.3 是正常值（填 0.3 会开口即判静音）
- 🔧 显示：阈值输入框 `maxLength` 5 → 6，支持 `0.0025` 这类四位小数（原默认值 0.003 已占满 5 字符上限）
- 🔧 显示：阈值行下方新增说明 `RMS(0~1)，越小越灵敏：0.003≈安静环境，0.01≈行车噪声；可填范围 0.001-0.3`
- 说明：`静音超时` 的 hint 仍为 30（默认值），车机上实际值 5 是用户自定义，非不一致

### 🐛 修复（2026-10-24 真车测试批次十五：静音自动关闭误判，versionCode 146）

> 数据来源：车机 G636 实测日志 1470 帧录音统计——说话时 RMS p50=0.0005、p90=0.001、p99=0.0435，单帧峰值可达 0.3172；旧阈值 0.05 位于 99.4 分位以上。

- 🐛 修复：**正常说话被静音检测误判为静音，5 秒后自动关闭喊话**——原检测读取 `getCurrentRms()`（最近 10 帧≈160ms 的平均值），说话能量是稀疏尖峰，单帧 0.3 混在 9 个近 0 帧里平均只剩 0.03，低于阈值 0.05。实车 17:05:24 那次喊话中帧峰值达 0.3172/0.2555，仍被判静音并在 17:05:34 自动关闭。
  - 修复方式（A：峰值保持）：`MicrophoneManager` 新增 `peakRmsSinceLastRead`，录制循环内对每帧 `currentRms` 取最大值；新增 `getAndResetPeakRms()` 供静音检测每 500ms 读取并清零。只要采样区间内出现过一次高于阈值的声音即视为有声，不再被平均值稀释。停止录音时同步清零，避免残留峰值。
  - 修复方式（B：阈值下调）：静音阈值默认值 `0.05 → 0.003`，合法区间 `[0.03, 0.3] → [0.001, 0.3]`（`MicOutputConfig` 读写两侧与 `SettingsFragment` 保存逻辑同步）；设置页提示更新为 `0.003` / `0.001-0.3`。取值依据：实测真正安静时峰值 RMS ≤0.001、轻声说话 0.004~0.006、正常说话 0.013+，0.01 仍会漏判轻声说话。
  - 一次性迁移：新增 `mic_silence_threshold_version` 标记（当前版本 3），历史默认值 `0.05` / `0.01` 自动迁移为 `0.003`，用户自定义过的其它值保持不动。
- 🔧 日志：静音检测相关日志中的 `RMS=` 改为 `峰值RMS=`，便于区分采样口径。

### 🐛 修复（2026-10-24 真车测试批次十四：车内/车外切换与悬浮窗自动收起，versionCode 145）

> 复现设备：银河 L7 车机（ecarx G636 / Android 11），日志取自 `l7audio_log.txt` 与 logcat。

- 🐛 修复：**音乐"车内/车外"切换无效**（用户可感知故障）——`MainActivity.setAudioOutput()` 整体移到 IO 线程后，`music.updateAudioOutputUsage()` 在 `AppExecutors-IO` 线程调用 ExoPlayer；media3 的 `ExoPlayerImpl.setAudioAttributes()` 会先执行 `verifyApplicationThread()`，直接抛 `IllegalStateException: Player is accessed on the wrong thread`，又被 `PlaybackController.updateAudioUsage()` 的 `catch` 静默吞掉。日志表现为"输出模式已设置"但 `Audio usage updated` 从未成功（16:36:11/24/25 三次全部如此）。
  - 修复方式：`PlaybackController.updateAudioUsage()` 内部判断线程，非主线程时经主线程 Handler 转发（同时覆盖 `MicOutputController.stopAnnouncement()` 这个喊话执行器线程的调用点）。media3 的 `setAudioAttributes()` → `reconfigureAndFlush()` → `flush()` 会释放并重建 AudioOutput，因此主线程调用即可真正切换 usage 路由，无需重启播放。
- 🐛 修复：**悬浮窗用过一次车外喊话后不再自动收起**——喊话按钮点击时 `cancelAutoHideTimer()` 取消定时器，而 `MicOutputListener` 回调只更新 `isAnnouncing` 与按钮文字，从不重新武装定时器。实车日志中列表从 16:36:56 一直挂到 16:37:25 仍未 `hideListView`。
  - 修复方式：`onAnnouncementStateChanged()` 在喊话进行中保持取消（避免打断操作），喊话结束且列表可见时 `resetAutoHideTimer()`；`onAnnouncementAutoClosed()`（静音超时/录制异常自动关闭）同样恢复计时并刷新按钮状态。
- 🐛 修复：**车外喊话在应用界面之外失败且完全静默**——实车日志显示 16:37:06~16:37:14 连续 19 次启动全部失败，logcat 为 `ServiceUtilities: Request denied by app op: 27`、`AudioPolicyIntefaceImpl: getInputForAttr permission denied: recording not allowed for uid 10238`，即系统在 AppOps 层判定"此刻不允许录音"（不是清单权限缺失，`cmd appops get` 为 allow）。而我们的两个前台服务只声明了 `mediaPlayback`，没有 `microphone` 类型，后台录音不具备合法前台服务类型。
  - 修复方式（三项）：
    1. `AndroidManifest.xml`：两个前台服务改为 `foregroundServiceType="mediaPlayback|microphone"`，并补 `FOREGROUND_SERVICE_MICROPHONE` 权限声明
    2. `FloatingWindowService` / `AudioForegroundService`：`startForeground` 加 try-catch，若因录音权限未授予（首装首次启动）导致含 microphone 类型启动失败，降级为仅 `mediaPlayback`，避免服务崩溃
    3. `MicOutputController.startAnnouncement()` 失败分支：补 `notifyStateChanged(false)` + Toast 提示，用户不再"点了没反应"，悬浮窗自动收起定时器也会恢复

### 🔧 其他（2026-10-24 批次十三：最小单测骨架，versionCode 144）

- 🔧 新增 JVM 单元测试骨架：`testImplementation(junit:4.13.2)` + `testOptions.unitTests.isReturnDefaultValues`，测试源码位于 `app/src/test`，不打包进 APK
- 🔧 新增 2 个测试类共 10 个用例：
  - `AudioProcessorSafetyTest`：HPF/LPF/AGC/AFC 静音输入必须输出静音；GainLimiter 4 倍增益 + 满量程输入保持极性（防整数回绕爆音）；关闭态的 HPF/LPF 原样透传
  - `FileItemTest`：8 种受支持扩展名、大写扩展名（`Locale.ROOT` 回归）、非音频/空名/无后缀拒绝
- 🔧 移除已无引用的 `localbroadcastmanager` 依赖（已由 `ContextCompat.registerReceiver` 替代）

### 🔧 其他（2026-10-24 代码审查批次十二：P2 整洁与兼容，versionCode 143）

- 🔧 配置读侧 clamp：`MicOutputConfig` 的防抖间隔/静音超时/静音阈值在 getter 侧也限制到合法区间，历史越界值不再影响运行期行为
- 🔧 音频格式判断统一：`FileItem.isAudioFileName` 提升为 public，`FileBrowserAdapter`/`MusicPlayerFragment` 改为复用，删除两份重复实现；`toLowerCase` 全部改用 `Locale.ROOT`（含文件排序），避免土耳其语等区域判断异常
- 🔧 死代码清理：删除无调用方的 `AudioFocusManager.hasAnyFocus()`、`MicrophoneManager.getPostProcessRms()`
- 🔧 性能：`AudioOutputManager.getAudioUsage()` 去掉 `synchronized`（`currentOutputMode` 已是 volatile），消除建流热点路径上的锁内 SharedPreferences 读取；日志级别 i→d
- 🔧 API 兼容清理：
  - `LocalBroadcastManager` → `ContextCompat.registerReceiver(RECEIVER_NOT_EXPORTED)` + 同应用 `sendBroadcast`，并删除重复 import
  - 9 处 `getResources().getColor()` → `ContextCompat.getColor()`
  - `stopForeground(boolean)` → `ServiceCompat.stopForeground(STOP_FOREGROUND_DETACH)`
  - 悬浮窗授权 `startActivityForResult/onActivityResult` → `registerForActivityResult`
  - `TTSManager` 单参数 `onError` 改为委托给 `onError(String,int)`（保留抽象方法实现，消除重复逻辑并局部抑制废弃警告）；`Thread.getId()` 日志改用线程名
  - `displayAudioRoutes()` 的旧音频路由查询 API 局部 `@SuppressWarnings`（无公开替代接口，仅调试展示）

### 🐛 修复（2026-10-24 代码审查批次十一：P1 竞态与稳定性，versionCode 142）

- 🐛 修复：静音超时/录制异常自动关闭补 `isAnnouncing` 校验，并统一走同步 `stopImmediately()`；`stopAnnouncement` 加锁，避免主线程延时任务与串行执行器上的 `toggle` 交错执行（输出模式二次恢复、焦点重复释放）
- 🐛 修复：麦克风启动失败回滚输出模式后补 `notifyOutputModeChanged()`，界面与悬浮窗不再停留在"车外"状态
- 🐛 修复：`AutomaticGainControlProcessor` / `SpectralNoiseReduction` 的 `enabled` 改为 `volatile`，修复 UI 线程写入后录制线程可能长时间读不到（开关延迟生效）
- 🐛 修复：通知栏异步封面加载校验请求路径与当前歌曲一致，切歌时放弃本次加载，避免封面串图与 `AlbumArtCache` 写错缓存
- 🐛 修复：`PlaylistManager.removeItems` 把 `setLastPlayedIndex` 移出锁外，避免持锁期间写 SharedPreferences 阻塞其它播放列表操作
- 🐛 修复：AFC 自适应回声消除输出加 `[-32768, 32767]` 限幅，消除滤波器收敛前的整数回绕爆音

### 🐛 修复（2026-10-24 代码审查批次十：P0 故障收敛，versionCode 141）

- 🐛 修复（P0）：开机自启动开关关不掉——`SettingsFragment` 无论勾选与否都调用 `BootReceiver.enable()`（内部强制把配置写成 true），关闭操作被立刻覆盖回开启。改为按开关值写配置并同步 `BootReceiver.setEnabled()` 组件状态，同时删除已无调用方的 `enable()` 死代码
- 🐛 修复（P0）：第二次喊话完全没有降噪/AGC——硬件 `NoiseSuppressor`/`AutomaticGainControl` 接管时会关闭对应的软件处理器，但会话结束释放硬件后没有恢复，第二次喊话既无硬件（`initNoiseSuppressor` 因"用户禁用降噪"提前返回）也无软件。新增 `softwareNsSuspendedByHardware`/`softwareAgcSuspendedByHardware` 记录，释放硬件时恢复；用户显式关闭时放弃自动恢复，避免覆盖用户设置
- 🐛 修复（P0）：硬件 AGC 无视用户配置——`initAutomaticGainControl` 未校验 `agcProcessor.isEnabled()`，用户关闭 AGC 后仍会被硬件 AGC 接管
- 🐛 修复（P0）：喊话启停阻塞主线程——`MicrophoneManager.stop()` 在持有对象锁时 `join(1000)`，而录制线程异常退出路径又需要同一把锁调用 `releaseResources()`，两线程互等满 1 秒；`MainActivity.setAudioOutput()` 也在主线程直接 stop/start 麦克风。改为临界区只做标记与唤醒、锁外 join，输出模式切换整体移到 IO 线程
- 🐛 修复（P0）：喊话启停统一异步化——`MicOutputController.toggle()` 内部改为单线程串行执行器执行，悬浮窗按钮、麦克风页面、广播/Intent 入口不再在主线程初始化 AudioRecord/AudioTrack；顺带删除 `MicOutputReceiver`/`MicToggleActivity` 中重复的 IO 线程包装
- 🐛 修复（P0）：Activity 重建导致悬浮窗 TTS 监听失效——`MainActivity` 每次 `onCreate` 都 new 一套 Manager 并替换到 `AudioServiceLocator`，旧 `TTSManager` 被 shutdown 后悬浮窗注册的监听器永久失效、正在喊话被中断。改为复用 `AudioServiceLocator` 的进程级实例；`TTSManager.shutdown()` 在播报中销毁时补发 `onTTSError`，避免瞬态焦点不释放导致音乐永久暂停
- 🐛 修复（P0）：TTS 与喊话共用瞬态焦点互相踩踏——`AudioFocusManager` 的瞬态独占焦点改为引用计数，任一方结束只递减计数，全部释放后才真正让出焦点并恢复音乐；`AUDIOFOCUS_LOSS`/`LOSS_TRANSIENT` 时同步清零计数，修复瞬态焦点标志残留导致后续播放焦点申请被永久拒绝
- 🔧 其他（简洁化）：删除 `BootReceiver.enable()`、`PlaylistManager.rebuildExistingPaths()` 等无调用方死代码；`MicOutputFragment` 删除与 `MicrophoneManager` 重复的配置落盘（5 处）；`FloatingWindowService` 删除 TTS 监听的 500ms 延迟重试脚手架（Manager 已进程内常驻，不会为 null）；`MicOutputController.startAnnouncement` 删除仅打日志的两段 try-catch

### 🐛 修复（2026-10-23 代码审查批次九：新发现问题与代码整洁，versionCode 140）

- 🐛 修复：`MicToggleActivity` 同步 toggle 调用未捕获异常，音频系统异常（麦克风被占用、AudioRecord 初始化失败）时 Activity 崩溃。`AppExecutors` IO 线程内补 try-catch，仅记录日志不影响 finish
- 🔧 其他：`BootReceiver` 新增 `setEnabled()` 静态方法，支持运行时通过 `PackageManager.setComponentEnabledSetting()` 动态启用/禁用组件，相比仅靠 SP 开关可彻底阻止系统投递广播
- 🐛 修复：`AudioForegroundService.start()` 统一 catch Exception，无法区分 Android 12+ 后台启动限制、权限不足、状态异常。细分为 ForegroundServiceStartNotAllowedException（静默降级）、SecurityException（权限日志）、IllegalStateException（状态日志）、Exception（兜底）
- 🔧 其他（P2 整洁）：`MicOutputController` 提取魔法数字为常量：SILENCE_CHECK_INTERVAL_MS=500、STARTUP_GRACE_MS=3000
- 🔧 其他（P2 整洁）：`MicrophoneManager` 提取魔法数字为常量：STATS_LOG_INTERVAL_FRAMES=100、AFC_REPORT_INTERVAL_FRAMES=500，替换全部硬编码帧数值

### 🐛 修复（2026-10-23 代码审查批次八：P2 余项与结构小项，versionCode 139）

- 🐛 修复（P2-3）：`PlaybackController.resume()` 在 ExoPlayer 未就绪时 seek 到 TIME_UNSET 位置，可能导致播放异常。新增 TIME_UNSET 守卫，仅在进度有效时才执行 seek
- 🐛 修复（P2-5）：`MusicPlayerManager.getCurrentPosition()` 在 ExoPlayer 返回 TIME_UNSET 时回退到同步读 SharedPreferences，造成主线程阻塞。改为内存缓存 lastKnownPosition（构造时 SP 初始化一次，onProgressChanged/pause 刷新），回退直接读内存
- 🐛 修复（P2-6）：`AudioForegroundService` 通知防抖命中时直接 return，导致播放状态/进度被吞掉。命中防抖时仍执行 refreshPlaybackState()，并增加 trailingUpdate 保证最终一致性
- 🐛 修复（P2-7）：`AudioForegroundService.start()` 未捕获 SecurityException，Flyme Auto/QNX 定制 ROM 上 overlay 权限异常时崩溃。改包 try-catch Exception 兜底
- 🐛 修复（P2-9）：`FloatingWindowService.notifyThemeChanged()` 直接 startService，Android 8.0+ 后台启动限制可能抛 IllegalStateException。增加 try-catch 兜底
- 🐛 修复（P2-16）：`BaseActivity.attachBaseContext()` 每次从 SharedPreferences 读取字体缩放，主线程阻塞。改为静态缓存（volatile），字体变更后主动失效
- 🐛 修复（P2-18）：`PlaylistManager.addItemsInternal()` 锁内执行 normalizeFilePath（含 IO 操作），且空列表时仍触发 debounceSave。路径归一化移出锁外预计算，空列表时跳过保存
- 🐛 修复（P2-21）：`FileBrowserFragment` 异步回调未校验视图状态，onDestroyView 后回调触发 NPE。两处 runOnUiThread 回调增加 getView() 判空
- 🐛 修复（P2-22）：`HighPassFilterProcessor` 与 LowPassFilterProcessor 互斥策略不一致——process/setCutoffFrequency/reset/setEnabled/isEnabled 未加 synchronized，且 setEnabled(true) 未补 reset()。已对齐加锁并补 reset
- 🐛 修复（P2-26）：`MicOutputConfig` 各配置项无范围校验，外部传入异常值导致处理器行为异常。写侧增加 clamp：maxAmplification[1,50]、hpfCutoff[50,2000]、lpfCutoff[200,8000]、minGain[0.1,maxAmp]、debounce[500,2000]ms、silenceTimeout[5,300]s、silenceThreshold[0.03,0.3]
- 🐛 修复（P2-29）：`MusicPlaylistAdapter.removeMusicItems()` 为死代码；`BaseFragment.rootView` 注释语义不清。删除死代码并补注释
- 🔧 其他（结构优化）：`MicrophoneManager.start()` 中 getStackTrace() 构建调用方日志在 Release 下无意义，改为仅 DEBUG 保留；两处 `agcProcessor.reset()` 与 `pipeline.reset()` 重复，删除冗余调用
- 🔧 其他（结构优化）：`SpectralAndNotchProcessor.isVoiceActive()` 每帧对 129 频点连乘求几何均值，极易下溢为 0 导致 pow(0, 1/n) 失真。改为 log 域 exp(Σlog(x)/n) 保证数值稳定
- 🔧 其他（结构优化）：`GainLimiterProcessor.fastTanh()` 仅钳输出，输入侧无限制。高增益场景下输入可达数十，多项式严重发散。输入侧补 clamp 到 ±4 有效域，输出侧保留 [-1,1] 钳位
- 🔧 其他（自查）：`MediaSessionManager.updatePlaybackState()` 的 speed 恒为 1.0f，暂停时进度仍会虚走。改为 isPlaying ? 1.0f : 0.0f
- 🔧 其他（自查）：`AudioServiceLocator` 单例字段已确认全部 volatile，appContext 全程 synchronized 保护，无需修改
- 🔧 其他（自查）：`TTSFragment` 晚到回调（onTTSDone/onTTSError）未判空，onDestroyView 后 ttsItems 置空但回调在途。已补 null 校验

### 🐛 修复（2026-10-23 代码审查批次七：链路 C1/C6/C7/C8 收尾，versionCode 138）

- 🐛 修复（C1）：TTS 播放不检查焦点授予结果——`playTTS` 中 `ttsPausedMusic` 只看输出模式不看 `requestTransientFocus` 的 granted 结果；焦点被拒时音乐实际未暂停，TTS 播完却 `abandonTransientFocus` 释放本不持有的焦点，可能误伤其他应用焦点栈。焦点被拒时撤掉标记，仅有权释放时才置位
- 🐛 修复（C6）：删除正在播放的歌曲后播放不同步——`PlaylistManager.removeItems` 把 currentIndex 置 -1 并回调 `onCurrentIndexChanged`，但 `MusicPlayerManager` 的回调实现是空操作：ExoPlayer 继续播放已删除文件，MediaSession/通知栏仍显示该曲"播放中"。索引变 -1 时主动停止播放、清空当前曲目并按"未播放"同步 MediaSession 与通知栏
- 🐛 修复（C7）：喊话状态监听器仅悬浮窗列表展开期注册——收起期间喊话被广播/Intent/麦克风页启停时，`FloatingWindowService` 的 `isAnnouncing` 缓存不更新，自动收起逻辑与喊话按钮状态错乱。改为 onCreate 注册 / onDestroy 注销的服务存活期全局注册
- 🐛 修复（C8）：切换主题无条件拉起悬浮窗服务——`notifyThemeChanged` 直接 startService，用户已关闭悬浮窗功能时主题变化会把服务拉起、悬浮球凭空出现。增加悬浮窗功能开关 + 悬浮窗权限双重前置校验，未启用/无权限时直接不下发
- 🔧 其他（C5 补齐）：`MicToggleActivity` 同步 toggle 调用同样阻塞主线程（与广播入口同一 ANR 风险面），改经 AppExecutors IO 线程执行
- 📝 说明（C10）：喊话三入口参数差异确认为设计意图——悬浮窗按钮有视觉状态反馈故不弹 Toast（toggle(true) 无 Toast），广播/Intent 两个无头入口无 UI 反馈故弹 Toast（toggle(true,true)），不改代码

### 🐛 修复（2026-10-23 代码审查批次六：链路 C2-C5 + TTS，versionCode 137）

- 🐛 修复（C2）：音频焦点被拒导致假播放中——`MusicPlayerManager.start()` 先写当前曲目/索引/SP，再调 `PlaybackController.play()`；焦点被其他应用持有时 play 静默 return，调用方不知情继续 `notifyMediaSession(playing=true)` + 刷新通知，通知栏/车机媒体中心/方向盘按键全显示"播放中"但实际无声。`play()` 改为返回 boolean（焦点被拒/参数无效/异常均返回 false），start() 失败时按"未播放"同步 MediaSession 与通知栏，索引保留
- 🐛 修复（C3）：播放进度被状态去重吞掉——`setState` 去重命中（同状态同歌曲）时整体 return，每 500ms 的进度更新全部不写 `currentState`，`getCurrentPosition()/getDuration()` 在 ExoPlayer 返回 TIME_UNSET/异常时回退读到状态切换那一刻的陈旧进度。去重只跳过回调通知，状态写入无条件执行
- 🐛 修复（C4）：录制线程异常死亡形成喊话假活——`recordAndPlay` catch 只 `releaseResources()` 不复位 `isRecording`，线程已死但标记为 true：`start()` 幂等分支假成功（实际无声）、UI 持续显示"喊话中"、静音检测读到冻结的旧 RMS 值。catch 中先复位 `isRecording=false` 再释放资源
- 🐛 修复（C4 兜底）：静音检测对死录制线程空转——检测线程遇 `!isRecording()` 只 sleep+continue，录制异常死亡后喊话永久挂着（焦点/车外输出不释放）。宽限期外确认录制已停则自动关闭喊话并通知"录制异常终止"（主线程再校验 isAnnouncing 防竞态）
- 🐛 修复（C5）：广播触发喊话阻塞主线程——`MicOutputReceiver.onReceive` 直接同步调用 `toggle()`，内部 `micManager.start()` 是全量初始化（最坏 4 次 AudioRecord 构造回退 + AudioTrack + 7 处理器 + 硬件效果器），主线程阻塞数十至上百毫秒。改经 AppExecutors IO 线程执行（Controller 整体 synchronized、UI 回调均走 mainHandler，线程安全）
- 🐛 修复：TTS 中文被误判不可用退化成英文——`setLanguage` 判定用 `== LANG_AVAILABLE`，拒绝返回 LANG_COUNTRY_AVAILABLE(1)/LANG_COUNTRY_VAR_AVAILABLE(2) 的车载中文引擎，逐级回退落到英文。改为 `>= LANG_AVAILABLE`（0/1/2 均为成功，负值才失败）
- 🐛 修复：TTS stop() 无回调致焦点悬挂——部分引擎对被 stop() 中断的 utterance 不补发 onDone/onError，悬浮窗苦等 onTTSDone 释放瞬态焦点永远等不到 → 音乐永久暂停。stop() 在确实中断播报时主动补发 onTTSError（各监听方幂等）
- 🔧 其他：TTSManager 的 `isInitialized`/`isSpeaking` 补 volatile——onInit 与 utterance 回调在引擎绑定/Binder 线程写，业务线程经 getter 读，原声明无跨线程可见性保证

### 🐛 修复（2026-10-23 代码审查批次五：自查回归与监听器互踩，versionCode 136）

- 🐛 修复：歌单加载异常导致持久化永久失效——`loadFromStorage` 把 `storageLoaded=true` 放在正常路径末尾，读盘/索引恢复途中抛出未捕获异常（SP 读失败、Gson 内部错误未被内层 catch 覆盖等）时标记永远停留 false，`saveToStorage` 被永久抑制，用户对歌单的一切修改都不再落盘、重启后丢失。整段加载逻辑包 try/finally，标记在 finally 中置位（加载失败时已按空列表运行，持久化不应被一并禁用）
- 🐛 修复：麦克风永久占用的孤儿录音线程——`AudioServiceLocator.registerManagers` 覆盖旧 MicrophoneManager 前未停止其录音，旧实例若在喊话中被覆盖则再无任何引用能触发 stop()，录音线程成孤儿持续占用麦克风并外放；MicOutputController 每次现取新实例，其 stop() 因从未启动而直接返回，旧录音永远无法停止。覆盖前对旧实例 try-catch 调用 stop()（主题切换打断进行中的喊话属可接受降级）
- 🐛 修复：TTS 进度监听器互踩致音乐永久暂停——TTSManager 原为单槽位 setProgressListener，TTSFragment 打开时覆盖悬浮窗服务的监听器、销毁时又一并置 null，悬浮窗 TTS 播完收不到 onTTSDone，瞬态音频焦点永不释放、音乐永久暂停。改为多槽位注册表（CopyOnWriteArrayList，addIfAbsent 防重、逐个 try-catch 分发），TTSFragment 与 FloatingWindowService 各自持有监听器实例、按实例精确注册/注销，互不干扰

### 🐛 修复（2026-10-23 代码审查批次四：P2，versionCode 135）

- 🐛 修复：音频焦点分发并发修改崩溃——`dispatchGained/dispatchLostTransient/dispatchLostPermanent` 在无锁状态下遍历 `listeners`（ArrayList），而注册/注销监听器的方法是 synchronized 的，播放器重建或 Fragment 销毁时并发触发即抛 ConcurrentModificationException。改为锁内清理死弱引用并拷贝快照、锁外遍历回调（避免持锁回调业务代码造成死锁）
- 🐛 修复：显示"播放失败"但引擎正常——喊话/播报启动失败后防抖窗口被自己消耗，用户想立刻重试却在 800ms 内毫无反馈。启动失败（管理器为空、麦克风启动失败）时回滚防抖时间戳，失败不惩罚用户
- 🐛 修复：ExoPlayer 诊断用 AnalyticsListener 在 release() 时未移除——原来以匿名内部类添加且不留引用，无法移除，监听器连同匿名类实例被播放器长期持有。改为字段持有并在 release() 中显式 removeAnalyticsListener
- 🐛 修复：文件浏览器选完目录"毫无反应"或列表错乱——异步目录加载回调未校验视图与路径：adapter 已被 onDestroyView 置空时 setItems 触发 NPE；快速连续进入不同目录时先发出的任务后返回，把旧目录列表覆盖到当前目录上。回填前增加 isAdded/adapter 判空 + requestedPath 与 currentPath 一致性校验
- 🐛 修复：重启应用失败会彻底退出——restartApp 无论能否取到启动 Intent 都执行 Runtime.exit(0)，getLaunchIntentForPackage 返回 null 或 startActivity 抛异常时进程被杀且无人拉起。改为仅在启动 Intent 成功提交后才结束进程，失败时降级为 Activity.recreate()
- 🐛 修复：可视化视图重新 attach 后动画静止——onDetachedFromWindow 为防止泄漏移除全部动画更新监听器，但原实现没有恢复路径，视图复用后即使 startAnimation() 也因无监听器而不刷新。抽出 attachAnimatorUpdateListener()，在 onAttachedToWindow 中按 isAnimating 状态恢复监听器并重启动画
- 🐛 修复：设置页"恢复默认设置"按钮颜色硬编码——布局中写死 `#F44336`，深色模式下刺眼且不随主题变化。抽为 `@color/button_danger` 资源，浅色 `#F44336`、深色 `#B71C1C`

### 🐛 修复（2026-10-22 代码审查批次三：P1 健壮性/性能，versionCode 134）

- 🐛 修复：麦克风缓冲区静态求值崩溃路径——BUFFER_SIZE 是类加载时求值的 static final，若此时音频系统异常 getMinBufferSize 返回 -1，录制循环 `new byte[BUFFER_SIZE]` 直接抛 NegativeArraySizeException 且每次必现。改为 AudioRecord 初始化成功后从真实缓冲区反推 activeBufferSize（getBufferSizeInFrames×2），AudioTrack 构建与录制循环统一使用该动态值
- 🐛 修复：stop() 与录制线程异常路径并发释放资源——录制线程 catch 直接调 releaseResources() 而 stop() 也在调用，两个线程并发 stop/release 同一对象（字段无同步）；stop() join(1000) 超时后线程可能仍在使用已置 null 的 audioRecord。releaseResources 加 synchronized（与 stop 同一把对象锁）串行化
- 🐛 修复：悬浮窗 Service 销毁时 TTS 进度监听器泄漏——TTSManager 为静态单例链持有，匿名 progressListener 未注销会把整个 Service（含全部 View）钉在内存里。onDestroy 中 setProgressListener(null)，handler 改为 removeCallbacksAndMessages(null) 清空全部待处理回调
- 🐛 修复：悬浮窗 Service 销毁绕过 Controller 直接停麦克风导致状态脱同步——直接调 MicrophoneManager.stop()，Controller.isAnnouncing 仍为 true（麦克风页显示"正在放大"但麦克风已停），且喊话启动时保存的输出模式恢复逻辑被跳过、输出方向永久停留在车外。改为经 MicOutputController.toggle() 完整停止流程
- 🐛 修复：TTS 连续播报误报失败——speak 前的 isSpeaking() 前置判断在刚 stop() 后（异步生效）恒为 true，走失败分支返回 false，用户看到"播放失败"但引擎正常。删除前置判断，QUEUE_FLUSH 本身就是打断语义直接播报
- 🐛 修复：歌词文件路径无扩展名时 substring(0,-1) 抛异常、歌词静默失败（P2-24 顺带修复）
- 🐛 修复：旋屏后文件浏览器选完目录/文件毫无反应——覆盖层不参与功能页重建，callback 持有被 replace 掉的旧 Fragment 实例，回调内 isAdded() 为 false 静默丢弃。旋屏时先按"用户取消"路径关闭文件浏览器，重开后 callback 自然指向新实例
- 🐛 修复（内存防御）：WAV/FLAC 元数据块分配上界仅受文件大小约束，损坏/被构造的文件可声明与文件等大的 LIST/id3/VORBIS_COMMENT 块，一次性分配等大数组造成内存压力。WavMetadataReader/FlacMetadataReader 增加 4MB 常量上限（对齐 M4aMetadataReader 的正确写法）
- 🐛 修复（内存防御）：歌词文件读取无大小上限，异常/伪造的巨型 .lrc 会造成 3~4 倍内存峰值。增加 1MB/2 万行限制，超限截断并记警告日志
- 🚀 性能：录制数据通路三次全数组遍历合并——处理后 RMS 统计与 short→byte 写回合并为单次遍历，每帧省一次 O(N) 扫描
- 🚀 性能：音频管线每帧反射取处理器名——addProcessor 注册时缓存类名（IdentityHashMap），process() 每帧直接查表，消除 48kHz 实时管线每秒数千次的反射类名查询
- 🚀 性能：通知路径封面深拷贝——MusicItem.copy() 不再逐字节 clone albumArt（各持有方只读不写、共享引用安全），通知刷新极频繁时省去每次几十 KB~数 MB 的数组复制
- 🚀 性能：封面异步加载裸 new Thread 改共享 IO 线程池 + in-flight 去重集合，连续切歌不再并发拉起多线程重复解码
- 🚀 性能：文件浏览器每次点击/全选统计对列表每项做主线程磁盘 stat——FileItem 构造时预计算 isAudio 标志（纯字符串后缀判断），交互零 IO，U 盘大目录不再卡顿
- 🚀 性能：AppLog 每条日志重复创建 SimpleDateFormat——改为 ThreadLocal 缓存实例（多线程安全），高频日志场景消除重复对象创建
- 🚀 性能：歌词高亮每 500ms 全表线性扫描——播放进度单调递增时从上一高亮行继续向后扫（均摊 O(1)），仅 seek 回退时才全表扫描

### 🐛 修复（2026-10-22 代码审查批次二，versionCode 133）

- 🐛 修复：媒体样式通知未绑定 MediaSession token——MediaStyle 不 setMediaSession 时不会被系统识别为媒体通知，在车机通知栏可能按普通通知渲染、无法随播放状态收展。修复：AudioForegroundService 构建通知时绑定 session token（platform token 经 MediaSessionCompat.Token.fromToken 转换，报告原代码直接强转会编译失败），session 为空时跳过绑定，异常兜底不影响通知发出
- 🐛 修复：悬浮窗列表展开失败后悬浮球永久消失——showListView 抛异常时 catch 只打日志，半初始化的列表视图残留窗口且悬浮球不恢复，悬浮窗功能整体失联。修复：catch 中 safeRemoveView 列表视图、移除喊话监听器与自动隐藏回调、恢复悬浮球显示
- 🐛 修复：MainActivity 持有 Activity Context 的内存泄漏——initAudioManagers 把 Activity 本身传给 AudioOutputManager/MicrophoneManager/TTSManager，Manager 生命周期长于 Activity，旋转/重建即泄漏。修复：改传 getApplicationContext()（AudioFocusManager 内部已自行转换无需处理）
- 🐛 修复：重复初始化时旧 TTSManager 引擎连接泄漏——AudioServiceLocator.registerManagers 覆盖 ttsManager 前未 shutdown 旧实例，TextToSpeech 引擎连接悬挂。修复：覆盖前检测旧实例存在且非同一对象时 try-catch 调用 shutdown()
- 🐛 修复：BaseFragment.rootView 不释放——Fragment 视图销毁后 rootView 仍持有视图树，与 ViewPager/导航场景叠加形成泄漏环。修复：新增 onDestroyView() 置空 rootView，ViewCache 语义修正为根视图引用
- 🐛 修复：PlaylistManager 冷启动主线程同步读盘——构造函数同步执行 SP 读取 + 全量 JSON 解析（上限 1000 条），触发链路在主线程，大歌单阻塞可达数百毫秒。修复：改 IO 线程异步加载，加载完成前 saveToStorage 由 storageLoaded 抑制（防止空列表覆盖真实数据丢歌单），加载时与窗口期新增歌曲做规范化路径去重，完成后主线程通知刷新
- 🐛 修复：播放列表持久化在锁内做 O(N) 序列化——saveToStorage 整体（含 gson.toJson）持 lock 执行，1000 条序列化期间阻塞主线程 getAllItems()/getItemCount() 等同锁读取。修复：锁内仅做引用快照拷贝（极快），序列化移至锁外 IO 线程
- 🐛 修复：播放列表排序比较器嵌套三元运算符逻辑错误——areContentsTheSame 中嵌套三元右结合导致 artist 比较结果被 duration 覆盖，内容变化误判为未变化，DiffUtil 不刷新。修复：改为 Objects.equals(title) && Objects.equals(artist) && duration== 的明确组合

### 🐛 修复（2026-10-22 代码审查批次，versionCode 132）

- 🐛 修复：开机自启功能整体失效——Manifest 中 BootReceiver 被声明为 `android:enabled="false"`，而全工程没有任何 `setComponentEnabledSetting` 恢复代码，组件被永久禁用，BOOT_COMPLETED 广播永远收不到（设置页开关只写 SharedPreferences，接收器根本不在线）。移除 enabled="false" 恢复组件启用，是否自启仍由接收器内部 `AppConfig.isAutoStartOnBoot()` 开关控制
- 🐛 修复：悬浮窗 TTS 播报失败后音乐被永久暂停——`playTTS` 仅在 `speakWithUsage` 抛异常时回滚瞬时音频焦点，而 speak 返回 false 的失败路径（TTS 引擎不可用等）只打日志不回滚，申请到的焦点无人释放，音乐无法自动恢复。改为 try-finally：播报失败（异常或返回 false）时释放焦点并复位 ttsPausedMusic；仅在实际拿到焦点时才 abandon
- 🐛 修复：通知栏/MediaSession 与真实播放状态脱节——`handleStateChanged` 在 callback==null（Fragment 退后台/未注册回调）时直接 return，连通知与 MediaSession 同步也一并跳过；且 STOPPED/IDLE 分支完全不做同步，停止后通知残留"播放中"。重构为状态同步独立于 UI 回调执行，STOPPED/IDLE 补齐 MediaSession + 通知栏同步
- 🐛 修复：横屏音乐页歌词功能整体失效——layout-land 的歌词区只有无 ID 的 ScrollView + tv_lyrics，与 Fragment 绑定的 sv_lyrics/ll_lyrics_container/tv_lyrics_placeholder 不匹配，llLyricsContainer 为 null 后歌词加载/高亮全部静默跳过。横屏布局对齐竖屏 ID 结构，恢复歌词滚动与逐行高亮
- 🐛 修复：应用重启后同一首歌可重复添加进播放列表——`rebuildExistingPaths()` 重建去重集合时直接存原始路径，而查重侧（addItemsInternal）用 normalizeFilePath 规范化路径（小写+正斜杠），持久化恢复后两侧口径不一致必然查重失败。重建集合统一改用 normalizeFilePath
- 🐛 修复：车外喊话高增益下回绕爆音隐患——GainLimiterProcessor 的 fastTanh 多项式近似仅在 |x|≲1.1 内收敛，超出后输出超过 1（x=2 时约 5.73），`(short)` 强转整数回绕产生刺耳爆音（默认放大级别不触发，用户调高放大级别即触发）。多项式结果先钳位到 [-1,1] 再强转
- 🐛 修复：设置页"恢复默认"会意外清栈重启应用——恢复默认时程序化 `themeRadioGroup.check()` 会触发主题监听器，以 NEW_TASK|CLEAR_TASK + finishAffinity 重启整个应用。新增 suppressThemeCallback 抑制标志包裹程序化回填，主题配置已随 prefs.clear() 复位为默认（跟随系统），重启后自然生效

### ✨ 新功能

- ✨ 新功能：麦克风放大新增低通滤波器（LowPassFilterProcessor，二阶 Butterworth IIR），与既有高通组成"人声带通"（默认 100~4000Hz），砍掉 4kHz 以上的高频啸叫/嘶声/齿音，让车外喊话更清晰、更不易反馈啸叫；管线顺序为 HPF → LPF → AFC → 增益 → 降噪 → 陷波 → AGC
- ✨ 新功能：HPF/LPF 截止频率可在设置页配置并运行时即时生效（无需重启喊话）——高通 50~2000Hz、低通 200~8000Hz，另提供低通总开关；改截止频率时处理器会重算系数并 reset() 清空历史状态，避免换系数瞬间爆音（借鉴 Hey 项目滤波器可配思路）
- ✨ 新功能：麦克风增益范围（上下限）可配——最大放大倍率上限从 20 放宽到 50，新增"最小增益倍数"（下限，0.1~最大值），放大倍数按 [minGain, maxAmp] 双限幅裁剪；配置保存后运行时即时生效
- ✨ 新功能：设置页调试区新增“探测 usage 路由 (0~100)”——遍历 0~100 的 AudioAttributes.usage 值，逐个建一条静音 AudioTrack 并读取系统实际路由到的设备（bus/扬声器/地址），生成“usage→路由设备”映射表；后台线程执行不阻塞 UI，结果同步显示在文本框并写入日志文件（adb 可拉取）。用于确认车外喊话/音乐播放应使用哪个 usage 值才能命中目标 bus（如亿咖通 vendor usage → bus4 外部喇叭），补齐此前仅能列静态设备清单、无法看到 usage 路由映射的短板
- ✨ 新功能：关于页新增合规内容——免责声明、隐私说明、权限说明、使用须知（车外喊话/音量放大合规使用边界）、开源许可（media3 Apache 2.0）五张卡片
- ✨ 新功能：设置页新增“字体大小”滑动条（0.7×–1.5×，步进 0.05），实时预览、松手即时全局生效，并提供重启应用兜底
- ✨ 新功能：引入全局字体缩放能力——通过 BaseActivity.attachBaseContext 覆写 Configuration.fontScale，用户可统一放大/缩小全应用字体（悬浮窗不受影响）
- ✨ 新功能：字体缩放滑动条现在联动放大带文字的按钮（按钮高度随字体自适应）

### 🐛 修复

- 🐛 修复：放大（喊话）进行中在主页切换"仅车内/仅车外"，停止放大后会被打回放大前的方向——根因是喊话启动瞬间用 savedOutputMode 记下"放大前输出模式"用于结束后恢复，而放大中途在主页手动切换只改了当前输出模式与偏好，未更新这个快照，停止时便用旧快照覆盖了用户的新选择。修复：MicOutputController 新增 syncSavedOutputMode(mode)，仅在放大进行中把用户最新选择同步进恢复目标（与 toggle/start/stop 同为 synchronized 保证互斥）；MainActivity.setAudioOutput 切换后调用该方法，使停止放大恢复到用户最新意图
- 🔧 其他/健壮性：修复主页主动切换车内/车外时未对外通知的一致性欠债——此前只有喊话导致的被动切换（start/stopAnnouncement）会经 notifyOutputModeChanged 同步给注册了 OutputModeListener 的界面，而主页用户主动点"仅车内/仅车外"只刷新自身按钮、未对外广播（目前除主页外无界面显示方向，故无可见故障，但为潜在隐患）。修复：MicOutputController 新增 public broadcastOutputModeChanged(mode) 复用同一通知通道，MainActivity.setAudioOutput 切换后调用，使主动/被动切换共用同一同步通道（主页自身回调再刷一次 updateOutputButtons 幂等无副作用），保证未来新增方向显示的界面也能一致更新
- 🔧 其他/健壮性：统一音频输出模式的整数编码，消除潜在错位隐患——此前 AudioConfig 的常量语义（OUTPUT_MODE_EXTERNAL=0/OUTPUT_MODE_CAR=1）与全项目实际使用的 AudioOutputManager 语义（OUTPUT_CAR=0/OUTPUT_EXTERNAL=1）正好相反，虽然目前业务代码只用后者一套、值能自洽而未显现故障，但常量命名与存储语义矛盾极易误导后续开发。本次把 AudioConfig 的常量对齐为车内=0/车外=1，getOutputMode() 默认值改用 OUTPUT_MODE_CAR（仍为 0，行为不变），并将 AudioOutputManager 的 currentOutputMode 兜底初值由车外改为车内（构造时仍被持久化值覆盖）；三处默认（持久化默认、输出模式兜底、喊话偏好）自此全部统一为"车内"
- 🐛 修复：全新安装后首次点击"放大"会把发声路由从车内切到车外（停止后又显示车内，手动点一次"仅车内/仅车外"后恢复正常）——根因是喊话偏好 preferExternal 与主页显示的车内/车外来自两套状态且默认值相反：首次安装用户从未点过车内/车外，PREF_PREFER_EXTERNAL 尚未持久化，MicOutputController.init() 却硬编码默认车外(true)，而主页按钮依据 AudioOutputManager 的真实输出模式（默认车内）渲染，二者初始就矛盾。修复：init() 在偏好从未写入时改为从 AudioOutputManager.getOutputMode()（与主页按钮同源）派生 preferExternal；字段声明默认也从 true 改为 false（车内），确保"界面显示、点放大后的路由、停止后恢复"三者从首次安装起就一致
- 🐛 修复：侧边抽屉菜单高亮与实际页面错位（在主页却高亮设置/关于，或反之）——根因是 NavigationView 选中态仅在手动点击菜单时更新，而“底部功能按钮切页”“冷启动恢复上次页面”等路径未同步。新增 syncNavCheckedItem 统一同步：主页/麦克风/TTS/音乐均高亮“首页”，设置页高亮“设置”，关于页高亮“关于”，保证菜单高亮始终等于当前页面
- 🐛 修复：无悬浮窗权限时点开设置页“启用悬浮窗”开关闪退（BadTokenException）——新增三层防御：①入口层 SettingsFragment 启动服务前 canDrawOverlays 校验，无权限则 Toast 提示、弹回开关并跳系统授权页，授权返回后自动开启；②冷启动层 MainActivity 按配置拉起服务前复查权限，无权限跳过启动；③服务底层 FloatingWindowService.onCreate 前置权限自检 + addView try-catch 双重兜底，无权限即自停，绝不崩溃
- 🐛 修复：无悬浮窗权限时打开悬浮窗开关仍闪退（三层防御后仍复现）——根因是服务层 addView 只捕获 BadTokenException，而 Flyme Auto / QNX（8155）定制 ROM 无权限时抛出的常是 SecurityException / 普通 RuntimeException 而非该子类，直接穿透兜底崩溃。本次放宽为捕获 Exception 统一自停；onDestroy/视图切换的 removeView 统一走新增的 safeRemoveView 防 IllegalArgumentException（view 未 attach/已移除）；并用抑制标志收敛设置页开关无权限弹回时的 setChecked 再入抖动
- 🐛 修复：字体缩放调整后松手会跳回上一个页面——重建后一次性标记自动返回设置页（不影响其它重建场景）
- 🐛 修复：全新安装扫描音乐后列表不刷新——扫描/添加完成回调直接刷新列表，不再依赖被文件浏览器覆盖时可能置空的反应式回调
- 🐛 修复：TTS 页退后台后可视化动画未停止导致的持续 CPU 占用
- 🐛 修复：从后台返回音乐页时播放列表停留在旧位置、不定位到当前播放曲目——根因是自动滚动仅在 onPlaybackStarted（新曲开始播放）时触发，后台返回时索引未变故不触发。新增 scrollPlaylistToCurrent()：在 updateUIWithCurrentState（onResume/onViewCreated 均会调用）中强制定位到当前播放项，用 post + scrollToPositionWithOffset 等 RecyclerView 布局就绪后直接置顶定位，并绕过 lastScrollToIndex 去重

### 🚀 性能优化

- 🚀 性能：加大 ExoPlayer 播放缓冲（DefaultLoadControl，maxBuffer 90s、minBuffer 60s，起播缓冲保持 2.5s 不变），降低软解场景下 CPU 被唤醒喂 PCM 的频率——本车机（亿咖通 AAOS + 8155）实测 offload=false、audio HAL 只吃 PCM 无 compress offload 通道，音频只能 CPU 软解；加大缓冲让解码线程一次填满、长时间休眠，唤醒频率下降一个数量级，降低后台播放 CPU 表观占用
- 🚀 性能：ExoPlayer 请求音频硬件 offload（aDSP 解码）+ 硬解优先，降低后台播放 CPU 占用
- 🚀 性能：为 ExoPlayer 显式设置 AudioAttributes（USAGE_MEDIA/CONTENT_TYPE_MUSIC），修复因缺少音频属性导致 offload 被平台拒绝（offload=false）的问题
- 🚀 性能：后台进度更新降频至 1s、播放位置落盘节流至 5s，删除 PlaybackController 中无效的假节流死代码；暂停时兜底落盘防止丢进度
- 🚀 性能：引入 ProcessLifecycleOwner 全局前后台感知，App 退后台时自动降低播放进度更新频率
- 🚀 性能：AudioVisualizerView 缓存渐变 Shader，去掉 onDraw 逐帧对象分配

### ✨ UI

- ✨ UI：在 v1.5.9 基础上再次将播放器、麦克风放大器、TTS、悬浮窗列表等界面字体统一 +3sp，进一步提升车机可读性
- ✨ UI：音乐播放器封面右侧的歌曲信息（标题+歌手）整体垂直居中——将标题歌手包进独立子容器与下方歌词各占一半列高（weight 1）并组内 center_vertical 居中，不再被歌词区顶到顶部

### 🔧 其他

- 🔧 其他：补齐全局字体缩放的最后一批布局——将主界面（activity_main）、文件浏览页（fragment_file_browser）、歌单项/文件项（item_music_playlist、item_file_browser）、抽屉头（nav_header）共 5 个布局中写死的字体大小集中到 dimens.xml（新增 text_main_*、text_browser_*、text_item_*、text_nav_* 系列，数值沿用原字面量，纯重构、视觉不变），至此除悬浮窗外全部界面均支持全局 fontScale 缩放
- 🔧 其他：主界面顶栏 ☰/✕、仅车内/仅车外、底部麦克风/TTS/音乐按钮，以及文件浏览页工具栏（←/全选/⌂）、底部取消/确认按钮，高度统一改为 wrap_content + minHeight，字号放大时按钮随之长高、不再裁字（车机常驻横屏，未建 values-land 覆写，横竖屏共用同值，与麦克风/TTS/设置/关于一致）
- 🔧 其他：歌单/文件列表项仅字号随 fontScale 缩放，图标保持固定 dp，避免大字号下图标膨胀、保持列表紧凑
- 🔧 其他：将关于页、设置页的字体大小也统一集中到 dimens.xml（新增 text_about_*、text_settings_* 系列，共 84 处 sp 引用化，纯重构、视觉不变）
- 🔧 其他：关于页超大字号适配——项目链接长 URL 增加按字符断行（breakStrategy），避免大字号下横向溢出被裁；5 张合规卡片正文行距提升至 1.3，超大字号下更易读
- 🔧 其他：字体缩放滑动条松手/保存/recreate 全链路及 BaseActivity.attachBaseContext 增加详细日志（TAG=FontScale），便于排查界面未刷新问题
- 🔧 其他：attachBaseContext 的 fontScale 日志改用 AppLog 落盘，便于导出排查
- 🔧 其他：删除 AudioForegroundService 中不存在的 KeepAliveWorker 过时注释

### 字体尺寸对照（本批 +3sp 后的基准值）

| 文件 | 元素 | 修改 |
|------|------|------|
| [view_floating_list.xml](app/src/main/res/layout/view_floating_list.xml) | 标题/按钮/标签 | 26sp→29sp, 20sp→23sp, 22sp→25sp |
| [fragment_music_player.xml](app/src/main/res/layout/fragment_music_player.xml) | 歌曲信息/时间/按钮 | 24sp→27sp, 19sp→22sp, 17sp→20sp, 21sp→24sp |
| [fragment_music_player.xml (land)](app/src/main/res/layout-land/fragment_music_player.xml) | 横屏布局同步增大 | 20sp→23sp, 16sp→19sp, 14sp→17sp, 18sp→21sp |
| [fragment_mic_amplifier.xml](app/src/main/res/layout/fragment_mic_amplifier.xml) | 状态/标签/按钮 | 22sp→25sp, 19sp→22sp, 17sp→20sp, 28sp→31sp |
| [fragment_tts.xml](app/src/main/res/layout/fragment_tts.xml) | 输入框/按钮 | 19sp→22sp |
| [item_floating_tts.xml](app/src/main/res/layout/item_floating_tts.xml) | TTS列表项按钮 | 18sp→21sp, 16sp→19sp |

---

## v1.5.9 UI字体增大优化 (versionCode: 105)

- ✨ **UI字体增大优化**：全界面字体统一增大 3sp，提升可读性
- 🚀 **性能优化**（v1.5.8 累积）：内存泄漏修复、对象复用、线程安全优化、资源管理、缓存优化

---

## v1.5.8 性能优化 (versionCode: 104)

### 🚀 性能优化

| 文件 | 修改内容 |
|------|----------|
| [FloatingWindowService.java](app/src/main/java/com/aug32/l7audio/service/floating/FloatingWindowService.java) | 匿名 Runnable 改为静态内部类 + WeakReference，防止 Handler 内存泄漏 |
| [MicOutputController.java](app/src/main/java/com/aug32/l7audio/domain/audio/micoutput/MicOutputController.java) | 静音检测循环：String.format → DecimalFormat 复用，Debug 日志加开关 |
| [MicOutputController.java](app/src/main/java/com/aug32/l7audio/domain/audio/micoutput/MicOutputController.java) | 监听器列表改用 CopyOnWriteArrayList，移除 synchronized 和副本创建 |
| [MicOutputController.java](app/src/main/java/com/aug32/l7audio/domain/audio/micoutput/MicOutputController.java) | 静音检测线程中断处理优化：try-catch-finally 结构，恢复中断状态 |
| [PlaylistManager.java](app/src/main/java/com/aug32/l7audio/domain/audio/player/PlaylistManager.java) | MediaMetadataRetriever 改用 try-with-resources，确保资源释放 |
| [PlaylistManager.java](app/src/main/java/com/aug32/l7audio/domain/audio/player/PlaylistManager.java) | normalizeFilePath 添加 LruCache 缓存，减少文件系统调用 |

### 技术说明

- **内存泄漏修复**：静态内部类不持有外部类引用，WeakReference 自动释放
- **对象复用**：DecimalFormat 静态常量复用，避免每次格式化创建对象
- **线程安全**：CopyOnWriteArrayList 读操作无锁，写操作加锁但读不受影响
- **资源管理**：try-with-resources 保证异常情况下也能释放资源
- **缓存优化**：LruCache 缓存路径规范化结果，容量 500 条

---

## v1.5.8 扫描音乐状态显示优化 (versionCode: 103)

### 扫描状态显示

| 文件 | 修改内容 |
|------|----------|
| [fragment_music_player.xml](app/src/main/res/layout/fragment_music_player.xml) | 在按钮上方添加扫描状态 TextView，默认隐藏 |
| [fragment_music_player.xml (land)](app/src/main/res/layout-land/fragment_music_player.xml) | 横屏布局同步添加扫描状态 TextView |
| [MusicPlayerFragment.java](app/src/main/java/com/aug32/l7audio/ui/fragment/player/MusicPlayerFragment.java) | 将 Toast 提示替换为状态 TextView，扫描开始时显示，完成时隐藏 |

### 技术说明

- **替换逻辑**：扫描开始时 `tvScanStatus.setVisibility(View.VISIBLE)`，扫描完成回调中 `tvScanStatus.setVisibility(View.GONE)`
- **布局位置**：状态文字位于"播放列表"标题上方，居中显示，不影响原有按钮布局
- **空指针保护**：所有视图操作均添加 null 检查

---

## v1.5.8 UI字体调整与悬浮窗位置优化 (versionCode: 102)

### UI字体调整

| 文件 | 元素 | 修改 |
|------|------|------|
| [view_floating_list.xml](app/src/main/res/layout/view_floating_list.xml) | 标题/按钮/标签 | 22sp→26sp, 17sp→20sp, 19sp→22sp |
| [fragment_music_player.xml](app/src/main/res/layout/fragment_music_player.xml) | 歌曲信息/时间/按钮 | 20sp→24sp, 16sp→19sp, 18sp→21sp, 14sp→17sp |
| [fragment_mic_amplifier.xml](app/src/main/res/layout/fragment_mic_amplifier.xml) | 状态/标签/按钮 | 18sp→22sp, 16sp→19sp, 14sp→17sp, 24sp→28sp |
| [fragment_tts.xml](app/src/main/res/layout/fragment_tts.xml) | 输入框/按钮 | 16sp→19sp |
| [item_floating_tts.xml](app/src/main/res/layout/item_floating_tts.xml) | TTS列表项按钮 | 14sp→18sp, 12sp→16sp |

### 悬浮窗位置优化

| 文件 | 修改内容 |
|------|----------|
| [FloatingWindowService.java](app/src/main/java/com/aug32/l7audio/service/floating/FloatingWindowService.java) | 悬浮窗列表跟随悬浮球位置显示，添加边界检查防止超出屏幕 |

### 技术说明

- **字体调整策略**：主标题/大按钮 +4sp，普通文本/按钮 +3sp，辅助文本 +3-4sp
- **悬浮窗定位**：使用 `appConfig.getFloatingWindowX/Y()` 获取悬浮球位置，`post()` 延迟测量确保高度正确，边界检查预留 20px 边距

---

## v1.5.8 AFC 调试日志增强 (versionCode: 101)

### 新增日志

| 文件 | 日志内容 |
|------|----------|
| [MicrophoneManager.java](app/src/main/java/com/aug32/l7audio/domain/audio/micoutput/MicrophoneManager.java) | AFC 参考信号设置日志（每100帧），显示参考RMS和当前帧RMS |
| [MicrophoneManager.java](app/src/main/java/com/aug32/l7audio/domain/audio/micoutput/MicrophoneManager.java) | AFC 每帧调试日志：ERLE、系数范数、RMS |
| [MicrophoneManager.java](app/src/main/java/com/aug32/l7audio/domain/audio/micoutput/MicrophoneManager.java) | AFC 生效提示：ERLE > 6dB 时输出 INFO 级别日志 |
| [MicrophoneManager.java](app/src/main/java/com/aug32/l7audio/domain/audio/micoutput/MicrophoneManager.java) | AFC 效果不佳警告：ERLE < 1dB 且运行超过1000帧时输出 WARN 级别日志 |
| [AdaptiveFeedbackCancellationProcessor.java](app/src/main/java/com/aug32/l7audio/domain/audio/micoutput/processor/AdaptiveFeedbackCancellationProcessor.java) | 新增 getCoefficientNorm() 方法，用于判断滤波器是否收敛 |

### 日志解读指南

**正常日志（修复生效）：**
```
AFC参考信号已设置, 参考RMS=0.0234, 当前帧RMS=0.0123
AFC帧1000: ERLE=8.5dB, 系数范数=0.123, RMS=0.0123
AFC回声消除生效! ERLE=8.5dB
```

**异常日志（参考信号问题）：**
```
AFC帧1500: ERLE=0.5dB, 系数范数=0.001, RMS=0.0123
AFC效果不佳! ERLE=0.5dB, 请检查参考信号是否正确
```

---

## v1.5.8 AFC 参考信号修复 (versionCode: 100)

### 核心修复

| 文件 | 问题 | 修复 |
|------|------|------|
| [MicrophoneManager.java](app/src/main/java/com/aug32/l7audio/domain/audio/micoutput/MicrophoneManager.java) | AFC 参考信号断裂：传入处理后的麦克风信号而非扬声器输出信号 | 用上一帧管线输出作为参考信号，修复时序逻辑 |

### 修复原理

1. **新增 `lastOutputSamples` 数组**：保存上一帧管线处理后的输出
2. **调整调用时序**：`setReference()` 移到 `pipeline.process()` 之前，传入上一帧输出
3. **保存当前帧输出**：`pipeline.process()` 之后用 `System.arraycopy()` 保存到 `lastOutputSamples`

### 技术说明

- **延迟影响**：引入约 2.67ms 延迟（一帧），远小于车内声学传播延迟（10-50ms），可忽略
- **原理**：扬声器播放的是管线输出信号，经过声学传播后被麦克风采集形成回声。AFC 需要用这个即将被播放的信号作为参考，才能学习真实回声路径

---

## v1.5.8 日志中文化 (versionCode: 99)

### 日志中文化

| 文件 | 修改内容 |
|------|----------|
| [AudioOutputManager.java](app/src/main/java/com/aug32/l7audio/domain/audio/micoutput/AudioOutputManager.java) | 5处日志字符串中文化 |
| [AudioPipeline.java](app/src/main/java/com/aug32/l7audio/domain/audio/micoutput/AudioPipeline.java) | 4处日志字符串中文化 |
| [MicOutputController.java](app/src/main/java/com/aug32/l7audio/domain/audio/micoutput/MicOutputController.java) | 约30处日志字符串中文化 |
| [MicrophoneManager.java](app/src/main/java/com/aug32/l7audio/domain/audio/micoutput/MicrophoneManager.java) | 约100处日志字符串中文化 |
| [SpectralAndNotchProcessor.java](app/src/main/java/com/aug32/l7audio/domain/audio/micoutput/processor/SpectralAndNotchProcessor.java) | 3处日志字符串中文化 |

### 规划中的修复

| 项目 | 说明 |
|------|------|
| AFC 参考信号修复 | 用上一帧管线输出作为参考信号，修复当前参考信号断裂问题 |

---

## v1.5.8 音频处理器专家审查修复 (versionCode: 98)

### 严重问题修复

| 文件 | 问题 | 修复 |
|------|------|------|
| [AutomaticGainControlProcessor.java](app/src/main/java/com/aug32/l7audio/domain/audio/micoutput/processor/AutomaticGainControlProcessor.java) | AGC 和 GainLimiter 都使用 tanh 软限幅，双重非线性变换导致谐波失真 | AGC 移除 tanh，仅保留增益应用，由 GainLimiter 统一负责软限幅 |
| [SpectralAndNotchProcessor.java](app/src/main/java/com/aug32/l7audio/domain/audio/micoutput/processor/SpectralAndNotchProcessor.java) | 啸叫检测 FFT_SIZE=256，频率分辨率 187.5Hz，陷波器频率偏差可达 93Hz | 啸叫检测独立使用 512 点 FFT，频率分辨率提升至 93.75Hz |

### 重要问题修复

| 文件 | 问题 | 修复 |
|------|------|------|
| [AdaptiveFeedbackCancellationProcessor.java](app/src/main/java/com/aug32/l7audio/domain/audio/micoutput/processor/AdaptiveFeedbackCancellationProcessor.java) | AFC 内循环使用模运算，CPU 开销大 | 位掩码 `& 511` 替换模运算，性能提升 |
| [GainLimiterProcessor.java](app/src/main/java/com/aug32/l7audio/domain/audio/micoutput/processor/GainLimiterProcessor.java) | Math.tanh() 逐样本 native 调用，实时路径性能瓶颈 | 多项式近似 `x - x³/3 + x⁵/5` 替代 Math.tanh() |
| [SpectralAndNotchProcessor.java](app/src/main/java/com/aug32/l7audio/domain/audio/micoutput/processor/SpectralAndNotchProcessor.java) | VAD 仅用能量比值判断，车内非平稳噪声下频繁误判 | 添加谱平坦度检测，结合能量和谱特征双重判断 |
| [SpectralAndNotchProcessor.java](app/src/main/java/com/aug32/l7audio/domain/audio/micoutput/processor/SpectralAndNotchProcessor.java) | 噪声学习时间过短（80ms），噪声模型不可靠 | 延长至 1 秒（375帧），噪声模型更稳定 |

### 次要问题修复

| 文件 | 问题 | 修复 |
|------|------|------|
| [AutomaticGainControlProcessor.java](app/src/main/java/com/aug32/l7audio/domain/audio/micoutput/processor/AutomaticGainControlProcessor.java) | calculateRms() 方法从未被调用，死代码 | 删除未使用方法 |
| [HighPassFilterProcessor.java](app/src/main/java/com/aug32/l7audio/domain/audio/micoutput/processor/HighPassFilterProcessor.java) | 截止频率 80Hz 偏低，胎噪抑制不足 | 提升至 100Hz |
| [AutomaticGainControlProcessor.java](app/src/main/java/com/aug32/l7audio/domain/audio/micoutput/processor/AutomaticGainControlProcessor.java) | TARGET_RMS=0.3 过高，安静时段过度放大背景噪声 | 降至 0.2（约 -14dBFS） |

---

## v1.5.8 全面性能优化 (versionCode: 97)

### 音频处理器性能优化

| 文件 | 优化内容 |
|------|----------|
| [AdaptiveFeedbackCancellationProcessor.java](app/src/main/java/com/aug32/l7audio/domain/audio/micoutput/processor/AdaptiveFeedbackCancellationProcessor.java) | 滤波器长度从 1024 降至 512，添加系数归一化防止数值漂移 |
| [SpectralAndNotchProcessor.java](app/src/main/java/com/aug32/l7audio/domain/audio/micoutput/processor/SpectralAndNotchProcessor.java) | FFT 大小从 512 降至 256，噪声谱更新间隔从每帧改为每 3 帧 |
| [SpectralAndNotchProcessor.java](app/src/main/java/com/aug32/l7audio/domain/audio/micoutput/processor/SpectralAndNotchProcessor.java) | 啸叫检测添加频率锁定机制，避免已激活陷波器附近的频点重复检测 |
| [AutomaticGainControlProcessor.java](app/src/main/java/com/aug32/l7audio/domain/audio/micoutput/processor/AutomaticGainControlProcessor.java) | 增益更新间隔从 10 帧增至 20 帧，使用增量 RMS 计算减少遍历次数 |
| [HighPassFilterProcessor.java](app/src/main/java/com/aug32/l7audio/domain/audio/micoutput/processor/HighPassFilterProcessor.java) | 升级为二阶 Butterworth 高通滤波器，截止频率 80Hz |

### 稳定性优化

| 文件 | 优化内容 |
|------|----------|
| [AudioPipeline.java](app/src/main/java/com/aug32/l7audio/domain/audio/micoutput/AudioPipeline.java) | 添加处理器异常自动降级机制：连续异常超过 5 次自动禁用，重置时恢复 |

### UI/UX 优化

| 文件 | 优化内容 |
|------|----------|
| [FloatingWindowService.java](app/src/main/java/com/aug32/l7audio/service/floating/FloatingWindowService.java) | 悬浮窗启用硬件加速渲染（FLAG_HARDWARE_ACCELERATED） |
| [AudioForegroundService.java](app/src/main/java/com/aug32/l7audio/service/player/AudioForegroundService.java) | 专辑封面异步加载，避免阻塞主线程导致通知更新延迟 |

---

---

## v1.5.7 修复静音检测误判问题 (versionCode: 96)

### 修复的问题

| 文件 | 问题 | 修复 |
|------|------|------|
| MicrophoneManager.java | 静音检测采样到瞬时静音帧导致误判，即使有声音输入也会触发自动关闭 | 实现 RMS 滑动窗口平均（最近10帧），`getCurrentRms()` 返回平均值而非单帧值 |
| MicOutputController.java | 静音检测日志不够详细，无法观察计时进度 | 增强静音检测日志，包含宽限期状态、RMS与阈值对比、计时进度 |

---

## v1.5.7 修复输出模式偏好与反射异常 (versionCode: 95)

### 修复的问题

| 文件 | 问题 | 修复 |
|------|------|------|
| MicOutputController.java | `toggle(true)` 时错误调用 `setPreferExternalMode(true)` 修改用户偏好，导致后续从麦克风页面启动时自动切到车外 | 移除 `toggle()` 中 `setPreferExternalMode(true)` 调用，`forceExternal` 仅控制本次输出模式 |
| MicrophoneManager.java | 反射调用 `getErrorCode()` 在车机 Android 版本上不存在，每次初始化都抛出 NoSuchMethodException | 移除 `getAudioRecordErrorCode()` 方法及相关调用 |

---

## v1.5.7 增强麦克风初始化日志与动态缓冲区 (versionCode: 94)

### 优化的功能

| 文件 | 功能 | 说明 |
|------|------|------|
| MicrophoneManager.java | 动态缓冲区大小 | 每次初始化时动态调用 `getMinBufferSize()` 获取缓冲区大小，避免类加载时获取的静态值在音频系统状态变化后失效 |
| MicrophoneManager.java | 详细初始化日志 | 记录静态 BUFFER_SIZE、动态缓冲区大小、音频源名称、AudioRecord 状态、错误码（API 29+）、sessionId |
| MicrophoneManager.java | 调用堆栈日志 | 在 `start()` 方法中记录调用线程名称、线程 ID 和调用者堆栈，便于定位第三方调用（Intent/Broadcast）问题 |
| MicrophoneManager.java | 资源释放日志 | 在 `releaseResources()` 中记录释放前后的 AudioRecord/AudioTrack 状态，确认资源是否真正释放 |
| MicOutputController.java | 状态前置日志 | 在 `startAnnouncement()` 中记录音乐播放器状态（isPlaying）、AudioFocusManager 状态，便于分析音乐播放与车外喊话的资源竞争 |

### 修复的潜在问题

| 文件 | 问题 | 修复 |
|------|------|------|
| MicrophoneManager.java | BUFFER_SIZE 作为 static final 在类加载时获取，若此时音频系统状态异常则整个生命周期都使用错误值 | 新增 `getBufferSize()` 方法，每次初始化时动态获取缓冲区大小 |

---

## v1.5.7 增强悬浮窗按钮可视性 (versionCode: 93)

### 优化的功能

| 文件 | 功能 | 说明 |
|------|------|------|
| floating_button_light.xml | 按钮边框 | 添加 2dp 浅灰色边框（#BDBDBD），增强浅色模式下按钮边界识别 |
| floating_button_dark.xml | 按钮边框 | 添加 2dp 中灰色边框（#757575），增强深色模式下按钮边界识别 |
| floating_button_dark_selected.xml | 按钮边框 | 添加 2dp 亮灰色边框（#9E9E9E），增强选中状态按钮边界识别 |

---

## v1.5.7 优化通知更新与车外喊话输出模式恢复 (versionCode: 92)

### 优化的功能

| 文件 | 功能 | 说明 |
|------|------|------|
| AudioForegroundService.java | 通知更新防抖 | 添加 200ms 防抖间隔，避免快速切换歌曲时通知栏频繁闪烁 |

### 修复的问题

| 文件 | 问题 | 修复 |
|------|------|------|
| MicOutputController.java | 车外喊话停止后输出模式未恢复，仍保持 OUTPUT_EXTERNAL | 在 `stopAnnouncement()` 中恢复 `savedOutputMode`，同步更新 UI 按钮状态和音乐播放器音频属性 |

---

## v1.5.7 修复悬浮窗深色模式显示问题 (versionCode: 91)

### 修复的问题

| 文件 | 问题 | 修复 |
|------|------|------|
| floating_list_bg_dark.xml | 深色模式下悬浮窗列表背景颜色过浅（#BDBDBD），看起来发白 | 改为 #464646ff，与深色模式按钮背景协调 |
| FloatingWindowService.java | 自动收起时长标签 `tv_auto_hide_label` 未应用主题颜色 | 添加 `applyTextTheme()` 调用 |

---

## v1.5.7 新增第三方 Intent 调用方式 (versionCode: 90)

### 新增功能

| 文件 | 功能 | 说明 |
|------|------|------|
| MicToggleActivity.java | 新增透明 Activity，支持 Intent 调用车外喊话 | 通过 `startActivity` 触发，保证完整初始化音频管线 |
| themes.xml | 新增透明主题 `Theme.L7Audio.Transparent` | 用于 MicToggleActivity，无 UI 显示 |

### 修复的问题

| 文件 | 问题 | 修复 |
|------|------|------|
| MicOutputReceiver.java | 广播触发时 `AudioServiceLocator` 未初始化，导致 Manager 为 null 静默失败 | 广播接收时主动调用 `locator.init()` |

### 变更说明

- **推荐方式**：第三方 APP 使用 Intent `com.aug32.l7audio.ACTION_TOGGLE_MIC` 通过 `startActivity` 调用
- **兼容方式**：保留原有广播 `com.aug32.l7audio.OUTSIDE_MIC_TOGGLE`，但已修复初始化问题

---

## v1.5.6 修复悬浮窗跳转编辑对话框问题 (versionCode: 89)

### 修复的问题

| 文件 | 问题 | 修复 |
|------|------|------|
| TTSFragment.java | 从悬浮窗跳转时 `open_floating_editor` 在数据加载前执行，对话框显示为空 | 使用 `pendingFloatingEditor` 标志延迟到数据加载完成后再打开对话框，防止 LiveData 多次发射导致请求丢失 |

---

## v1.5.6 性能优化与稳定性修复 (versionCode: 88)

### 修复的问题

| 文件 | 问题 | 修复 |
|------|------|------|
| FloatingWindowService.java | onDestroy 未注销 MicOutputListener，服务实例泄漏 | 添加 removeListener |
| FloatingWindowService.java | retrySetupTTSRunnable 无限重试 | 加最大重试次数 10 次后放弃 |
| MicOutputController.java | 静音检测使用 postProcessRms 被 AGC 拉平，静音检测形同虚设 | 改用 getCurrentRms() 获取原始输入 RMS |
| MicrophoneManager.java | 录制线程异常崩溃后不释放资源，AudioRecord/AudioTrack 泄漏 | catch 中调用 releaseResources() |
| MicrophoneManager.java | AudioRecord.read() 永久阻塞导致僵尸线程 | stop() 中先 stop AudioRecord 强制解除阻塞 |
| TTSManager.java | 模拟进度 Runnable 每 200ms 轮询，空转性能浪费 | 删除 progressHandler/progressRunnable/currentProgress 及所有 onTTSProgress 调用 |
| AudioFocusManager.java | `hasPlaybackFocus`/`hasTransientFocus` 非 volatile，Binder 线程写入对 synchronized 方法不可见 | 加 volatile |
| AudioFocusManager.java | `focusListener` 在系统 Binder 线程回调，dispatch 触发的 ExoPlayer API 可能崩溃 | synchronized 写 flag + mainHandler.post 切主线程分发 |
| AudioFocusManager.java | `requestTransientFocus()` 在申请成功前就设 `hasTransientFocus=true` | 移到申请成功后再设 |
| PlaybackController.java | `isInternalFocusChange` 死代码（异步回调永远不拦截） | 删除全部 8 处引用 |
| TTSRepository.java | `addTTSItem`/`removeTTSItem` 通过异步 saveTTSItems 写入，并发增删丢失数据 | 改为同步写入，消除读-改-写竞态 |
| TTSRepository.java | `loadTTSItemsSync()` 读路径含写磁盘副作用 | 默认创建/保存逻辑移到构造函数 |
| MusicConfig.java + AppConfig.java | `setShuffleModeEnabled` 无对应 getter | 新增 `isShuffleModeEnabled()` |
| AudioServiceLocator.java | `registerManagers()` 参数无 null 保护，传 null 会覆盖已有实例 | 各参数加 null 守卫，已有值不被覆盖 |
| PlaybackController.java | `play()`/`resume()` 中 `requestFocus()` 被拒仍继续播放 | 焦点被拒时直接 return 不播放 |
| AudioForegroundService.java | `ACTION_STOP` 不调 `stopForeground()`，通知栏残留 | 添加 `stopForeground(false)` + `stopSelf()` |
| MusicItem.java | `lyrics`/`lyricsModified` 计算线程写主线程读，数据竞争 | 加 volatile |
| MediaSessionManager.java | MediaSession 永不 release | 新增 `release()` 方法 |
| MediaSessionManager.java | 缺失 `onStop()`/`onSeekTo()` 回调 + 对应 actions | 补充回调与 PlaybackState actions |
| MusicPlayerManager.java | `seekTo()` 后不刷新 MediaSession 位置 | 添加 notifyMediaSessionPlaybackState 调用 |
| PlaylistManager.java | `gson.toJson()` O(N) 在防抖 Handler 主线程 + 锁内执行 | 改为 IO 线程异步序列化 |
| AlbumArtCache.java | 磁盘缓存无淘汰 → 竞态 + 写磨损 + 字节管理 | 删除全部磁盘存储，put() 只预热 LruCache |
| PlaylistManager.java | `getRandomIndex()` 偏倚——下一首概率是其他歌的 2 倍 | 改为拒绝采样循环，保证无偏 |
| PlaylistManager.java | 播放列表无大小上限，SP 序列化 O(N) 无保护 | 新增 MAX_PLAYLIST_SIZE=1000 上限检查 |
| FileBrowserAdapter.java | `getChildCount()` 中 `dir.listFiles()` 在 UI 线程阻塞 RecyclerView 滑动 | 后台线程预计算 childCount 写入 FileItem，UI 线程直接读内存字段 |
| FileItem.java | `lastModified` 全局未读，dead code | 删除字段；新增 `childCount` 字段 |
| FileUtils.java + 3 文件 | `AUDIO_EXTENSIONS` 数组在 3 处重复定义 | 集中到 FileUtils.AUDIO_EXTENSIONS |
| MicOutputFragment.java | `isAmplifying` 跨线程读写无 volatile | 加 volatile |
| MusicPlayerFragment.java | 6 处 `Toast.makeText(getActivity())` 无 `getActivity()` null guard；`hasStoragePermission()` 使用 `requireActivity()` 无 `isAdded()` 守卫 | 加 null guard；`requireActivity()` → `getActivity()` + null check |
| MusicPlayerFragment.java | `getResources().getColor()` 已废弃 | 改为 `ContextCompat.getColor()` |
| SettingsFragment.java | 多处 `requireActivity()/requireContext()` 在 listener 回调中无 `isAdded()` 守卫 | 加 `if (!isAdded()) return;` + 部分改为 `getActivity()/getContext()` null check |
| AdaptiveFeedbackCancellationProcessor.java | AFC xpos 双重推进导致环形缓冲间隙，低延迟 taps 读不到最新参考数据 | 删除 process() 中 `xpos = (xpos + 1) % FILTER_LENGTH`，每 4 帧后缓冲变为完美 FIFO |
| AdaptiveFeedbackCancellationProcessor.java | 双讲检测逐样本进行，误判率高 | 改为帧级能量累计后统一判断 |
| SpectralNoiseReduction | 帧长非256整数倍时尾部样本未处理，输出错误数据 | 修改帧处理逻辑，使用向上取整和零填充处理残余帧 |
| SpectralNoiseReduction | `System.arraycopy` 越界风险 | 添加 `n >= HALF_FFT` 边界检查 |
| AutomaticGainControlProcessor.java | tanh 软限幅在阈值处不连续，产生 click 噪声 | 对全部样本统一应用 tanh |
| AutomaticGainControlProcessor.java | GAIN_CHANGE_LIMIT=0.2f 过大，gain pumping 明显 | 降低至 0.05f |
| GainLimiterProcessor.java | tanh 软限幅在阈值处不连续 | 对全部样本统一应用 tanh |
| HighPassFilterProcessor.java | 无输出限幅，瞬态响应可能 clip | 添加 clamp(y, -1.0f, 1.0f) |
| HowlingNotchFilter | 啸叫检测仅分析前512样本，帧后半部分漏检 | 使用分块检测覆盖全帧 |
| HowlingNotchFilter | 固定阈值未归一化，安静环境误检 | 改为自适应阈值（基于频带平均幅度） |
| HowlingNotchFilter | 达到最大陷波数时直接丢弃新检测 | 改为替换最旧的陷波 |
| MusicPlayerManager.java | `ensureAlbumArt()` 中 `MediaMetadataRetriever` I/O 在主线程同步执行，阻塞播放启动 30-200ms | 先调 `playbackController.play()` 确保立即出声，封面提取改为 `AppExecutors` 计算线程异步执行，加载后 `mainHandler.post` 刷新通知/MediaSession |
| AlbumArtCache.java | `entryRemoved` 淘汰时 `recycle()` Bitmap，外部仍持有引用（MediaSession/通知/Fragment）绘制已回收图 → Crash | 删除 `recycle()`，交由 GC；LruCache `sizeOf` 已正确计入内存 |
| AlbumArtCache.java | `cacheKey()` 用 `hashCode()` 32 位碰撞，不同文件映射同 key → 封面错位 | 直接用文件路径字符串作 key，消除碰撞 |
| AudioFocusManager.java | `requestTransientFocus()`/`abandonTransientFocus()` 直接 dispatch，若未来后台线程调用则回调 ExoPlayer 崩溃 | 全部改为 `mainHandler.post` 切主线程，与系统 Binder 回调保持一致 |
| AudioForegroundService.java | `onDestroy` 回收 `currentAlbumArt`（LruCache 内对象直接引用）→ 缓存对象变 recycled，后续 get 跳过且占条目 | 仅置 null 断开引用，不 recycle |
| AudioForegroundService.java | `notifyUpdate()` 每次状态变化 `startService` → 频繁 IPC 开销（每 500ms 进度更新触发） | 改为本地广播，零 IPC，进程内直接分发 `updateNotification()` |
| TTSRepository.java | 构造函数执行 `loadTTSItemsSync()` → 读路径写磁盘副作用，并发增删丢失数据 | 构造函数仅读取，新增 `initializeIfNeeded()` 显式初始化，首次使用前显式调用 |
| MicOutputController.java | `init()` 无锁，多线程并发初始化可能重复创建 AudioRecord/AudioTrack | 加 `synchronized`，双重检查幂等 |
| PlaylistManager.java | `addItemsInternal()` 每次重建 `existingPaths` HashSet，O(N) 重复计算 | 类成员 `existingPaths` 增量维护，`loadFromStorage`/`add`/`remove` 增量同步，O(1) 查重 |
| FloatingWindowService.java | 悬浮窗"添加"按钮点击仍跳转 MainActivity，未直接弹出编辑对话框 | 新增 `showFloatingListEditor()`，直接弹出编辑悬浮窗列表对话框（复用 TTSFragment 逻辑） |
| FloatingWindowService.java | 自动收起时长 SeekBar 无数值显示 | `tv_auto_hide_label` 实时显示"自动收起时长：X 秒" |
| FloatingWindowService.java | 悬浮窗 TTS 列表"选择"按钮文案不符合功能 | 文案改为"添加"，点击直接弹出编辑对话框 |

### 性能优化

| 文件 | 优化 |
|------|------|
| AlbumArtCache.java | cacheKey() 去掉 MD5+16次 String.format()，改用文件路径直接作 key |
| AlbumArtCache.java | 去掉 LruCache 淘汰时 `recycle()`，避免外部引用 Crash，交由 GC |
| PlaylistManager.java | saveToStorage() 防抖（1s 窗口），合并多次增删为一次序列化 |
| MicrophoneManager.java | L581-583 每帧 2×String.format+拼接在 Release 构建仍执行（~748 临时对象/秒） | 加 `if (BuildConfig.DEBUG)` 包裹，编译器死代码消除 |
| GainLimiterProcessor.java | 日志字符串在 Release 构建仍拼接执行 | 加 `if (BuildConfig.DEBUG)` 包裹，仅 Debug 构建构造日志字符串 |

### 代码重构

| 文件 | 重构内容 |
|------|----------|
| TTSFragment.java | 删内部类 TTSItem，改用 data.model.TTSItem；编辑器悬浮窗配置从 index 匹配迁移为 uid 匹配 |
| TTSItem.java | 新增 uid（UUID 唯一标识）+ transient isPlaying（View 层播放状态） |
| FloatingWindowConfig.java | 新增 tts_selected_uids / tts_names_by_uid getter/setter，旧 indices/names 方法保留但不使用 |
| FloatingWindowService.java | loadTTSItems() 按 uid 匹配而非下标，删除按钮按 uid 删除 |
| AppConfig.java | 新增 getFloatingWindowTTSSelectedUids / setFloatingWindowTTSSelectedUids / getFloatingWindowTTSNamesByUid / setFloatingWindowTTSNamesByUid |
| GainLimiterProcessor.java | 默认 public 构造器可被外部实例化 | 改为 private 构造器 |
| HighPassFilterProcessor.java | B1 系数无注释说明来源 | 加注释：对应 @80Hz 截止频率（fs=48000） |
| SpectralAndNotchProcessor.java | SpectralNoiseReduction / HowlingNotchFilter 内部类缺 Javadoc | 补充类注释说明算法原理 |
| AutomaticGainControlProcessor.java | 无运行时日志，增益变化不可追踪 | 加 `if (BuildConfig.DEBUG)` 包裹的增益/smoothedRms 日志 |

---

## v1.5.5 Buffer 脏数据修复 + 悬浮窗增强

### 修复的问题

| 文件 | 问题 | 修复 |
|------|------|------|
| MicrophoneManager.java | `samples`数组固定为`BUFFER_SIZE/2`(2048)，实际`readSize/2`仅768，管线处理1280个脏数据，三抑制模块同时开启后人声严重失真 | `samples`大小动态匹配`readSize/2`，脏数据清零 |

### 日志确认

- AFC `input` 从0.0001~0.0004（脏数据稀释）升至 **0.1690**（真实语音能量），`postRms` 正确显示0.0000

### 性能优化

| 文件 | 优化 |
|------|------|
| PlaylistManager.java | `getAllItems()`/`getItemAt()`/`getCurrentItem()` 去掉深拷贝 |
| AlbumArtCache.java | `loadFromDisk()` 加 `readFully()` 循环 |
| MicrophoneManager.java | 预分配`short[]`消除每帧`new short[]`；合并byte→short与RMS为一次循环 |
| SpectralNoiseReductionProcessor.java | `input`/`output`改为成员懒分配，`System.arraycopy` |
| AutomaticGainControlProcessor.java | 降频至每10帧更新增益 |
| SpectralAndNotchProcessor | 新建（内部类SpectralNoiseReduction+HowlingNotchFilter+共享FFT），删除两个旧处理器文件 |
| AdaptiveFeedbackCancellationProcessor.java | FILTER_LENGTH 4096→1024，MU 0.3→0.05 |
| GainLimiterProcessor.java | 每25帧输出tanh限幅百分比+累计限幅数 |

### 日志增强

- 全链路入口/出口日志补全：MicrophoneManager/FloatingWindowService/GainLimiterProcessor 等

### 新功能

| 功能 | 说明 |
|------|------|
| 自动收起时长滑动条 | 悬浮窗设置新增5-30秒可调SeekBar，替代硬编码10秒 |
| 悬浮球按钮增大 | 88dp×77dp → 100dp×90dp |
| OutputModeListener | MicOutputController新增输出模式监听器，MainActivity `onResume`注册/`onPause`注销 |
| 喊话停止保持输出模式 | `stopAnnouncement()`中保持喊话期间模式不变 |
| startupGraceMs | `startSilenceDetection()`新增3000ms宽限期 |

### 构建

- `app/build.gradle.kts` versionCode 65 → 66

---

---

## v1.5.4 扫码性能优化 + 车机适配修复

### 修复的问题

| 文件 | 问题 | 修复 |
|------|------|------|
| MicOutputController.java | Toast 提示"仅车外/仅车内"未记录用户选择，重新启动后丢失偏好 | 新增 `preferExternal` 持久化偏好，`startAnnouncement(forceExternal=false)` 自动切换 |
| MainActivity.java | 悬浮窗/主界面未同步 `preferExternal` 偏好 | 悬浮窗 `toggle(true)` 后同步记录，MainActivity 初始化时读取 |
| SettingsFragment.java:880 | TTS 播报错误使用外放通道 | 改为 `getCarAudioUsage()` |
| SettingsFragment.java:692 | 枚举音频设备时未输出设备地址 | 增加 `device.getAddress()` 日志 |
| MicrophoneManager.java / HowlingNotchFilterProcessor | 采样率硬编码 16000Hz，导致部分机型音质劣化 | 改为 48000Hz |

### 扫描性能优化

- **PlaylistManager.java** `createItemFromFile` 重写为格式感知分派：
  - WAV：直接自解析 `WavMetadataReader`（标题/艺术家/时长）→ 文件名兜底 → 头计算时长（只读 44 字节头）
  - FLAC/M4A：直接自解析 `AudioMetadataReader.readMetadata()` → 文件名兜底，时长从 STREAMINFO/mvhd 解析
  - MP3/其余：保留 `MediaMetadataRetriever`，新增文件名兜底 fallback
  - content:// URI：保留原 `MediaMetadataRetriever` 逻辑
  - **IO 收益**：16 个 WAV 文件从 ~800MB 无用 IO 降至 ~704 字节
- **WavMetadataReader.java** 新增 `id3 ` RIFF 块支持（Mp3tag 写入格式），复用 `parseId3v2Frames`
- **FlacMetadataReader.java** 从 STREAMINFO 解析 `sampleRate`+`totalSamples` → `durationMs`
- **M4aMetadataReader.java** 从 `mvhd` atom 解析 `timeScale`+`duration` → `durationMs`
- 三个 self-parser 改为始终返回 `AudioMetadata` 对象（可能仅有 durationMs）

### 文件名显示策略

- `parseTitleFromFileName` 仅做去扩展名，不再去除编号前缀
- 删除 `parseArtistFromFileName` 方法（不再从文件名猜解艺术家）
- 无元数据时：title = 文件名去扩展名（原样），artist = ""（空串）
- 有元数据时：使用元数据

### CHANGELOG

- `app/build.gradle.kts` versionCode 61 → 63

---

## v1.5.3 包结构按功能模块重组

### 重构

- **domain/audio/** 拆分为 `micoutput/`、`player/`、`tts/` 三个子包，6 个处理器移入 `micoutput/processor/`
- **ui/fragment/** 拆分为 `micoutput/`、`tts/`、`player/`、`settings/`、`about/` 子包
- **service/** 拆分为 `player/`、`floating/` 子包
- **receiver/** 拆分为 `micoutput/`、`boot/` 子包
- **data/local/config/** 拆分为 `micoutput/`、`tts/`、`player/`、`floating/` 子包
- `AnnouncementController` → `MicOutputController`（统一命名）
- `AnnouncementReceiver` → `MicOutputReceiver`
- `MicAmplifierFragment` → `MicOutputFragment`
- `MicConfig` → `MicOutputConfig`
- `AndroidManifest.xml` 同步更新 4 处 service/receiver 类路径

---

> 日期：2026-07-07
> 版本：v1.5.2 (versionCode: 60)

---

## v1.5.2 音频输出通道集中化管理

### 修复的问题

| 文件 | 问题 | 修复 |
|------|------|------|
| TTSFragment.java | `playTTS()` 调用 `speak(text)` 跟随全局模式，车外 TTS 播报走车内喇叭 | 改用 `speakWithUsage(text, externalUsage)`，始终走车外通道 |
| SettingsFragment.java | 两个反馈 TTS（保存设置后提示音）错误使用车内 usage | 改为 `audioOutputManager.getExternalAudioUsage()` |
| MainActivity.java | 音乐播放器启动时 ExoPlayer 硬编码 USAGE_MEDIA，不读取配置 | `initAudioManagers()` 中调用 `updateAudioOutputUsage()` |
| PlaybackController.java | `initPlayer()` 硬编码 `USAGE_MEDIA` 用作默认值 | 移除，完全交由 `updateAudioUsage()` 负责 |

### 重构

- **AudioOutputManager** 新增 `getExternalAudioUsage()` / `getCarAudioUsage()` 集中方法
- 所有模块统一通过 `AudioOutputManager` 获取 audio usage，清理所有散落在各文件的 `appConfig.getAudioOutputUsage*()` 直接调用

---

## v1.5.1 音频处理管线全面升级（综合版）

汇总 v1.5.0 至当前的全部迭代，核心目标：解决手机端麦克风放大时的回声/啸叫/音量不稳问题。

### 1）管线架构

```
输入(16kHz, 16-bit PCM)
     ↓
[Android 原生 3A]   ← HW NS/AEC/AGC 优先，不可用时软件回退
     ↓
[HPF @80Hz]         ← 一阶 IIR B1=0.969，滤除 DC / 低频噪声
     ↓
[AFC NLMS]          ← 256 阶自适应滤波 MU=0.3 LEAKAGE=0.001 DT_THRESHOLD=1.5
                        参考信号来自上一帧输出；HW AEC 启用时仍串联运行消残余
     ↓
[Gain]              ← 用户可调"最大放大倍数"
     ↓
[SpectralNR]        ← 512 FFT + Sine 窗 + 50% overlap-add；alpha=1.3 beta=0.01 自学习噪声谱
     ↓
[HowlingNotch]      ← 512 FFT 峰值检测 + IIR 窄带陷波 Q=30 -12dB，最多 3 频点
     ↓
[AGC]               ← 目标 RMS=0.3 MAX_GAIN=2.0 gain 变化 <=+-2%/帧；tanh 软限幅；用户可开关
     ↓
[setReference]      ← 保存为下一帧 AFC 参考（含 AGC 增益，AFC 正确跟踪）
     ↓
扬声器 / AudioTrack
```

### 2）关键参数演进

| 参数 | 初始 | 最终 | 演进路径 |
|------|------|------|----------|
| 采样率 | 44100Hz | 16000Hz | 与 WebRTC 对齐 |
| Buffer | min*2 | minBufferSize | 降低延迟 |
| AFC MU | 0.005 | 0.3 | 0.005->0.05->0.3 |
| AFC DT_THRESHOLD | -- | 1.5 | 2.0->1.5 |
| AFC LEAKAGE | -- | 0.001 | 新增防漂移 |
| SpectralNR alpha | 2.0 | 1.3 | 保留语音谐波 |
| AGC MAX_GAIN | 5.0 | 2.0 | 5->2 限幅防正反馈 |
| AGC 增益算法 | 指数 alpha=0.2 | 硬限幅 +-2%/帧 | 慢到 AFC 追得上 |
| HPF B1 | 0.989 | 0.969 | 适配 16000Hz |

### 3）硬件 3A 自适应矩阵

| 硬件可用 | NS (SpectralNR) | AEC (AFC NLMS) | AGC (sw AGC) |
|----------|----------------|----------------|--------------|
| 全不可用 | 启用 | 启用 | 启用 |
| 仅 NS | 禁用 | 启用 | 启用 |
| 仅 AEC | 启用 | HW+AFC 串联 | 启用 |
| 全可用 | 禁用 | HW+AFC 串联 | 禁用 |

### 4）新增功能

| 功能 | 说明 |
|------|------|
| AGC 开关 | 麦克风页面第 4 个 Switch，持久化到配置，可关闭 AGC |
| AEC AudioTrack fallback | AudioRecord create 返回 null 时尝试 AudioTrack session |

### 5）Bug 修复

| 文件 | 问题 | 修复 |
|------|------|------|
| HowlingNotchFilterProcessor | 数组越界 magnitude[256] | 循环条件 i < HALF_FFT - 1 |
| AdaptiveFeedbackCancellationProcessor | AFC 发散/收敛慢 | MU->0.3 + 双讲检测 + 系数泄漏 |
| SpectralNoiseReductionProcessor | 人声过减劣化 | alpha 2.0 -> 1.3 |
| MicrophoneManager | AEC AudioRecord null 时不尝试 AudioTrack | 新增 AudioTrack session fallback |
| SettingsFragment | 进入页面 NPE | micConfig 空指针保护 |
| AnnouncementController | 静音检测误判 | 改用 getPostProcessRms() |

### 6）文件改动一览

| 文件 | 动作 |
|------|------|
| 新增 | |
| HighPassFilterProcessor.java | 80Hz 一阶 IIR |
| SpectralNoiseReductionProcessor.java | 512 FFT 谱减法降噪 |
| AdaptiveFeedbackCancellationProcessor.java | 256 阶 NLMS AFC |
| HowlingNotchFilterProcessor.java | FFT + IIR 啸叫陷波 |
| AutomaticGainControlProcessor.java | 目标 RMS AGC + tanh 软限幅 |
| 删除 | |
| AudioSuppressionProcessor.java | 旧能量交叉相关回声 + 宽带啸叫衰减 |
| 修改 | |
| MicrophoneManager.java | 管线重构 + HW 3A 初始化 + AudioTrack fallback + AGC 开关 |
| MicConfig.java / AppConfig.java | AGC 开关配置持久化 |
| fragment_mic_amplifier.xml / MicAmplifierFragment.java | AGC 开关 UI |
| HighPassFilterProcessor.java | B1 0.989->0.969 |
| HowlingNotchFilterProcessor.java | SAMPLE_RATE 44100->16000 |
| AdaptiveFeedbackCancellationProcessor.java | MU 0.005->0.3, +DT_THRESHOLD, +LEAKAGE, xnorm O(N^2)->O(1), +getLastErleDb |
| SpectralNoiseReductionProcessor.java | alpha 2.0->1.3 |
| AutomaticGainControlProcessor.java | MAX_GAIN 5->2, +GAIN_CHANGE_LIMIT=0.02 |
| AppLog.java | +AppLog.i() 供 OPPO 可见 |
| AnnouncementController.java | 静音检测改 getPostProcessRms |
| build.gradle.kts | versionCode 多次迭代至 59 |

## v1.4.9 封面提取延迟到播放时

### 1）性能优化

| 类型 | 优化 | 修改文件 | 改动 |
|------|------|----------|------|
| 🚀 | 扫描时不再提取专辑封面 | PlaylistManager.java | 删除 `getEmbeddedPicture()` + `AlbumArtCache.put()`，扫描 200 首歌省 200 次文件 IO + 200 次磁盘写入 |
| 🚀 | 播放时按需提取封面 | MusicPlayerManager.java | `start()` 中新增 `ensureAlbumArt()`，只在播放前提取封面并回写 `item.albumArt` + `AlbumArtCache.put()` |

---

## v1.4.8 代码质量优化

### 1）修复的问题

| 类型 | 问题 | 修改文件 | 改动 |
|------|------|----------|------|
| 🐛 | Release 构建日志未禁用 | AppLog.java | `debugEnabled` 改为 `BuildConfig.DEBUG`，Release 不再输出日志 |
| 🐛 | 封面全尺寸解码 OOM 风险 | AlbumArtCache.java | `get(key, albumArt)` 默认 512px 采样解码，不再全尺寸解码 |
| 🐛 | 磁盘缓存无淘汰策略 | AlbumArtCache.java | `saveToDisk()` 新增文件数上限(200)检查，超出删除最旧文件 |
| 🐛 | init() 部分初始化 NPE 路径 | AnnouncementController.java | `appContext` 最后赋值，确保依赖全部就绪后才暴露 |
| 🐛 | `getAdapterPosition()` 已废弃 | FileBrowserAdapter.java | 替换为 `getBindingAdapterPosition()` |
| 🐛 | `getResources().getColor()` 废弃用法 | TTSFragment.java | 替换为 `ContextCompat.getColor()` |
| 🔧 | `new Handler()` 缺 Looper | TTSManager.java | 添加 `Looper.getMainLooper()` |
| 🔧 | `Gson` 重复创建 | FloatingWindowService.java | 提取为 `static final` 字段复用 |
| 🔧 | 空 `onPause()` 方法 | MainActivity.java | 删除 |
| 🔧 | `CopyOnWriteArrayList` 过杀 | AnnouncementController.java | 替换为 `ArrayList + synchronized` |
| 🔧 | 颜色初始化哨兵值 `0` | MusicPlaylistAdapter.java | 改用 `boolean colorsInitialized` 标记 |
| 🔧 | Dead code: `TYPE_PHONE` 分支 | FloatingWindowService.java | 移除（minSdk=30，永不执行） |

---

## v1.4.7 死代码深度清理（第二轮）

### 1）清理清单

| 类型 | 清理内容 |
|------|----------|
| 删除文件 | KeepAliveManager.java、KeepAliveWorker.java、MusicSource.java、ScannedMusicInfo.java |
| 删除字符串 | 26 个未使用的 `<string>` 资源（strings.xml 从 47 行缩至 9 行） |
| 删除 Gradle 依赖 | work-runtime:2.9.0（KeepAliveManager/Worker 已删，全项目无 androidx.work.* 引用） |
| 删除权限 | WAKE_LOCK（代码中无任何 PowerManager/WakeLock 使用） |
| 清理死方法 | PlaylistManager: addFromSource/addFromScannedInfo；MusicPlayerManager: resume(long)/setShuffleModeEnabled/isShuffleModeEnabled；AppExecutors: executeOnIOThread(Runnable,Runnable)；AudioConfig: resetAudioChannel；AudioFocusManager: hasAnyFocus；LrcParser: parsePlainTextAsLines；FileUtils: getNameWithoutExtension(两个重载)；AppLog: i()；BootReceiver: disable()；TTSRepository: loadTTSItems(OnTTSItemsLoadedCallback) + 回调接口；TTSViewModel: addTTSItem(String,String)；MediaSessionManager: getSessionToken/release；MusicConfig: isShuffleModeEnabled(getter) |

### 2）修复的前轮问题

| 问题 | 修复 |
|------|------|
| PlaylistManager.java:302 编译错误 | 上轮误删了 AppExecutors 的 import，addFromFilePaths() 仍在使用，已加回 |

---

## v1.4.6 音乐模块性能优化

### 1）封面图片存储与解码全面优化

**问题描述**：albumArt(byte[]) 随播放列表全量序列化到 SharedPreferences，JSON 体积巨大，接近 2MB 上限；同一封面在 Fragment/MediaSession/Notification 三处独立解码，内存浪费。

**优化方案**：

| 修改文件 | 改动内容 |
|----------|----------|
| [MusicItem.java](app/src/main/java/com/aug32/l7audio/domain/audio/MusicItem.java) | `albumArt`、`lyrics`、`lyricsModified` 加 `transient` 关键字，不再参与 Gson 序列化；`copy()` 深拷贝 byte[] |
| [AlbumArtCache.java](app/src/main/java/com/aug32/l7audio/utils/AlbumArtCache.java) | **新增**：LRU 内存缓存(10MB) + 文件缓存 + 采样解码 + 统一入口 |
| [PlaylistManager.java](app/src/main/java/com/aug32/l7audio/domain/audio/playlist/PlaylistManager.java) | 提取封面后调用 `AlbumArtCache.put()` 落盘文件缓存，不再依赖 SP 序列化 |
| [MusicPlayerFragment.java](app/src/main/java/com/aug32/l7audio/ui/fragment/MusicPlayerFragment.java) | `loadAlbumArt()` 改用 `AlbumArtCache` + 后台线程解码 + 采样匹配 240dp ImageView |
| [MediaSessionManager.java](app/src/main/java/com/aug32/l7audio/domain/audio/MediaSessionManager.java) | `updateMetadata()` 改用 `AlbumArtCache`，共享缓存 Bitmap |
| [AudioForegroundService.java](app/src/main/java/com/aug32/l7audio/service/AudioForegroundService.java) | `updateNotification()` 改用 `AlbumArtCache`，共享缓存 Bitmap |

### 2）播放列表 RecyclerView 性能优化

**问题描述**：`notifyDataSetChanged()` 频繁全量刷新；`onBindViewHolder` 中每次调用 `ContextCompat.getColor()` 重复获取颜色值。

**优化方案**：

| 修改文件 | 改动内容 |
|----------|----------|
| [MusicPlaylistAdapter.java](app/src/main/java/com/aug32/l7audio/ui/adapter/MusicPlaylistAdapter.java) | DiffUtil 增量刷新替代 `notifyDataSetChanged`；颜色值缓存为 int 字段避免重复调用；`setHasStableIds(true)` 稳定 ID |

---

**优化收益**：
- SharedPreferences 存储量减少 90%+，彻底消除 2MB 上限风险
- 三处封面解码合为一处，LRU 缓存避免重复解码
- 封面采样压缩适配 240dp 目标尺寸，大图内存显著降低
- 列表 DiffUtil 增量刷新，大列表操作流畅度提升

---

## v1.4.5 修复与优化

### 1）扫描/添加大量音乐后播放列表不刷新

**问题描述**：添加200+首歌曲后，Toast显示但播放列表为空，需重新进入音乐Tab才能看到歌曲。

**原因分析**：`closeFileBrowserFragment()` 异步投递 Fragment 事务，`addFilesToPlaylist()` 向计算线程投递任务。若计算线程先完成，`onPlaylistChanged()` 和 `onAddComplete()` 回调在 Fragment 恢复前执行，`isAdded()` 返回 false，`refreshPlaylist()` 被跳过。

**修复方案**：

| 修改文件 | 改动内容 |
|----------|----------|
| [MusicPlayerFragment.java](app/src/main/java/com/aug32/l7audio/ui/fragment/MusicPlayerFragment.java) | `onResume()` 中添加 `refreshPlaylist()` 调用，确保 Fragment 可见时播放列表一定刷新 |

---

### 2）死代码全面清理

**优化内容**：清理项目中积累的未使用代码、资源和依赖，精简代码库。

**清理清单**：

| 类型 | 清理内容 |
|------|----------|
| 删除文件 | AudioUtils.java（音频工具）、BaseService.java（空基类） |
| 删除工具类 | ArrayUtils.java、CollectionUtils、DateUtils.java、JsonUtils.java |
| 删除 Domain 类 | PlaylistExporter.java、PlaylistShuffleHelper.java、AudioDeviceCompat.java、AudioFormat.java |
| 删除布局 | activity_main_landscape.xml、item_music.xml |
| 删除颜色 | colorPrimaryDark、colorAccentDark、divider_color |
| 删除 Gradle 依赖 | media3-ui、media3-session、junit、extJunit、espressoCore、okhttp |
| 精简 FileUtils.java | 389行 → 57行（移除未使用的文件操作方法） |
| 精简 MicrophoneManager.java | 685行 → 635行 |
| 精简 BaseActivity.java | 131行 → 69行 |

---

### 3）设置页面新增「恢复默认设置」按钮

**功能描述**：设置页面底部新增「恢复默认设置」按钮，一键清除所有用户配置并重新初始化为默认值。

**修改文件**：

| 修改文件 | 改动内容 |
|----------|----------|
| [fragment_settings.xml](app/src/main/res/layout/fragment_settings.xml) | 底部新增「恢复默认设置」CardView 按钮 |
| [SettingsFragment.java](app/src/main/java/com/aug32/l7audio/ui/fragment/SettingsFragment.java) | 新增 `restoreDefaults()` 方法，清除 SharedPreferences 并重新初始化所有 Config 对象 |

---

详细版本历史请参考 [README.md](README.md)

---

## 四、旧版本记录

> 日期：2026-07-02
> 版本：v1.4.4 (versionCode: 44)

---

## v1.4.4 修复

### 1）麦克风放大按钮失效

**问题描述**：进入麦克风页面点击"开始放大"按钮无任何响应。

**原因分析**：`AnnouncementController.init()` 仅在 FloatingWindowService 和 AnnouncementReceiver 中调用，正常打开主界面进入麦克风页面时 Controller 未初始化，`toggle()` 中 `appContext == null` 直接 return。

**修复方案**：

| 修改文件 | 改动内容 |
|----------|----------|
| [L7AudioApp.java](app/src/main/java/com/aug32/l7audio/L7AudioApp.java) | `onCreate()` 中添加 `AnnouncementController.getInstance().init(this)` |

---

### 2）GainLimiterProcessor tanh 过度压缩

**问题描述**：重构后声音极小，即使增益设为 1.0x 输出也只有原始信号的 76%。

**原因分析**：`Math.tanh(x)` 对所有采样值压缩，tanh(1.0)≈0.76，正常范围信号也被压缩。

**修复方案**：

| 修改文件 | 改动内容 |
|----------|----------|
| [GainLimiterProcessor.java](app/src/main/java/com/aug32/l7audio/domain/audio/processor/GainLimiterProcessor.java) | tanh 软限幅仅在 `|x| > 1.0` 溢出时使用，正常范围直通 |

---

## v1.4.3

> 日期：2026-07-02
> 版本：v1.4.3 (versionCode: 43)

---

## 一、修改文件总览

| 类型 | 数量 | 文件 |
|------|------|------|
| ✨ 新增 | 6 | AnnouncementController.java、AnnouncementReceiver.java、AudioProcessor.java、AudioPipeline.java、GainLimiterProcessor.java、AudioSuppressionProcessor.java |
| 🐛 修复 | 2 | 关闭主界面后悬浮窗车外喊话不可用、悬浮窗按钮文字显示不全 |
| 🔧 优化 | 8 | MicConfig.java、MicrophoneManager.java（重构为管线模式）、FloatingWindowService.java、MicAmplifierFragment.java、MainActivity.java、SettingsFragment.java、fragment_settings.xml、AndroidManifest.xml |
| 📝 文档 | 2 | README.md、CHANGELOG.md |

---

## 二、核心问题修复

### 1）关闭主界面后悬浮窗车外喊话不可用

**问题描述**：关闭软件主界面仅保留悬浮窗时，车外喊话功能 UI 显示正常但实际不发声，需进入麦克风页面才能使用。

**原因分析**：`MainActivity.onDestroy()` 注销了 `AudioServiceLocator` 中的管理器，导致悬浮窗中获取的是新实例，输出模式未正确设置。

**修复方案**：

| 修改文件 | 改动内容 |
|----------|----------|
| [MainActivity.java](app/src/main/java/com/aug32/l7audio/ui/activity/MainActivity.java) | `onDestroy()` 不再注销音频管理器（麦克风、音频输出、音频焦点），确保全局使用同一实例 |

---

### 2）悬浮窗按钮文字显示不全

**问题描述**：车机上悬浮窗按钮的"L7"字符显示不完整。

**原因分析**：按钮内边距和字体大小导致文字显示不全。

**修复方案**：

| 修改文件 | 改动内容 |
|----------|----------|
| [view_floating_ball.xml](app/src/main/res/layout/view_floating_ball.xml) | 移除按钮内边距，设置 `android:includeFontPadding="false"`，避免字体额外间距 |

---

## 三、新增功能

### 1）车外喊话统一管理（AnnouncementController）

**功能描述**：创建 `AnnouncementController` 单例类，集中处理车外喊话状态切换、防抖、静音检测和焦点管理。

**新增文件**：

| 文件 | 职责 |
|------|------|
| [AnnouncementController.java](app/src/main/java/com/aug32/l7audio/domain/audio/AnnouncementController.java) | DCL 单例，管理喊话状态、防抖检查、静音检测线程、音频焦点协调 |

**核心功能**：

- **状态管理**：统一管理 `isAnnouncing` 状态，支持多个入口（悬浮窗、麦克风页面、第三方按键）
- **防抖处理**：记录上次触发时间，过滤短时间内的连续触发请求（默认 800ms）
- **静音检测**：通过 RMS 音量检测判断是否有声音输入，超时后自动关闭（默认 30 秒）
- **焦点管理**：申请短暂独占焦点暂停音乐，结束后释放焦点恢复音乐
- **观察者模式**：`AnnouncementListener` 接口实现悬浮窗和麦克风页面状态同步

---

### 2）第三方 APP 按键控制支持

**功能描述**：支持第三方 APP 通过实体按键发送广播控制车外喊话（触发后开启，再次触发关闭）。

**新增文件**：

| 文件 | 职责 |
|------|------|
| [AnnouncementReceiver.java](app/src/main/java/com/aug32/l7audio/receiver/AnnouncementReceiver.java) | 接收广播 `com.aug32.l7audio.OUTSIDE_MIC_TOGGLE`，调用控制器切换状态 |

**使用方式**：

```java
Intent intent = new Intent("com.aug32.l7audio.OUTSIDE_MIC_TOGGLE");
context.sendBroadcast(intent);
```

---

### 3）车外喊话设置配置项

**功能描述**：设置页面新增车外喊话相关配置项，支持用户自定义参数。

**修改文件**：

| 文件 | 改动内容 |
|----------|----------|
| [fragment_settings.xml](app/src/main/res/layout/fragment_settings.xml) | 新增"车外喊话设置"卡片，包含防抖间隔、静音检测开关、静音超时、静音阈值输入框 |
| [SettingsFragment.java](app/src/main/java/com/aug32/l7audio/ui/fragment/SettingsFragment.java) | 加载/保存车外喊话配置，含范围校验和 Toast 提示 |
| [MicConfig.java](app/src/main/java/com/aug32/l7audio/data/local/config/MicConfig.java) | 新增配置字段：`debounceInterval`、`silenceDetectionEnabled`、`silenceTimeout`、`silenceThreshold` |

**配置项说明**：

| 配置项 | 范围 | 默认值 | 说明 |
|--------|------|--------|------|
| 防抖间隔 | 500-2000ms | 800ms | 屏蔽快速连续触发，避免麦克风频繁启停啸叫、硬件损伤 |
| 静音检测 | 开启/关闭 | 开启 | 无声音输入时自动关闭功能 |
| 静音超时 | 5-300秒 | 30秒 | 静音持续多久后自动关闭 |
| 静音阈值 | 0.03-0.3 | 0.05 | 判定为静音的 RMS 音量阈值（适配车内环境噪音） |

---

### 4）状态同步机制

**功能描述**：悬浮窗和麦克风页面通过观察者模式同步 UI 状态。

**修改文件**：

| 文件 | 改动内容 |
|----------|----------|
| [FloatingWindowService.java](app/src/main/java/com/aug32/l7audio/service/FloatingWindowService.java) | 添加 `AnnouncementListener`，在 `showListView()` 注册、`hideListView()` 注销 |
| [MicAmplifierFragment.java](app/src/main/java/com/aug32/l7audio/ui/fragment/MicAmplifierFragment.java) | 添加 `AnnouncementListener`，在 `onViewCreated()` 注册、`onDestroyView()` 注销 |

---

### 5）RMS 音量检测

**功能描述**：`MicrophoneManager` 新增 `getCurrentRms()` 方法，计算音频帧的均方根值用于静音检测。

**修改文件**：

| 文件 | 改动内容 |
|----------|----------|
| [MicrophoneManager.java](app/src/main/java/com/aug32/l7audio/domain/audio/MicrophoneManager.java) | 新增 `currentRms` 变量和 `calculateRms()` 方法，实时计算音频 RMS 值 |

---

### 6）Toast 提示增强

**功能描述**：车外喊话开启/关闭时显示 Toast 提示，自动关闭时显示原因。

**修改文件**：

| 文件 | 改动内容 |
|----------|----------|
| [AnnouncementController.java](app/src/main/java/com/aug32/l7audio/domain/audio/AnnouncementController.java) | 开启时显示"车外喊话已开启"，关闭时显示"车外喊话已关闭"，自动关闭时显示"车外喊话已关闭：{原因}" |

---

### 7）MicrophoneManager 管线模式重构

**功能描述**：将 MicrophoneManager 从 ~900 行重构为 ~685 行，采用管线模式拆分音频处理逻辑。

**架构变更**：
- 新增 `AudioProcessor` 接口：定义音频处理器的统一契约
- 新增 `AudioPipeline` 管线类：按注册顺序串联执行处理器
- 新增 `GainLimiterProcessor`：增益放大 + tanh 软限幅（替代 clamp 硬限幅，减少削波失真）
- 新增 `AudioSuppressionProcessor`：噪声门 + 回声消除 + 啸叫抑制（三合一，共享 reset() 消除状态泄漏）

**修复的问题**：
- 状态泄漏：`releaseResources()` 遗漏 `previousAvgVolume`、`previousEnergy`、`mCurrentGain` 重置 → 通过 `pipeline.reset()` 统一清零
- 死代码移除：`detectSteadyNoise()` 计算未使用
- 处理顺序优化：增益→限幅→噪声门→回声→啸叫（原为噪声→回声→增益→限幅→啸叫）
- 噪声抑制算法升级：从能量阈值法改为噪声门（Noise Gate），避免将正常语音误判为噪声
- 回声消除优化：能量比判断 + 动态衰减，替代固定条件判断
- 啸叫抑制强化：动态衰减根据啸叫强度自动调整，替代固定衰减系数

**对外接口**：零变化，所有调用方无需修改。

**修改文件**：

| 文件 | 改动内容 |
|------|----------|
| [AudioProcessor.java](app/src/main/java/com/aug32/l7audio/domain/audio/AudioProcessor.java) | 新增：音频处理器接口 |
| [AudioPipeline.java](app/src/main/java/com/aug32/l7audio/domain/audio/AudioPipeline.java) | 新增：管线编排类 |
| [GainLimiterProcessor.java](app/src/main/java/com/aug32/l7audio/domain/audio/processor/GainLimiterProcessor.java) | 新增：增益+软限幅处理器 |
| [AudioSuppressionProcessor.java](app/src/main/java/com/aug32/l7audio/domain/audio/processor/AudioSuppressionProcessor.java) | 新增：三合一抑制处理器 |
| [MicrophoneManager.java](app/src/main/java/com/aug32/l7audio/domain/audio/MicrophoneManager.java) | 重构：退化为协调者，委托管线处理 |

---

详细版本历史请参考 [README.md](README.md)

---

## 四、旧版本记录

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

### 1）删除 TTS 语速/音调设置功能

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

### 2）重构 MediaSession 为 Android 原生实现

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

### 3）前台服务通知升级为 MediaStyle 标准样式

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

### 4）通知栏状态同步修复

**问题描述**：在音乐模块内点击播放/暂停按钮后，通知栏状态未同步更新，只有点击通知栏按钮才能正常更新。

**原因分析**：MusicPlayerManager 的状态变化回调中没有调用通知更新方法。

**修复方案**：

| 修改文件 | 改动内容 |
|----------|----------|
| [MusicPlayerManager.java](app/src/main/java/com/aug32/l7audio/domain/audio/MusicPlayerManager.java) | 在播放开始、暂停、停止、切歌等状态变化时调用 `AudioForegroundService.notifyUpdate()` |

---

### 5）底部导航选中效果修复

**问题描述**：打开音乐模块后，底部三大模块（音乐、麦克风、TTS）的选中高亮效果丢失。

**原因分析**：`loadFunctionPage()` 方法中缺少 `updateFunctionButtons()` 调用。

**修复方案**：

| 修改文件 | 改动内容 |
|----------|----------|
| [MainActivity.java](app/src/main/java/com/aug32/l7audio/ui/activity/MainActivity.java) | 在 `loadFunctionPage()` 方法末尾添加 `updateFunctionButtons()` 调用 |

---

### 6）枚举设备显示优化（添加滚动）

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

### 1）接入 Android 媒体中心（MediaSession）

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

### 2）显示音频路由按钮内容增强

**优化内容**：「显示音频路由」按钮展示更详细的音频路由信息，方便调试和排查问题。

**优化方案**：

| 修改文件 | 改动内容 |
|----------|----------|
| [SettingsFragment.java](app/src/main/java/com/aug32/l7audio/ui/fragment/SettingsFragment.java) | 重构 `displayAudioRoutes()` 方法，新增以下内容：<br>1. 基本音频信息：音频模式、铃声模式、蓝牙SCO状态<br>2. 音量设置：音乐流、铃声流、通话流、闹钟流的当前音量/最大音量<br>3. 系统音频设备列表：所有输入/输出设备的详细信息（名称、类型、ID、地址、方向）<br>4. 系统信息：Android版本、API Level、设备品牌/型号/厂商 |

---

### 3）添加重复音乐吐司提示优化

**优化内容**：添加音乐时，如果有重复或失败的歌曲，吐司提示会显示详细的统计信息。

**优化方案**：

| 修改文件 | 改动内容 |
|----------|----------|
| [MusicPlayerFragment.java](app/src/main/java/com/aug32/l7audio/ui/fragment/MusicPlayerFragment.java) | 优化 `onAddComplete()` 中的吐司提示，根据成功数量、重复数量、失败数量动态生成提示信息 |

---

### 4）全项目 import 语句规范化整理

**优化内容**：统一所有 Java 文件的 import 语句格式，提升代码可读性和规范性。

**优化方案**：

| 修改文件 | 改动内容 |
|----------|----------|
| 全部 40 个 Java 文件 | 1. 统一分组顺序：Android系统包（android.*）→ AndroidX/第三方（androidx.* 等）→ JDK标准库（java.*）→ 项目本地包（com.aug32.l7audio.*）<br>2. 组内按字母顺序严格排序<br>3. 组与组之间空一行分隔<br>4. 移除未使用的 import（共清理 28 个冗余导入） |

---

### 5）Fragment 生命周期规范（onDestroyView 内存泄漏防护）

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

### 1）扫描音乐完成后播放列表不自动刷新

**问题描述**：音乐文件较多时，等待时间长，扫描完列表不会自动刷新，要重新点开音乐页面才能看到歌曲。

**原因**：`addFilesToPlaylist` 回调中没有调用 `refreshPlaylist()` 方法。

**修复方案**：

| 修改文件 | 改动内容 |
|----------|----------|
| [MusicPlayerFragment.java](app/src/main/java/com/aug32/l7audio/ui/fragment/MusicPlayerFragment.java) | 在 `onAddComplete` 回调中添加 `refreshPlaylist()` 调用 |

---

### 2）TTS语音测试（车外）声音从车内喇叭发出

**问题描述**：设置页的"测试TTS发声（车外）"按钮，声音依旧从车内喇叭发出。

**原因**：`testTTS()` 方法使用的是默认音频输出模式，而非车外模式。

**修复方案**：

| 修改文件 | 改动内容 |
|----------|----------|
| [SettingsFragment.java](app/src/main/java/com/aug32/l7audio/ui/fragment/SettingsFragment.java) | 使用 `speakWithUsage(testMessage, externalUsage)` 传入车外音频 usage |

---

### 3）设置页枚举设备、查看音频路由与旧版不一致

**问题描述**：设置页中的"枚举麦克风"、"枚举输出设备"、"枚举车内输出设备"按钮，点开后的内容与1.1.1旧版不一致。

**原因**：重构时只保留了基础的状态显示，未实现反射调用 `AudioManager.getDevices()` 的完整实现。

**修复方案**：

| 修改文件 | 改动内容 |
|----------|----------|
| [SettingsFragment.java](app/src/main/java/com/aug32/l7audio/ui/fragment/SettingsFragment.java) | 按1.1.1旧版重新实现 `enumMicrophones()` / `enumOutputDevices()` / `enumCarOutputDevices()`，使用反射调用 `getDevices()` 方法遍历 `AudioDeviceInfo[]`，并新增 `getDeviceTypeName()` 工具方法映射22种设备类型中文名 |

---

### 4）歌曲封面无法显示

**问题描述**：歌曲封面读取不出来。

**原因**：`MusicItem` 类缺少封面字段，`MusicPlayerFragment` 无封面加载逻辑。

**修复方案**：

| 修改文件 | 改动内容 |
|----------|----------|
| [MusicItem.java](app/src/main/java/com/aug32/l7audio/domain/audio/MusicItem.java) | 新增 `albumArt` 字段存储封面字节数组 |
| [PlaylistManager.java](app/src/main/java/com/aug32/l7audio/domain/audio/playlist/PlaylistManager.java) | 在 `createItemFromFile` 中通过 `retriever.getEmbeddedPicture()` 提取封面 |
| [MusicPlayerFragment.java](app/src/main/java/com/aug32/l7audio/ui/fragment/MusicPlayerFragment.java) | 新增 `loadAlbumArt()` 方法，在 `updateCurrentSongInfo()` 中调用加载封面 |

---

### 5）文件选择器文件夹视觉效果问题

**问题描述**：点击添加音乐打开文件选择窗口，文件夹是灰色但可以点击进入。

**原因**：目录在文件选择模式下被设为不可选并设置了半透明。

**修复方案**：

| 修改文件 | 改动内容 |
|----------|----------|
| [FileBrowserAdapter.java](app/src/main/java/com/aug32/l7audio/ui/adapter/FileBrowserAdapter.java) | 目录保持正常显示，只对非音频文件设置半透明 |

---

### 6）横竖屏顶部多余空行/状态栏

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

### 7）文件浏览器存储设备页无返回上级目录按钮

**问题描述**：文件浏览器的内部存储、U盘等页面没有返回上级目录按钮。

**原因**：存储设备选择页是根级别，没有".."虚拟项。

**修复方案**：

| 修改文件 | 改动内容 |
|----------|----------|
| [FileBrowserFragment.java](app/src/main/java/com/aug32/l7audio/ui/fragment/FileBrowserFragment.java) | 在 `loadStorageList()` 中添加".."虚拟项，点击返回主页 |

---

### 8）文件浏览器目录层级导航统一优化

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

### 1）悬浮窗 TTS 选择按钮点击不自动跳转

**问题描述**：在悬浮窗的 TTS 列表中点击「选择」按钮后，主界面的 TTS 模块不自动跳转。

**原因**：`MainActivity` 已在运行时（Activity 栈中存在），`Intent` 通过 `onCreate()` 处理，后续启动 Activity 不触发 `onCreate()`，导致跳转逻辑不执行。

**修复方案**：

| 修改文件 | 修改位置 | 改动内容 |
|----------|----------|----------|
| [FloatingWindowService.java](app/src/main/java/com/aug32/l7audio/service/FloatingWindowService.java) | `openMainActivityToSelectTTS()` | 添加 `FLAG_ACTIVITY_NEW_TASK \| FLAG_ACTIVITY_CLEAR_TOP \| FLAG_ACTIVITY_SINGLE_TOP`，确保主界面被唤起 |
| [MainActivity.java](app/src/main/java/com/aug32/l7audio/MainActivity.java) | `onNewIntent()` | 新增覆盖方法，在 Activity 已运行时处理新 Intent 并切换到 TTS Fragment |
| [MainActivity.java](app/src/main/java/com/aug32/l7audio/MainActivity.java) | `handleFloatingWindowIntent()` | 重构，确保无论在 onCreate 还是 onNewIntent 中都能正确处理跳转 |

---

### 2）音乐模块「添加音乐」与「扫描音乐」列表重复

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

### 3）Release APK 日志输出未禁用

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
