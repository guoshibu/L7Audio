package com.aug32.l7audio.data.local.config;

import android.content.SharedPreferences;

/**
 * 主题与启动配置
 *
 * 职责：管理应用主题模式（跟随系统/浅色/深色）和开机自启动开关等配置
 * 通过 SharedPreferences 持久化存储主题和启动相关参数
 */
public class ThemeConfig {

    private static final String PREF_THEME_MODE = "theme_mode";
    private static final String PREF_AUTO_START_ON_BOOT = "auto_start_on_boot";
    private static final String PREF_FONT_SCALE = "font_scale";

    /** 跟随系统主题 */
    public static final int THEME_MODE_SYSTEM = 0;
    /** 浅色主题 */
    public static final int THEME_MODE_LIGHT = 1;
    /** 深色主题 */
    public static final int THEME_MODE_DARK = 2;

    /** 字体缩放系数下限 */
    public static final float FONT_SCALE_MIN = 0.7f;
    /** 字体缩放系数上限 */
    public static final float FONT_SCALE_MAX = 1.5f;
    /** 默认字体缩放系数 */
    public static final float FONT_SCALE_DEFAULT = 1.0f;

    private final SharedPreferences preferences;

    /**
     * 构造函数
     *
     * @param preferences SharedPreferences 实例，用于持久化存储主题配置
     */
    public ThemeConfig(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    public int getThemeMode() {
        return preferences.getInt(PREF_THEME_MODE, THEME_MODE_SYSTEM);
    }

    public void setThemeMode(int mode) {
        preferences.edit().putInt(PREF_THEME_MODE, mode).apply();
    }

    public boolean isAutoStartOnBoot() {
        return preferences.getBoolean(PREF_AUTO_START_ON_BOOT, false);
    }

    public void setAutoStartOnBoot(boolean autoStart) {
        preferences.edit().putBoolean(PREF_AUTO_START_ON_BOOT, autoStart).apply();
    }

    /**
     * 读取全局字体缩放系数，并夹紧到 [FONT_SCALE_MIN, FONT_SCALE_MAX] 区间，
     * 防止历史脏数据或异常值导致界面字体过大/过小。
     */
    public float getFontScale() {
        float v = preferences.getFloat(PREF_FONT_SCALE, FONT_SCALE_DEFAULT);
        if (v < FONT_SCALE_MIN) return FONT_SCALE_MIN;
        if (v > FONT_SCALE_MAX) return FONT_SCALE_MAX;
        return v;
    }

    public void setFontScale(float scale) {
        // 写入前同样夹紧，保证持久化的值始终合法
        float v = Math.max(FONT_SCALE_MIN, Math.min(FONT_SCALE_MAX, scale));
        preferences.edit().putFloat(PREF_FONT_SCALE, v).apply();
    }
}
