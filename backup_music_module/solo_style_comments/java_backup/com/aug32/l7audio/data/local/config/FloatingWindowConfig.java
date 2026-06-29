package com.aug32.l7audio.data.local.config;

import android.content.SharedPreferences;

/**
 * 悬浮窗配置
 *
 * 职责：悬浮窗开关 + 位置 + 透明度 + 宽度 + TTS 选择状态
 */
public class FloatingWindowConfig {

    private static final String PREF_FLOATING_WINDOW_ENABLED = "floating_window_enabled";
    private static final String PREF_FLOATING_WINDOW_X = "floating_window_x";
    private static final String PREF_FLOATING_WINDOW_Y = "floating_window_y";
    private static final String PREF_FLOATING_WINDOW_ALPHA = "floating_window_alpha";
    private static final String PREF_FLOATING_WINDOW_WIDTH_DP = "floating_window_width_dp";
    private static final String PREF_FLOATING_WINDOW_TTS_INDICES = "floating_window_tts_indices";
    private static final String PREF_FLOATING_WINDOW_TTS_NAMES = "floating_window_tts_names";

    private final SharedPreferences preferences;

    public FloatingWindowConfig(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    public boolean isEnabled() {
        return preferences.getBoolean(PREF_FLOATING_WINDOW_ENABLED, false);
    }

    public void setEnabled(boolean enabled) {
        preferences.edit().putBoolean(PREF_FLOATING_WINDOW_ENABLED, enabled).apply();
    }

    public int getX() {
        return preferences.getInt(PREF_FLOATING_WINDOW_X, 0);
    }

    public void setX(int x) {
        preferences.edit().putInt(PREF_FLOATING_WINDOW_X, x).apply();
    }

    public int getY() {
        return preferences.getInt(PREF_FLOATING_WINDOW_Y, 100);
    }

    public void setY(int y) {
        preferences.edit().putInt(PREF_FLOATING_WINDOW_Y, y).apply();
    }

    public int getAlpha() {
        int value = preferences.getInt(PREF_FLOATING_WINDOW_ALPHA, 80);
        if (value < 20) value = 20;
        if (value > 100) value = 100;
        return value;
    }

    public void setAlpha(int alpha) {
        if (alpha < 20) alpha = 20;
        if (alpha > 100) alpha = 100;
        preferences.edit().putInt(PREF_FLOATING_WINDOW_ALPHA, alpha).apply();
    }

    public int getWidthDp() {
        return preferences.getInt(PREF_FLOATING_WINDOW_WIDTH_DP, 336);
    }

    public void setWidthDp(int widthDp) {
        preferences.edit().putInt(PREF_FLOATING_WINDOW_WIDTH_DP, widthDp).apply();
    }

    public String getTTSIndices() {
        return preferences.getString(PREF_FLOATING_WINDOW_TTS_INDICES, "[]");
    }

    public void setTTSIndices(String indicesJson) {
        preferences.edit().putString(PREF_FLOATING_WINDOW_TTS_INDICES, indicesJson).apply();
    }

    public String getTTSNames() {
        return preferences.getString(PREF_FLOATING_WINDOW_TTS_NAMES, "{}");
    }

    public void setTTSNames(String namesJson) {
        preferences.edit().putString(PREF_FLOATING_WINDOW_TTS_NAMES, namesJson).apply();
    }
}
