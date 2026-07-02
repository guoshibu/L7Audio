package com.aug32.l7audio.domain.audio;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;

import androidx.core.content.ContextCompat;

import com.aug32.l7audio.data.local.AppConfig;
import com.aug32.l7audio.domain.audio.processor.AudioSuppressionProcessor;
import com.aug32.l7audio.domain.audio.processor.GainLimiterProcessor;
import com.aug32.l7audio.utils.AppLog;

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

    // 采样率：44100Hz，CD音质标准
    private static final int SAMPLE_RATE = 44100;
    // 声道配置：单声道输入
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    // 音频格式：16位PCM编码
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    // 缓冲区大小：取最小缓冲区的2倍，防止音频数据丢失
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
    ) * 2;

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
    // 放大级别（0-10）
    private int amplificationLevel = 5;

    // 硬件AEC是否可用
    private boolean hardwareAecAvailable = false;
    // 硬件回声消除器
    private AcousticEchoCanceler acousticEchoCanceler;

    // ========== 音频处理管线 ==========
    /** 音频处理管线，按序执行增益→限幅→噪声门→回声消除→啸叫抑制 */
    private final AudioPipeline pipeline;
    /** 增益限幅处理器 */
    private final GainLimiterProcessor gainProcessor;
    /** 抑制处理器（噪声门、回声消除、啸叫抑制） */
    private final AudioSuppressionProcessor suppressionProcessor;

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

        // 初始化处理管线
        this.gainProcessor = new GainLimiterProcessor();
        this.suppressionProcessor = new AudioSuppressionProcessor();
        this.suppressionProcessor.setNoiseGateEnabled(appConfig.isNoiseReductionEnabled());
        this.suppressionProcessor.setEchoCancelEnabled(appConfig.isEchoCancellationEnabled());
        this.suppressionProcessor.setHowlingSuppressionEnabled(appConfig.isHowlingSuppressionEnabled());

        this.pipeline = new AudioPipeline();
        pipeline.addProcessor(gainProcessor);
        pipeline.addProcessor(suppressionProcessor);

        AppLog.d(TAG, "MicrophoneManager initialized");
        AppLog.d(TAG, "Noise gate: " + suppressionProcessor.isNoiseGateEnabled());
        AppLog.d(TAG, "Echo cancel: " + suppressionProcessor.isEchoCancelEnabled());
        AppLog.d(TAG, "Howling suppression: " + suppressionProcessor.isHowlingSuppressionEnabled());
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
            AppLog.d(TAG, "Starting microphone amplifier with fresh initialization");

            // 初始化音频录制和播放组件
            if (!initializeAudioComponents()) {
                return false;
            }

            // 重置管线状态（清除上一轮会话的累积检测数据）
            pipeline.reset();

            // 初始化硬件回声消除器
            initializeEchoCanceler();

            // 启动音频录制和播放
            startAudioComponents();
            // 启动录制处理线程
            startRecordingThread();

            AppLog.d(TAG, "Microphone amplifier started successfully");
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
     * 初始化音频组件
     * <p>
     * 三级回退机制确保 AudioRecord 初始化成功：
     * 1. 使用用户配置的音频源
     * 2. 回退到 MIC 音频源
     * 3. 回退到 DEFAULT 音频源
     * </p>
     *
     * @return true 表示初始化成功，false 表示初始化失败
     */
    @SuppressLint("MissingPermission")
    private boolean initializeAudioComponents() {
        int audioSource = appConfig.getAudioInputSource();

        // 第一级：使用用户配置的音频源
        audioRecord = new AudioRecord(
                audioSource,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                BUFFER_SIZE
        );

        // 第二级回退：用户配置的音频源失败，回退到MIC
        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            AppLog.e(TAG, "Failed to initialize AudioRecord with user configured source, falling back to MIC");
            audioSource = MediaRecorder.AudioSource.MIC;

            audioRecord = new AudioRecord(
                    audioSource,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    BUFFER_SIZE
            );

            // 第三级回退：MIC也失败，回退到DEFAULT
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                AppLog.e(TAG, "Failed to initialize AudioRecord with MIC, falling back to DEFAULT");
                audioSource = MediaRecorder.AudioSource.DEFAULT;

                audioRecord = new AudioRecord(
                        audioSource,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        BUFFER_SIZE
                );

                // 三级都失败，返回失败
                if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                    AppLog.e(TAG, "Failed to initialize AudioRecord");
                    return false;
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
            audioUsage = appConfig.getAudioOutputUsageExternal();
            AppLog.d(TAG, "Using audio usage from AppConfig: " + audioUsage);
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
            AppLog.e(TAG, "Failed to initialize AudioTrack");
            return false;
        }

        AppLog.d(TAG, "AudioTrack initialized successfully with audioUsage: " + audioUsage);
        return true;
    }

    /**
     * 初始化硬件级回声消除器
     * <p>
     * 优先尝试在 AudioRecord 上创建硬件回声消除器，失败则尝试在 AudioTrack 上创建。
     * 硬件 AEC 可用时，软件回声消除自动跳过，避免冲突。
     * </p>
     */
    private void initializeEchoCanceler() {
        if (!suppressionProcessor.isEchoCancelEnabled()) {
            hardwareAecAvailable = false;
            return;
        }

        try {
            int audioRecordSessionId = audioRecord.getAudioSessionId();
            // 硬件AEC可用时，优先使用硬件回声消除
            if (AcousticEchoCanceler.isAvailable()) {
                acousticEchoCanceler = AcousticEchoCanceler.create(audioRecordSessionId);
                if (acousticEchoCanceler != null) {
                    acousticEchoCanceler.setEnabled(true);
                    hardwareAecAvailable = true;
                    AppLog.d(TAG, "Hardware echo canceler enabled on AudioRecord (Session ID: " + audioRecordSessionId + ")");
                    // 硬件 AEC 可用，禁用软件回声消除
                    suppressionProcessor.setEchoCancelEnabled(false);
                    return;
                } else {
                    // AudioRecord上创建失败，尝试在AudioTrack上创建
                    AppLog.w(TAG, "Failed to create echo canceler on AudioRecord, trying AudioTrack");
                    int audioTrackSessionId = audioTrack.getAudioSessionId();
                    acousticEchoCanceler = AcousticEchoCanceler.create(audioTrackSessionId);
                    if (acousticEchoCanceler != null) {
                        acousticEchoCanceler.setEnabled(true);
                        hardwareAecAvailable = true;
                        AppLog.d(TAG, "Hardware echo canceler enabled on AudioTrack (Session ID: " + audioTrackSessionId + ")");
                        // 硬件 AEC 可用，禁用软件回声消除
                        suppressionProcessor.setEchoCancelEnabled(false);
                        return;
                    } else {
                        AppLog.w(TAG, "Failed to create hardware echo canceler on both AudioRecord and AudioTrack");
                        hardwareAecAvailable = false;
                    }
                }
            } else {
                // 设备不支持硬件AEC，使用软件回声消除作为兜底
                AppLog.w(TAG, "Hardware echo canceler not available on this device, will use software echo cancellation as fallback");
                hardwareAecAvailable = false;
            }
        } catch (Exception e) {
            // 初始化异常时，标记硬件AEC不可用，使用软件方案
            AppLog.e(TAG, "Error initializing hardware echo canceler", e);
            hardwareAecAvailable = false;
        }
    }

    /**
     * 开始录音和播放
     * <p>
     * 启动 AudioRecord 录制和 AudioTrack 播放，并标记为正在录制状态。
     * </p>
     */
    private void startAudioComponents() {
        audioRecord.startRecording();
        audioTrack.play();
        isRecording = true;
    }

    /**
     * 启动录制线程
     * <p>
     * 创建并启动独立的录制处理线程，避免阻塞主线程。
     * </p>
     */
    private void startRecordingThread() {
        recordingThread = new Thread(() -> {
            recordAndPlay();
        }, "MicrophoneRecordingThread");
        recordingThread.start();
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
        AppLog.d(TAG, "Microphone amplifier stopped, all resources released for fresh initialization next time");
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

        while (isRecording) {
            try {
                // 从麦克风读取音频数据
                int readSize = audioRecord.read(buffer, 0, BUFFER_SIZE);

                if (readSize > 0) {
                    // 计算当前帧的 RMS 均值，供静音检测使用
                    calculateRms(buffer, readSize);
                    // 通过管线处理音频数据（增益→限幅→噪声门→回声消除→啸叫抑制）
                    processAudioData(buffer, readSize);
                    // 播放处理后的音频
                    audioTrack.write(buffer, 0, readSize);
                }
            } catch (Exception e) {
                // 录制过程中发生异常，退出循环
                AppLog.e(TAG, "Error in recording thread", e);
                break;
            }
        }
    }

    /**
     * 通过管线处理音频数据
     * <p>
     * 处理流程：byte[] → short[] → pipeline.process → short[] → byte[]
     * 增益处理器在每次处理前更新放大倍数，确保实时响应配置变化。
     * </p>
     *
     * @param buffer 音频数据缓冲区
     * @param length 有效数据长度
     */
    private void processAudioData(byte[] buffer, int length) {
        // 更新增益倍数（在录制过程中实时响应配置变化）
        int maxAmplification = appConfig.getMaxAmplification();
        float factor = 1.0f + (amplificationLevel / 10.0f) * (maxAmplification - 1.0f);
        gainProcessor.setAmplificationFactor(factor);

        // byte[] 转 short[]
        short[] samples = new short[length / 2];
        for (int i = 0; i < samples.length; i++) {
            int index = i * 2;
            samples[i] = (short) ((buffer[index] & 0xFF) | (buffer[index + 1] << 8));
        }

        // 管线处理
        pipeline.process(samples);

        // short[] 转 byte[]
        for (int i = 0; i < samples.length; i++) {
            int index = i * 2;
            buffer[index] = (byte) (samples[i] & 0xFF);
            buffer[index + 1] = (byte) (samples[i] >> 8);
        }
    }

    /**
     * 计算音频帧的 RMS 均值并更新 currentRms
     * <p>
     * 将 16-bit PCM 采样归一化到 [-1, 1] 后计算 RMS。
     * 在录制线程中每帧调用，供静音检测使用。
     * </p>
     *
     * @param buffer 音频数据缓冲区
     * @param length 有效数据长度
     */
    private void calculateRms(byte[] buffer, int length) {
        if (length < 2) {
            currentRms = 0.0f;
            return;
        }
        double sum = 0.0;
        int sampleCount = length / 2;
        for (int i = 0; i < sampleCount; i++) {
            int index = i * 2;
            short sample = (short) ((buffer[index] & 0xFF) | (buffer[index + 1] << 8));
            float normalized = sample / (float) Short.MAX_VALUE;
            sum += normalized * normalized;
        }
        currentRms = (float) Math.sqrt(sum / sampleCount);
    }

    /** 释放所有资源 */
    private void releaseResources() {
        releaseEchoCanceler();
        releaseAudioRecord();
        releaseAudioTrack();

        recordingThread = null;
        // 重置管线状态（清除累积的检测数据）
        pipeline.reset();
    }

    /** 释放回声消除器 */
    private void releaseEchoCanceler() {
        if (acousticEchoCanceler != null) {
            try {
                acousticEchoCanceler.setEnabled(false);
                acousticEchoCanceler.release();
            } catch (Exception e) {
                AppLog.e(TAG, "Error releasing AcousticEchoCanceler", e);
            }
            acousticEchoCanceler = null;
        }
        hardwareAecAvailable = false;
    }

    /** 释放 AudioRecord */
    private void releaseAudioRecord() {
        if (audioRecord != null) {
            try {
                if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                    if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                        audioRecord.stop();
                    }
                    audioRecord.release();
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
                    }
                    audioTrack.release();
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
        // 边界处理：限制放大级别在有效范围内
        if (level < 0) {
            level = 0;
        } else if (level > 10) {
            level = 10;
        }
        this.amplificationLevel = level;
        appConfig.setMicAmplificationLevel(level);
        AppLog.d(TAG, "Amplification level set to: " + level);
    }

    /**
     * 获取当前放大级别
     *
     * @return 当前放大级别，取值范围 0-10
     */
    public int getAmplificationLevel() {
        return amplificationLevel;
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
     * 获取当前音频帧的 RMS 均值
     * <p>
     * 用于静音检测，判断是否有声音输入。归一化到 0-1 范围。
     * 未录制时返回 0。
     * </p>
     *
     * @return 当前 RMS 值（0-1），0 表示无音频输入或未录制
     */
    public float getCurrentRms() {
        return currentRms;
    }

    /**
     * 设置是否启用噪声抑制
     * <p>
     * 设置后会自动保存到配置中，并立即生效（无需重启录制）。
     * </p>
     *
     * @param enabled true 启用噪声抑制，false 禁用噪声抑制
     */
    public void setNoiseReductionEnabled(boolean enabled) {
        suppressionProcessor.setNoiseGateEnabled(enabled);
        appConfig.setNoiseReductionEnabled(enabled);
        AppLog.d(TAG, "Noise reduction " + (enabled ? "enabled" : "disabled"));
    }

    /**
     * 获取噪声抑制状态
     *
     * @return true 表示噪声抑制已启用，false 表示已禁用
     */
    public boolean isNoiseReductionEnabled() {
        return suppressionProcessor.isNoiseGateEnabled();
    }

    /**
     * 设置是否启用回声抑制
     * <p>
     * 设置后会自动保存到配置中。
     * 硬件AEC可用时优先使用硬件回声消除，否则使用软件方案。
     * </p>
     *
     * @param enabled true 启用回声抑制，false 禁用回声抑制
     */
    public void setEchoCancellationEnabled(boolean enabled) {
        suppressionProcessor.setEchoCancelEnabled(enabled);
        appConfig.setEchoCancellationEnabled(enabled);
        AppLog.d(TAG, "Echo cancellation " + (enabled ? "enabled" : "disabled"));
    }

    /**
     * 获取回声抑制状态
     *
     * @return true 表示回声抑制已启用，false 表示已禁用
     */
    public boolean isEchoCancellationEnabled() {
        return suppressionProcessor.isEchoCancelEnabled();
    }

    /**
     * 设置是否启用啸叫抑制
     * <p>
     * 设置后会自动保存到配置中，并立即生效。
     * </p>
     *
     * @param enabled true 启用啸叫抑制，false 禁用啸叫抑制
     */
    public void setHowlingSuppressionEnabled(boolean enabled) {
        suppressionProcessor.setHowlingSuppressionEnabled(enabled);
        appConfig.setHowlingSuppressionEnabled(enabled);
        AppLog.d(TAG, "Howling suppression " + (enabled ? "enabled" : "disabled"));
    }

    /**
     * 获取啸叫抑制状态
     *
     * @return true 表示啸叫抑制已启用，false 表示已禁用
     */
    public boolean isHowlingSuppressionEnabled() {
        return suppressionProcessor.isHowlingSuppressionEnabled();
    }

    /**
     * 获取硬件 AEC 可用性状态
     * <p>
     * 仅在启动麦克风放大后才有意义，启动前始终返回 false。
     * </p>
     *
     * @return true 表示硬件回声消除可用，false 表示不可用
     */
    public boolean isHardwareAecAvailable() {
        return hardwareAecAvailable;
    }
}