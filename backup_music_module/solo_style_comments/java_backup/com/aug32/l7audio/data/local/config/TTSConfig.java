package com.aug32.l7audio.data.local.config;

import android.content.SharedPreferences;

/**
 * TTS 配置
 *
 * 职责：TTS 语速/音调 + TTS 预设文本列表
 */
public class TTSConfig {

    private static final String PREF_TTS_SPEED = "tts_speed";
    private static final String PREF_TTS_PITCH = "tts_pitch";
    private static final String PREF_TTS_ITEMS = "tts_items";

    private final SharedPreferences preferences;

    public TTSConfig(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    public float getTTSSpeed() {
        return preferences.getFloat(PREF_TTS_SPEED, 1.0f);
    }

    public void setTTSSpeed(float speed) {
        preferences.edit().putFloat(PREF_TTS_SPEED, speed).apply();
    }

    public float getTTSPitch() {
        return preferences.getFloat(PREF_TTS_PITCH, 1.0f);
    }

    public void setTTSPitch(float pitch) {
        preferences.edit().putFloat(PREF_TTS_PITCH, pitch).apply();
    }

    public String getTTSItems() {
        return preferences.getString(PREF_TTS_ITEMS, "");
    }

    public void setTTSItems(String ttsItemsJson) {
        preferences.edit().putString(PREF_TTS_ITEMS, ttsItemsJson).apply();
    }
}
