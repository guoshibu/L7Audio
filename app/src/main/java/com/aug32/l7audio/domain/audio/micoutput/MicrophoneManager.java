package com.aug32.l7audio.domain.audio.micoutput;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;

import androidx.core.content.ContextCompat;

import com.aug32.l7audio.data.local.AppConfig;
import com.aug32.l7audio.domain.audio.micoutput.processor.AdaptiveFeedbackCancellationProcessor;
import com.aug32.l7audio.domain.audio.micoutput.processor.AutomaticGainControlProcessor;
import com.aug32.l7audio.domain.audio.micoutput.processor.GainLimiterProcessor;
import com.aug32.l7audio.domain.audio.micoutput.processor.HighPassFilterProcessor;
import com.aug32.l7audio.domain.audio.micoutput.processor.LowPassFilterProcessor;
import com.aug32.l7audio.domain.audio.micoutput.processor.SpectralAndNotchProcessor;
import com.aug32.l7audio.BuildConfig;
import com.aug32.l7audio.utils.AppLog;

import java.util.Locale;

/**
 * 麦克风放大管理器
 *
 * <p>职责：
 * <ul>
 *   <li>麦克风音频录制与播放生命周期管理</li>
 *   <li>硬件回声消除器（AEC）的初始化与释放</li>
 *   <li>通过 {@link AudioPipeline} 串联音频处理管线</li>
 * </ul>
 *
 * <p>架构：采用管线模式（Pipeline Pattern），将增益放大、噪声门、回声消除、
 * 啸叫抑制拆分为独立的 {@link AudioProcessor} 处理器，由 {@link AudioPipeline} 按序执行。
 * MicrophoneManager 退化为协调者，负责录制/播放生命周期和硬件 AEC 管理。
 *
 * <p>目标 SDK：Android 11 (API 30)
 *
 * @author L7Audio
 * @since 1.4.3
 */
public class MicrophoneManager {
    private static final String TAG = "MicrophoneManager";

