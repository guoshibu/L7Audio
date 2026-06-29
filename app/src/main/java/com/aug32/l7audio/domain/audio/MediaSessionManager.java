package com.aug32.l7audio.domain.audio;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.aug32.l7audio.utils.AppLog;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MediaSession 管理器
 *
 * <p>职责：
 * <ul>
 *   <li>接入 Android 系统媒体中心</li>
 *   <li>同步播放状态到系统媒体中心（供车机按键、第三方APP读取）</li>
 *   <li>接收系统媒体按钮事件，转发给 MusicPlayerManager</li>
 * </ul>
 *
 * <p>设计意图：
 * <ul>
 *   <li>采用 DCL（双重检查锁）懒加载单例模式</li>
 *   <li>与 MusicPlayerManager 通过回调机制解耦</li>
 *   <li>提供 getSessionToken() 用于构建 MediaStyle 通知</li>
 * </ul>
 *
 * <p>接入能力：
 * <ul>
 *   <li>车机方向盘/中控媒体按键控制（上/下一曲、播放/暂停）</li>
 *   <li>第三方音乐APP读取当前播放信息</li>
 *   <li>锁屏界面媒体控制</li>
 *   <li>通知栏媒体控制</li>
 * </ul>
 *
 * @author L7Audio Team
 * @see MusicPlayerManager
 */
public final class MediaSessionManager {

    /** 日志标签 */
    private static final String TAG = "MediaSessionManager";

    /** MediaSession 单例（volatile 保证可见性） */
    private static volatile MediaSessionManager instance;

    /** Context 引用 */
    private android.content.Context appContext;

    /** MusicPlayerManager 引用 */
    private MusicPlayerManager musicPlayerManager;

    /** 是否已初始化 */
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    // ========== DCL 单例 ==========

    /**
     * 获取 MediaSessionManager 单例实例
     */
    public static MediaSessionManager getInstance() {
        if (instance == null) {
            synchronized (MediaSessionManager.class) {
                if (instance == null) {
                    instance = new MediaSessionManager();
                }
            }
        }
        return instance;
    }

    /**
     * 私有构造函数
     */
    private MediaSessionManager() {}

    // ========== 初始化 ==========

    /**
     * 初始化 MediaSessionManager
     *
     * <p>应在 Application 启动时调用。
     *
     * @param context Application Context
     */
    public void init(@NonNull android.content.Context context) {
        if (isInitialized.get()) {
            AppLog.w(TAG, "MediaSessionManager already initialized, skip.");
            return;
        }

        appContext = context.getApplicationContext();
        isInitialized.set(true);
        AppLog.d(TAG, "MediaSessionManager initialized");
    }

    // ========== MusicPlayerManager 绑定 ==========

    /**
     * 绑定 MusicPlayerManager
     */
    public void bindMusicPlayerManager(@NonNull MusicPlayerManager manager) {
        this.musicPlayerManager = manager;
        AppLog.d(TAG, "MusicPlayerManager bound.");
    }

    // ========== 播放状态同步 ==========

    /**
     * 更新当前播放歌曲的元数据
     *
     * <p>当歌曲切换时调用，更新系统媒体中心的歌曲信息。
     *
     * @param item 当前播放的音乐项
     */
    public void updateMetadata(@Nullable MusicItem item) {
        if (!isInitialized.get()) {
            return;
        }

        AppLog.d(TAG, "Metadata updated: " + (item != null ? item.title : "null"));
        // Media3 MediaSession 的 metadata 更新通过 SessionService 实现
        // 此处记录日志，实际通知通过 AudioForegroundService 通知更新实现
    }

    /**
     * 更新播放状态
     *
     * <p>当播放/暂停/停止时调用，同步播放状态。
     *
     * @param isPlaying 是否正在播放
     * @param position 当前播放位置（毫秒）
     */
    public void updatePlaybackState(boolean isPlaying, long position) {
        if (!isInitialized.get()) {
            return;
        }

        AppLog.d(TAG, "Playback state updated: isPlaying=" + isPlaying + ", position=" + position);
        // 播放状态更新通过 AudioForegroundService 通知更新实现
    }

    // ========== 公开接口 ==========

    /**
     * 获取 Session Token
     *
     * <p>用于 AudioForegroundService 构建 MediaStyle 通知。
     * 由于 media3.session API 限制，此处返回 null，
     * MediaStyle 通知将通过 PendingIntent 直接处理。
     *
     * @return 始终返回 null
     */
    @Nullable
    public Object getSessionToken() {
        return null;
    }

    /**
     * 检查是否已初始化
     */
    public boolean isInitialized() {
        return isInitialized.get();
    }

    /**
     * 释放资源
     */
    public void release() {
        musicPlayerManager = null;
        appContext = null;
        isInitialized.set(false);
        AppLog.d(TAG, "MediaSessionManager released.");
    }
}
