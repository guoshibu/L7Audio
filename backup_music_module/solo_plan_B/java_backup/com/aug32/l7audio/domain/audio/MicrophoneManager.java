package com.aug32.l7audio.domain.audio;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;

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
    private boolean hardwareAecAvailable = false;
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

    private float previousAvgVolume = 0.0f;
    private float previousEnergy = 0.0f;

    /** 构造函数 */
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

    /** 开始麦克风放大 */
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

    /** 检查录音权限 */
    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(context, "android.permission.RECORD_AUDIO")
                == PackageManager.PERMISSION_GRANTED;
    }

    /** 初始化音频组件 */
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

    /** 初始化 AudioTrack */
    private boolean initializeAudioTrack() {
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

    /** 初始化硬件级回声消除器 */
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

    /** 开始录音和播放 */
    private void startAudioComponents() {
        audioRecord.startRecording();
        audioTrack.play();
        isRecording = true;
    }

    /** 启动录制线程 */
    private void startRecordingThread() {
        recordingThread = new Thread(() -> {
            recordAndPlay();
        }, "MicrophoneRecordingThread");
        recordingThread.start();
    }

    /** 停止麦克风放大 */
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

        releaseResources();
        AppLog.d(TAG, "Microphone amplifier stopped, all resources released for fresh initialization next time");
    }

    /** 录制并播放音频的主循环 */
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

    /** 处理音频数据 */
    private void processAudioData(byte[] buffer, int length) {
        int maxAmplification = appConfig.getMaxAmplification();
        float amplificationFactor = 1.0f + (amplificationLevel / 10.0f) * (maxAmplification - 1.0f);

        short[] samples = new short[length / 2];

        convertBytesToSamples(buffer, samples);

        if (noiseReductionEnabled) {
            applyNoiseReduction(samples);
        }

        if (echoCancellationEnabled && !hardwareAecAvailable) {
            AppLog.d(TAG, "Using software echo cancellation (hardware AEC not available)");
            applyEchoCancellation(samples);
        } else if (echoCancellationEnabled && hardwareAecAvailable) {
            AppLog.d(TAG, "Skipping software echo cancellation (hardware AEC is active)");
        }

        applyGain(samples, amplificationFactor);
        limitVolume(samples);

        if (howlingSuppressionEnabled) {
            applyHowlingSuppression(samples);
        }

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

    /** 设置放大级别 */
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

    /** 获取当前放大级别 */
    public int getAmplificationLevel() {
        return amplificationLevel;
    }

    /** 检查是否正在录制 */
    public boolean isRecording() {
        return isRecording;
    }

    /** 设置是否启用噪声抑制 */
    public void setNoiseReductionEnabled(boolean enabled) {
        this.noiseReductionEnabled = enabled;
        appConfig.setNoiseReductionEnabled(enabled);
        AppLog.d(TAG, "Noise reduction " + (enabled ? "enabled" : "disabled"));
    }

    /** 获取噪声抑制状态 */
    public boolean isNoiseReductionEnabled() {
        return noiseReductionEnabled;
    }

    /** 设置是否启用回声抑制 */
    public void setEchoCancellationEnabled(boolean enabled) {
        this.echoCancellationEnabled = enabled;
        appConfig.setEchoCancellationEnabled(enabled);
        AppLog.d(TAG, "Echo cancellation " + (enabled ? "enabled" : "disabled"));
    }

    /** 获取回声抑制状态 */
    public boolean isEchoCancellationEnabled() {
        return echoCancellationEnabled;
    }
    
    /** 设置是否启用啸叫抑制 */
    public void setHowlingSuppressionEnabled(boolean enabled) {
        this.howlingSuppressionEnabled = enabled;
        appConfig.setHowlingSuppressionEnabled(enabled);
        AppLog.d(TAG, "Howling suppression " + (enabled ? "enabled" : "disabled"));
    }
    
    /** 获取啸叫抑制状态 */
    public boolean isHowlingSuppressionEnabled() {
        return howlingSuppressionEnabled;
    }
    
    /** 获取硬件 AEC 可用性状态 */
    public boolean isHardwareAecAvailable() {
        return hardwareAecAvailable;
    }
}
