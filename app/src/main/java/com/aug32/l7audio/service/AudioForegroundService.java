package com.aug32.l7audio.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

import com.aug32.l7audio.R;
import com.aug32.l7audio.domain.audio.MediaSessionManager;
import com.aug32.l7audio.domain.audio.MusicPlayerManager;
import com.aug32.l7audio.ui.activity.MainActivity;
import com.aug32.l7audio.utils.AppLog;

/**
 * 音频前台服务
 *
 * <p>职责说明：
 * <ul>
 *   <li>以 Android 前台服务（Foreground Service）的形式持续运行</li>
 *   <li>通过 MediaStyle 通知栏通知，提升应用进程优先级</li>
 *   <li>提供媒体控制按钮（上/下一曲、播放/暂停）</li>
 *   <li>绑定 MediaSession，让系统统一管理媒体按键事件</li>
 *   <li>防止系统在后台杀死音频播放相关功能</li>
 * </ul>
 *
 * <p>设计意图：
 * <ul>
 *   <li>Android 系统对后台服务有严格的限制，前台服务配合通知栏通知可有效降低被系统回收的概率</li>
 *   <li>使用 START_STICKY 模式，服务被意外杀死后系统会自动尝试重建</li>
 *   <li>使用 MediaStyle 通知 + MediaSession，支持媒体按钮和专辑封面，与系统媒体中心联动</li>
 *   <li>与 KeepAliveWorker 配合，形成双重保活机制</li>
 * </ul>
 *
 * @author L7Audio Team
 */
public class AudioForegroundService extends Service {
    /** 日志标签 */
    private static final String TAG = "AudioForegroundService";
    /** 通知渠道ID（Android O及以上必须） */
    private static final String CHANNEL_ID = "l7audio_foreground_service";
    /** 媒体控制通知ID */
    private static final int NOTIFICATION_ID = 1;

    /** 广播 Action 常量 */
    public static final String ACTION_PLAY = "com.aug32.l7audio.ACTION_PLAY";
    public static final String ACTION_PAUSE = "com.aug32.l7audio.ACTION_PAUSE";
    public static final String ACTION_PLAY_PAUSE = "com.aug32.l7audio.ACTION_PLAY_PAUSE";
    public static final String ACTION_PREVIOUS = "com.aug32.l7audio.ACTION_PREVIOUS";
    public static final String ACTION_NEXT = "com.aug32.l7audio.ACTION_NEXT";
    public static final String ACTION_STOP = "com.aug32.l7audio.ACTION_STOP";

    /** 媒体按钮请求码 */
    private static final int REQUEST_PLAY = 1;
    private static final int REQUEST_PAUSE = 2;
    private static final int REQUEST_PLAY_PAUSE = 3;
    private static final int REQUEST_PREVIOUS = 4;
    private static final int REQUEST_NEXT = 5;
    private static final int REQUEST_STOP = 6;

    /** 媒体按钮广播接收器 */
    private BroadcastReceiver mediaButtonReceiver;

    /** 缓存当前播放状态，用于更新通知 */
    private boolean isPlaying = false;
    private String currentTitle = "L7Audio";
    private String currentArtist = "";
    private Bitmap currentAlbumArt = null;

    /**
     * 服务创建时调用
     *
     * <p>初始化通知渠道，注册媒体按钮广播接收器，启动前台服务。
     */
    @Override
    public void onCreate() {
        super.onCreate();
        AppLog.d(TAG, "Service created");
        createNotificationChannel();
        registerMediaButtonReceiver();
        startForeground(NOTIFICATION_ID, createNotification());
    }

    /**
     * 处理服务启动命令
     *
     * @param intent  启动意图
     * @param flags   启动标志
     * @param startId 启动ID
     * @return START_STICKY，服务被杀死后自动重建
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        AppLog.d(TAG, "Service started");
        if (intent != null && intent.getAction() != null) {
            handleMediaAction(intent.getAction());
        } else {
            updateNotification();
        }
        return START_STICKY;
    }

    /**
     * 服务销毁时调用
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterMediaButtonReceiver();
        if (currentAlbumArt != null && !currentAlbumArt.isRecycled()) {
            currentAlbumArt.recycle();
            currentAlbumArt = null;
        }
        AppLog.d(TAG, "Service destroyed");
    }

    /**
     * 绑定服务接口
     *
     * @param intent 绑定意图
     * @return null，不支持绑定
     */
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ==================== 通知相关 ====================

