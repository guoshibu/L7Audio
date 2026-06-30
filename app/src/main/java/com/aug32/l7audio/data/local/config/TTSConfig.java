package com.aug32.l7audio.data.local.config;

import android.content.SharedPreferences;

/**
 * TTS（语音合成）配置
 *
 * <p>职责：管理 TTS 预设文本列表等配置
 * 通过 SharedPreferences 持久化存储语音合成相关参数
 *
 * @author L7Audio Team
 */
public class TTSConfig {

    private static final String PREF_TTS_ITEMS = "tts_items";

    private final SharedPreferences preferences;

    /**
     * 构造函数
     *
     * @param preferences SharedPreferences 实例，用于持久化存储 TTS 配置
     */
    public TTSConfig(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    public String getTTSItems() {
        return preferences.getString(PREF_TTS_ITEMS, "");
    }

    public void setTTSItems(String ttsItemsJson) {
        preferences.edit().putString(PREF_TTS_ITEMS, ttsItemsJson).apply();
    }
}
