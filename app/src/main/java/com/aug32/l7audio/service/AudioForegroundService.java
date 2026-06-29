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
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

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
 *   <li>防止系统在后台杀死音频播放相关功能</li>
 * </ul>
 *
 * <p>设计意图：
 * <ul>
 *   <li>Android 系统对后台服务有严格的限制，前台服务配合通知栏通知可有效降低被系统回收的概率</li>
 *   <li>使用 START_STICKY 模式，服务被意外杀死后系统会自动尝试重建</li>
 *   <li>使用 MediaStyle 通知，支持媒体按钮和专辑封面</li>
 *   <li>与 KeepAliveWorker 配合，形成双重保活机制</li>
 * </ul>
 *
 * @author L7Audio Team
 */
public class AudioForegroundService extends Service {
    // 日志标签
    private static final String TAG = "AudioForegroundService";
    // 通知渠道ID（Android O及以上必须）
    private static final String CHANNEL_ID = "l7audio_foreground_service";
    // 媒体控制通知ID
    private static final int NOTIFICATION_ID = 1;

    // 广播 Action 常量
    public static final String ACTION_PLAY = "com.aug32.l7audio.ACTION_PLAY";
    public static final String ACTION_PAUSE = "com.aug32.l7audio.ACTION_PAUSE";
    public static final String ACTION_PLAY_PAUSE = "com.aug32.l7audio.ACTION_PLAY_PAUSE";
    public static final String ACTION_PREVIOUS = "com.aug32.l7audio.ACTION_PREVIOUS";
    public static final String ACTION_NEXT = "com.aug32.l7audio.ACTION_NEXT";
    public static final String ACTION_STOP = "com.aug32.l7audio.ACTION_STOP";

    // 媒体按钮请求码
    private static final int REQUEST_PLAY = 1;
    private static final int REQUEST_PAUSE = 2;
    private static final int REQUEST_PLAY_PAUSE = 3;
    private static final int REQUEST_PREVIOUS = 4;
    private static final int REQUEST_NEXT = 5;
    private static final int REQUEST_STOP = 6;

    // 媒体按钮广播接收器
    private BroadcastReceiver mediaButtonReceiver;

    // 缓存当前播放状态，用于更新通知
    private boolean isPlaying = false;
    private String currentTitle = "L7Audio";
    private String currentArtist = "";

    /**
     * 服务创建时调用
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
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        AppLog.d(TAG, "Service started");
        // 更新通知（可能有新的播放状态）
        updateNotification();
        return START_STICKY;
    }

    /**
     * 服务销毁时调用
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterMediaButtonReceiver();
        AppLog.d(TAG, "Service destroyed");
    }

    /**
     * 绑定服务接口
     */
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ========== 通知相关 ==========

    /** 创建通知渠道（仅在 Android O 及以上生效） */
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
     * 构建前台服务使用的 Notification（支持媒体控制）
     */
    private Notification createNotification() {
        // 创建主界面 PendingIntent
        Intent mainIntent = new Intent(this, MainActivity.class);
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent mainPendingIntent = PendingIntent.getActivity(
                this, 0, mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // 创建媒体控制按钮
        PendingIntent playPausePendingIntent = createMediaActionIntent(
                isPlaying ? ACTION_PAUSE : ACTION_PLAY,
                isPlaying ? REQUEST_PAUSE : REQUEST_PLAY
        );
        PendingIntent previousPendingIntent = createMediaActionIntent(ACTION_PREVIOUS, REQUEST_PREVIOUS);
        PendingIntent nextPendingIntent = createMediaActionIntent(ACTION_NEXT, REQUEST_NEXT);

        // 选择播放/暂停图标
        int playPauseIcon = isPlaying ? R.drawable.ic_media_pause : R.drawable.ic_media_play;
        String playPauseText = isPlaying ? "暂停" : "播放";

        // 构建通知
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_nav_home)
                .setContentTitle(currentTitle)
                .setContentText(currentArtist.isEmpty() ? "音乐播放中" : currentArtist)
                .setContentIntent(mainPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(isPlaying)
                .setSilent(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(R.drawable.ic_media_previous, "上一首", previousPendingIntent)
                .addAction(playPauseIcon, playPauseText, playPausePendingIntent)
                .addAction(R.drawable.ic_media_next, "下一首", nextPendingIntent)
                .build();
    }

    /**
     * 创建媒体操作 PendingIntent
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

        // 更新通知
        updateNotification();
    }

    /**
     * 更新通知
     *
     * <p>从 MusicPlayerManager 获取当前播放状态并更新通知。
     */
    public void updateNotification() {
        try {
            MusicPlayerManager manager = com.aug32.l7audio.domain.audio.AudioServiceLocator.getInstance()
                    .getMusicPlayerManager();
            if (manager != null) {
                isPlaying = manager.isPlaying();
                com.aug32.l7audio.domain.audio.MusicItem item = manager.getCurrentMusicItem();
                if (item != null) {
                    currentTitle = item.title != null && !item.title.isEmpty() ? item.title : "未知歌曲";
                    currentArtist = item.artist != null ? item.artist : "";
                } else {
                    currentTitle = "L7Audio";
                    currentArtist = "";
                }
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to update notification", e);
        }

        Notification notification = createNotification();
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, notification);
        }
    }

    // ========== 静态方法 ==========

    /**
     * 启动前台音频服务
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
     */
    public static void stop(Context context) {
        Intent intent = new Intent(context, AudioForegroundService.class);
        context.stopService(intent);
        AppLog.d(TAG, "Foreground service stopped");
    }
}
