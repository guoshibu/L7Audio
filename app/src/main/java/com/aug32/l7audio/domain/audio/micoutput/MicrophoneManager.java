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
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
    );

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
    // RMS 滑动窗口平均，避免静音检测采样到瞬时静音帧（最近10帧的平均值）
    private static final int RMS_WINDOW_SIZE = 10;
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

    // ========== 音频处理管线 ==========
    /** 音频处理管线，按序执行 HPF → AFC → 增益 → 谱减法降噪 → 啸叫陷波 → AGC */
    private final AudioPipeline pipeline;
    /** 高通滤波器 */
    private final HighPassFilterProcessor hpfProcessor;
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

        // 初始化处理管线：HPF → AFC → 增益 → 谱减法降噪 → 啸叫陷波 → AGC
        this.hpfProcessor = new HighPassFilterProcessor();
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
        pipeline.addProcessor(afcProcessor);
        pipeline.addProcessor(gainProcessor);
        pipeline.addProcessor(noiseReductionProcessor);
        pipeline.addProcessor(howlingNotchProcessor);
        pipeline.addProcessor(agcProcessor);

        AppLog.i(TAG, "MicrophoneManager initialized, SR=" + SAMPLE_RATE + "Hz");
        AppLog.i(TAG, "AFC (NLMS): " + afcProcessor.isEnabled());
        AppLog.i(TAG, "Noise reduction (spectral): " + noiseReductionProcessor.isEnabled());
        AppLog.i(TAG, "Howling notch: " + howlingNotchProcessor.isEnabled());

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
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        StringBuilder callerInfo = new StringBuilder();
        for (int i = 2; i < Math.min(6, stackTrace.length); i++) {
            StackTraceElement element = stackTrace[i];
            if (i > 2) callerInfo.append(" -> ");
            callerInfo.append(element.getClassName()).append(".").append(element.getMethodName())
                    .append("(").append(element.getFileName()).append(":").append(element.getLineNumber()).append(")");
        }

        AppLog.i(TAG, "========== MicrophoneManager.start() called ==========");
        AppLog.i(TAG, "Thread: " + threadName + " (id=" + Thread.currentThread().getId() + ")");
        AppLog.i(TAG, "Caller: " + callerInfo.toString());
        AppLog.i(TAG, "Current isRecording=" + isRecording + ", audioRecord=" + (audioRecord == null ? "null" : "not null"));

        // 已在录制，直接返回成功
        if (isRecording) {
            AppLog.d(TAG, "Already recording");
            return true;
        }

        // 检查录音权限，无权限则失败
        if (!checkPermissions()) {
            AppLog.e(TAG, "RECORD_AUDIO permission not granted");
            return false;
        }

        try {
            // 先释放旧资源，确保每次启动都是全新初始化
            releaseResources();
            AppLog.i(TAG, "Starting microphone amplifier with fresh initialization");

            // 初始化音频录制和播放组件
            if (!initializeAudioComponents()) {
                return false;
            }

            // 重置管线及 AGC 状态（清除上一轮会话的累积检测数据）
            pipeline.reset();
            agcProcessor.reset();

            // 初始化 Android 原生 3A 硬件效果器
            initializeHardwareEffects();

            // 启动音频录制和播放
            startAudioComponents();
            // 启动录制处理线程
            startRecordingThread();

            AppLog.i(TAG, "Microphone amplifier started successfully");
            AppLog.i(TAG, "========== MicrophoneManager.start() completed ==========");

            return true;
        } catch (Exception e) {
            // 启动失败时释放所有资源，避免资源泄漏
            AppLog.e(TAG, "Failed to start microphone amplifier", e);

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
            AppLog.e(TAG, "getMinBufferSize returned invalid value: " + bufferSize + ", using fallback buffer size");
            bufferSize = 2 * AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AUDIO_FORMAT);
            if (bufferSize <= 0) {
                bufferSize = 1536 * 2;
            }
        }
        AppLog.d(TAG, "getBufferSize(): SR=" + SAMPLE_RATE + ", channel=" + CHANNEL_CONFIG + ", format=" + AUDIO_FORMAT + ", bufferSize=" + bufferSize);
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
                AppLog.i(TAG, "AudioRecord init SUCCESS: source=" + sourceName + "(" + audioSource + "), SR=" + SAMPLE_RATE + ", buf=" + bufferSize + ", sessionId=" + audioRecord.getAudioSessionId());
            } else {
                AppLog.e(TAG, "AudioRecord init FAILED: source=" + sourceName + "(" + audioSource + "), SR=" + SAMPLE_RATE + ", buf=" + bufferSize + ", state=" + state);
            }
            return success;
        } catch (Exception e) {
            String sourceName = getAudioSourceName(audioSource);
            AppLog.e(TAG, "AudioRecord init EXCEPTION: source=" + sourceName + "(" + audioSource + "), SR=" + SAMPLE_RATE + ", buf=" + bufferSize, e);
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

        AppLog.i(TAG, "=== AudioRecord init starting ===");
        AppLog.i(TAG, "Static BUFFER_SIZE=" + BUFFER_SIZE + ", dynamic bufferSize=" + bufferSize);
        AppLog.i(TAG, "User configured audio source: " + getAudioSourceName(audioSource) + "(" + audioSource + ")");

        // 第一级：使用用户配置的音频源
        if (tryInitAudioRecord(audioSource, bufferSize)) {
            AppLog.i(TAG, "=== AudioRecord init succeeded at level 1 ===");
        } else {
            // 第二级回退：用户配置的音频源失败，回退到MIC
            AppLog.w(TAG, "Falling back to MIC");
            audioSource = MediaRecorder.AudioSource.MIC;
            if (tryInitAudioRecord(audioSource, bufferSize)) {
                AppLog.i(TAG, "=== AudioRecord init succeeded at level 2 ===");
            } else {
                // 第三级回退：MIC也失败，回退到DEFAULT
                AppLog.e(TAG, "Falling back to DEFAULT");
                audioSource = MediaRecorder.AudioSource.DEFAULT;
                if (tryInitAudioRecord(audioSource, bufferSize)) {
                    AppLog.i(TAG, "=== AudioRecord init succeeded at level 3 ===");
                } else {
                    // 三级都失败，尝试增大缓冲区重试一次
                    AppLog.e(TAG, "All audio sources failed, trying with larger buffer");
                    int largerBufferSize = bufferSize * 2;
                    audioSource = MediaRecorder.AudioSource.MIC;
                    if (tryInitAudioRecord(audioSource, largerBufferSize)) {
                        AppLog.i(TAG, "=== AudioRecord init succeeded with larger buffer ===");
                    } else {
                        AppLog.e(TAG, "=== AudioRecord init FAILED, all retries exhausted ===");
                        return false;
                    }
                }
            }
        }

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
            AppLog.d(TAG, "Using audio usage from AudioOutputManager: " + audioUsage);
        } else {
            audioUsage = android.media.AudioAttributes.USAGE_MEDIA;
            AppLog.d(TAG, "AudioOutputManager null, fallback to USAGE_MEDIA");
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

        audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(BUFFER_SIZE)
                .build();

        if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
            AppLog.e(TAG, "AudioTrack init FAILED: usage=" + audioUsage + " SR=" + SAMPLE_RATE + " buf=" + BUFFER_SIZE);
            return false;
        }

        AppLog.i(TAG, "AudioTrack init OK: usage=" + audioUsage + " SR=" + SAMPLE_RATE + " buf=" + BUFFER_SIZE + " state=" + audioTrack.getState());
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

        AppLog.i(TAG, "── Hardware Effects ──");
        initNoiseSuppressor(sessionId);
        initEchoCanceler(sessionId);
    initAutomaticGainControl(sessionId);
        String pStatus = pipelineStatus();
        AppLog.i(TAG, "── Pipeline Summary ──");
        AppLog.i(TAG, pStatus);
    }

    private void initNoiseSuppressor(int sessionId) {
        if (!noiseReductionProcessor.isEnabled()) {
            AppLog.i(TAG, "NoiseSuppressor:   SKIPPED (noise reduction disabled by user)");
            return;
        }
        try {
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(sessionId);
                if (noiseSuppressor != null) {
                    noiseSuppressor.setEnabled(true);
                    hardwareNsAvailable = true;
                    noiseReductionProcessor.setEnabled(false);
                    AppLog.i(TAG, "NoiseSuppressor:   AVAILABLE → enabled, SpectralNR disabled");
        } else {
                    AppLog.i(TAG, "NoiseSuppressor:   create() returned null");
                }
            } else {
                AppLog.i(TAG, "NoiseSuppressor:   NOT_AVAILABLE → using software SpectralNR");
            }
        } catch (Exception e) {
            AppLog.e(TAG, "NoiseSuppressor:   ERROR → using software SpectralNR", e);
        }
    }

    private void initEchoCanceler(int sessionId) {
        if (!afcProcessor.isEnabled()) {
            AppLog.i(TAG, "AcousticEchoCanceler: SKIPPED (echo cancellation disabled by user)");
            return;
        }
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                acousticEchoCanceler = AcousticEchoCanceler.create(sessionId);
                if (acousticEchoCanceler != null) {
                    acousticEchoCanceler.setEnabled(true);
                    hardwareAecAvailable = true;
                    AppLog.i(TAG, "AcousticEchoCanceler: AVAILABLE → enabled (AFC NLMS residual on)");
                } else {
                    AppLog.i(TAG, "AcousticEchoCanceler: create() on AudioRecord returned null, trying AudioTrack");
                    try {
                        int trackSessionId = audioTrack.getAudioSessionId();
                        acousticEchoCanceler = AcousticEchoCanceler.create(trackSessionId);
                        if (acousticEchoCanceler != null) {
                            acousticEchoCanceler.setEnabled(true);
                            hardwareAecAvailable = true;
                            AppLog.i(TAG, "AcousticEchoCanceler: AVAILABLE on AudioTrack → enabled (AFC NLMS residual on)");
                        } else {
                            AppLog.i(TAG, "AcousticEchoCanceler: create() on AudioTrack also returned null → using software AFC");
                        }
                    } catch (Exception e2) {
                        AppLog.e(TAG, "AcousticEchoCanceler: AudioTrack fallback error", e2);
                    }
                }
            } else {
                AppLog.i(TAG, "AcousticEchoCanceler: NOT_AVAILABLE → using software AFC (NLMS)");
            }
        } catch (Exception e) {
            AppLog.e(TAG, "AcousticEchoCanceler: ERROR → using software AFC", e);
        }
    }

    private void initAutomaticGainControl(int sessionId) {
        try {
            if (AutomaticGainControl.isAvailable()) {
                automaticGainControl = AutomaticGainControl.create(sessionId);
                if (automaticGainControl != null) {
                    automaticGainControl.setEnabled(true);
                    hardwareAgcAvailable = true;
                    agcProcessor.setEnabled(false);
                    AppLog.i(TAG, "AutomaticGainControl: AVAILABLE → enabled, AGC Processor disabled");
                } else {
                    AppLog.i(TAG, "AutomaticGainControl: create() returned null → using software AGC");
                }
            } else {
                AppLog.i(TAG, "AutomaticGainControl: NOT_AVAILABLE → using software AGC");
            }
        } catch (Exception e) {
            AppLog.e(TAG, "AutomaticGainControl: ERROR → using software AGC", e);
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
        AppLog.d(TAG, "Starting AudioRecord & AudioTrack");
        audioRecord.startRecording();
        audioTrack.play();
        isRecording = true;
        AppLog.i(TAG, "AudioRecord & AudioTrack started, sessionId=" + audioRecord.getAudioSessionId());
    }

    /**
     * 启动录制线程
     * <p>
     * 创建并启动独立的录制处理线程，避免阻塞主线程。
     * </p>
     */
    private void startRecordingThread() {
        recordingThread = new Thread(() -> {
            AppLog.d(TAG, "Recording thread started");
            recordAndPlay();
        }, "MicrophoneRecordingThread");
        recordingThread.start();
        AppLog.d(TAG, "Recording thread dispatched");
    }

    /**
     * 停止麦克风放大
     * <p>
     * 停止录制和播放，等待录制线程结束，并释放所有音频资源。
     * 下次启动时会重新初始化所有组件。
     * </p>
     */
    public synchronized void stop() {
        if (!isRecording) {
            AppLog.d(TAG, "Not recording");
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
                AppLog.e(TAG, "Error force-stopping AudioRecord", e);
            }
        }

        // 等待录制线程结束，最多等待1秒，避免ANR
        if (recordingThread != null) {
            try {
                recordingThread.join(1000);
            } catch (InterruptedException e) {
                AppLog.e(TAG, "Failed to join recording thread", e);
            }
        }

        // 释放所有音频资源
        releaseResources();
        currentRms = 0.0f;
        AppLog.i(TAG, "Microphone amplifier stopped, all resources released");
    }

    /**
     * 录制并播放音频的主循环
     * <p>
     * 在独立线程中运行，不断从麦克风读取音频数据，处理后通过 AudioTrack 播放。
     * isRecording 标记为 false 时退出循环。
     * </p>
     */
    private void recordAndPlay() {
        byte[] buffer = new byte[BUFFER_SIZE];
        short[] samples = new short[0];

        int frameCount = 0;
        while (isRecording) {
            try {
                int readSize = audioRecord.read(buffer, 0, BUFFER_SIZE);

                if (readSize > 0) {
                    int sampleCount = readSize / 2;
                    if (samples.length != sampleCount) {
                        samples = new short[sampleCount];
                    }
                    processAudioData(buffer, readSize, samples);
                    audioTrack.write(buffer, 0, readSize);
                    frameCount++;
                    if (frameCount % 100 == 0) {
                        AppLog.i(TAG, "Recording alive: frame=" + frameCount + " preRms=" + String.format(java.util.Locale.US, "%.4f", currentRms) + " postRms=" + String.format(java.util.Locale.US, "%.4f", currentPostProcessRms) + " written=" + readSize);
                    }
                } else if (readSize < 0) {
                    AppLog.e(TAG, "AudioRecord read error: " + readSize);
                } else {
                    if (frameCount % 100 == 0) {
                        AppLog.w(TAG, "AudioRecord read zero bytes, frame=" + frameCount);
                    }
                }
            } catch (Exception e) {
                AppLog.e(TAG, "Error in recording thread at frame " + frameCount, e);
                releaseResources();
                break;
            }
        }
        AppLog.i(TAG, "Recording thread exited, total frames=" + frameCount);
    }

    /**
     * 通过管线处理音频数据
     * <p>
     * 处理流程：byte[] → short[] → pipeline.process → setReference → short[] → byte[]
     * 增益处理器在每次处理前更新放大倍数，确保实时响应配置变化。
     * pipeline 执行后，将处理后的信号传给 AFC 作为下一轮的参考信号，
     * 并计算处理后 RMS 供静音检测使用。
     * </p>
     *
     * @param buffer 音频数据缓冲区
     * @param length 有效数据长度
     */
    private void processAudioData(byte[] buffer, int length, short[] samples) {
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

        rmsWindow[rmsWindowIndex] = currentRms;
        rmsWindowIndex = (rmsWindowIndex + 1) % RMS_WINDOW_SIZE;
        float sumRms = 0;
        for (float r : rmsWindow) sumRms += r;
        averageRms = sumRms / RMS_WINDOW_SIZE;

        try {
            pipeline.process(samples);
        } catch (Exception e) {
            AppLog.e(TAG, "Pipeline error", e);
        }

        if (BuildConfig.DEBUG && afcProcessor.isEnabled()) {
            AppLog.d(TAG, "ERLE=" + String.format(Locale.US, "%.1f", afcProcessor.getLastErleDb()) + "dB rms=" + String.format(Locale.US, "%.4f", currentRms));
        }

        afcProcessor.setReference(samples);

        calculatePostProcessRms(samples);

        for (int i = 0; i < sampleCount; i++) {
            int index = i * 2;
            buffer[index] = (byte) (samples[i] & 0xFF);
            buffer[index + 1] = (byte) (samples[i] >> 8);
        }
    }

    /** 计算处理后 RMS */
    private void calculatePostProcessRms(short[] samples) {
        double sum = 0.0;
        for (short sample : samples) {
            float normalized = sample / 32768.0f;
            sum += normalized * normalized;
        }
        currentPostProcessRms = sum > 0 ? (float) Math.sqrt(sum / samples.length) : 0.0f;
    }

    /** 释放所有资源 */
    private void releaseResources() {
        AppLog.d(TAG, "========== releaseResources() called ==========");
        AppLog.d(TAG, "Before release: audioRecord=" + (audioRecord == null ? "null" : "not null")
                + ", audioTrack=" + (audioTrack == null ? "null" : "not null")
                + ", isRecording=" + isRecording);

        releaseHardwareEffects();
        releaseAudioRecord();
        releaseAudioTrack();

        recordingThread = null;
        // 重置管线及 AGC 状态（清除累积的检测数据）
        pipeline.reset();
        agcProcessor.reset();

        AppLog.d(TAG, "After release: audioRecord=" + (audioRecord == null ? "null" : "not null")
                + ", audioTrack=" + (audioTrack == null ? "null" : "not null")
                + ", isRecording=" + isRecording);
        AppLog.d(TAG, "========== releaseResources() completed ==========");
    }

    /** 释放所有 Android 原生硬件效果器 */
    private void releaseHardwareEffects() {
        AppLog.d(TAG, "Releasing hardware effects NS=" + hardwareNsAvailable + " AEC=" + hardwareAecAvailable + " AGC=" + hardwareAgcAvailable);
        releaseNoiseSuppressor();
        releaseEchoCanceler();
        releaseAutomaticGainControl();
    }

    private void releaseNoiseSuppressor() {
        if (noiseSuppressor != null) {
            try {
                noiseSuppressor.setEnabled(false);
                noiseSuppressor.release();
                AppLog.d(TAG, "NoiseSuppressor released");
            } catch (Exception e) {
                AppLog.e(TAG, "Error releasing NoiseSuppressor", e);
            }
            noiseSuppressor = null;
        }
        hardwareNsAvailable = false;
    }

    private void releaseEchoCanceler() {
        if (acousticEchoCanceler != null) {
            try {
                acousticEchoCanceler.setEnabled(false);
                acousticEchoCanceler.release();
                AppLog.d(TAG, "AcousticEchoCanceler released");
            } catch (Exception e) {
                AppLog.e(TAG, "Error releasing AcousticEchoCanceler", e);
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
                AppLog.d(TAG, "AutomaticGainControl released");
            } catch (Exception e) {
                AppLog.e(TAG, "Error releasing AutomaticGainControl", e);
            }
            automaticGainControl = null;
        }
        hardwareAgcAvailable = false;
    }

    /** 释放 AudioRecord */
    private void releaseAudioRecord() {
        if (audioRecord != null) {
            try {
                if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                    if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                        audioRecord.stop();
                        AppLog.d(TAG, "AudioRecord stopped");
                    }
                    audioRecord.release();
                    AppLog.d(TAG, "AudioRecord released");
                }
            } catch (Exception e) {
                AppLog.e(TAG, "Error releasing AudioRecord", e);
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
                        AppLog.d(TAG, "AudioTrack stopped");
                    }
                    audioTrack.release();
                    AppLog.d(TAG, "AudioTrack released");
                }
            } catch (Exception e) {
                AppLog.e(TAG, "Error releasing AudioTrack", e);
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
        int maxAmp = appConfig.getMaxAmplification();
        float factor = level == 0 ? 0.0f : maxAmp * level / 10.0f;
        gainProcessor.setAmplificationFactor(factor);
        AppLog.d(TAG, "Amplification level set to: " + level + " (factor=" + String.format(java.util.Locale.US, "%.2f", factor) + ")");
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
     * 获取管线处理后的 RMS 均值
     * <p>
     * 经过全部处理器（降噪、AGC 等）后的信号 RMS，
     * 用于静音检测，更准确反映实际输出音量。
     * 当前暂无调用方，预留用于：
     * - 可视化界面显示输出音量
     * - 静音检测逻辑（检测处理后的实际音量）
     * - 调试日志输出
     * </p>
     *
     * @return 处理后 RMS 值（0-1），0 表示无音频输入或未录制
     */
    public float getPostProcessRms() {
        return currentPostProcessRms;
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
        appConfig.setNoiseReductionEnabled(enabled);
        AppLog.d(TAG, "Spectral noise reduction " + (enabled ? "enabled" : "disabled"));
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
        AppLog.d(TAG, "AFC (NLMS) " + (enabled ? "enabled" : "disabled"));
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
        AppLog.d(TAG, "Howling notch " + (enabled ? "enabled" : "disabled"));
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
        }
        appConfig.setAgcEnabled(enabled);
        AppLog.d(TAG, "AGC " + (enabled ? "enabled" : "disabled"));
    }

}
