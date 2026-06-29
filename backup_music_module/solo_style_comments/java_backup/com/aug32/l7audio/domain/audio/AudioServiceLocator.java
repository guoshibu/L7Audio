package com.aug32.l7audio.domain.audio;

import android.content.Context;

import com.aug32.l7audio.utils.AppLog;

/**
 * Audio 组件服务定位器
 *
 * 职责：统一管理 Audio 相关 Manager 实例的生命周期
 * - 懒加载创建各 Manager 实例，保证全局单例
 * - 在 MainActivity 中注册外部 Manager 实例（用于共享）
 * - 为 Fragment、Service 等组件提供 Manager 访问
 * - 避免静态单例 MainActivity.getInstance() 的内存泄漏风险
 *
 * 使用方式：
 * 1. 任意组件调用 getInstance().init(context) 初始化
 * 2. 调用 getXXXManager() 获取 Manager 实例（自动懒加载）
 * 3. MainActivity 销毁时调用 unregisterManagers() 释放资源
 */
public class AudioServiceLocator {

    private static final String TAG = "AudioServiceLocator";

    // 单例
    private static volatile AudioServiceLocator instance;

    // Application Context
    private Context appContext;

    // Manager 实例（volatile 保证可见性）
    private volatile AudioOutputManager audioOutputManager;
    private volatile MicrophoneManager microphoneManager;
    private volatile TTSManager ttsManager;
    private volatile MusicPlayerManager musicPlayerManager;
    private volatile AudioFocusManager audioFocusManager;

    private volatile boolean isRegistered = false;

    private AudioServiceLocator() {
        // 私有构造函数
    }

    public static AudioServiceLocator getInstance() {
        if (instance == null) {
            synchronized (AudioServiceLocator.class) {
                if (instance == null) {
                    instance = new AudioServiceLocator();
                }
            }
        }
        return instance;
    }

    /**
     * 初始化 Application Context（必须在第一次获取 Manager 前调用）
     */
    public synchronized void init(Context context) {
        if (appContext == null && context != null) {
            this.appContext = context.getApplicationContext();
        }
    }

    /**
     * 注册 Manager 实例（在 MainActivity 初始化完成后调用）
     * 用于替换懒加载创建的实例，确保与 MainActivity 共享同一实例
     * 注意：MusicPlayerManager 是全局单例，不会被覆盖，保持原有实例
     */
    public synchronized void registerManagers(
            AudioOutputManager audioOutputManager,
            MicrophoneManager microphoneManager,
            TTSManager ttsManager,
            MusicPlayerManager musicPlayerManager,
            AudioFocusManager audioFocusManager) {

        this.audioOutputManager = audioOutputManager;
        this.microphoneManager = microphoneManager;
        this.ttsManager = ttsManager;
        this.audioFocusManager = audioFocusManager;

        if (this.musicPlayerManager == null) {
            this.musicPlayerManager = musicPlayerManager;
        }

        this.isRegistered = true;

        AppLog.d(TAG, "Audio managers registered (MusicPlayerManager kept as singleton)");
    }

    /**
     * 注销 Manager 实例（在 MainActivity 销毁时调用）
     * 注意：MusicPlayerManager 是全局单例，会保留，供后台播放和下次进入使用
     */
    public synchronized void unregisterManagers() {
        this.audioOutputManager = null;
        this.microphoneManager = null;
        this.ttsManager = null;
        this.audioFocusManager = null;
        this.isRegistered = false;
        AppLog.d(TAG, "Audio managers unregistered (MusicPlayerManager kept as singleton)");
    }

    /**
     * 检查是否已由 MainActivity 注册
     */
    public boolean isRegistered() {
        return isRegistered;
    }

    /**
     * 获取音频输出管理器（懒加载）
     */
    public AudioOutputManager getAudioOutputManager() {
        if (audioOutputManager == null) {
            synchronized (this) {
                if (audioOutputManager == null && appContext != null) {
                    audioOutputManager = new AudioOutputManager(appContext);
                    AppLog.d(TAG, "Lazy-created AudioOutputManager");
                }
            }
        }
        return audioOutputManager;
    }

    /**
     * 获取麦克风管理器（懒加载）
     */
    public MicrophoneManager getMicrophoneManager() {
        if (microphoneManager == null) {
            synchronized (this) {
                if (microphoneManager == null && appContext != null) {
                    microphoneManager = new MicrophoneManager(appContext, getAudioOutputManager());
                    AppLog.d(TAG, "Lazy-created MicrophoneManager");
                }
            }
        }
        return microphoneManager;
    }

    /**
     * 获取 TTS 管理器（懒加载）
     */
    public TTSManager getTTSManager() {
        if (ttsManager == null) {
            synchronized (this) {
                if (ttsManager == null && appContext != null) {
                    ttsManager = new TTSManager(appContext, getAudioOutputManager());
                    AppLog.d(TAG, "Lazy-created TTSManager");
                }
            }
        }
        return ttsManager;
    }

    /**
     * 获取音乐播放器管理器（懒加载）
     */
    public MusicPlayerManager getMusicPlayerManager() {
        if (musicPlayerManager == null) {
            synchronized (this) {
                if (musicPlayerManager == null && appContext != null) {
                    musicPlayerManager = new MusicPlayerManager(appContext);
                    AppLog.d(TAG, "Lazy-created MusicPlayerManager");
                }
            }
        }
        return musicPlayerManager;
    }

    /**
     * 获取音频焦点管理器（懒加载）
     */
    public AudioFocusManager getAudioFocusManager() {
        if (audioFocusManager == null) {
            synchronized (this) {
                if (audioFocusManager == null && appContext != null) {
                    audioFocusManager = AudioFocusManager.from(appContext);
                    AppLog.d(TAG, "Lazy-created AudioFocusManager");
                }
            }
        }
        return audioFocusManager;
    }
}
