package com.aug32.l7audio;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class AppConfig {
    private static final String TAG = "AppConfig";
    private static final String PREF_AUTO_START_ON_BOOT = "auto_start_on_boot";
    private static final String PREF_AUDIO_OUTPUT_MODE = "audio_output_mode";
    private static final String PREF_MIC_AMPLIFICATION_LEVEL = "mic_amplification_level";
    private static final String PREF_NOISE_REDUCTION_ENABLED = "noise_reduction_enabled";
    private static final String PREF_ECHO_CANCELLATION_ENABLED = "echo_cancellation_enabled";
    private static final String PREF_HOWLING_SUPPRESSION_ENABLED = "howling_suppression_enabled";
    private static final String PREF_TTS_SPEED = "tts_speed";
    private static final String PREF_TTS_PITCH = "tts_pitch";
    private static final String PREF_THEME_MODE = "theme_mode";
    private static final String PREF_TTS_ITEMS = "tts_items";
    private static final String PREF_CURRENT_FUNCTION = "current_function";
    // 音频使用类型对应的 SharedPreferences key
    private static final String PREF_AUDIO_OUTPUT_USAGE_EXTERNAL = "audio_output_usage_external"; // 车外
    private static final String PREF_AUDIO_OUTPUT_USAGE_CAR = "audio_output_usage_car";//车内
    private static final String PREF_AUDIO_INPUT_SOURCE = "audio_input_source";
    // 音乐播放器相关配置
    private static final String PREF_MUSIC_PLAYLIST = "music_playlist";
    private static final String PREF_LAST_PLAYED_INDEX = "last_played_index";
    private static final String PREF_LAST_PLAYED_POSITION = "last_played_position";
    private static final String PREF_REPEAT_MODE = "repeat_mode";
    private static final String PREF_SHUFFLE_MODE = "shuffle_mode";
    private static final String PREF_CAR_VOLUME = "car_volume";
    private static final String PREF_EXTERNAL_VOLUME = "external_volume";
    private static final String PREF_MAX_AMPLIFICATION = "max_amplification";

    // 主题模式常量
    public static final int THEME_MODE_SYSTEM = 0;
    public static final int THEME_MODE_LIGHT = 1;
    public static final int THEME_MODE_DARK = 2;

    private final SharedPreferences preferences;

    public AppConfig(Context context) {
        // TODO: PreferenceManager.getDefaultSharedPreferences() 在API 29中已过时
        // Android 11(API 30)上仍可正常使用，但建议未来迁移到Context.getSharedPreferences()
        // 替代方案: context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE)
        this.preferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    // 开机自启动设置
    public boolean isAutoStartOnBoot() {
        return preferences.getBoolean(PREF_AUTO_START_ON_BOOT, false);
    }

    public void setAutoStartOnBoot(boolean autoStart) {
        preferences.edit().putBoolean(PREF_AUTO_START_ON_BOOT, autoStart).apply();
    }

    // 音频输出模式设置
    public int getAudioOutputMode() {
        return preferences.getInt(PREF_AUDIO_OUTPUT_MODE, 0); // 0: 仅车内
    }

    public void setAudioOutputMode(int mode) {
        preferences.edit().putInt(PREF_AUDIO_OUTPUT_MODE, mode).apply();
    }

    // 麦克风放大级别设置
    public int getMicAmplificationLevel() {
        return preferences.getInt(PREF_MIC_AMPLIFICATION_LEVEL, 5); // 默认中等水平
    }

    public void setMicAmplificationLevel(int level) {
        preferences.edit().putInt(PREF_MIC_AMPLIFICATION_LEVEL, level).apply();
    }

    // TTS 语速设置
    public float getTTSSpeed() {
        return preferences.getFloat(PREF_TTS_SPEED, 1.0f); // 默认正常语速
    }

    public void setTTSSpeed(float speed) {
        preferences.edit().putFloat(PREF_TTS_SPEED, speed).apply();
    }

    // TTS 音调设置
    public float getTTSPitch() {
        return preferences.getFloat(PREF_TTS_PITCH, 1.0f); // 默认正常音调
    }

    public void setTTSPitch(float pitch) {
        preferences.edit().putFloat(PREF_TTS_PITCH, pitch).apply();
    }

    // 主题模式设置
    public int getThemeMode() {
        return preferences.getInt(PREF_THEME_MODE, THEME_MODE_SYSTEM); // 默认跟随系统
    }

    public void setThemeMode(int mode) {
        preferences.edit().putInt(PREF_THEME_MODE, mode).apply();
    }

    // TTS内容设置
    public String getTTSItems() {
        return preferences.getString(PREF_TTS_ITEMS, "");
    }

    public void setTTSItems(String ttsItemsJson) {
        preferences.edit().putString(PREF_TTS_ITEMS, ttsItemsJson).apply();
    }

    // 当前功能设置
    public int getCurrentFunction() {
        return preferences.getInt(PREF_CURRENT_FUNCTION, -1); // -1: 无
    }

    public void setCurrentFunction(int function) {
        preferences.edit().putInt(PREF_CURRENT_FUNCTION, function).apply();
    }

    // 噪声抑制设置
    public boolean isNoiseReductionEnabled() {
        return preferences.getBoolean(PREF_NOISE_REDUCTION_ENABLED, true); // 默认开启
    }

    public void setNoiseReductionEnabled(boolean enabled) {
        preferences.edit().putBoolean(PREF_NOISE_REDUCTION_ENABLED, enabled).apply();
    }

    // 回声抑制设置
    public boolean isEchoCancellationEnabled() {
        return preferences.getBoolean(PREF_ECHO_CANCELLATION_ENABLED, true); // 默认开启
    }

    public void setEchoCancellationEnabled(boolean enabled) {
        preferences.edit().putBoolean(PREF_ECHO_CANCELLATION_ENABLED, enabled).apply();
    }

    // 啸叫抑制设置
    public boolean isHowlingSuppressionEnabled() {
        return preferences.getBoolean(PREF_HOWLING_SUPPRESSION_ENABLED, true); // 默认开启
    }

    public void setHowlingSuppressionEnabled(boolean enabled) {
        preferences.edit().putBoolean(PREF_HOWLING_SUPPRESSION_ENABLED, enabled).apply();
    }



    // 车外音频使用类型设置
    public int getAudioOutputUsageExternal() {
        return preferences.getInt(PREF_AUDIO_OUTPUT_USAGE_EXTERNAL, 15); // 默认15 (bus15 ktvout)
    }

    public void setAudioOutputUsageExternal(int usageType) {
        preferences.edit().putInt(PREF_AUDIO_OUTPUT_USAGE_EXTERNAL, usageType).apply();
    }

    // 车内音频使用类型设置
    public int getAudioOutputUsageCar() {
        return preferences.getInt(PREF_AUDIO_OUTPUT_USAGE_CAR, 1); // 默认1 (USAGE_MEDIA)
    }

    public void setAudioOutputUsageCar(int usageType) {
        preferences.edit().putInt(PREF_AUDIO_OUTPUT_USAGE_CAR, usageType).apply();
    }

    // 音频输入源设置
    public int getAudioInputSource() {
        return preferences.getInt(PREF_AUDIO_INPUT_SOURCE, 1); // 默认MIC
    }

    public void setAudioInputSource(int sourceType) {
        preferences.edit().putInt(PREF_AUDIO_INPUT_SOURCE, sourceType).apply();
    }

    // 重置音频通道设置
    public void resetAudioChannel() {
        preferences.edit()
                .putInt(PREF_AUDIO_OUTPUT_USAGE_EXTERNAL, 15) // 车外默认15 (bus15 ktvout)
                .putInt(PREF_AUDIO_OUTPUT_USAGE_CAR, 1) // 车内默认1 (USAGE_MEDIA)
                .putInt(PREF_AUDIO_INPUT_SOURCE, 1) // 默认MIC
                .apply();
    }

    // 重置所有设置
    public void resetAllSettings() {
        preferences.edit()
                .clear()
                .apply();
    }

    // 音乐播放器播放列表设置
    public String getMusicPlaylist() {
        return preferences.getString(PREF_MUSIC_PLAYLIST, "");
    }

    public void setMusicPlaylist(String playlistJson) {
        preferences.edit().putString(PREF_MUSIC_PLAYLIST, playlistJson).apply();
    }

    // 上次播放索引设置
    public int getLastPlayedIndex() {
        return preferences.getInt(PREF_LAST_PLAYED_INDEX, -1);
    }

    public void setLastPlayedIndex(int index) {
        preferences.edit().putInt(PREF_LAST_PLAYED_INDEX, index).apply();
    }

    // 上次播放位置设置
    public long getLastPlayedPosition() {
        return preferences.getLong(PREF_LAST_PLAYED_POSITION, 0);
    }

    public void setLastPlayedPosition(long position) {
        preferences.edit().putLong(PREF_LAST_PLAYED_POSITION, position).apply();
    }

    // 循环模式设置
    public int getRepeatMode() {
        return preferences.getInt(PREF_REPEAT_MODE, 0); // 0: 不循环, 1: 全部循环, 2: 单曲循环
    }

    public void setRepeatMode(int mode) {
        preferences.edit().putInt(PREF_REPEAT_MODE, mode).apply();
    }

    // 随机播放模式设置
    public boolean isShuffleModeEnabled() {
        return preferences.getBoolean(PREF_SHUFFLE_MODE, false);
    }

    public void setShuffleModeEnabled(boolean enabled) {
        preferences.edit().putBoolean(PREF_SHUFFLE_MODE, enabled).apply();
    }

    // 车内音量设置
    public int getCarVolume() {
        return preferences.getInt(PREF_CAR_VOLUME, 100); // 0-100
    }

    public void setCarVolume(int volume) {
        preferences.edit().putInt(PREF_CAR_VOLUME, volume).apply();
    }

    // 车外音量设置
    public int getExternalVolume() {
        return preferences.getInt(PREF_EXTERNAL_VOLUME, 100); // 0-100
    }

    public void setExternalVolume(int volume) {
        preferences.edit().putInt(PREF_EXTERNAL_VOLUME, volume).apply();
    }

    // 最大放大倍数设置
    public int getMaxAmplification() {
        return preferences.getInt(PREF_MAX_AMPLIFICATION, 2); // 默认2倍
    }

    public void setMaxAmplification(int maxAmplification) {
        preferences.edit().putInt(PREF_MAX_AMPLIFICATION, maxAmplification).apply();
    }
}