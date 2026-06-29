package com.aug32.l7audio.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import com.aug32.l7audio.utils.AppLog;
import com.aug32.l7audio.R;

public class AudioForegroundService extends Service {
    private static final String TAG = "AudioForegroundService";
    private static final String CHANNEL_ID = "l7audio_foreground_service";
    private static final int NOTIFICATION_ID = 1;

    /** 服务创建，初始化通知渠道并进入前台 */
    @Override
    public void onCreate() {
        super.onCreate();
        AppLog.d(TAG, "Service created");
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
    }

    /** 处理启动命令，返回 START_STICKY 以便服务被杀后自动重建 */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        AppLog.d(TAG, "Service started");
        return START_STICKY;
    }

    /** 服务销毁时的资源清理与日志 */
    @Override
    public void onDestroy() {
        super.onDestroy();
        AppLog.d(TAG, "Service destroyed");
    }

    /** 不提供绑定服务，返回 null */
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** 创建通知渠道（仅在 Android O 及以上生效） */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "L7Audio 前台服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("保持音频服务活跃");
            channel.setSound(null, null);
            channel.setVibrationPattern(null);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    /** 构建前台服务使用的 Notification */
    private Notification createNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_nav_home)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("音频服务运行中")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setSilent(true);

        return builder.build();
    }

    /** 启动前台音频服务（Android O 及以上使用 startForegroundService） */
    public static void start(Context context) {
        Intent intent = new Intent(context, AudioForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
        AppLog.d(TAG, "Foreground service started");
    }

    /** 停止前台音频服务 */
    public static void stop(Context context) {
        Intent intent = new Intent(context, AudioForegroundService.class);
        context.stopService(intent);
        AppLog.d(TAG, "Foreground service stopped");
    }
}