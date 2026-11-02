package com.aug32.l7audio.service.player;

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
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

import com.aug32.l7audio.R;
import com.aug32.l7audio.domain.audio.player.MediaSessionManager;
import com.aug32.l7audio.domain.audio.player.MusicPlayerManager;
import com.aug32.l7audio.utils.AlbumArtCache;
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
    /** 本地广播 Action：更新通知（替代 startService IPC） */
    public static final String ACTION_UPDATE_NOTIFICATION = "com.aug32.l7audio.ACTION_UPDATE_NOTIFICATION";

    /** 媒体按钮请求码 */
    private static final int REQUEST_PLAY = 1;
    private static final int REQUEST_PAUSE = 2;
    private static final int REQUEST_PLAY_PAUSE = 3;
    private static final int REQUEST_PREVIOUS = 4;
    private static final int REQUEST_NEXT = 5;
    private static final int REQUEST_STOP = 6;

    /** 媒体按钮广播接收器 */
    private BroadcastReceiver mediaButtonReceiver;
    /** 本地广播接收器：通知更新 */
    private BroadcastReceiver localUpdateReceiver;

    /** 缓存当前播放状态，用于更新通知 */
    private boolean isPlaying = false;
    private String currentTitle = "L7Audio";
    private String currentArtist = "";

    /** 通知更新防抖间隔（毫秒），避免快速切换歌曲时通知栏频繁闪烁 */
    private static final long UPDATE_DEBOUNCE_MS = 200;
    /** 上次通知更新时间戳 */
    private long lastUpdateTime = 0;
    /** 主线程 Handler：用于防抖窗口后的终态补发更新 */
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    /** 是否已安排防抖窗口后的终态更新（防止重复排队） */
    private boolean trailingUpdateScheduled = false;

    /**
     * 封面加载 in-flight 去重集合：记录正在异步加载封面的文件路径。
     * 同步集合包装——通知更新可能来自多个线程，防止并发修改
     */
    private final java.util.Set<String> pendingAlbumArtLoads =
            java.util.Collections.synchronizedSet(new java.util.HashSet<>());

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
        registerLocalUpdateReceiver();
        // 服务已声明 mediaPlayback|microphone 两种类型（后台喊话需要 microphone 类型）。
        // 首装首次启动时录音权限可能尚未授予，startForeground 会抛 SecurityException；
        // 失败时降级为仅 mediaPlayback，保证音乐前台服务本身不受影响
        try {
            startForeground(NOTIFICATION_ID, createNotification());
        } catch (Exception e) {
            AppLog.w(TAG, "startForeground（含 microphone 类型）失败，降级为仅 mediaPlayback：" + e);
            try {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, createNotification(),
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } catch (Exception fallbackError) {
                AppLog.e(TAG, "startForeground 降级仍失败", fallbackError);
            }
        }
    }

    /**
     * 注册本地广播接收器（通知更新，替代 startService IPC）
     */
    private void registerLocalUpdateReceiver() {
        localUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_UPDATE_NOTIFICATION.equals(intent.getAction())) {
                    updateNotification();
                }
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_UPDATE_NOTIFICATION);
        // 用 ContextCompat.registerReceiver + RECEIVER_NOT_EXPORTED 替代已废弃的
        // LocalBroadcastManager：同样只接收本应用内广播，且兼容 API 30
        ContextCompat.registerReceiver(this, localUpdateReceiver, filter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
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
        unregisterLocalUpdateReceiver();
        AppLog.d(TAG, "Service destroyed");
    }

    /**
     * 注销本地广播接收器
     */
    private void unregisterLocalUpdateReceiver() {
        if (localUpdateReceiver != null) {
            // 与 ContextCompat.registerReceiver 配对使用
            unregisterReceiver(localUpdateReceiver);
            localUpdateReceiver = null;
        }
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

        // 从缓存获取封面（不持有成员引用）
        MusicPlayerManager manager = com.aug32.l7audio.domain.audio.AudioServiceLocator.getInstance()
                .getMusicPlayerManager();
        if (manager != null) {
            com.aug32.l7audio.domain.audio.player.MusicItem item = manager.getCurrentMusicItem();
            if (item != null && item.filePath != null) {
                Bitmap cached = AlbumArtCache.getInstance().get(item.filePath);
                if (cached != null && !cached.isRecycled()) {
                    builder.setLargeIcon(cached);
                    AppLog.d(TAG, "createNotification: setLargeIcon, width=" + cached.getWidth());
                } else {
                    AppLog.d(TAG, "createNotification: no largeIcon available, triggering async load");
                    // 异步加载封面，加载完成后更新通知
                    triggerAsyncAlbumArtLoad(item.filePath);
                }
            }
        }

        MediaStyle mediaStyle = new MediaStyle();
        mediaStyle.setShowActionsInCompactView(0, 1, 2);
        // 修复：MediaStyle 必须绑定 MediaSession token，系统才会把该通知识别为媒体通知。
        // 未绑定时锁屏/车机媒体卡片可能不显示、蓝牙与方向盘按键状态与 MediaSession 脱节
        try {
            android.media.session.MediaSession session =
                    MediaSessionManager.getInstance().getMediaSession();
            if (session != null && session.getSessionToken() != null) {
                // 项目使用平台 MediaSession，需转换为 compat Token 才能传给 androidx 的 MediaStyle
                mediaStyle.setMediaSession(
                        android.support.v4.media.session.MediaSessionCompat.Token
                                .fromToken(session.getSessionToken()));
            }
        } catch (Exception e) {
            // 绑定失败不影响通知本身显示，仅退化为普通通知样式
            AppLog.e(TAG, "Failed to bind MediaSession token to MediaStyle", e);
        }
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
                // stopForeground(false) 的等价新 API：保留通知但退出前台状态
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH);
                stopSelf();
                return;
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
        long now = System.currentTimeMillis();
        if (now - lastUpdateTime < UPDATE_DEBOUNCE_MS) {
            // 防抖窗口内：仍刷新成员状态，并安排一次延迟终态更新，
            // 避免暂停/切歌等最终状态被防抖吞掉导致通知栏停留在旧状态
            refreshPlaybackState();
            scheduleTrailingUpdate();
            return;
        }
        lastUpdateTime = now;

        AppLog.d(TAG, "updateNotification called, isPlaying=" + isPlaying);
        refreshPlaybackState();
        postNotification();
    }

    /**
     * 从 MusicPlayerManager 刷新成员状态（isPlaying/标题/艺术家）
     */
    private void refreshPlaybackState() {
        try {
            MusicPlayerManager manager = com.aug32.l7audio.domain.audio.AudioServiceLocator.getInstance()
                    .getMusicPlayerManager();
            if (manager != null) {
                AppLog.d(TAG, "updateNotification: got MusicPlayerManager, refreshing state");
                // 直接使用 MusicPlayerManager 的 isPlaying 状态
                isPlaying = manager.isPlaying();
                AppLog.d(TAG, "updateNotification: manager.isPlaying()=" + isPlaying);
                com.aug32.l7audio.domain.audio.player.MusicItem item = manager.getCurrentMusicItem();
                if (item != null) {
                    currentTitle = item.title != null && !item.title.isEmpty() ? item.title : "未知歌曲";
                    currentArtist = item.artist != null ? item.artist : "";

                    if (item.filePath != null) {
                        AppLog.d(TAG, "updateNotification: loading album art for " + item.filePath);
                        Bitmap cached = AlbumArtCache.getInstance()
                                .get(item.filePath);
                        if (cached != null && !cached.isRecycled()) {
                            // 直接用缓存的 Bitmap，不再持有成员引用
                            // NotificationBuilder 会内部复制/引用
                        } else {
                            AppLog.d(TAG, "updateNotification: no cached album art");
                        }
                    }
                } else {
                    currentTitle = "L7Audio";
                    currentArtist = "";
                }
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to update notification", e);
        }
    }

    /**
     * 防抖窗口后的终态补发：确保最终播放状态一定落到通知栏
     */
    private void scheduleTrailingUpdate() {
        if (trailingUpdateScheduled) {
            return;
        }
        trailingUpdateScheduled = true;
        mainHandler.postDelayed(() -> {
            trailingUpdateScheduled = false;
            // 清零防抖计时，让终态更新绕过防抖立即生效
            lastUpdateTime = 0;
            updateNotification();
        }, UPDATE_DEBOUNCE_MS);
    }

    /**
     * 构建并下发通知
     */
    private void postNotification() {
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
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
            AppLog.d(TAG, "Foreground service started");
        } catch (Exception e) {
            // 用 instanceof 判断而非直接 catch 具体类，避免 minSdk < 31 时
            // ForegroundServiceStartNotAllowedException 类不存在导致 ClassNotFoundException
            if (Build.VERSION.SDK_INT >= 31
                    && "ForegroundServiceStartNotAllowedException".equals(e.getClass().getSimpleName())) {
                // Android 12+ 后台启动限制：应用退后台后系统禁止启动前台服务
                // 静默降级，不启动服务，调用方需自行处理"无前台服务"场景
                AppLog.w(TAG, "后台启动前台服务被系统限制（Android 12+），跳过本次启动");
            } else if (e instanceof SecurityException) {
                // 权限不足：如未声明 FOREGROUND_SERVICE_MEDIA_PLAYBACK 或权限被撤销
                AppLog.e(TAG, "启动前台服务权限不足", e);
            } else if (e instanceof IllegalStateException) {
                // 服务状态异常：如服务正在销毁过程中被重复启动
                AppLog.e(TAG, "启动前台服务状态异常", e);
            } else {
                // 其他未知系统异常
                AppLog.e(TAG, "Failed to start foreground service", e);
            }
        }
    }

    /**
     * 触发通知更新（本地广播，零 IPC 开销）
     *
     * @param context 上下文对象
     */
    public static void notifyUpdate(Context context) {
        // 接收方是 RECEIVER_NOT_EXPORTED 的上下文注册广播，同一应用内的 sendBroadcast 即可送达
        context.sendBroadcast(new Intent(ACTION_UPDATE_NOTIFICATION));
    }

    /**
     * 异步加载专辑封面
     * <p>
     * 在后台线程加载封面，加载完成后通过本地广播通知更新通知栏。
     * 避免在主线程阻塞加载导致通知更新延迟。
     * </p>
     * <p>
     * 性能修复：①裸 new Thread 改为共享 IO 线程池（AppExecutors），避免连续切歌时
     * 并发拉起多个线程重复解码同一封面；②新增 in-flight 去重集合，同一 filePath
     * 的加载请求进行中时直接跳过后续重复请求。
     * </p>
     *
     * @param filePath 音乐文件路径
     */
    private void triggerAsyncAlbumArtLoad(String filePath) {
        // in-flight 去重：add 返回 false 说明该路径已有加载在进行，直接跳过
        if (filePath == null || !pendingAlbumArtLoads.add(filePath)) {
            return;
        }
        com.aug32.l7audio.utils.AppExecutors.getInstance().executeOnIOThread(() -> {
            try {
                MusicPlayerManager manager = com.aug32.l7audio.domain.audio.AudioServiceLocator.getInstance()
                        .getMusicPlayerManager();
                if (manager != null) {
                    com.aug32.l7audio.domain.audio.player.MusicItem item = manager.getCurrentMusicItem();
                    // 校验请求路径与当前歌曲一致：异步任务执行期间用户可能已经切歌，
                    // 若直接拿"当前歌曲"的封面写进请求路径的缓存，会同时造成
                    // ① 通知栏串图  ② AlbumArtCache 被写入错误封面（下次该路径直接命中错图）
                    if (item == null || item.filePath == null || !item.filePath.equals(filePath)) {
                        AppLog.d(TAG, "Skip async album art: current item changed, requested=" + filePath);
                        return;
                    }
                    if (item.albumArt != null) {
                        AlbumArtCache.getInstance().put(filePath, item.albumArt);
                        AppLog.d(TAG, "Async album art loaded for " + filePath);
                        notifyUpdate(this);
                    }
                }
            } catch (Exception e) {
                AppLog.e(TAG, "Failed to load album art asynchronously", e);
            } finally {
                // 无论成功失败都要移除 in-flight 标记，否则该路径后续永远无法重试
                pendingAlbumArtLoads.remove(filePath);
            }
        });
    }
}
