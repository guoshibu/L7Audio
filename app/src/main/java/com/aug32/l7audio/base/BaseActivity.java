package com.aug32.l7audio.base;

import android.content.Context;
import android.os.Bundle;
import android.view.WindowInsetsController;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.aug32.l7audio.data.local.AppConfig;

/**
 * Activity 基类，提供应用内所有 Activity 的通用基础能力。
 *
 * <p>主要职责：
 * <ul>
 *   <li>主题模式管理：支持跟随系统、浅色、深色三种主题模式切换</li>
 *   <li>状态栏样式设置：统一处理状态栏颜色与图标明暗适配</li>
 *   <li>字体缩放调整：基于 Configuration 实现全局字体大小控制</li>
 *   <li>配置管理：统一维护 AppConfig 实例，供子类直接使用</li>
 * </ul>
 *
 * <p>设计意图：
 * 抽取所有 Activity 共有的 UI 初始化逻辑，避免重复代码，
 * 确保应用内各页面在主题、状态栏、字体等视觉表现上保持一致。
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

    /**
     * 设置状态栏颜色和图标主题（兼容 Android 11+）
     * <p><b>已弃用</b>：状态栏完全交由车机系统管理，本应用不再干预。
     *
     * @param statusBarColor 状态栏颜色资源 ID（已忽略）
     * @param isLightIcon 是否使用浅色图标（已忽略）
     */
    protected void setupStatusBar(int statusBarColor, boolean isLightIcon) {
        // 状态栏交由车机系统管理，不调用 setStatusBarColor/setDecorFitsSystemWindows/WindowInsetsController
    }

    /**
     * 根据当前主题模式自动设置状态栏图标颜色
     *
     * @param statusBarColor 状态栏颜色资源 ID
     */
    protected void setupStatusBarWithTheme(int statusBarColor) {
        boolean isDarkTheme = isDarkTheme();
        setupStatusBar(statusBarColor, !isDarkTheme);
    }

    /**
     * 判断当前是否为深色主题
     *
     * @return true=深色主题，false=浅色主题
     */
    protected boolean isDarkTheme() {
        int themeMode = appConfig != null ? appConfig.getThemeMode() : AppConfig.THEME_MODE_SYSTEM;
        if (themeMode == AppConfig.THEME_MODE_DARK) {
            return true;
        } else if (themeMode == AppConfig.THEME_MODE_LIGHT) {
            return false;
        } else {
            // 跟随系统
            int nightModeFlags = getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            return nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        }
    }

    /**
     * 调整字体缩放比例（兼容 Android 11+）
     * 使用 Context.createConfigurationContext() 替代已过时的 Resources.updateConfiguration()
     *
     * @param scale 缩放比例，1.0f 为默认大小
     * @return 应用了缩放的新 Context
     */
    protected Context applyFontScale(float scale) {
        android.content.res.Configuration configuration = getResources().getConfiguration();
        configuration.fontScale = scale;

        // 使用 createConfigurationContext 替代已过时的 updateConfiguration
        return createConfigurationContext(configuration);
    }

    /**
     * 获取应用配置实例
     *
     * @return AppConfig 实例
     */
    protected AppConfig getAppConfig() {
        return appConfig;
    }
}
