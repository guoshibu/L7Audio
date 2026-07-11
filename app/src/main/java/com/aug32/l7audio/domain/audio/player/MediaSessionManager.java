package com.aug32.l7audio.domain.audio.player;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;

import androidx.annotation.Nullable;

import com.aug32.l7audio.domain.audio.player.MusicItem;
import com.aug32.l7audio.utils.AlbumArtCache;
import com.aug32.l7audio.utils.AppLog;

/**
 * MediaSession 管理器
 *
 * <p>职责：
 * <ul>
 *   <li>管理 Android MediaSession 生命周期</li>
 *   <li>同步音乐元数据（歌名、艺术家、专辑、时长、封面）到系统媒体中心</li>
 *   <li>同步播放状态（播放/暂停、进度）到系统媒体中心</li>
 *   <li>接收系统媒体按键事件（播放/暂停、上一曲、下一曲）并转发给播放器</li>
 * </ul>
 *
 * <p>设计说明：
 * <ul>
 *   <li>采用 DCL 双重检查锁懒加载单例模式，确保全局唯一实例</li>
 *   <li>使用 Android 原生 MediaSession（API 21+），无需额外依赖</li>
 *   <li>通过 {@link MediaSession.Callback} 接收系统媒体按键事件，转发给 MusicPlayerManager</li>
 *   <li>元数据和播放状态更新由 MusicPlayerManager 主动调用，保持数据单向流动</li>
 *   <li>初始化采用 init(context) 方式，与 AudioServiceLocator 初始化流程一致</li>
 * </ul>
 *
 * @author L7Audio Team
 */
public class MediaSessionManager {

    /** 日志标签 */
    private static final String TAG = "MediaSessionManager";

    /** 单例实例（volatile 保证 DCL 可见性） */
    private static volatile MediaSessionManager instance;

    /** 上下文对象 */
    private Context appContext;

    /** MediaSession 实例 */
    private MediaSession mediaSession;

    /** 音乐播放器管理器引用 */
    private MusicPlayerManager musicPlayerManager;

    /** 是否已初始化 */
    private boolean isInitialized = false;

    /**
     * 私有构造函数
     */
    private MediaSessionManager() {
    }

    /**
     * 获取 MediaSessionManager 单例实例
     *
     * <p>使用 DCL 双重检查锁保证线程安全，仅在首次调用时创建实例。
     * 注意：获取实例后需调用 {@link #init(Context)} 完成初始化。
     *
     * @return MediaSessionManager 单例实例
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
     * 初始化 MediaSession
     *
     * <p>必须在第一次使用前调用，由 AudioServiceLocator 统一管理初始化时机。
     * 重复调用安全，仅首次调用生效。
     *
     * @param context 上下文对象，自动转换为 Application Context
     */
    public synchronized void init(Context context) {
        if (isInitialized || context == null) {
            return;
        }
        this.appContext = context.getApplicationContext();
        initMediaSession();
        isInitialized = true;
        AppLog.d(TAG, "MediaSessionManager initialized");
    }

    /**
     * 绑定 MusicPlayerManager
     *
     * <p>在 MediaSession.Callback 中需要引用播放器进行实际控制。
     * 由 AudioServiceLocator 在注册 Manager 时调用。
     *
     * @param manager 音乐播放器管理器实例
     */
    public synchronized void bindMusicPlayerManager(MusicPlayerManager manager) {
        this.musicPlayerManager = manager;
        AppLog.d(TAG, "MusicPlayerManager bound to MediaSessionManager");
    }

    /**
     * 初始化 MediaSession
     *
     * <p>创建 MediaSession 实例并设置回调，处理系统媒体按键事件。
     * 回调中转发到 MusicPlayerManager 进行实际播放控制。
     * 设置支持的媒体控制动作：播放、暂停、播放/暂停切换、上一曲、下一曲、停止、拖拽。
     */
    private void initMediaSession() {
        if (appContext == null) {
            AppLog.e(TAG, "Context is null, cannot initialize MediaSession");
            return;
        }

        try {
            mediaSession = new MediaSession(appContext, "L7AudioMediaSession");

            mediaSession.setCallback(new MediaSession.Callback() {
                @Override
                public void onPlay() {
                    AppLog.d(TAG, "MediaSession callback: onPlay");
                    try {
                        if (musicPlayerManager != null) {
                            musicPlayerManager.resume();
                        }
                    } catch (Exception e) {
                        AppLog.e(TAG, "Error handling play command", e);
                    }
                }

                @Override
                public void onPause() {
                    AppLog.d(TAG, "MediaSession callback: onPause");
                    try {
                        if (musicPlayerManager != null) {
                            musicPlayerManager.pause();
                        }
                    } catch (Exception e) {
                        AppLog.e(TAG, "Error handling pause command", e);
                    }
                }

                @Override
                public void onStop() {
                    AppLog.d(TAG, "MediaSession callback: onStop");
                    try {
                        if (musicPlayerManager != null) {
                            musicPlayerManager.stop();
                        }
                    } catch (Exception e) {
                        AppLog.e(TAG, "Error handling stop command", e);
                    }
                }

                @Override
                public void onSkipToNext() {
                    AppLog.d(TAG, "MediaSession callback: onSkipToNext");
                    try {
                        if (musicPlayerManager != null) {
                            musicPlayerManager.playNext();
                        }
                    } catch (Exception e) {
                        AppLog.e(TAG, "Error handling next command", e);
                    }
                }

                @Override
                public void onSkipToPrevious() {
                    AppLog.d(TAG, "MediaSession callback: onSkipToPrevious");
                    try {
                        if (musicPlayerManager != null) {
                            musicPlayerManager.playPrevious();
                        }
                    } catch (Exception e) {
                        AppLog.e(TAG, "Error handling previous command", e);
                    }
                }

                @Override
                public void onSeekTo(long pos) {
                    AppLog.d(TAG, "MediaSession callback: onSeekTo " + pos);
                    try {
                        if (musicPlayerManager != null) {
                            musicPlayerManager.seekTo(pos);
                        }
                    } catch (Exception e) {
                        AppLog.e(TAG, "Error handling seekTo command", e);
                    }
                }
            });

            PlaybackState.Builder stateBuilder = new PlaybackState.Builder();
            long actions = PlaybackState.ACTION_PLAY
                    | PlaybackState.ACTION_PAUSE
                    | PlaybackState.ACTION_PLAY_PAUSE
                    | PlaybackState.ACTION_SKIP_TO_NEXT
                    | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                    | PlaybackState.ACTION_STOP
                    | PlaybackState.ACTION_SEEK_TO;
            stateBuilder.setActions(actions);
            stateBuilder.setState(PlaybackState.STATE_NONE, 0, 1.0f);
            mediaSession.setPlaybackState(stateBuilder.build());

            mediaSession.setActive(true);

            AppLog.d(TAG, "MediaSession initialized successfully");
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to initialize MediaSession", e);
            mediaSession = null;
        }
    }

