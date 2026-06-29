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
import com.aug32.l7audio.utils.AppLog;

/**
 * 麦克风放大管理器
 *
 * 职责：
 * - 麦克风音频录制
 * - 音频放大和播放
 * - 噪声抑制、回声消除、啸叫抑制
 *
 * 目标 SDK：Android 11 (API 30)
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
    // 放大级别（0-10）
    private int amplificationLevel = 5;
    // 是否启用噪声抑制
    private boolean noiseReductionEnabled;
    // 是否启用回声消除
    private boolean echoCancellationEnabled;
    // 是否启用啸叫抑制
    private boolean howlingSuppressionEnabled;
    // 是否使用系统音频处理
    private boolean useSystemAudioProcessing = false;
    // 硬件AEC是否可用
    private boolean hardwareAecAvailable = false;
    // 硬件回声消除器
    private AcousticEchoCanceler acousticEchoCanceler;
    
    // 自动增益控制目标音量（dB）
    private static final float TARGET_DB = -20.0f;
    // 自动增益控制最大增益
    private static final float MAX_GAIN = 2.0f;
    // 自动增益调整步长（平滑处理）
    private static final float GAIN_ADJUST_STEP = 0.1f;
    // 当前自动增益值
    private float mCurrentGain = 1.0f;
    
    // 回声检测阈值
    private static final float ECHO_THRESHOLD = 0.05f;
    // 回声最大衰减系数
    private static final float MAX_ECHO_ATTENUATION = 0.3f;
    // 回声能量衰减因子（平滑处理）
    private static final float ECHO_DECAY_FACTOR = 0.95f;
    // 当前回声能量
    private float echoEnergy = 0.0f;
    
    // 啸叫检测阈值
    private static final float HOWLING_THRESHOLD = 0.1f;
    // 啸叫最大衰减系数
    private static final float MAX_HOWLING_ATTENUATION = 0.4f;
    // 啸叫能量衰减因子（平滑处理）
    private static final float HOWLING_DECAY_FACTOR = 0.98f;
    // 当前啸叫能量
    private float howlingEnergy = 0.0f;
    // 啸叫检测计数器（连续检测到多少次才判定为啸叫）
    private int howlingCounter = 0;
    // 啸叫检测次数阈值
    private static final int HOWLING_DETECTION_COUNT = 3;

    // 上一帧平均音量（用于噪声抑制平滑）
    private float previousAvgVolume = 0.0f;
    // 上一帧能量（用于稳态噪声检测）
    private float previousEnergy = 0.0f;

    /**
     * 构造函数
     * <p>
     * 从配置中读取放大级别和各音频处理开关的状态。
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
        this.noiseReductionEnabled = appConfig.isNoiseReductionEnabled();
        this.echoCancellationEnabled = appConfig.isEchoCancellationEnabled();
        this.howlingSuppressionEnabled = appConfig.isHowlingSuppressionEnabled();
        AppLog.d(TAG, "MicrophoneManager initialized");
        AppLog.d(TAG, "Noise reduction: " + noiseReductionEnabled);
        AppLog.d(TAG, "Echo cancellation: " + echoCancellationEnabled);
        AppLog.d(TAG, "Howling suppression: " + howlingSuppressionEnabled);
    }

    /**
     * 开始麦克风放大
     * <p>
     * 启动麦克风录制和音频播放，实现实时麦克风放大功能。
     * 若已在录制则直接返回成功。
     * 启动流程：检查权限 → 释放旧资源 → 初始化音频组件 → 初始化回声消除 → 启动音频 → 启动录制线程
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
        useSystemAudioProcessing = true;

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
            useSystemAudioProcessing = true;
            
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
                useSystemAudioProcessing = false;
                
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
     * 硬件回声消除不可用时，会使用软件回声消除作为兜底。
     * 硬件AEC和软件AEC不同时启用，避免冲突。
     * </p>
     */
    private void initializeEchoCanceler() {
        if (echoCancellationEnabled) {
            try {
                int audioRecordSessionId = audioRecord.getAudioSessionId();
                // 硬件AEC可用时，优先使用硬件回声消除
                if (AcousticEchoCanceler.isAvailable()) {
                    acousticEchoCanceler = AcousticEchoCanceler.create(audioRecordSessionId);
                    if (acousticEchoCanceler != null) {
                        acousticEchoCanceler.setEnabled(true);
                        hardwareAecAvailable = true;
                        AppLog.d(TAG, "Hardware echo canceler enabled on AudioRecord (Session ID: " + audioRecordSessionId + ")");
                        AppLog.d(TAG, "Software echo cancellation will be disabled to avoid conflict");
                    } else {
                        // AudioRecord上创建失败，尝试在AudioTrack上创建
                        AppLog.w(TAG, "Failed to create echo canceler on AudioRecord, trying AudioTrack");
                        int audioTrackSessionId = audioTrack.getAudioSessionId();
                        acousticEchoCanceler = AcousticEchoCanceler.create(audioTrackSessionId);
                        if (acousticEchoCanceler != null) {
                            acousticEchoCanceler.setEnabled(true);
                            hardwareAecAvailable = true;
                            AppLog.d(TAG, "Hardware echo canceler enabled on AudioTrack (Session ID: " + audioTrackSessionId + ")");
                            AppLog.d(TAG, "Software echo cancellation will be disabled to avoid conflict");
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
        } else {
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
                    // 处理音频数据（降噪、回声消除、增益、啸叫抑制等）
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
     * 处理音频数据
     * <p>
     * 音频处理流程：
     * 1. 字节转采样
     * 2. 噪声抑制（可选）
     * 3. 回声消除（可选，硬件AEC不可用时使用软件方案）
     * 4. 增益放大
     * 5. 音量限制（防止溢出）
     * 6. 啸叫抑制（可选）
     * 7. 采样转字节
     * </p>
     *
     * @param buffer 音频数据缓冲区
     * @param length 有效数据长度
     */
    private void processAudioData(byte[] buffer, int length) {
        int maxAmplification = appConfig.getMaxAmplification();
        // 根据放大级别计算放大倍数（0级为1倍，10级为maxAmplification倍）
        float amplificationFactor = 1.0f + (amplificationLevel / 10.0f) * (maxAmplification - 1.0f);

        short[] samples = new short[length / 2];

        // 字节数组转换为short采样数组
        convertBytesToSamples(buffer, samples);

        // 噪声抑制
        if (noiseReductionEnabled) {
            applyNoiseReduction(samples);
        }

        // 回声消除：硬件AEC可用时跳过软件处理，避免冲突
        if (echoCancellationEnabled && !hardwareAecAvailable) {
            AppLog.d(TAG, "Using software echo cancellation (hardware AEC not available)");
            applyEchoCancellation(samples);
        } else if (echoCancellationEnabled && hardwareAecAvailable) {
            AppLog.d(TAG, "Skipping software echo cancellation (hardware AEC is active)");
        }

        // 增益放大
        applyGain(samples, amplificationFactor);
        // 音量限制（防止采样值溢出）
        limitVolume(samples);

        // 啸叫抑制
        if (howlingSuppressionEnabled) {
            applyHowlingSuppression(samples);
        }

        // short采样数组转换回字节数组
        convertSamplesToBytes(samples, buffer);
    }

    /** 将字节数组转换为样本数组 */
    private void convertBytesToSamples(byte[] buffer, short[] samples) {
        for (int i = 0; i < samples.length; i++) {
            int index = i * 2;
            samples[i] = (short) ((buffer[index] & 0xFF) | (buffer[index + 1] << 8));
        }
    }

    /** 将样本数组转换为字节数组 */
    private void convertSamplesToBytes(short[] samples, byte[] buffer) {
        for (int i = 0; i < samples.length; i++) {
            int index = i * 2;
            buffer[index] = (byte) (samples[i] & 0xFF);
            buffer[index + 1] = (byte) (samples[i] >> 8);
        }
    }

    /** 应用增益放大 */
    private void applyGain(short[] samples, float amplificationFactor) {
        if (!useSystemAudioProcessing) {
            float agcGain = calculateAutoGain(samples);
            for (int i = 0; i < samples.length; i++) {
                samples[i] = (short) (samples[i] * amplificationFactor * agcGain);
            }
        } else {
            for (int i = 0; i < samples.length; i++) {
                samples[i] = (short) (samples[i] * amplificationFactor);
            }
        }
    }

    /** 限制音量防止溢出 */
    private void limitVolume(short[] samples) {
        for (int i = 0; i < samples.length; i++) {
            if (samples[i] > Short.MAX_VALUE) {
                samples[i] = Short.MAX_VALUE;
            } else if (samples[i] < Short.MIN_VALUE) {
                samples[i] = Short.MIN_VALUE;
            }
        }
    }

    /** 计算自动增益控制 */
    private float calculateAutoGain(short[] audioData) {
        double sum = 0.0;
        for (short sample : audioData) {
            sum += sample * sample;
        }
        double rms = Math.sqrt(sum / audioData.length);
        double currentDb = 20 * Math.log10(rms / 32767.0);

        float gainAdjust = 1.0f;
        if (currentDb < TARGET_DB) {
            gainAdjust = (float) Math.pow(10, (TARGET_DB - currentDb) / 20);
        } else {
            gainAdjust = (float) Math.pow(10, (TARGET_DB - currentDb) / 20);
        }

        mCurrentGain = mCurrentGain + (gainAdjust - mCurrentGain) * GAIN_ADJUST_STEP;
        mCurrentGain = Math.max(1.0f, Math.min(mCurrentGain, MAX_GAIN));

        return mCurrentGain;
    }

    /** 应用噪声抑制 */
    private void applyNoiseReduction(short[] samples) {
        long sum = 0;
        for (short sample : samples) {
            sum += Math.abs(sample);
        }
        float avgVolume = (float) sum / samples.length / Short.MAX_VALUE;
        
        float baseThreshold = 0.015f;
        float smoothedAvgVolume = avgVolume * 0.7f + previousAvgVolume * 0.3f;
        previousAvgVolume = smoothedAvgVolume;
        
        float dynamicThreshold = Math.max(baseThreshold, smoothedAvgVolume * 0.6f);
        
        boolean isSteadyNoise = detectSteadyNoise(samples);
        
        for (int i = 0; i < samples.length; i++) {
            float normalizedSample = Math.abs(samples[i]) / (float) Short.MAX_VALUE;
            
            if (normalizedSample < dynamicThreshold) {
                float attenuation = normalizedSample / dynamicThreshold;
                samples[i] = (short) (samples[i] * (0.5f + attenuation * 0.3f));
            } else if (normalizedSample < dynamicThreshold * 1.3f) {
                float attenuation = 0.8f + (normalizedSample - dynamicThreshold) / (dynamicThreshold * 0.3f) * 0.2f;
                samples[i] = (short) (samples[i] * attenuation);
            }
        }
    }
    
    /** 检测稳态噪声 */
    private boolean detectSteadyNoise(short[] samples) {
        float energy = 0.0f;
        for (short sample : samples) {
            float normalizedSample = Math.abs(sample) / (float) Short.MAX_VALUE;
            energy += normalizedSample * normalizedSample;
        }
        energy /= samples.length;
        
        float energyDiff = Math.abs(energy - previousEnergy);
        previousEnergy = energy;
        
        return energyDiff < 0.001f;
    }

    /** 应用回声抑制 */
    private void applyEchoCancellation(short[] samples) {
        float frameEnergy = 0.0f;
        for (short sample : samples) {
            float normalizedSample = Math.abs(sample) / (float) Short.MAX_VALUE;
            frameEnergy += normalizedSample * normalizedSample;
        }
        frameEnergy /= samples.length;
        
        echoEnergy = ECHO_DECAY_FACTOR * echoEnergy + (1.0f - ECHO_DECAY_FACTOR) * frameEnergy;
        
        for (int i = 0; i < samples.length; i++) {
            float normalizedSample = Math.abs(samples[i]) / (float) Short.MAX_VALUE;
            
            if (normalizedSample > ECHO_THRESHOLD * 1.5f &&
                frameEnergy > echoEnergy * 0.7f &&
                frameEnergy < echoEnergy * 1.3f) {
                float attenuation = 1.0f - (normalizedSample - ECHO_THRESHOLD) * (1.0f - MAX_ECHO_ATTENUATION) / (1.0f - ECHO_THRESHOLD);
                attenuation = Math.max(attenuation, MAX_ECHO_ATTENUATION + 0.2f);
                
                samples[i] = (short) (samples[i] * attenuation);
            }
        }
    }
    
    /** 应用啸叫抑制 */
    private void applyHowlingSuppression(short[] samples) {
        float frameEnergy = 0.0f;
        for (short sample : samples) {
            float normalizedSample = Math.abs(sample) / (float) Short.MAX_VALUE;
            frameEnergy += normalizedSample * normalizedSample;
        }
        frameEnergy /= samples.length;
        
        howlingEnergy = HOWLING_DECAY_FACTOR * howlingEnergy + (1.0f - HOWLING_DECAY_FACTOR) * frameEnergy;
        
        boolean isHowling = false;
        if (frameEnergy > howlingEnergy * 2.0f && frameEnergy > HOWLING_THRESHOLD) {
            howlingCounter++;
            if (howlingCounter >= HOWLING_DETECTION_COUNT) {
                isHowling = true;
            }
        } else {
            howlingCounter = Math.max(0, howlingCounter - 1);
        }
        
        if (isHowling) {
            AppLog.d(TAG, "Howling detected! Energy: " + frameEnergy + ", Avg: " + howlingEnergy);
            for (int i = 0; i < samples.length; i++) {
                float attenuation = MAX_HOWLING_ATTENUATION + (1.0f - MAX_HOWLING_ATTENUATION) * 0.3f;
                samples[i] = (short) (samples[i] * attenuation);
            }
        }
    }

    /** 释放所有资源 */
    private void releaseResources() {
        releaseEchoCanceler();
        releaseAudioRecord();
        releaseAudioTrack();

        recordingThread = null;
        echoEnergy = 0.0f;
        howlingEnergy = 0.0f;
        howlingCounter = 0;
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
     * 设置是否启用噪声抑制
     * <p>
     * 设置后会自动保存到配置中。
     * </p>
     *
     * @param enabled true 启用噪声抑制，false 禁用噪声抑制
     */
    public void setNoiseReductionEnabled(boolean enabled) {
        this.noiseReductionEnabled = enabled;
        appConfig.setNoiseReductionEnabled(enabled);
        AppLog.d(TAG, "Noise reduction " + (enabled ? "enabled" : "disabled"));
    }

    /**
     * 获取噪声抑制状态
     *
     * @return true 表示噪声抑制已启用，false 表示已禁用
     */
    public boolean isNoiseReductionEnabled() {
        return noiseReductionEnabled;
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
        this.echoCancellationEnabled = enabled;
        appConfig.setEchoCancellationEnabled(enabled);
        AppLog.d(TAG, "Echo cancellation " + (enabled ? "enabled" : "disabled"));
    }

    /**
     * 获取回声抑制状态
     *
     * @return true 表示回声抑制已启用，false 表示已禁用
     */
    public boolean isEchoCancellationEnabled() {
        return echoCancellationEnabled;
    }
    
    /**
     * 设置是否启用啸叫抑制
     * <p>
     * 设置后会自动保存到配置中。
     * </p>
     *
     * @param enabled true 启用啸叫抑制，false 禁用啸叫抑制
     */
    public void setHowlingSuppressionEnabled(boolean enabled) {
        this.howlingSuppressionEnabled = enabled;
        appConfig.setHowlingSuppressionEnabled(enabled);
        AppLog.d(TAG, "Howling suppression " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * 获取啸叫抑制状态
     *
     * @return true 表示啸叫抑制已启用，false 表示已禁用
     */
    public boolean isHowlingSuppressionEnabled() {
        return howlingSuppressionEnabled;
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
