package com.aug32.l7audio.base;

import android.content.Context;
import android.os.Bundle;
import android.view.WindowInsetsController;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;

import com.aug32.l7audio.R;
import com.aug32.l7audio.data.local.AppConfig;

/**
 * Activity 基类，提供通用功能：
 * - 主题模式管理
 * - 状态栏样式设置
 * - 字体缩放调整
 *
 * 目标 SDK：Android 11 (API 30)
 * 最低 SDK：Android 11 (API 30)
 */
public abstract class BaseActivity extends AppCompatActivity {

    protected AppConfig appConfig;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
     * 使用 WindowInsetsControllerCompat 替代已过时的 setSystemUiVisibility()
     *
     * @param statusBarColor 状态栏颜色资源 ID
     * @param isLightIcon 是否使用浅色图标（true=深色背景用浅色图标）
     */
    protected void setupStatusBar(int statusBarColor, boolean isLightIcon) {
        // 设置状态栏颜色
        getWindow().setStatusBarColor(ContextCompat.getColor(this, statusBarColor));

        // 使用 WindowInsetsControllerCompat 设置状态栏图标颜色（Android 11+）
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            WindowInsetsController windowInsetsController = getWindow().getInsetsController();
            if (windowInsetsController != null) {
                if (isLightIcon) {
                    // 浅色图标（深色背景）
                    windowInsetsController.setSystemBarsAppearance(
                            0,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    );
                } else {
                    // 深色图标（浅色背景）
                    windowInsetsController.setSystemBarsAppearance(
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    );
                }
            }
        }
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
