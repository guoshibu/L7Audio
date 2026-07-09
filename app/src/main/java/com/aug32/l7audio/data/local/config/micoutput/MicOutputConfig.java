package com.aug32.l7audio.data.local.config.micoutput;

import android.content.SharedPreferences;

/**
 * 麦克风配置
 *
 * 职责：管理麦克风放大级别、噪声抑制、回声消除和啸叫抑制等音频处理功能的开关和参数
 * 通过 SharedPreferences 持久化存储麦克风相关配置
 */
public class MicOutputConfig {

    private static final String PREF_MIC_AMPLIFICATION_LEVEL = "mic_amplification_level";
    private static final String PREF_NOISE_REDUCTION_ENABLED = "noise_reduction_enabled";
    private static final String PREF_ECHO_CANCELLATION_ENABLED = "echo_cancellation_enabled";
    private static final String PREF_HOWLING_SUPPRESSION_ENABLED = "howling_suppression_enabled";
    private static final String PREF_AGC_ENABLED = "agc_enabled";
    private static final String PREF_MAX_AMPLIFICATION = "max_amplification";

    // 车外喊话相关配置
    /** 防抖间隔（毫秒），屏蔽快速连续触发，避免麦克风频繁启停啸叫 */
    private static final String PREF_DEBOUNCE_INTERVAL = "mic_debounce_interval";
    /** 静音检测开关，开启后无声音输入超时自动关闭喊话 */
    private static final String PREF_SILENCE_DETECTION_ENABLED = "mic_silence_detection_enabled";
    /** 静音超时时间（秒），超过此时间无声音输入则自动关闭 */
    private static final String PREF_SILENCE_TIMEOUT = "mic_silence_timeout";
    /** 静音阈值（RMS），低于此值视为无声音输入 */
    private static final String PREF_SILENCE_THRESHOLD = "mic_silence_threshold";

    // 默认值
    private static final int DEFAULT_DEBOUNCE_INTERVAL = 800;
    private static final boolean DEFAULT_SILENCE_DETECTION_ENABLED = true;
    private static final int DEFAULT_SILENCE_TIMEOUT = 30;
    private static final float DEFAULT_SILENCE_THRESHOLD = 0.05f;

    private final SharedPreferences preferences;

    /**
     * 构造函数
     *
     * @param preferences SharedPreferences 实例，用于持久化存储麦克风配置
     */
    public MicOutputConfig(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    public int getAmplificationLevel() {
        return preferences.getInt(PREF_MIC_AMPLIFICATION_LEVEL, 5);
    }

    public void setAmplificationLevel(int level) {
        preferences.edit().putInt(PREF_MIC_AMPLIFICATION_LEVEL, level).apply();
    }

    public boolean isNoiseReductionEnabled() {
        return preferences.getBoolean(PREF_NOISE_REDUCTION_ENABLED, true);
    }

    public void setNoiseReductionEnabled(boolean enabled) {
        preferences.edit().putBoolean(PREF_NOISE_REDUCTION_ENABLED, enabled).apply();
    }

    public boolean isEchoCancellationEnabled() {
        return preferences.getBoolean(PREF_ECHO_CANCELLATION_ENABLED, true);
    }

    public void setEchoCancellationEnabled(boolean enabled) {
        preferences.edit().putBoolean(PREF_ECHO_CANCELLATION_ENABLED, enabled).apply();
    }

    public boolean isHowlingSuppressionEnabled() {
        return preferences.getBoolean(PREF_HOWLING_SUPPRESSION_ENABLED, true);
    }

    public void setHowlingSuppressionEnabled(boolean enabled) {
        preferences.edit().putBoolean(PREF_HOWLING_SUPPRESSION_ENABLED, enabled).apply();
    }

    public boolean isAgcEnabled() {
        return preferences.getBoolean(PREF_AGC_ENABLED, true);
    }

    public void setAgcEnabled(boolean enabled) {
        preferences.edit().putBoolean(PREF_AGC_ENABLED, enabled).apply();
    }

    public int getMaxAmplification() {
        return preferences.getInt(PREF_MAX_AMPLIFICATION, 2);
    }

    public void setMaxAmplification(int maxAmplification) {
        preferences.edit().putInt(PREF_MAX_AMPLIFICATION, maxAmplification).apply();
    }

    /**
     * 获取防抖间隔（毫秒）
     * <p>
     * 屏蔽快速连续触发，避免麦克风频繁启停导致啸叫和硬件损伤。
     * </p>
     *
     * @return 防抖间隔，范围 500-2000ms，默认 800ms
     */
    public int getDebounceInterval() {
        return preferences.getInt(PREF_DEBOUNCE_INTERVAL, DEFAULT_DEBOUNCE_INTERVAL);
    }

    /**
     * 设置防抖间隔（毫秒）
     *
     * @param interval 防抖间隔，范围 500-2000ms
     */
    public void setDebounceInterval(int interval) {
        preferences.edit().putInt(PREF_DEBOUNCE_INTERVAL, interval).apply();
    }

    /**
     * 获取静音检测开关状态
     *
     * @return true 表示开启静音自动关闭，false 表示关闭
     */
    public boolean isSilenceDetectionEnabled() {
        return preferences.getBoolean(PREF_SILENCE_DETECTION_ENABLED, DEFAULT_SILENCE_DETECTION_ENABLED);
    }

    /**
     * 设置静音检测开关
     *
     * @param enabled true 开启静音检测，false 关闭
     */
    public void setSilenceDetectionEnabled(boolean enabled) {
        preferences.edit().putBoolean(PREF_SILENCE_DETECTION_ENABLED, enabled).apply();
    }

    /**
     * 获取静音超时时间（秒）
     *
     * @return 超时时间，范围 5-300s，默认 30s
     */
    public int getSilenceTimeout() {
        return preferences.getInt(PREF_SILENCE_TIMEOUT, DEFAULT_SILENCE_TIMEOUT);
    }

    /**
     * 设置静音超时时间（秒）
     *
     * @param timeout 超时时间，范围 5-300s
     */
    public void setSilenceTimeout(int timeout) {
        preferences.edit().putInt(PREF_SILENCE_TIMEOUT, timeout).apply();
    }

    /**
     * 获取静音阈值（RMS）
     *
     * @return 阈值，范围 0.03-0.3，默认 0.05
     */
    public float getSilenceThreshold() {
        return preferences.getFloat(PREF_SILENCE_THRESHOLD, DEFAULT_SILENCE_THRESHOLD);
    }

    /**
     * 设置静音阈值（RMS）
     *
     * @param threshold 阈值，范围 0.03-0.3
     */
    public void setSilenceThreshold(float threshold) {
        preferences.edit().putFloat(PREF_SILENCE_THRESHOLD, threshold).apply();
    }
}
