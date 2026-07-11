package com.aug32.l7audio.data.local.config.floating;

import android.content.SharedPreferences;

/**
 * 悬浮窗配置
 *
 * 职责：管理悬浮窗的开关状态、显示位置、透明度、宽度以及 TTS 选择状态等配置
 * 通过 SharedPreferences 持久化存储悬浮窗相关参数
 */
public class FloatingWindowConfig {

    private static final String PREF_FLOATING_WINDOW_ENABLED = "floating_window_enabled";
    private static final String PREF_FLOATING_WINDOW_X = "floating_window_x";
    private static final String PREF_FLOATING_WINDOW_Y = "floating_window_y";
    private static final String PREF_FLOATING_WINDOW_ALPHA = "floating_window_alpha";
    private static final String PREF_FLOATING_WINDOW_WIDTH_DP = "floating_window_width_dp";
    private static final String PREF_FLOATING_WINDOW_TTS_INDICES = "floating_window_tts_indices";
    private static final String PREF_FLOATING_WINDOW_TTS_NAMES = "floating_window_tts_names";
    private static final String PREF_FLOATING_WINDOW_AUTO_HIDE_TIMEOUT_SEC = "floating_window_auto_hide_timeout_sec";
    // 新 sp key：基于 uid 的选中持久化，旧 indices/names 数据自然废弃
    private static final String PREF_FLOATING_WINDOW_TTS_UIDS = "floating_window_tts_uids";
    private static final String PREF_FLOATING_WINDOW_TTS_NAMES_BY_UID = "floating_window_tts_names_by_uid";

    private final SharedPreferences preferences;

    /**
     * 构造函数
     *
     * @param preferences SharedPreferences 实例，用于持久化存储悬浮窗配置
     */
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

    /**
     * 获取悬浮窗透明度
     * 透明度范围自动限制在 20-100 之间
     *
     * @return 悬浮窗透明度值（20-100）
     */
    public int getAlpha() {
        int value = preferences.getInt(PREF_FLOATING_WINDOW_ALPHA, 80);
        if (value < 20) value = 20;
        if (value > 100) value = 100;
        return value;
    }

    /**
     * 设置悬浮窗透明度
     * 透明度范围自动限制在 20-100 之间，超出范围将被截断
     *
     * @param alpha 悬浮窗透明度值（20-100）
     */
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

    /** @return 选中 TTS 项的 uid 列表 JSON，默认 "[]" */
    public String getTTSSelectedUids() {
        return preferences.getString(PREF_FLOATING_WINDOW_TTS_UIDS, "[]");
    }

    /** 持久化选中 TTS 项的 uid 列表 JSON */
    public void setTTSSelectedUids(String uidsJson) {
        preferences.edit().putString(PREF_FLOATING_WINDOW_TTS_UIDS, uidsJson).apply();
    }

    /** @return uid → 自定义名称 映射 JSON，默认 "{}" */
    public String getTTSNamesByUid() {
        return preferences.getString(PREF_FLOATING_WINDOW_TTS_NAMES_BY_UID, "{}");
    }

    /** 持久化 uid → 自定义名称 映射 JSON */
    public void setTTSNamesByUid(String namesJson) {
        preferences.edit().putString(PREF_FLOATING_WINDOW_TTS_NAMES_BY_UID, namesJson).apply();
    }

    public int getAutoHideTimeoutSec() {
        return preferences.getInt(PREF_FLOATING_WINDOW_AUTO_HIDE_TIMEOUT_SEC, 10);
    }

    public void setAutoHideTimeoutSec(int seconds) {
        if (seconds < 5) seconds = 5;
        if (seconds > 30) seconds = 30;
        preferences.edit().putInt(PREF_FLOATING_WINDOW_AUTO_HIDE_TIMEOUT_SEC, seconds).apply();
    }
}