    /**
     * 创建通知渠道（仅在 Android O 及以上生效）
     *
     * <p>设置低重要性，避免发出提示音和震动，仅在通知栏显示。
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "L7Audio 音乐播放",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("音乐播放控制");
            channel.setSound(null, null);
            channel.setVibrationPattern(null);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * 构建前台服务使用的 Notification（MediaStyle 媒体通知）
     *
     * <p>使用 MediaStyle 样式，系统原生媒体通知布局，
     * 支持车机、蓝牙耳机等外设的媒体按键，封面显示在右侧。
     *
     * @return 构建好的 Notification 实例
     */
    private Notification createNotification() {
        Intent mainIntent = new Intent(this, MainActivity.class);
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent mainPendingIntent = PendingIntent.getActivity(
                this, 0, mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        PendingIntent playPausePendingIntent = createMediaActionIntent(
                isPlaying ? ACTION_PAUSE : ACTION_PLAY,
                isPlaying ? REQUEST_PAUSE : REQUEST_PLAY
        );
        PendingIntent previousPendingIntent = createMediaActionIntent(ACTION_PREVIOUS, REQUEST_PREVIOUS);
        PendingIntent nextPendingIntent = createMediaActionIntent(ACTION_NEXT, REQUEST_NEXT);

        int playPauseIcon = isPlaying ? R.drawable.ic_media_pause : R.drawable.ic_media_play;
        String playPauseText = isPlaying ? "暂停" : "播放";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_nav_home)
                .setContentTitle(currentTitle.isEmpty() ? "L7Audio" : currentTitle)
                .setContentText(currentArtist.isEmpty() ? "音乐播放中" : currentArtist)
                .setContentIntent(mainPendingIntent)
                .addAction(R.drawable.ic_media_previous, "上一首", previousPendingIntent)
                .addAction(playPauseIcon, playPauseText, playPausePendingIntent)
                .addAction(R.drawable.ic_media_next, "下一首", nextPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(isPlaying)
                .setSilent(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        if (currentAlbumArt != null && !currentAlbumArt.isRecycled()) {
            builder.setLargeIcon(currentAlbumArt);
            AppLog.d(TAG, "createNotification: setLargeIcon, width=" + currentAlbumArt.getWidth());
        } else {
            AppLog.d(TAG, "createNotification: no largeIcon available");
        }

        MediaStyle mediaStyle = new MediaStyle();
        mediaStyle.setShowActionsInCompactView(0, 1, 2);
        builder.setStyle(mediaStyle);

        return builder.build();
    }

    /**
     * 创建媒体操作 PendingIntent
     *
     * @param action      广播 Action
     * @param requestCode 请求码（区分不同 PendingIntent）
     * @return PendingIntent 实例
     */
    private PendingIntent createMediaActionIntent(String action, int requestCode) {
        Intent intent = new Intent(this, AudioForegroundService.class);
        intent.setAction(action);
        return PendingIntent.getService(
                this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    /**
     * 注册媒体按钮广播接收器
     *
     * <p>接收通知栏媒体按钮点击事件，转发给 MusicPlayerManager 处理。
     * 同时注册在 MediaSession.Callback 中，由系统统一派发按键事件。
     */
    private void registerMediaButtonReceiver() {
        mediaButtonReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || intent.getAction() == null) return;
                handleMediaAction(intent.getAction());
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PLAY);
        filter.addAction(ACTION_PAUSE);
        filter.addAction(ACTION_PLAY_PAUSE);
        filter.addAction(ACTION_PREVIOUS);
        filter.addAction(ACTION_NEXT);
        filter.addAction(ACTION_STOP);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mediaButtonReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mediaButtonReceiver, filter);
        }
    }

    /**
     * 注销媒体按钮广播接收器
     */
    private void unregisterMediaButtonReceiver() {
        if (mediaButtonReceiver != null) {
            try {
                unregisterReceiver(mediaButtonReceiver);
            } catch (Exception e) {
                AppLog.e(TAG, "Failed to unregister receiver", e);
            }
            mediaButtonReceiver = null;
        }
    }

