# L7Audio CHANGELOG

> 日期：2026-07-11
> 版本：v1.5.8 (versionCode: 96)

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
