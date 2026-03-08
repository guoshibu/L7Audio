package com.aug32.l7audio.audio;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.MediaRecorder;
import android.os.Build;
import androidx.core.content.ContextCompat;

import com.aug32.l7audio.AppLog;
import com.aug32.l7audio.AppConfig;

/**
 * 麦克风放大管理器
 * 负责麦克风音频的录制、放大和播放
 * 关键点：初始化AudioTrack时会根据当前车内外模式设置AudioAttributes.Usage
 */
public class MicrophoneManager {
    private static final String TAG = "MicrophoneManager";

    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
    ) * 2;

    private final Context context;
    private final AudioOutputManager audioOutputManager;
    private final AppConfig appConfig;

    private AudioRecord audioRecord;
    private AudioTrack audioTrack;
    private Thread recordingThread;
    private volatile boolean isRecording = false;
    private int amplificationLevel = 5;
    private boolean noiseReductionEnabled;
    private boolean echoCancellationEnabled;
    private boolean howlingSuppressionEnabled;
    private boolean useSystemAudioProcessing = false;
    private boolean hardwareAecAvailable = false; // 硬件 AEC 可用性标志
    private AcousticEchoCanceler acousticEchoCanceler;
    
    private static final float TARGET_DB = -20.0f;
    private static final float MAX_GAIN = 2.0f;
    private static final float GAIN_ADJUST_STEP = 0.1f;
    private float mCurrentGain = 1.0f;
    
    private static final float ECHO_THRESHOLD = 0.05f;
    private static final float MAX_ECHO_ATTENUATION = 0.3f;
    private static final float ECHO_DECAY_FACTOR = 0.95f;
    private float echoEnergy = 0.0f;
    
    private static final float HOWLING_THRESHOLD = 0.1f;
    private static final float MAX_HOWLING_ATTENUATION = 0.4f;
    private static final float HOWLING_DECAY_FACTOR = 0.98f;
    private float howlingEnergy = 0.0f;
    private int howlingCounter = 0;
    private static final int HOWLING_DETECTION_COUNT = 3;

    // RNNoise 接口预留（未来扩展）
    // private static native long rnnoiseCreate();
    // private static native void rnnoiseDestroy(long state);
    // private static native int rnnoiseProcessFrame(long state, float[] input, float[] output);
    // private static native void rnnoiseSetModel(long state, String modelPath);
    // private long rnnoiseState = 0; // RNNoise 状态句柄
    // private boolean useRnnoise = false; // 是否使用 RNNoise

    /**
     * 构造函数
     * @param context 上下文对象
     * @param audioOutputManager 音频输出管理器，用于获取当前音频输出模式
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
     * 关键点：每次调用都会重新初始化所有音频组件，包括AudioTrack
     * 这样可以确保获取最新的车内外模式audioUsage，实现车内外切换生效
     * @return 是否成功启动
     */
    public synchronized boolean start() {
        if (isRecording) {
            AppLog.d(TAG, "Already recording");
            return true;
        }

        if (!checkPermissions()) {
            AppLog.e(TAG, "RECORD_AUDIO permission not granted");
            return false;
        }

        try {
            // 确保完全释放旧资源，重新初始化所有组件
            // 这是获取最新车内外模式的关键步骤
            releaseResources();
            
            AppLog.d(TAG, "Starting microphone amplifier with fresh initialization");
            
            if (!initializeAudioComponents()) {
                return false;
            }

            initializeEchoCanceler();

            startAudioComponents();

            startRecordingThread();

            AppLog.d(TAG, "Microphone amplifier started successfully");
            return true;
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to start microphone amplifier", e);
            releaseResources();
            return false;
        }
    }

    /**
     * 检查录音权限
     * @return 是否拥有权限
     */
    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(context, "android.permission.RECORD_AUDIO")
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 初始化音频组件（AudioRecord和AudioTrack）
     * @return 是否初始化成功
     */
    @SuppressLint("MissingPermission")
    private boolean initializeAudioComponents() {
        int audioSource = appConfig.getAudioInputSource();
        useSystemAudioProcessing = true;

        audioRecord = new AudioRecord(
                audioSource,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                BUFFER_SIZE
        );

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
                
                if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                    AppLog.e(TAG, "Failed to initialize AudioRecord");
                    return false;
                }
            }
        }

        if (!initializeAudioTrack()) {
            return false;
        }

        return true;
    }

    /**
     * 初始化AudioTrack
     * 关键点：每次调用都会获取最新的车内外模式并设置AudioAttributes.Usage
     * 这是实现车内外切换的核心方法 - 必须在每次start()前重新调用以确保使用最新模式
     * @return 是否初始化成功
     */
    private boolean initializeAudioTrack() {
        int audioUsage;
        if (audioOutputManager != null) {
            // 关键点：从audioOutputManager获取最新的车内外音频输出模式
            // 这个调用是车内外切换生效的关键，必须每次初始化时都调用
            audioUsage = audioOutputManager.getAudioUsage();
            AppLog.d(TAG, "Using audio usage from AudioOutputManager: " + audioUsage);
        } else {
            audioUsage = appConfig.getAudioOutputUsageExternal();
            AppLog.d(TAG, "Using audio usage from AppConfig: " + audioUsage);
        }
        
        // 根据最新的audioUsage创建AudioAttributes
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(audioUsage)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();

        // 关键点：根据最新的audioUsage设置AudioFormat
        // 这确保了在不同车内外模式下音频格式的一致性
        AudioFormat audioFormat = new AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)// 采样率
                .setEncoding(AUDIO_FORMAT)// 音频编码格式
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)// 音频通道
                .build();

        // 关键点：根据最新的audioUsage设置AudioTrack
        // 这确保了在不同车内外模式下音频播放的一致性
        audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)// 音频属性
                .setAudioFormat(audioFormat)// 音频格式
                .setBufferSizeInBytes(BUFFER_SIZE)// 缓冲区大小
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
     */
    private void initializeEchoCanceler() {
        if (echoCancellationEnabled) {
            try {
                int audioRecordSessionId = audioRecord.getAudioSessionId();
                if (AcousticEchoCanceler.isAvailable()) {
                    acousticEchoCanceler = AcousticEchoCanceler.create(audioRecordSessionId);
                    if (acousticEchoCanceler != null) {
                        acousticEchoCanceler.setEnabled(true);
                        hardwareAecAvailable = true;
                        AppLog.d(TAG, "Hardware echo canceler enabled on AudioRecord (Session ID: " + audioRecordSessionId + ")");
                        AppLog.d(TAG, "Software echo cancellation will be disabled to avoid conflict");
                    } else {
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
                    AppLog.w(TAG, "Hardware echo canceler not available on this device, will use software echo cancellation as fallback");
                    hardwareAecAvailable = false;
                }
            } catch (Exception e) {
                AppLog.e(TAG, "Error initializing hardware echo canceler", e);
                hardwareAecAvailable = false;
            }
        } else {
            hardwareAecAvailable = false;
        }
    }

    /**
     * 开始录音和播放
     */
    private void startAudioComponents() {
        audioRecord.startRecording();
        audioTrack.play();
        isRecording = true;
    }

    /**
     * 启动录制线程
     */
    private void startRecordingThread() {
        recordingThread = new Thread(() -> {
            recordAndPlay();
        }, "MicrophoneRecordingThread");
        recordingThread.start();
    }

    /**
     * 停止麦克风放大
     * 关键点：完全释放所有音频资源，确保下次start()时重新初始化AudioTrack
     * 这样每次start()都能获取最新的车内外模式audioUsage
     */
    public synchronized void stop() {
        if (!isRecording) {
            AppLog.d(TAG, "Not recording");
            return;
        }

        isRecording = false;

        if (recordingThread != null) {
            try {
                recordingThread.join(1000);
            } catch (InterruptedException e) {
                AppLog.e(TAG, "Failed to join recording thread", e);
            }
        }

        // 完全释放所有音频资源，确保下次start()时重新初始化
        // 这是实现车内外切换生效的关键 - 必须完全释放才能重新创建AudioTrack
        releaseResources();
        AppLog.d(TAG, "Microphone amplifier stopped, all resources released for fresh initialization next time");
    }

    /**
     * 录制并播放音频的主循环
     */
    private void recordAndPlay() {
        byte[] buffer = new byte[BUFFER_SIZE];

        while (isRecording) {
            try {
                int readSize = audioRecord.read(buffer, 0, BUFFER_SIZE);

                if (readSize > 0) {
                    processAudioData(buffer, readSize);

                    audioTrack.write(buffer, 0, readSize);
                }
            } catch (Exception e) {
                AppLog.e(TAG, "Error in recording thread", e);
                break;
            }
        }
    }

    /**
     * 处理音频数据
     * @param buffer 音频数据缓冲区
     * @param length 数据长度
     */
    private void processAudioData(byte[] buffer, int length) {
        // 根据最大放大倍数和当前级别计算放大因子
        int maxAmplification = appConfig.getMaxAmplification();
        float amplificationFactor = 1.0f + (amplificationLevel / 10.0f) * (maxAmplification - 1.0f);

        short[] samples = new short[length / 2];

        convertBytesToSamples(buffer, samples);

        // 1. 先处理噪声抑制
        if (noiseReductionEnabled) {
            applyNoiseReduction(samples);
        }

        // 2. 再处理回声抑制：仅在硬件 AEC 不可用时启用
        if (echoCancellationEnabled && !hardwareAecAvailable) {
            AppLog.d(TAG, "Using software echo cancellation (hardware AEC not available)");
            applyEchoCancellation(samples);
        } else if (echoCancellationEnabled && hardwareAecAvailable) {
            // 硬件 AEC 可用，跳过软件回声抑制
            AppLog.d(TAG, "Skipping software echo cancellation (hardware AEC is active)");
        }

        // 3. 应用增益
        applyGain(samples, amplificationFactor);

        // 4. 限制音量
        limitVolume(samples);

        // 5. 最后处理啸叫抑制
        if (howlingSuppressionEnabled) {
            applyHowlingSuppression(samples);
        }

        convertSamplesToBytes(samples, buffer);
    }

    /**
     * 将字节数组转换为样本数组
     * @param buffer 字节数组
     * @param samples 样本数组
     */
    private void convertBytesToSamples(byte[] buffer, short[] samples) {
        for (int i = 0; i < samples.length; i++) {
            int index = i * 2;
            samples[i] = (short) ((buffer[index] & 0xFF) | (buffer[index + 1] << 8));
        }
    }

    /**
     * 将样本数组转换为字节数组
     * @param samples 样本数组
     * @param buffer 字节数组
     */
    private void convertSamplesToBytes(short[] samples, byte[] buffer) {
        for (int i = 0; i < samples.length; i++) {
            int index = i * 2;
            buffer[index] = (byte) (samples[i] & 0xFF);
            buffer[index + 1] = (byte) (samples[i] >> 8);
        }
    }

    /**
     * 应用增益放大
     * @param samples 样本数组
     * @param amplificationFactor 放大因子
     */
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

    /**
     * 限制音量防止溢出
     * @param samples 样本数组
     */
    private void limitVolume(short[] samples) {
        for (int i = 0; i < samples.length; i++) {
            if (samples[i] > Short.MAX_VALUE) {
                samples[i] = Short.MAX_VALUE;
            } else if (samples[i] < Short.MIN_VALUE) {
                samples[i] = Short.MIN_VALUE;
            }
        }
    }

    /**
     * 计算自动增益控制（AGC）
     * @param audioData 音频数据
     * @return 增益值
     */
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

    /**
     * 应用噪声抑制
     * @param samples 样本数组
     */
    private void applyNoiseReduction(short[] samples) {
        long sum = 0;
        for (short sample : samples) {
            sum += Math.abs(sample);
        }
        float avgVolume = (float) sum / samples.length / Short.MAX_VALUE;
        
        // 改进的阈值计算：使用更智能的动态阈值
        float baseThreshold = 0.015f; // 降低基础阈值，更保守
        // 使用平滑的平均音量，避免阈值突变
        float smoothedAvgVolume = avgVolume * 0.7f + previousAvgVolume * 0.3f;
        previousAvgVolume = smoothedAvgVolume;
        
        float dynamicThreshold = Math.max(baseThreshold, smoothedAvgVolume * 0.6f);
        
        // 频谱分析预留：检测稳态噪声
        boolean isSteadyNoise = detectSteadyNoise(samples);
        
        for (int i = 0; i < samples.length; i++) {
            float normalizedSample = Math.abs(samples[i]) / (float) Short.MAX_VALUE;
            
            if (normalizedSample < dynamicThreshold) {
                // 对于明显的低电平噪声，使用更强的衰减
                float attenuation = normalizedSample / dynamicThreshold;
                samples[i] = (short) (samples[i] * (0.5f + attenuation * 0.3f)); // 提高最小衰减系数
            } else if (normalizedSample < dynamicThreshold * 1.3f) { // 扩大过渡区域
                // 对于接近阈值的信号，使用平滑的过渡
                float attenuation = 0.8f + (normalizedSample - dynamicThreshold) / (dynamicThreshold * 0.3f) * 0.2f;
                samples[i] = (short) (samples[i] * attenuation);
            }
            // 对于明显高于阈值的信号，不进行处理，保留原始音频
        }
    }
    
    // 用于平滑噪声抑制的平均音量
    private float previousAvgVolume = 0.0f;
    
    /**
     * 检测稳态噪声（预留频谱分析接口）
     * @param samples 样本数组
     * @return 是否为稳态噪声
     */
    private boolean detectSteadyNoise(short[] samples) {
        // 简单的能量方差检测（未来可以用 FFT 频谱分析替代）
        float energy = 0.0f;
        for (short sample : samples) {
            float normalizedSample = Math.abs(sample) / (float) Short.MAX_VALUE;
            energy += normalizedSample * normalizedSample;
        }
        energy /= samples.length;
        
        // 如果能量变化很小，可能是稳态噪声
        float energyDiff = Math.abs(energy - previousEnergy);
        previousEnergy = energy;
        
        return energyDiff < 0.001f; // 能量变化小于阈值
    }
    
    // 用于稳态噪声检测的能量值
    private float previousEnergy = 0.0f;

    /**
     * 应用回声抑制（仅在硬件 AEC 不可用时使用）
     * @param samples 样本数组
     */
    private void applyEchoCancellation(short[] samples) {
        float frameEnergy = 0.0f;
        for (short sample : samples) {
            float normalizedSample = Math.abs(sample) / (float) Short.MAX_VALUE;
            frameEnergy += normalizedSample * normalizedSample;
        }
        frameEnergy /= samples.length;
        
        // 更新回声能量估计（使用更平滑的衰减因子）
        echoEnergy = ECHO_DECAY_FACTOR * echoEnergy + (1.0f - ECHO_DECAY_FACTOR) * frameEnergy;
        
        // 更严格的回声检测条件
        for (int i = 0; i < samples.length; i++) {
            float normalizedSample = Math.abs(samples[i]) / (float) Short.MAX_VALUE;
            
            // 仅当信号能量在平均能量附近且超过阈值时才进行抑制
            // 增加检测条件，减少误判
            if (normalizedSample > ECHO_THRESHOLD * 1.5f && // 提高阈值要求
                frameEnergy > echoEnergy * 0.7f && // 更严格的下限
                frameEnergy < echoEnergy * 1.3f) { // 更严格的上限
                // 使用更保守的衰减系数
                float attenuation = 1.0f - (normalizedSample - ECHO_THRESHOLD) * (1.0f - MAX_ECHO_ATTENUATION) / (1.0f - ECHO_THRESHOLD);
                attenuation = Math.max(attenuation, MAX_ECHO_ATTENUATION + 0.2f); // 提高最小衰减值，更保守
                
                samples[i] = (short) (samples[i] * attenuation);
            }
        }
    }
    
    /**
     * 应用啸叫抑制
     * @param samples 样本数组
     */
    private void applyHowlingSuppression(short[] samples) {
        float frameEnergy = 0.0f;
        for (short sample : samples) {
            float normalizedSample = Math.abs(sample) / (float) Short.MAX_VALUE;
            frameEnergy += normalizedSample * normalizedSample;
        }
        frameEnergy /= samples.length;
        
        howlingEnergy = HOWLING_DECAY_FACTOR * howlingEnergy + (1.0f - HOWLING_DECAY_FACTOR) * frameEnergy;
        
        // 检测啸叫：能量突然增加且超过阈值
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
            // 应用衰减抑制啸叫
            for (int i = 0; i < samples.length; i++) {
                float attenuation = MAX_HOWLING_ATTENUATION + (1.0f - MAX_HOWLING_ATTENUATION) * 0.3f;
                samples[i] = (short) (samples[i] * attenuation);
            }
        }
    }

    /**
     * 释放所有资源
     */
    private void releaseResources() {
        releaseEchoCanceler();

        releaseAudioRecord();

        releaseAudioTrack();

        recordingThread = null;
        echoEnergy = 0.0f;
        howlingEnergy = 0.0f;
        howlingCounter = 0;
    }

    /**
     * 释放回声消除器
     */
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

    /**
     * 释放AudioRecord
     */
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

    /**
     * 释放AudioTrack
     */
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
     * @param level 放大级别，范围0-10
     */
    public void setAmplificationLevel(int level) {
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
     * @return 放大级别
     */
    public int getAmplificationLevel() {
        return amplificationLevel;
    }

    /**
     * 检查是否正在录制
     * @return 是否正在录制
     */
    public boolean isRecording() {
        return isRecording;
    }

    /**
     * 设置是否启用噪声抑制
     * @param enabled 是否启用
     */
    public void setNoiseReductionEnabled(boolean enabled) {
        this.noiseReductionEnabled = enabled;
        appConfig.setNoiseReductionEnabled(enabled);
        AppLog.d(TAG, "Noise reduction " + (enabled ? "enabled" : "disabled"));
    }

    /**
     * 获取噪声抑制状态
     * @return 是否启用
     */
    public boolean isNoiseReductionEnabled() {
        return noiseReductionEnabled;
    }

    /**
     * 设置是否启用回声抑制
     * @param enabled 是否启用
     */
    public void setEchoCancellationEnabled(boolean enabled) {
        this.echoCancellationEnabled = enabled;
        appConfig.setEchoCancellationEnabled(enabled);
        AppLog.d(TAG, "Echo cancellation " + (enabled ? "enabled" : "disabled"));
    }

    /**
     * 获取回声抑制状态
     * @return 是否启用
     */
    public boolean isEchoCancellationEnabled() {
        return echoCancellationEnabled;
    }
    
    /**
     * 设置是否启用啸叫抑制
     * @param enabled 是否启用
     */
    public void setHowlingSuppressionEnabled(boolean enabled) {
        this.howlingSuppressionEnabled = enabled;
        appConfig.setHowlingSuppressionEnabled(enabled);
        AppLog.d(TAG, "Howling suppression " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * 获取啸叫抑制状态
     * @return 是否启用
     */
    public boolean isHowlingSuppressionEnabled() {
        return howlingSuppressionEnabled;
    }
    
    /**
     * 获取硬件 AEC 可用性状态
     * @return 硬件 AEC 是否可用
     */
    public boolean isHardwareAecAvailable() {
        return hardwareAecAvailable;
    }
}
