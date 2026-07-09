package com.aug32.l7audio.base;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.aug32.l7audio.data.local.AppConfig;

/**
 * Activity 基类，提供应用内所有 Activity 的通用基础能力。
 *
 * <p>主要职责：
 * <ul>
 *   <li>主题模式管理：支持跟随系统、浅色、深色三种主题模式切换</li>
 *   <li>配置管理：统一维护 AppConfig 实例，供子类直接使用</li>
 * </ul>
 *
 * <p>目标 SDK：Android 11 (API 30)
 * <br>最低 SDK：Android 11 (API 30)
 */
public abstract class BaseActivity extends AppCompatActivity {

    /** 应用配置实例，用于读取和管理主题、字体等全局配置 */
    protected AppConfig appConfig;

    /**
     * Activity 创建时的初始化回调。
     * <p>在此方法中完成 AppConfig 的实例化，确保子类在后续生命周期中可直接使用配置对象。
     *
     * @param savedInstanceState 保存的 Activity 状态，首次创建时为 null
     */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 在 super.onCreate 之后立即初始化配置，保证后续流程可用
        appConfig = new AppConfig(this);
    }

    /**
     * 根据配置设置主题模式（跟随系统/浅色/深色）
     *
     * @param themeMode AppConfig.THEME_MODE_SYSTEM / THEME_MODE_LIGHT / THEME_MODE_DARK
     */
    protected void applyThemeMode(int themeMode) {
        switch (themeMode) {
            case AppConfig.THEME_MODE_LIGHT:
                getDelegate().setLocalNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case AppConfig.THEME_MODE_DARK:
                getDelegate().setLocalNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case AppConfig.THEME_MODE_SYSTEM:
            default:
                getDelegate().setLocalNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

}
