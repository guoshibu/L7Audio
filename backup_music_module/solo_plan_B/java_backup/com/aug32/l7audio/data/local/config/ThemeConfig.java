package com.aug32.l7audio.data.local.config;

import android.content.SharedPreferences;

/**
 * 主题与启动配置
 *
 * 职责：主题模式 + 开机自启动
 */
public class ThemeConfig {

    private static final String PREF_THEME_MODE = "theme_mode";
    private static final String PREF_AUTO_START_ON_BOOT = "auto_start_on_boot";

    /** 主题模式常量 */
    public static final int THEME_MODE_SYSTEM = 0;
    public static final int THEME_MODE_LIGHT = 1;
    public static final int THEME_MODE_DARK = 2;

    private final SharedPreferences preferences;

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
}
