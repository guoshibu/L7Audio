package com.aug32.l7audio.domain.audio;

import android.content.Context;

import com.aug32.l7audio.domain.audio.micoutput.AudioOutputManager;
import com.aug32.l7audio.domain.audio.micoutput.MicrophoneManager;
import com.aug32.l7audio.domain.audio.player.MediaSessionManager;
import com.aug32.l7audio.domain.audio.player.MusicPlayerManager;
import com.aug32.l7audio.domain.audio.tts.TTSManager;
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
 * 3. 通过 getXXXManager() 获取 Manager 实例
 */
public class AudioServiceLocator {

    private static final String TAG = "AudioServiceLocator";

    // 单例实例（volatile 保证多线程可见性）
    private static volatile AudioServiceLocator instance;

    // Application Context（避免持有 Activity 导致内存泄漏）
    private Context appContext;

    // Manager 实例（volatile 保证可见性）
    private volatile AudioOutputManager audioOutputManager;
    private volatile MicrophoneManager microphoneManager;
    private volatile TTSManager ttsManager;
    private volatile MusicPlayerManager musicPlayerManager;
    private volatile AudioFocusManager audioFocusManager;


    private AudioServiceLocator() {
        // 私有构造函数
    }

    /**
     * 获取服务定位器单例实例
     * <p>
     * 使用双重检查锁（DCL）实现线程安全的懒加载单例模式。
     * volatile 关键字防止指令重排序，确保多线程环境下 instance 的可见性。
     * </p>
     *
     * @return AudioServiceLocator 单例实例
     */
    public static AudioServiceLocator getInstance() {
        if (instance == null) {
            // 第一次检查：无锁快速判断，避免每次都进入同步块
            synchronized (AudioServiceLocator.class) {
                if (instance == null) {
                    // 第二次检查：持锁后再次判断，防止多线程并发创建
                    instance = new AudioServiceLocator();
                }
            }
        }
        return instance;
    }

    /**
     * 初始化 Application Context
     * <p>
     * 必须在第一次获取 Manager 前调用。仅在首次调用时生效，后续调用不会覆盖已有 Context。
     * 使用 Application Context 而非 Activity Context，避免内存泄漏。
     * </p>
     *
     * @param context 上下文对象，会自动转换为 Application Context
     */
    public synchronized void init(Context context) {
        if (appContext == null && context != null) {
            this.appContext = context.getApplicationContext();
            // 初始化 MediaSessionManager
            MediaSessionManager.getInstance().init(this.appContext);
            AppLog.i(TAG, "MediaSessionManager initialized");
        }
    }

    /**
     * 注册 Manager 实例
     * <p>
     * 在 MainActivity 初始化完成后调用，用于替换懒加载创建的实例，
     * 确保 ServiceLocator 与 MainActivity 共享同一批 Manager 实例。
     * </p>
     * <p>
     * 注意：MusicPlayerManager 是全局单例，不会被覆盖，保持原有实例不变，
     * 以支持后台音乐播放和下次进入时的状态恢复。
     * </p>
     *
     * @param audioOutputManager   音频输出管理器实例
     * @param microphoneManager    麦克风管理器实例
     * @param ttsManager           TTS语音播报管理器实例
     * @param musicPlayerManager   音乐播放器管理器实例（仅在当前为null时设置）
     * @param audioFocusManager    音频焦点管理器实例
     */
    public synchronized void registerManagers(
            AudioOutputManager audioOutputManager,
            MicrophoneManager microphoneManager,
            TTSManager ttsManager,
            MusicPlayerManager musicPlayerManager,
            AudioFocusManager audioFocusManager) {

        // 每个参数加 null 守卫，防止误传 null 覆盖已有实例
        if (audioOutputManager != null) this.audioOutputManager = audioOutputManager;
        if (microphoneManager != null) {
            // 修复（孤儿录音线程）：覆盖旧 MicrophoneManager 前必须停止其录音。
            // 旧实例若在喊话中（录音线程存活），被覆盖后不再有任何外部引用能触发
            // stop()，录音线程成为孤儿持续占用麦克风并外放；而 MicOutputController
            // 每次都从本定位器现取实例，后续 toggle 拿到的是新实例，其 stop() 因
            // 新实例从未启动而直接返回，旧录音永远无法停止 → 麦克风永久占用。
            // 注：这会导致主题切换/字体缩放触发 Activity 重建时正在进行的喊话被打断，
            // 属于可接受降级（相比麦克风永久占用）。
            if (this.microphoneManager != null && this.microphoneManager != microphoneManager) {
                try {
                    this.microphoneManager.stop();
                    AppLog.d(TAG, "Stopped previous MicrophoneManager before registering new one");
                } catch (Exception e) {
                    // 停止失败不阻塞新实例注册，仅记录日志
                    AppLog.e(TAG, "Failed to stop previous MicrophoneManager", e);
                }
            }
            this.microphoneManager = microphoneManager;
        }
        if (ttsManager != null) {
            // 修复：覆盖旧 TTSManager 前必须 shutdown 旧的 TextToSpeech 引擎连接。
            // TextToSpeech 是重量级 binder 资源且全工程原本无任何 shutdown 调用点，
            // 字体缩放/主题切换导致 Activity 重建并重注册时，旧引擎连接只能等 GC 兜底，
            // 反复重建会持续堆积引擎连接并造成新旧实例状态割裂
            if (this.ttsManager != null && this.ttsManager != ttsManager) {
                try {
                    this.ttsManager.shutdown();
                    AppLog.d(TAG, "Shut down previous TTSManager before registering new one");
                } catch (Exception e) {
                    // 释放失败不阻塞新实例注册，仅记录日志
                    AppLog.e(TAG, "Failed to shutdown previous TTSManager", e);
                }
            }
            this.ttsManager = ttsManager;
        }
        if (audioFocusManager != null) this.audioFocusManager = audioFocusManager;

        if (musicPlayerManager != null && this.musicPlayerManager == null) {
            this.musicPlayerManager = musicPlayerManager;
        }

        // 绑定 MusicPlayerManager 到 MediaSessionManager
        if (this.musicPlayerManager != null) {
            MediaSessionManager.getInstance().bindMusicPlayerManager(this.musicPlayerManager);
        }


        AppLog.d(TAG, "Audio managers registered (MusicPlayerManager kept as singleton)");
    }

