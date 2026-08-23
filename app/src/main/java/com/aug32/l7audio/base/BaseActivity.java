package com.aug32.l7audio.base;

import android.content.Context;
import android.content.res.Configuration;
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

    /** 字体缩放排查专用 TAG，与 SettingsFragment 保持一致，便于 adb logcat 统一过滤 */
    private static final String FONT_SCALE_TAG = "FontScale";

    /**
     * 在 Activity 附加基础 Context 时覆写 Configuration.fontScale。
     *
     * <p>【全局字体缩放】通过覆写 fontScale，使全应用所有 sp 单位字体按用户设置的系数统一缩放。
     * 此时成员 appConfig 尚未创建（在 onCreate 中初始化），因此临时构造 AppConfig 读取持久化的缩放系数。
     *
     * @param newBase 系统传入的基础 Context
     */
    @Override
    protected void attachBaseContext(Context newBase) {
        // 读取用户持久化的字体缩放系数（临时构造，因 appConfig 尚未初始化）
        float fontScale = new AppConfig(newBase).getFontScale();
        // 【字体缩放排查】这是全局字体真正生效的入口。recreate() 后会重新走到这里，
        // 若这里读到的 fontScale 是新值，说明持久化+重建链路正常；界面若仍没变则问题在渲染/缓存层。
        android.util.Log.d(FONT_SCALE_TAG, "attachBaseContext: 读取 fontScale=" + fontScale
                + "，应用于 " + getClass().getSimpleName());
        // 基于原 Configuration 拷贝并覆写 fontScale，生成缩放后的 Context 供整个 Activity 使用
        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        config.fontScale = fontScale;
        Context wrapped = newBase.createConfigurationContext(config);
        super.attachBaseContext(wrapped);
    }

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