    // 采样率：48000Hz（正常音质）
    private static final int SAMPLE_RATE = 48000;
    // 声道配置：单声道输入
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    // 音频格式：16位PCM编码
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    // 缓冲区大小
    // 注意：BUFFER_SIZE 是类加载时求值的静态值，音频系统异常时可能为 -1（ERROR），
    // 绝不能直接用于 new byte[]/AudioTrack——实际运行一律使用 activeBufferSize（动态求值）
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
    );
    /**
     * 本次会话实际生效的缓冲区大小（字节）。
     * 修复：原实现在 recordAndPlay/AudioTrack 初始化处仍使用静态 BUFFER_SIZE，
     * 若类加载时 getMinBufferSize 返回 ERROR(-1)，new byte[-1] 会让录制线程
     * 抛 NegativeArraySizeException 且每次必现。改为 AudioRecord 初始化成功后
     * 从其真实缓冲区反推（getBufferSizeInFrames × 2 字节/帧），各处统一使用。
     * volatile：start 线程写、录制线程读，需保证可见性。
     */
    private volatile int activeBufferSize = 0;

    // 上下文对象
    private final Context context;
    // 音频输出管理器
    private final AudioOutputManager audioOutputManager;
    // 应用配置
    private final AppConfig appConfig;

    // 音频录制器
    private AudioRecord audioRecord;
    // 音频播放器
    private AudioTrack audioTrack;
    // 录制线程
    private Thread recordingThread;
    // 是否正在录制（volatile 保证多线程可见性）
    private volatile boolean isRecording = false;
    // 当前音频帧的 RMS 均值（volatile 保证静音检测线程可见性）
    private volatile float currentRms = 0.0f;
    // 管线处理后 RMS（供静音检测，volatile 保证线程可见性）
    private volatile float currentPostProcessRms = 0.0f;
    /**
     * 自上次读取以来的最高帧 RMS（峰值保持），专供静音检测。
     *
     * <p>为什么不能用平均 RMS：实车实测（1470 帧）说话时 p50=0.0005、p99=0.0435，
     * 但单帧峰值可达 0.3172——说话能量是稀疏尖峰。10 帧平均会把尖峰稀释掉
     * （单帧 0.3 + 9 帧近 0 ≈ 0.03），导致"正在说话"被静音检测误判为静音并关闭喊话。
     * 因此静音检测改读本字段：只要采样区间内出现过一次高于阈值的声音就认为有声。</p>
     */
    private volatile float peakRmsSinceLastRead = 0.0f;
    // RMS 滑动窗口平均，避免静音检测采样到瞬时静音帧（最近10帧的平均值）
    private static final int RMS_WINDOW_SIZE = 10;
    // 音频统计日志打印帧间隔（降低 DEBUG 日志 I/O 频率）
    private static final int STATS_LOG_INTERVAL_FRAMES = 100;
    // AFC ERLE 报告帧间隔（约 10s 一次，48kHz/100 帧缓冲）
    private static final int AFC_REPORT_INTERVAL_FRAMES = 500;
    private final float[] rmsWindow = new float[RMS_WINDOW_SIZE];
    private int rmsWindowIndex = 0;
    private volatile float averageRms = 0.0f;
    // 放大级别（0-10）
    private int amplificationLevel = 5;

    // ========== Android 原生 3A 硬件效果器 ==========
    private boolean hardwareAecAvailable = false;
    private boolean hardwareNsAvailable = false;
    private boolean hardwareAgcAvailable = false;
    private AcousticEchoCanceler acousticEchoCanceler;
    private NoiseSuppressor noiseSuppressor;
    private AutomaticGainControl automaticGainControl;
    // 硬件 NS/AGC 接管期间被临时关闭的软件处理器标记。
    // 硬件效果器与 AudioRecord 会话绑定，stop() 时必然释放；若不在此记录并恢复，
    // 第二次喊话时软件处理器开关仍是 false（initNoiseSuppressor 又会因"用户禁用降噪"提前返回），
    // 结果是硬件没了、软件也没开 —— 第二次喊话完全没有降噪/AGC
    private boolean softwareNsSuspendedByHardware = false;
    private boolean softwareAgcSuspendedByHardware = false;

    // ========== 音频处理管线 ==========
    /** 音频处理管线，按序执行 HPF → LPF → AFC → 增益 → 谱减法降噪 → 啸叫陷波 → AGC */
    private final AudioPipeline pipeline;
    /** 高通滤波器 */
    private final HighPassFilterProcessor hpfProcessor;
    /** 低通滤波器（与高通组成人声带通，砍高频啸叫/嘶声） */
    private final LowPassFilterProcessor lpfProcessor;
    /** NLMS 自适应反馈消除 */
    private final AdaptiveFeedbackCancellationProcessor afcProcessor;
    /** 增益限幅处理器 */
    private final GainLimiterProcessor gainProcessor;
    /** 谱减法降噪处理器 */
    private final SpectralAndNotchProcessor.SpectralNoiseReduction noiseReductionProcessor;
    /** FFT 啸叫陷波器 */
    private final SpectralAndNotchProcessor.HowlingNotchFilter howlingNotchProcessor;
    /** 自动增益控制器 */
    private final AutomaticGainControlProcessor agcProcessor;
    // 上一帧管线输出，作为 AFC 的参考信号（扬声器即将播放的信号）
    private short[] lastOutputSamples;

    /**
     * 构造函数
     * <p>
     * 从配置中读取放大级别和各音频处理开关的初始状态，
     * 并初始化音频处理管线。
     * </p>
     *
     * @param context             上下文对象
     * @param audioOutputManager  音频输出管理器，用于获取音频输出模式
     */
    public MicrophoneManager(Context context, AudioOutputManager audioOutputManager) {
        this.context = context;
        this.audioOutputManager = audioOutputManager;
        this.appConfig = new AppConfig(context);
        this.amplificationLevel = appConfig.getMicAmplificationLevel();

        // 初始化处理管线：HPF → LPF → AFC → 增益 → 谱减法降噪 → 啸叫陷波 → AGC
        // 高通截止频率从配置注入（默认 100Hz），砍低频胎噪
        this.hpfProcessor = new HighPassFilterProcessor(appConfig.getMicHpfCutoff());
        // 低通紧跟高通，与高通组成人声带通（约 100~4000Hz），砍高频啸叫/嘶声
        this.lpfProcessor = new LowPassFilterProcessor(appConfig.getMicLpfCutoff());
        this.lpfProcessor.setEnabled(appConfig.isMicLpfEnabled());
        this.afcProcessor = new AdaptiveFeedbackCancellationProcessor();
        this.afcProcessor.setEnabled(appConfig.isEchoCancellationEnabled());
        this.gainProcessor = new GainLimiterProcessor();
        this.noiseReductionProcessor = new SpectralAndNotchProcessor.SpectralNoiseReduction();
        this.noiseReductionProcessor.setEnabled(appConfig.isNoiseReductionEnabled());
        this.howlingNotchProcessor = new SpectralAndNotchProcessor.HowlingNotchFilter();
        this.howlingNotchProcessor.setEnabled(appConfig.isHowlingSuppressionEnabled());
        this.agcProcessor = new AutomaticGainControlProcessor();
        this.agcProcessor.setEnabled(appConfig.isAgcEnabled());

        this.pipeline = new AudioPipeline();
        pipeline.addProcessor(hpfProcessor);
        pipeline.addProcessor(lpfProcessor);
        pipeline.addProcessor(afcProcessor);
        pipeline.addProcessor(gainProcessor);
        pipeline.addProcessor(noiseReductionProcessor);
        pipeline.addProcessor(howlingNotchProcessor);
        pipeline.addProcessor(agcProcessor);

        // 根据配置的放大级别与增益范围，计算并设置初始增益倍数
        applyAmplificationFactor(amplificationLevel);

        AppLog.i(TAG, "麦克风管理器初始化，SR=" + SAMPLE_RATE + "Hz");
        AppLog.i(TAG, "HPF 截止=" + appConfig.getMicHpfCutoff() + "Hz, LPF 截止="
                + appConfig.getMicLpfCutoff() + "Hz (开启=" + lpfProcessor.isEnabled() + ")");
        AppLog.i(TAG, "AFC (NLMS): " + afcProcessor.isEnabled());
        AppLog.i(TAG, "谱减法降噪: " + noiseReductionProcessor.isEnabled());
        AppLog.i(TAG, "啸叫陷波: " + howlingNotchProcessor.isEnabled());

    }

    /**
     * 开始麦克风放大
     * <p>
     * 启动麦克风录制和音频播放，实现实时麦克风放大功能。
     * 若已在录制则直接返回成功。
     * 启动流程：检查权限 → 释放旧资源 → 初始化音频组件 → 重置管线 → 初始化回声消除 → 启动音频 → 启动录制线程
     * </p>
     *
     * @return true 表示启动成功，false 表示启动失败
     */
    public synchronized boolean start() {
        String threadName = Thread.currentThread().getName();

        AppLog.i(TAG, "========== 麦克风管理器启动() ==========");
        AppLog.i(TAG, "线程: " + threadName);
        // 调用栈构建涉及逐帧对象访问与字符串拼接，Release 下无日志需求，仅 DEBUG 保留
        if (BuildConfig.DEBUG) {
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            StringBuilder callerInfo = new StringBuilder();
            for (int i = 2; i < Math.min(6, stackTrace.length); i++) {
                StackTraceElement element = stackTrace[i];
                if (i > 2) callerInfo.append(" -> ");
                callerInfo.append(element.getClassName()).append(".").append(element.getMethodName())
                        .append("(").append(element.getFileName()).append(":").append(element.getLineNumber()).append(")");
            }
            AppLog.i(TAG, "调用方: " + callerInfo.toString());
        }
        AppLog.i(TAG, "当前 isRecording=" + isRecording + ", audioRecord=" + (audioRecord == null ? "null" : "not null"));

        // 已在录制，直接返回成功
        if (isRecording) {
            AppLog.d(TAG, "已在录制");
            return true;
        }

        // 检查录音权限，无权限则失败
        if (!checkPermissions()) {
            AppLog.e(TAG, "RECORD_AUDIO 权限未授予");
            return false;
        }

        try {
            // 先释放旧资源，确保每次启动都是全新初始化
            releaseResources();
            AppLog.i(TAG, "启动麦克风放大器（全新初始化）");

            // 初始化音频录制和播放组件
            if (!initializeAudioComponents()) {
                return false;
            }

            // 重置管线状态（清除上一轮会话的累积检测数据）；AGC 已含在管线内，无需单独复位
            pipeline.reset();

            // 初始化 Android 原生 3A 硬件效果器
            initializeHardwareEffects();

            // 启动音频录制和播放
            startAudioComponents();
            // 启动录制处理线程
            startRecordingThread();

            AppLog.i(TAG, "麦克风放大器启动成功");
            AppLog.i(TAG, "========== 麦克风管理器启动() 完成 ==========");

            return true;
        } catch (Exception e) {
            // 启动失败时释放所有资源，避免资源泄漏
            AppLog.e(TAG, "启动麦克风放大器失败", e);

            releaseResources();
            return false;
        }
    }

    /** 检查录音权限 */
    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(context, "android.permission.RECORD_AUDIO")
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 获取音频缓冲区大小（动态获取，避免类加载时获取错误值）
     * <p>
     * BUFFER_SIZE 作为 static final 在类加载时获取，如果此时音频系统状态异常，
     * 返回的缓冲区大小可能无效，导致整个应用生命周期内 AudioRecord 无法初始化。
     * 此方法在每次初始化时动态获取缓冲区大小。
     * </p>
     *
     * @return 缓冲区大小字节数，或 AudioRecord.ERROR_BAD_VALUE
     */
    private int getBufferSize() {
        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        if (bufferSize <= 0) {
            AppLog.e(TAG, "getMinBufferSize 返回无效值: " + bufferSize + ", 使用回退缓冲区大小");
            bufferSize = 2 * AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AUDIO_FORMAT);
            if (bufferSize <= 0) {
                bufferSize = 1536 * 2;
            }
        }
        AppLog.d(TAG, "获取缓冲区大小: SR=" + SAMPLE_RATE + ", channel=" + CHANNEL_CONFIG + ", format=" + AUDIO_FORMAT + ", bufferSize=" + bufferSize);
        return bufferSize;
    }

    /**
     * 尝试初始化 AudioRecord
     * <p>
     * 使用指定的音频源和缓冲区大小初始化 AudioRecord，并记录详细日志。
     * </p>
     *
     * @param audioSource 音频源
     * @param bufferSize 缓冲区大小
     * @return true 表示初始化成功，false 表示失败
     */
    @SuppressLint("MissingPermission")
    private boolean tryInitAudioRecord(int audioSource, int bufferSize) {
        try {
            audioRecord = new AudioRecord(audioSource, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize);
            int state = audioRecord.getState();
            boolean success = state == AudioRecord.STATE_INITIALIZED;

            String sourceName = getAudioSourceName(audioSource);
            if (success) {
                AppLog.i(TAG, "AudioRecord 初始化成功: source=" + sourceName + "(" + audioSource + "), SR=" + SAMPLE_RATE + ", buf=" + bufferSize + ", sessionId=" + audioRecord.getAudioSessionId());
            } else {
                AppLog.e(TAG, "AudioRecord 初始化失败: source=" + sourceName + "(" + audioSource + "), SR=" + SAMPLE_RATE + ", buf=" + bufferSize + ", state=" + state);
            }
            return success;
        } catch (Exception e) {
            String sourceName = getAudioSourceName(audioSource);
            AppLog.e(TAG, "AudioRecord 初始化异常: source=" + sourceName + "(" + audioSource + "), SR=" + SAMPLE_RATE + ", buf=" + bufferSize, e);
            return false;
        }
    }

    /**
     * 获取音频源名称（用于日志）
     *
     * @param source 音频源常量
     * @return 音频源名称
     */
    private String getAudioSourceName(int source) {
        switch (source) {
            case MediaRecorder.AudioSource.DEFAULT: return "DEFAULT";
            case MediaRecorder.AudioSource.MIC: return "MIC";
            case MediaRecorder.AudioSource.VOICE_RECOGNITION: return "VOICE_RECOGNITION";
            case MediaRecorder.AudioSource.VOICE_COMMUNICATION: return "VOICE_COMMUNICATION";
            case MediaRecorder.AudioSource.CAMCORDER: return "CAMCORDER";
            case MediaRecorder.AudioSource.UNPROCESSED: return "UNPROCESSED";
            default: return "UNKNOWN(" + source + ")";
        }
    }

    /**
     * 初始化音频组件
     * <p>
     * 三级回退机制确保 AudioRecord 初始化成功：
     * 1. 使用用户配置的音频源
     * 2. 回退到 MIC 音频源
     * 3. 回退到 DEFAULT 音频源
     * </p>
     * <p>
     * 每次初始化时动态获取缓冲区大小，避免类加载时获取的静态缓冲区大小导致的问题。
     * </p>
     *
     * @return true 表示初始化成功，false 表示初始化失败
     */
    @SuppressLint("MissingPermission")
    private boolean initializeAudioComponents() {
        int bufferSize = getBufferSize();
        int audioSource = appConfig.getAudioInputSource();

        AppLog.i(TAG, "=== AudioRecord 初始化开始 ===");
        AppLog.i(TAG, "静态 BUFFER_SIZE=" + BUFFER_SIZE + ", 动态 bufferSize=" + bufferSize);
        AppLog.i(TAG, "用户配置的音频源: " + getAudioSourceName(audioSource) + "(" + audioSource + ")");

        // 第一级：使用用户配置的音频源
        if (tryInitAudioRecord(audioSource, bufferSize)) {
            AppLog.i(TAG, "=== AudioRecord 初始化在第1级成功 ===");
        } else {
            // 第二级回退：用户配置的音频源失败，回退到MIC
            AppLog.w(TAG, "回退到 MIC");
            audioSource = MediaRecorder.AudioSource.MIC;
            if (tryInitAudioRecord(audioSource, bufferSize)) {
                AppLog.i(TAG, "=== AudioRecord 初始化在第2级成功 ===");
            } else {
                // 第三级回退：MIC也失败，回退到DEFAULT
                AppLog.e(TAG, "回退到 DEFAULT");
                audioSource = MediaRecorder.AudioSource.DEFAULT;
                if (tryInitAudioRecord(audioSource, bufferSize)) {
                    AppLog.i(TAG, "=== AudioRecord 初始化在第3级成功 ===");
                } else {
                    // 三级都失败，尝试增大缓冲区重试一次
                    AppLog.e(TAG, "所有音频源都失败，尝试增大缓冲区");
                    int largerBufferSize = bufferSize * 2;
                    audioSource = MediaRecorder.AudioSource.MIC;
                    if (tryInitAudioRecord(audioSource, largerBufferSize)) {
                        AppLog.i(TAG, "=== AudioRecord 使用增大缓冲区初始化成功 ===");
                    } else {
                        AppLog.e(TAG, "=== AudioRecord 初始化失败，所有重试都已耗尽 ===");
                        return false;
                    }
                }
            }
        }

        // AudioRecord 初始化成功后，从真实缓冲区反推实际生效的缓冲区字节数，
        // 供 AudioTrack 初始化与录制循环统一使用（替代可能有误的静态 BUFFER_SIZE）
        activeBufferSize = audioRecord.getBufferSizeInFrames() * 2;
        if (activeBufferSize <= 0) {
            // getBufferSizeInFrames 异常回退：使用本次动态求值并通过兜底校验的 bufferSize
            activeBufferSize = bufferSize;
        }
        AppLog.i(TAG, "实际生效缓冲区: " + activeBufferSize + " bytes (静态 BUFFER_SIZE=" + BUFFER_SIZE + ")");

        // 初始化 AudioTrack 播放组件
        if (!initializeAudioTrack()) {
            return false;
        }

        return true;
    }

    /**
     * 初始化 AudioTrack
     * <p>
     * 根据当前音频输出模式配置 AudioTrack，用于播放麦克风采集并处理后的音频。
     * </p>
     *
     * @return true 表示初始化成功，false 表示初始化失败
     */
    private boolean initializeAudioTrack() {
        // 获取音频输出模式：优先从AudioOutputManager获取，兜底用配置中的车外模式
        int audioUsage;
        if (audioOutputManager != null) {
            audioUsage = audioOutputManager.getAudioUsage();
            AppLog.d(TAG, "使用 AudioOutputManager 的音频用法: " + audioUsage);
        } else {
            audioUsage = android.media.AudioAttributes.USAGE_MEDIA;
            AppLog.d(TAG, "AudioOutputManager 为空，回退到 USAGE_MEDIA");
        }

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(audioUsage)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();

        AudioFormat audioFormat = new AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AUDIO_FORMAT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build();

        // 修复：改用 AudioRecord 实际生效的缓冲区大小（activeBufferSize）。
        // 原来用静态 BUFFER_SIZE，类加载时音频系统异常会得到 -1，直接导致 AudioTrack 构建失败
        int trackBufferSize = activeBufferSize > 0 ? activeBufferSize : getBufferSize();
        audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(trackBufferSize)
                .build();

        if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
            AppLog.e(TAG, "AudioTrack 初始化失败: usage=" + audioUsage + " SR=" + SAMPLE_RATE + " buf=" + trackBufferSize);
            return false;
        }

        AppLog.i(TAG, "AudioTrack 初始化成功: usage=" + audioUsage + " SR=" + SAMPLE_RATE + " buf=" + trackBufferSize + " state=" + audioTrack.getState());
        return true;
    }

    /**
     * 初始化 Android 原生 3A 硬件效果器（NS + AEC + AGC）
     * <p>
     * 仅在用户启用对应功能时才尝试初始化硬件效果器。
     * 硬件效果器可用时自动禁用对应的软件处理器。
     * 每个效果器状态都输出到日志，方便分析设备支持情况。
     * </p>
     */
    private void initializeHardwareEffects() {
        int sessionId = audioRecord.getAudioSessionId();
        hardwareNsAvailable = false;
        hardwareAecAvailable = false;
        hardwareAgcAvailable = false;

        AppLog.i(TAG, "── 硬件效果器 ──");
        initNoiseSuppressor(sessionId);
        initEchoCanceler(sessionId);
    initAutomaticGainControl(sessionId);
        String pStatus = pipelineStatus();
        AppLog.i(TAG, "── 管线状态摘要 ──");
        AppLog.i(TAG, pStatus);
    }

    private void initNoiseSuppressor(int sessionId) {
        if (!noiseReductionProcessor.isEnabled()) {
            AppLog.i(TAG, "NoiseSuppressor:   跳过 (用户禁用降噪)");
            return;
        }
        try {
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(sessionId);
                if (noiseSuppressor != null) {
                    noiseSuppressor.setEnabled(true);
                    hardwareNsAvailable = true;
                    // 记录"软件降噪是被硬件接管才关的"，会话结束后据此恢复用户原配置
                    softwareNsSuspendedByHardware = noiseReductionProcessor.isEnabled();
                    noiseReductionProcessor.setEnabled(false);
                    AppLog.i(TAG, "NoiseSuppressor:   可用 → 已启用, 谱减降噪禁用");
        } else {
                    AppLog.i(TAG, "NoiseSuppressor:   create() 返回 null");
                }
            } else {
                AppLog.i(TAG, "NoiseSuppressor:   不可用 → 使用软件谱减降噪");
            }
        } catch (Exception e) {
            AppLog.e(TAG, "NoiseSuppressor:   错误 → 使用软件谱减降噪", e);
        }
    }

    private void initEchoCanceler(int sessionId) {
        if (!afcProcessor.isEnabled()) {
            AppLog.i(TAG, "AcousticEchoCanceler: 跳过 (用户禁用回声消除)");
            return;
        }
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                acousticEchoCanceler = AcousticEchoCanceler.create(sessionId);
                if (acousticEchoCanceler != null) {
                    acousticEchoCanceler.setEnabled(true);
                    hardwareAecAvailable = true;
                    AppLog.i(TAG, "AcousticEchoCanceler: 可用 → 已启用 (AFC NLMS 残留处理开启)");
                } else {
                    AppLog.i(TAG, "AcousticEchoCanceler: 在 AudioRecord 上 create() 返回 null, 尝试 AudioTrack");
                    try {
                        int trackSessionId = audioTrack.getAudioSessionId();
                        acousticEchoCanceler = AcousticEchoCanceler.create(trackSessionId);
                        if (acousticEchoCanceler != null) {
                            acousticEchoCanceler.setEnabled(true);
                            hardwareAecAvailable = true;
                            AppLog.i(TAG, "AcousticEchoCanceler: 在 AudioTrack 上可用 → 已启用 (AFC NLMS 残留处理开启)");
                        } else {
                            AppLog.i(TAG, "AcousticEchoCanceler: 在 AudioTrack 上 create() 也返回 null → 使用软件 AFC");
                        }
                    } catch (Exception e2) {
                        AppLog.e(TAG, "AcousticEchoCanceler: AudioTrack 回退错误", e2);
                    }
                }
            } else {
                AppLog.i(TAG, "AcousticEchoCanceler: 不可用 → 使用软件 AFC (NLMS)");
            }
        } catch (Exception e) {
            AppLog.e(TAG, "AcousticEchoCanceler: 错误 → 使用软件 AFC", e);
        }
    }

    private void initAutomaticGainControl(int sessionId) {
        // 与降噪一致：用户关闭 AGC 时不应偷偷启用硬件 AGC，否则配置项形同虚设
        if (!agcProcessor.isEnabled()) {
            AppLog.i(TAG, "AutomaticGainControl: 跳过 (用户禁用 AGC)");
            return;
        }
        try {
            if (AutomaticGainControl.isAvailable()) {
                automaticGainControl = AutomaticGainControl.create(sessionId);
                if (automaticGainControl != null) {
                    automaticGainControl.setEnabled(true);
                    hardwareAgcAvailable = true;
                    // 同 NS：记录软件 AGC 是"被硬件接管"才关的，会话结束后恢复
                    softwareAgcSuspendedByHardware = true;
                    agcProcessor.setEnabled(false);
                    AppLog.i(TAG, "AutomaticGainControl: 可用 → 已启用, AGC 处理器禁用");
                } else {
                    AppLog.i(TAG, "AutomaticGainControl: create() 返回 null → 使用软件 AGC");
                }
            } else {
                AppLog.i(TAG, "AutomaticGainControl: 不可用 → 使用软件 AGC");
            }
        } catch (Exception e) {
            AppLog.e(TAG, "AutomaticGainControl: 错误 → 使用软件 AGC", e);
        }
    }

    /** 生成管线状态摘要 */
    private String pipelineStatus() {
        return "HPF[on]" +
                " NS[" + (hardwareNsAvailable ? "hw" : (noiseReductionProcessor.isEnabled() ? "sw" : "off")) + "]" +
                " AEC[" + (hardwareAecAvailable ? "hw" : (afcProcessor.isEnabled() ? "sw" : "off")) + "]" +
                " Gain[" + (gainProcessor.isEnabled() ? "on" : "off") + "]" +
                " Notch[" + (howlingNotchProcessor.isEnabled() ? "on" : "off") + "]" +
                " AGC[" + (hardwareAgcAvailable ? "hw" : (agcProcessor.isEnabled() ? "sw" : "off")) + "]" +
                " SR=" + SAMPLE_RATE;
    }

    /**
     * 开始录音和播放
     * <p>
     * 启动 AudioRecord 录制和 AudioTrack 播放，并标记为正在录制状态。
     * </p>
     */
    private void startAudioComponents() {
        AppLog.d(TAG, "启动 AudioRecord & AudioTrack");
        audioRecord.startRecording();
        audioTrack.play();
        isRecording = true;
        AppLog.i(TAG, "AudioRecord & AudioTrack 已启动, sessionId=" + audioRecord.getAudioSessionId());
    }

    /**
     * 启动录制线程
     * <p>
     * 创建并启动独立的录制处理线程，避免阻塞主线程。
     * </p>
     */
    private void startRecordingThread() {
        recordingThread = new Thread(() -> {
            AppLog.d(TAG, "录制线程已启动");
            recordAndPlay();
        }, "MicrophoneRecordingThread");
        recordingThread.start();
        AppLog.d(TAG, "录制线程已分发");
    }

    /**
     * 停止麦克风放大
     * <p>
     * 停止录制和播放，等待录制线程结束，并释放所有音频资源。
     * 下次启动时会重新初始化所有组件。
     * </p>
     */
    public void stop() {
        Thread threadToJoin;
        // 临界区只做"标记停止 + 强制唤醒 read()"，不在这里等线程
        synchronized (this) {
            if (!isRecording) {
                AppLog.d(TAG, "未在录制");
                return;
            }

            // 先设置标记，让录制线程自然退出
            isRecording = false;

            // 强制解除 AudioRecord.read() 阻塞，确保录制线程能退出
            if (audioRecord != null
                    && audioRecord.getState() == AudioRecord.STATE_INITIALIZED
                    && audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                try {
                    audioRecord.stop();
                } catch (Exception e) {
                    AppLog.e(TAG, "强制停止 AudioRecord 错误", e);
                }
            }
            threadToJoin = recordingThread;
        }

        // 等待录制线程结束，最多等待1秒，避免ANR。
        // 必须在锁外 join：录制线程异常退出时会在 catch 中调用 synchronized 的
        // releaseResources()，若持锁等待，两线程互等满 1 秒（主线程掉帧）
        if (threadToJoin != null && threadToJoin != Thread.currentThread()) {
            try {
                threadToJoin.join(1000);
            } catch (InterruptedException e) {
                AppLog.e(TAG, "等待录制线程结束失败", e);
                // 恢复中断标记，交由上层决定是否继续传播
                Thread.currentThread().interrupt();
            }
        }

        // 释放所有音频资源（与录制线程异常退出路径共用同一把锁，串行化，避免双释放）
        synchronized (this) {
            releaseResources();
            currentRms = 0.0f;
            // 峰值同样清零，避免下一次喊话沿用上一轮的残留峰值
            peakRmsSinceLastRead = 0.0f;
        }
        AppLog.i(TAG, "麦克风放大器已停止，所有资源已释放");
    }

    /**
     * 录制并播放音频的主循环
     * <p>
     * 在独立线程中运行，不断从麦克风读取音频数据，处理后通过 AudioTrack 播放。
     * isRecording 标记为 false 时退出循环。
     * </p>
     */
    private void recordAndPlay() {
        // 修复：使用本次会话实际生效的缓冲区大小（activeBufferSize）。
        // 原来用静态 BUFFER_SIZE——类加载时 getMinBufferSize 返回 ERROR(-1) 的话，
        // new byte[-1] 会让录制线程抛 NegativeArraySizeException 且每次必现崩溃
        int bufSize = activeBufferSize > 0 ? activeBufferSize : getBufferSize();
        byte[] buffer = new byte[bufSize];
        short[] samples = new short[0];

        int frameCount = 0;
        while (isRecording) {
            try {
                int readSize = audioRecord.read(buffer, 0, bufSize);

                if (readSize > 0) {
                    int sampleCount = readSize / 2;
                    if (samples.length != sampleCount) {
                        samples = new short[sampleCount];
                    }
                    frameCount++;
                    processAudioData(buffer, readSize, samples, frameCount);
                    audioTrack.write(buffer, 0, readSize);
                    if (frameCount % STATS_LOG_INTERVAL_FRAMES == 0) {
                        AppLog.i(TAG, "录制中: frame=" + frameCount + " preRms=" + String.format(java.util.Locale.US, "%.4f", currentRms) + " postRms=" + String.format(java.util.Locale.US, "%.4f", currentPostProcessRms) + " written=" + readSize);
                    }
                } else if (readSize < 0) {
                    AppLog.e(TAG, "AudioRecord 读取错误: " + readSize);
                } else {
                    if (frameCount % STATS_LOG_INTERVAL_FRAMES == 0) {
                        AppLog.w(TAG, "AudioRecord 读取零字节, frame=" + frameCount);
                    }
                }
            } catch (Exception e) {
                AppLog.e(TAG, "录制线程错误, frame=" + frameCount, e);
                // 修复（C4）：必须先复位 isRecording 再释放资源。
                // 原实现只 releaseResources() 就退出——releaseResources 不碰该标记，
                // 线程已死但 isRecording()==true：start() 幂等分支假成功（无声）、
                // 悬浮窗/UI 持续显示"喊话中"，且静音检测读到的 RMS 是冻结的旧值，
                // 形成"喊话假活"窗口
                isRecording = false;
                releaseResources();
                break;
            }
        }
        AppLog.i(TAG, "录制线程退出, 总帧数=" + frameCount);
    }

    /**
     * 通过管线处理音频数据
     * <p>
     * 处理流程：byte[] → short[] → setReference(上一帧输出) → pipeline.process → 保存当前帧输出 → short[] → byte[]
     * </p>
     * <p>
     * AFC 参考信号修复：用上一帧的管线输出（即将写入 AudioTrack 并被扬声器播放的信号）作为当前帧的参考，
     * 这样 NLMS 滤波器才能学习真实的回声路径（扬声器输出 → 车内声学传播 → 麦克风采集）。
     * 引入约 2.67ms 延迟（一帧），远小于车内声学传播延迟（10-50ms），可忽略。
     * </p>
     *
     * @param buffer     音频数据缓冲区
     * @param length     有效数据长度
     * @param samples    采样数据数组
     * @param frameCount 当前帧计数
     */
    private void processAudioData(byte[] buffer, int length, short[] samples, int frameCount) {
        double sum = 0.0;
        int sampleCount = length / 2;
        for (int i = 0; i < sampleCount; i++) {
            int index = i * 2;
            short s = (short) ((buffer[index] & 0xFF) | (buffer[index + 1] << 8));
            samples[i] = s;
            float normalized = s / 32768.0f;
            sum += normalized * normalized;
        }
        currentRms = (float) Math.sqrt(sum / sampleCount);

        // 峰值保持：静音检测线程每 500ms 消费一次并清零，区间内任意一帧达到峰值即算有声
        if (currentRms > peakRmsSinceLastRead) {
            peakRmsSinceLastRead = currentRms;
        }

        rmsWindow[rmsWindowIndex] = currentRms;
        rmsWindowIndex = (rmsWindowIndex + 1) % RMS_WINDOW_SIZE;
        float sumRms = 0;
        for (float r : rmsWindow) sumRms += r;
        averageRms = sumRms / RMS_WINDOW_SIZE;

        if (afcProcessor.isEnabled()) {
            if (lastOutputSamples != null) {
                afcProcessor.setReference(lastOutputSamples);
                if (BuildConfig.DEBUG && frameCount % STATS_LOG_INTERVAL_FRAMES == 0) {
                    float refRms = calculateShortArrayRms(lastOutputSamples);
                    AppLog.d(TAG, "AFC参考信号已设置, 参考RMS=" + String.format(Locale.US, "%.4f", refRms) + ", 当前帧RMS=" + String.format(Locale.US, "%.4f", currentRms));
                }
            } else {
                if (BuildConfig.DEBUG && frameCount == 1) {
                    AppLog.d(TAG, "AFC参考信号暂不可用(首帧), 等待下一帧");
                }
            }
        }

        try {
            pipeline.process(samples);
        } catch (Exception e) {
            AppLog.e(TAG, "管线处理错误", e);
        }

        if (BuildConfig.DEBUG && afcProcessor.isEnabled()) {
            float erle = afcProcessor.getLastErleDb();
            float coeffNorm = afcProcessor.getCoefficientNorm();
            AppLog.d(TAG, "AFC帧" + frameCount + ": ERLE=" + String.format(Locale.US, "%.1f", erle) + "dB, 系数范数=" + String.format(Locale.US, "%.3f", coeffNorm) + ", RMS=" + String.format(Locale.US, "%.4f", currentRms));
            if (erle > 6.0f && frameCount % AFC_REPORT_INTERVAL_FRAMES == 0) {
                AppLog.i(TAG, "AFC回声消除生效! ERLE=" + String.format(Locale.US, "%.1f", erle) + "dB");
            }
            if (erle < 1.0f && frameCount > 1000 && frameCount % AFC_REPORT_INTERVAL_FRAMES == 0) {  // 跳过前 1000 帧让 NLMS 收敛
                AppLog.w(TAG, "AFC效果不佳! ERLE=" + String.format(Locale.US, "%.1f", erle) + "dB, 请检查参考信号是否正确");
            }
        }

        if (lastOutputSamples == null || lastOutputSamples.length != samples.length) {
            lastOutputSamples = new short[samples.length];
            if (BuildConfig.DEBUG) {
                AppLog.d(TAG, "初始化AFC参考缓冲区, 大小=" + samples.length);
            }
        }
        System.arraycopy(samples, 0, lastOutputSamples, 0, samples.length);

        // 性能优化：处理后 RMS 统计与 short→byte 写回原是两次全数组遍历，
        // 合并为单次遍历（逐样本顺带累加平方和），每帧省一次 O(N) 扫描
        double postSum = 0.0;
        for (int i = 0; i < sampleCount; i++) {
            int index = i * 2;
            short s = samples[i];
            buffer[index] = (byte) (s & 0xFF);
            buffer[index + 1] = (byte) (s >> 8);
            float normalized = s / 32768.0f;
            postSum += normalized * normalized;
        }
        currentPostProcessRms = postSum > 0 ? (float) Math.sqrt(postSum / sampleCount) : 0.0f;
    }

    /** 计算 short 数组的 RMS 值 */
    private float calculateShortArrayRms(short[] samples) {
        double sum = 0.0;
        for (short s : samples) {
            float normalized = s / 32768.0f;
            sum += normalized * normalized;
        }
        return sum > 0 ? (float) Math.sqrt(sum / samples.length) : 0.0f;
    }

    /** 释放所有资源 */
    // 修复（线程安全）：synchronized 防止 stop() 与录制线程异常退出路径并发释放。
    // 原实现录制线程 catch 中直接调 releaseResources()，与 stop()（synchronized）
    // 无互斥，可能出现双释放/释放期间字段被置 null 后再访问；stop() join(1000) 超时后
    // 释放资源时录制线程可能仍在跑，两线程同时操作同一 AudioRecord/AudioTrack。
    // 统一锁到对象监视器（与 stop() 同一把锁）后串行化，方法内部对 null 字段幂等安全
    private synchronized void releaseResources() {
        AppLog.d(TAG, "========== 释放资源() ==========");
        AppLog.d(TAG, "释放前: audioRecord=" + (audioRecord == null ? "null" : "not null")
                + ", audioTrack=" + (audioTrack == null ? "null" : "not null")
                + ", isRecording=" + isRecording);

        releaseHardwareEffects();
        releaseAudioRecord();
        releaseAudioTrack();

        recordingThread = null;
        // 重置管线状态（清除累积的检测数据）；AGC 已含在管线内，无需单独复位
        pipeline.reset();

        AppLog.d(TAG, "释放后: audioRecord=" + (audioRecord == null ? "null" : "not null")
                + ", audioTrack=" + (audioTrack == null ? "null" : "not null")
                + ", isRecording=" + isRecording);
        AppLog.d(TAG, "========== 释放资源() 完成 ==========");
    }

    /** 释放所有 Android 原生硬件效果器 */
    private void releaseHardwareEffects() {
        AppLog.d(TAG, "释放硬件效果器 NS=" + hardwareNsAvailable + " AEC=" + hardwareAecAvailable + " AGC=" + hardwareAgcAvailable);
        releaseNoiseSuppressor();
        releaseEchoCanceler();
        releaseAutomaticGainControl();
    }

    private void releaseNoiseSuppressor() {
        if (noiseSuppressor != null) {
            try {
                noiseSuppressor.setEnabled(false);
                noiseSuppressor.release();
                AppLog.d(TAG, "NoiseSuppressor 已释放");
            } catch (Exception e) {
                AppLog.e(TAG, "释放 NoiseSuppressor 错误", e);
            }
            noiseSuppressor = null;
        }
        hardwareNsAvailable = false;
        // 会话结束：恢复被硬件 NS 临时关闭的软件降噪，保证下一次喊话仍有降噪
        if (softwareNsSuspendedByHardware) {
            noiseReductionProcessor.setEnabled(true);
            softwareNsSuspendedByHardware = false;
            AppLog.d(TAG, "NoiseSuppressor 已释放，恢复软件谱减降噪");
        }
    }

    private void releaseEchoCanceler() {
        if (acousticEchoCanceler != null) {
            try {
                acousticEchoCanceler.setEnabled(false);
                acousticEchoCanceler.release();
                AppLog.d(TAG, "AcousticEchoCanceler 已释放");
            } catch (Exception e) {
                AppLog.e(TAG, "释放 AcousticEchoCanceler 错误", e);
            }
            acousticEchoCanceler = null;
        }
        hardwareAecAvailable = false;
    }

    private void releaseAutomaticGainControl() {
        if (automaticGainControl != null) {
            try {
                automaticGainControl.setEnabled(false);
                automaticGainControl.release();
                AppLog.d(TAG, "AutomaticGainControl 已释放");
            } catch (Exception e) {
                AppLog.e(TAG, "释放 AutomaticGainControl 错误", e);
            }
            automaticGainControl = null;
        }
        hardwareAgcAvailable = false;
        // 会话结束：恢复被硬件 AGC 临时关闭的软件 AGC，保证下一次喊话仍有增益控制
        if (softwareAgcSuspendedByHardware) {
            agcProcessor.setEnabled(true);
            softwareAgcSuspendedByHardware = false;
            AppLog.d(TAG, "AutomaticGainControl 已释放，恢复软件 AGC");
        }
    }

    /** 释放 AudioRecord */
    private void releaseAudioRecord() {
        if (audioRecord != null) {
            try {
                if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                    if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                        audioRecord.stop();
                        AppLog.d(TAG, "AudioRecord 已停止");
                    }
                    audioRecord.release();
                    AppLog.d(TAG, "AudioRecord 已释放");
                }
            } catch (Exception e) {
                AppLog.e(TAG, "释放 AudioRecord 错误", e);
            }
            audioRecord = null;
        }
    }

    /** 释放 AudioTrack */
    private void releaseAudioTrack() {
        if (audioTrack != null) {
            try {
                if (audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
                    if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                        audioTrack.stop();
                        AppLog.d(TAG, "AudioTrack 已停止");
                    }
                    audioTrack.release();
                    AppLog.d(TAG, "AudioTrack 已释放");
                }
            } catch (Exception e) {
                AppLog.e(TAG, "释放 AudioTrack 错误", e);
            }
            audioTrack = null;
        }
    }

    // ========== 公开 API（保持兼容） ==========

    /**
     * 设置放大级别
     * <p>
     * 放大级别范围 0-10，超出范围会被自动截断。
     * 设置后会自动保存到配置中。
     * </p>
     *
     * @param level 放大级别，取值范围 0-10
     */
    public void setAmplificationLevel(int level) {
        if (level < 0) level = 0;
        else if (level > 10) level = 10;
        this.amplificationLevel = level;
        appConfig.setMicAmplificationLevel(level);
        applyAmplificationFactor(level);
    }

    /**
     * 根据放大级别、最大放大倍率与最小增益，计算增益倍数并应用到增益处理器。
     *
     * <p>增益倍数 = 最大放大倍率 × 级别 / 10，随后用配置的增益上下限
     * [minGain, maxAmp] 裁剪（级别为 0 时静音，不参与裁剪）。
     * 抽取为独立方法，供构造函数初始化与运行时调节共用，避免逻辑重复。
     *
     * @param level 放大级别（0~10）
     */
    private void applyAmplificationFactor(int level) {
        int maxAmp = appConfig.getMaxAmplification();
        float minGain = appConfig.getMicMinGain();
        float factor = level == 0 ? 0.0f : maxAmp * level / 10.0f;
        // 级别非 0 时用增益上下限裁剪，保证放大倍数落在用户配置的 [minGain, maxAmp] 区间
        if (factor > 0) {
            if (factor < minGain) factor = minGain;
            if (factor > maxAmp) factor = maxAmp;
        }
        gainProcessor.setAmplificationFactor(factor);
        AppLog.d(TAG, "放大级别=" + level + " maxAmp=" + maxAmp
                + " minGain=" + String.format(java.util.Locale.US, "%.2f", minGain)
                + " -> factor=" + String.format(java.util.Locale.US, "%.2f", factor));
    }

    /**
     * 运行时修改高通截止频率并即时生效（无需重启喊话）。
     *
     * @param cutoffHz 截止频率（Hz），由处理器 clamp 到合法范围
     */
    public void setHpfCutoff(int cutoffHz) {
        hpfProcessor.setCutoffFrequency(cutoffHz);
        AppLog.d(TAG, "HPF 截止频率运行时更新为 " + cutoffHz + "Hz");
    }

    /**
     * 运行时修改低通截止频率并即时生效（无需重启喊话）。
     *
     * @param cutoffHz 截止频率（Hz），由处理器 clamp 到合法范围
     */
    public void setLpfCutoff(int cutoffHz) {
        lpfProcessor.setCutoffFrequency(cutoffHz);
        AppLog.d(TAG, "LPF 截止频率运行时更新为 " + cutoffHz + "Hz");
    }

    /**
     * 运行时开关低通滤波器并即时生效。
     *
     * @param enabled true 开启，false 关闭
     */
    public void setLowPassEnabled(boolean enabled) {
        lpfProcessor.setEnabled(enabled);
        AppLog.d(TAG, "LPF 运行时开关设为 " + enabled);
    }

    /**
     * 运行时重算并应用增益倍数（增益上下限或最大放大倍率改变后调用）。
     *
     * <p>沿用当前放大级别，按最新的 minGain/maxAmp 配置重新裁剪增益。
     */
    public void refreshAmplificationFactor() {
        applyAmplificationFactor(amplificationLevel);
    }

    /**
     * 检查是否正在录制
     *
     * @return true 表示正在录制，false 表示未在录制
     */
    public boolean isRecording() {
        return isRecording;
    }

    /**
     * 获取当前音频帧的 RMS 均值（滑动窗口平均）
     * <p>
     * 返回最近10帧的 RMS 平均值，用于静音检测，避免采样到瞬时静音帧导致误判。
     * 当前暂无调用方，预留用于：
     * - 可视化界面显示输入音量
     * - 静音检测逻辑
     * - 调试日志输出
     * </p>
     *
     * @return 平均 RMS 值（0-1），0 表示无音频输入或未录制
     */
    public float getCurrentRms() {
        return averageRms;
    }

    /**
     * 读取并清零"自上次读取以来的最高帧 RMS"（峰值保持）。
     *
     * <p>静音检测专用：相比平均 RMS，峰值不会被稀疏的说话尖峰稀释，
     * 避免"正在说话却判为静音"。读取后立即清零，下一次调用统计新的区间。</p>
     *
     * @return 自上次调用以来出现过的最高帧 RMS（0 表示区间内无数据）
     */
    public float getAndResetPeakRms() {
        float peak = peakRmsSinceLastRead;
        peakRmsSinceLastRead = 0.0f;
        return peak;
    }

    /**
     * 设置是否启用噪声抑制（谱减法）
     * <p>
     * 设置后会自动保存到配置中，并立即生效（无需重启录制）。
     * </p>
     *
     * @param enabled true 启用谱减法降噪，false 禁用
     */
    public void setNoiseReductionEnabled(boolean enabled) {
        noiseReductionProcessor.setEnabled(enabled);
        // 用户在硬件 NS 接管期间显式关掉降噪：放弃"会话结束后恢复开启"的承诺，
        // 否则 stop() 会把用户刚关掉的降噪又打开
        if (!enabled) {
            softwareNsSuspendedByHardware = false;
        }
        appConfig.setNoiseReductionEnabled(enabled);
        AppLog.d(TAG, "谱减法降噪 " + (enabled ? "已启用" : "已禁用"));
    }

    /**
     * 设置是否启用自适应反馈消除（NLMS）
     * <p>
     * 设置后会自动保存到配置中。
     * 硬件AEC可用时优先使用硬件回声消除，NLMS 作为软件补充。
     * </p>
     *
     * @param enabled true 启用 NLMS 反馈消除，false 禁用
     */
    public void setEchoCancellationEnabled(boolean enabled) {
        afcProcessor.setEnabled(enabled);
        appConfig.setEchoCancellationEnabled(enabled);
        AppLog.d(TAG, "AFC (NLMS) " + (enabled ? "已启用" : "已禁用"));
    }

    /**
     * 设置是否启用 FFT 啸叫陷波器
     * <p>
     * 设置后会自动保存到配置中，并立即生效。
     * </p>
     *
     * @param enabled true 启用啸叫陷波，false 禁用
     */
    public void setHowlingSuppressionEnabled(boolean enabled) {
        howlingNotchProcessor.setEnabled(enabled);
        appConfig.setHowlingSuppressionEnabled(enabled);
        AppLog.d(TAG, "啸叫陷波 " + (enabled ? "已启用" : "已禁用"));
    }

    /**
     * 设置是否启用自动增益控制（AGC）
     * <p>
     * 硬件 AGC 可用时优先使用硬件，此开关仅控制软件 AGC 处理器。
     * </p>
     *
     * @param enabled true 启用软件 AGC，false 禁用
     */
    public void setAgcEnabled(boolean enabled) {
        if (enabled && !hardwareAgcAvailable) {
            agcProcessor.setEnabled(true);
        } else if (!enabled) {
            agcProcessor.setEnabled(false);
            // 用户显式关闭 AGC：放弃会话结束后的自动恢复，避免覆盖用户选择
            softwareAgcSuspendedByHardware = false;
        }
        appConfig.setAgcEnabled(enabled);
        AppLog.d(TAG, "AGC " + (enabled ? "已启用" : "已禁用"));
    }

}
