package com.aug32.l7audio.data.local.config;

import android.content.SharedPreferences;

/**
 * 音频输出配置
 *
 * 职责：管理音频输出模式（车内/车外）、音量设置、音频使用类型及输入源等配置
 * 通过 SharedPreferences 持久化存储音频相关参数
 */
public class AudioConfig {

    private static final String PREF_AUDIO_OUTPUT_MODE = "audio_output_mode";
    private static final String PREF_AUDIO_OUTPUT_USAGE_EXTERNAL = "audio_output_usage_external";
    private static final String PREF_AUDIO_OUTPUT_USAGE_CAR = "audio_output_usage_car";
    private static final String PREF_AUDIO_INPUT_SOURCE = "audio_input_source";
    private static final String PREF_CAR_VOLUME = "car_volume";
    private static final String PREF_EXTERNAL_VOLUME = "external_volume";
    private static final String PREF_CURRENT_FUNCTION = "current_function";

    /** 输出模式：车外输出 */
    public static final int OUTPUT_MODE_EXTERNAL = 0;
    /** 输出模式：车内输出 */
    public static final int OUTPUT_MODE_CAR = 1;

    private final SharedPreferences preferences;

    /**
     * 构造函数
     *
     * @param preferences SharedPreferences 实例，用于持久化存储音频配置
     */
    public AudioConfig(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    public int getOutputMode() {
        return preferences.getInt(PREF_AUDIO_OUTPUT_MODE, OUTPUT_MODE_EXTERNAL);
    }

    public void setOutputMode(int mode) {
        preferences.edit().putInt(PREF_AUDIO_OUTPUT_MODE, mode).apply();
    }

    public int getUsageExternal() {
        return preferences.getInt(PREF_AUDIO_OUTPUT_USAGE_EXTERNAL, 9);
    }

    public void setUsageExternal(int usageType) {
        preferences.edit().putInt(PREF_AUDIO_OUTPUT_USAGE_EXTERNAL, usageType).apply();
    }

    public int getUsageCar() {
        return preferences.getInt(PREF_AUDIO_OUTPUT_USAGE_CAR, 1);
    }

    public void setUsageCar(int usageType) {
        preferences.edit().putInt(PREF_AUDIO_OUTPUT_USAGE_CAR, usageType).apply();
    }

    public int getAudioInputSource() {
        return preferences.getInt(PREF_AUDIO_INPUT_SOURCE, 1);
    }

    public void setAudioInputSource(int sourceType) {
        preferences.edit().putInt(PREF_AUDIO_INPUT_SOURCE, sourceType).apply();
    }

    public int getCarVolume() {
        return preferences.getInt(PREF_CAR_VOLUME, 100);
    }

    public void setCarVolume(int volume) {
        preferences.edit().putInt(PREF_CAR_VOLUME, volume).apply();
    }

    public int getExternalVolume() {
        return preferences.getInt(PREF_EXTERNAL_VOLUME, 100);
    }

    public void setExternalVolume(int volume) {
        preferences.edit().putInt(PREF_EXTERNAL_VOLUME, volume).apply();
    }

    public int getCurrentFunction() {
        return preferences.getInt(PREF_CURRENT_FUNCTION, -1);
    }

    public void setCurrentFunction(int function) {
        preferences.edit().putInt(PREF_CURRENT_FUNCTION, function).apply();
    }
}