    /**
     * 更新媒体元数据
     *
     * <p>将当前播放的音乐信息同步到系统媒体中心，包括：
     * 歌名、艺术家、专辑、时长、专辑封面。
     *
     * @param item 当前播放的音乐项，为 null 时清空元数据
     */
public void updateMetadata(@Nullable MusicItem item) {
        if (mediaSession == null) {
            AppLog.w(TAG, "MediaSession not initialized, skip updateMetadata");
            return;
        }

        try {
            android.media.MediaMetadata.Builder builder = new android.media.MediaMetadata.Builder();

            if (item != null) {
                builder.putString(android.media.MediaMetadata.METADATA_KEY_TITLE, item.title);
                builder.putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, item.artist);
                builder.putString(android.media.MediaMetadata.METADATA_KEY_ALBUM, item.album);
                if (item.duration > 0) {
                    builder.putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, item.duration);
                }

                if (appContext != null && item.filePath != null) {
                    Bitmap bitmap = AlbumArtCache.getInstance()
                            .get(item.filePath);
                    if (bitmap != null) {
                        builder.putBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap);
                    }
                }

                AppLog.d(TAG, "Update metadata: " + item.title + " - " + item.artist);
            } else {
                AppLog.d(TAG, "Update metadata: null (clear)");
            }

            mediaSession.setMetadata(builder.build());
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to update metadata", e);
        }
    }

    /**
     * 更新播放状态
     *
     * <p>将当前播放状态和进度同步到系统媒体中心。
     *
     * @param isPlaying      是否正在播放
     * @param currentPosition 当前播放位置（毫秒）
     */
    public void updatePlaybackState(boolean isPlaying, long currentPosition) {
        updatePlaybackState(isPlaying, currentPosition, 0);
    }

    /**
     * 更新播放状态（含时长）
     *
     * <p>将当前播放状态、进度和总时长同步到系统媒体中心。
     *
     * @param isPlaying      是否正在播放
     * @param currentPosition 当前播放位置（毫秒）
     * @param duration       总时长（毫秒）
     */
    public void updatePlaybackState(boolean isPlaying, long currentPosition, long duration) {
        if (mediaSession == null) {
            AppLog.w(TAG, "MediaSession not initialized, skip updatePlaybackState");
            return;
        }

        try {
            PlaybackState.Builder stateBuilder = new PlaybackState.Builder();

            int state = isPlaying
                    ? PlaybackState.STATE_PLAYING
                    : PlaybackState.STATE_PAUSED;
            stateBuilder.setState(state, currentPosition, 1.0f);

            long actions = PlaybackState.ACTION_PLAY
                    | PlaybackState.ACTION_PAUSE
                    | PlaybackState.ACTION_PLAY_PAUSE
                    | PlaybackState.ACTION_SKIP_TO_NEXT
                    | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                    | PlaybackState.ACTION_STOP
                    | PlaybackState.ACTION_SEEK_TO;
            stateBuilder.setActions(actions);

            mediaSession.setPlaybackState(stateBuilder.build());

            AppLog.d(TAG, "Update playback state: isPlaying=" + isPlaying
                    + ", position=" + currentPosition + "ms, duration=" + duration + "ms");
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to update playback state", e);
        }
    }

    /**
     * 获取 MediaSession 实例
     *
     * <p>供前台服务等组件使用，用于构建 MediaStyle 通知。
     *
     * @return MediaSession 实例，可能为 null（初始化失败时）
     */
    @Nullable
    public MediaSession getMediaSession() {
        return mediaSession;
    }

    /**
     * 释放 MediaSession 资源
     *
     * <p>停用并释放 MediaSession，清空引用。
     */
    public synchronized void release() {
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
        musicPlayerManager = null;
        isInitialized = false;
        AppLog.d(TAG, "MediaSession released");
    }

}