    /**
     * 获取音频输出管理器
     * <p>
     * 采用双重检查锁实现懒加载，首次调用时创建实例。
     * </p>
     *
     * @return AudioOutputManager 实例，若未初始化 context 则返回 null
     */
    public AudioOutputManager getAudioOutputManager() {
        if (audioOutputManager == null) {
            synchronized (this) {
                // 双重检查：持锁后再次判断，防止多线程并发创建
                if (audioOutputManager == null && appContext != null) {
                    audioOutputManager = new AudioOutputManager(appContext);
                    AppLog.d(TAG, "Lazy-created AudioOutputManager");
                }
            }
        }
        return audioOutputManager;
    }

    /**
     * 获取麦克风管理器
     * <p>
     * 采用双重检查锁实现懒加载，首次调用时创建实例。
     * 依赖 AudioOutputManager，会自动触发 AudioOutputManager 的懒加载。
     * </p>
     *
     * @return MicrophoneManager 实例，若未初始化 context 则返回 null
     */
    public MicrophoneManager getMicrophoneManager() {
        if (microphoneManager == null) {
            synchronized (this) {
                // 双重检查：持锁后再次判断，防止多线程并发创建
                if (microphoneManager == null && appContext != null) {
                    microphoneManager = new MicrophoneManager(appContext, getAudioOutputManager());
                    AppLog.d(TAG, "Lazy-created MicrophoneManager");
                }
            }
        }
        return microphoneManager;
    }

    /**
     * 获取 TTS 语音播报管理器
     * <p>
     * 采用双重检查锁实现懒加载，首次调用时创建实例。
     * 依赖 AudioOutputManager，会自动触发 AudioOutputManager 的懒加载。
     * </p>
     *
     * @return TTSManager 实例，若未初始化 context 则返回 null
     */
    public TTSManager getTTSManager() {
        if (ttsManager == null) {
            synchronized (this) {
                // 双重检查：持锁后再次判断，防止多线程并发创建
                if (ttsManager == null && appContext != null) {
                    ttsManager = new TTSManager(appContext, getAudioOutputManager());
                    AppLog.d(TAG, "Lazy-created TTSManager");
                }
            }
        }
        return ttsManager;
    }

    /**
     * 获取音乐播放器管理器
     * <p>
     * 采用双重检查锁实现懒加载，首次调用时创建实例。
     * MusicPlayerManager 是全局单例，MainActivity 销毁后仍保留。
     * </p>
     *
     * @return MusicPlayerManager 实例，若未初始化 context 则返回 null
     */
    public MusicPlayerManager getMusicPlayerManager() {
        if (musicPlayerManager == null) {
            synchronized (this) {
                // 双重检查：持锁后再次判断，防止多线程并发创建
                if (musicPlayerManager == null && appContext != null) {
                    musicPlayerManager = new MusicPlayerManager(appContext);
                    AppLog.d(TAG, "Lazy-created MusicPlayerManager");
                }
            }
        }
        return musicPlayerManager;
    }

    /**
     * 获取音频焦点管理器
     * <p>
     * 采用双重检查锁实现懒加载，首次调用时创建实例。
     * </p>
     *
     * @return AudioFocusManager 实例，若未初始化 context 则返回 null
     */
    public AudioFocusManager getAudioFocusManager() {
        if (audioFocusManager == null) {
            synchronized (this) {
                // 双重检查：持锁后再次判断，防止多线程并发创建
                if (audioFocusManager == null && appContext != null) {
                    audioFocusManager = AudioFocusManager.from(appContext);
                    AppLog.d(TAG, "Lazy-created AudioFocusManager");
                }
            }
        }
        return audioFocusManager;
    }
}