    /**
     * 处理媒体按钮动作
     *
     * <p>将通知栏按钮点击事件转发给 MusicPlayerManager，
     * 处理完成后更新通知显示状态。
     *
     * @param action 媒体动作 Action
     */
    private void handleMediaAction(String action) {
        AppLog.d(TAG, "handleMediaAction: " + action);
        MusicPlayerManager manager = com.aug32.l7audio.domain.audio.AudioServiceLocator.getInstance()
                .getMusicPlayerManager();
        if (manager == null) {
            AppLog.w(TAG, "MusicPlayerManager is null, skip action");
            return;
        }

        switch (action) {
            case ACTION_PLAY:
                manager.resume();
                break;
            case ACTION_PAUSE:
                manager.pause();
                break;
            case ACTION_PLAY_PAUSE:
                manager.togglePlayPause();
                break;
            case ACTION_PREVIOUS:
                manager.playPrevious();
                break;
            case ACTION_NEXT:
                manager.playNext();
                break;
            case ACTION_STOP:
                manager.stop();
                break;
        }

        updateNotification();
    }

    /**
     * 更新通知
     *
     * <p>从 MusicPlayerManager 获取当前播放状态并更新通知。
     * 包括播放/暂停状态、歌曲名、艺术家、专辑封面。
     */
    public void updateNotification() {
        AppLog.d(TAG, "updateNotification called, isPlaying=" + isPlaying);
        try {
            MusicPlayerManager manager = com.aug32.l7audio.domain.audio.AudioServiceLocator.getInstance()
                    .getMusicPlayerManager();
            if (manager != null) {
                AppLog.d(TAG, "updateNotification: got MusicPlayerManager, refreshing state");
                // 直接使用 MusicPlayerManager 的 isPlaying 状态
                isPlaying = manager.isPlaying();
                AppLog.d(TAG, "updateNotification: manager.isPlaying()=" + isPlaying);
                com.aug32.l7audio.domain.audio.MusicItem item = manager.getCurrentMusicItem();
                if (item != null) {
                    currentTitle = item.title != null && !item.title.isEmpty() ? item.title : "未知歌曲";
                    currentArtist = item.artist != null ? item.artist : "";

                    if (item.albumArt != null && item.albumArt.length > 0) {
                        AppLog.d(TAG, "updateNotification: albumArt found, length=" + item.albumArt.length);
                        try {
                            if (currentAlbumArt != null && !currentAlbumArt.isRecycled()) {
                                currentAlbumArt.recycle();
                            }
                            currentAlbumArt = BitmapFactory.decodeByteArray(
                                    item.albumArt, 0, item.albumArt.length);
                            AppLog.d(TAG, "updateNotification: albumArt decoded, width=" + 
                                    (currentAlbumArt != null ? currentAlbumArt.getWidth() : "null"));
                        } catch (Exception e) {
                            AppLog.w(TAG, "Failed to decode album art: " + e.getMessage());
                            currentAlbumArt = null;
                        }
                    } else {
                        AppLog.d(TAG, "updateNotification: no albumArt available");
                        if (currentAlbumArt != null && !currentAlbumArt.isRecycled()) {
                            currentAlbumArt.recycle();
                        }
                        currentAlbumArt = null;
                    }
                } else {
                    currentTitle = "L7Audio";
                    currentArtist = "";
                    if (currentAlbumArt != null && !currentAlbumArt.isRecycled()) {
                        currentAlbumArt.recycle();
                    }
                    currentAlbumArt = null;
                }
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to update notification", e);
        }

        Notification notification = createNotification();
        AppLog.d(TAG, "updateNotification: notification created, title=" + currentTitle);
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, notification);
            AppLog.d(TAG, "updateNotification: notification shown, isPlaying=" + isPlaying);
        }
    }

    // ==================== 静态方法 ====================

    /**
     * 启动前台音频服务
     *
     * @param context 上下文对象
     */
    public static void start(Context context) {
        Intent intent = new Intent(context, AudioForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
        AppLog.d(TAG, "Foreground service started");
    }

    /**
     * 停止前台音频服务
     *
     * @param context 上下文对象
     */
    public static void stop(Context context) {
        Intent intent = new Intent(context, AudioForegroundService.class);
        context.stopService(intent);
        AppLog.d(TAG, "Foreground service stopped");
    }

    /**
     * 触发通知更新
     *
     * <p>用于外部调用，通知前台服务更新通知栏状态。
     * 如果服务尚未启动，则先启动服务。
     *
     * @param context 上下文对象
     */
    public static void notifyUpdate(Context context) {
        Intent intent = new Intent(context, AudioForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }
}
