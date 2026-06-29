package com.aug32.l7audio.data.local.config;

import android.content.SharedPreferences;

/**
 * 麦克风放大配置
 *
 * 职责：放大级别 + 噪声抑制/回声消除/啸叫抑制开关
 */
public class MicConfig {

    private static final String PREF_MIC_AMPLIFICATION_LEVEL = "mic_amplification_level";
    private static final String PREF_NOISE_REDUCTION_ENABLED = "noise_reduction_enabled";
    private static final String PREF_ECHO_CANCELLATION_ENABLED = "echo_cancellation_enabled";
    private static final String PREF_HOWLING_SUPPRESSION_ENABLED = "howling_suppression_enabled";
    private static final String PREF_MAX_AMPLIFICATION = "max_amplification";

    private final SharedPreferences preferences;

    public MicConfig(SharedPreferences preferences) {
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

    public int getMaxAmplification() {
        return preferences.getInt(PREF_MAX_AMPLIFICATION, 2);
    }

    public void setMaxAmplification(int maxAmplification) {
        preferences.edit().putInt(PREF_MAX_AMPLIFICATION, maxAmplification).apply();
    }
}
