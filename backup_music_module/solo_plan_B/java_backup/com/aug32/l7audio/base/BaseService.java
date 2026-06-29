package com.aug32.l7audio.base;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/**
 * Service 基类，提供通用功能：
 * - 前台通知渠道创建
 * - 前台通知构建
 * - START_STICKY 返回确保被杀死后自动重建
 *
 * 目标 SDK：Android 11 (API 30)
 * 最低 SDK：Android 11 (API 30)
 */
public abstract class BaseService extends Service {

    /**
     * 前台通知渠道 ID
     * 子类需要重写返回各自的通知渠道 ID
     */
    protected abstract String getNotificationChannelId();

    /**
     * 前台通知渠道名称
     * 子类需要重写返回渠道显示名称
     */
    protected abstract String getNotificationChannelName();

    /**
     * 前台通知渠道描述
     */
    protected String getNotificationChannelDescription() {
        return "L7Audio 服务通知";
    }

    /**
     * 前台通知 ID
     * 子类需要重写返回各自的通知 ID
     */
    protected abstract int getNotificationId();

    /**
     * 通知图标资源 ID
     * 子类需要返回图标资源 ID
     */
    protected int getNotificationIconResId() {
        return android.R.drawable.ic_dialog_info;
    }

    /**
     * 通知标题
     * 子类需要返回通知标题
     */
    protected abstract String getNotificationTitle();

    /**
     * 通知内容
     * 子类需要返回通知内容文本
     */
    protected abstract String getNotificationContent();

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(getNotificationId(), buildNotification());
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * 创建通知渠道（仅 Android O 及以上生效）
     * Android 11 (API 30) 仍然需要创建通知渠道
     */
    protected void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    getNotificationChannelId(),
                    getNotificationChannelName(),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(getNotificationChannelDescription());
            channel.setSound(null, null);
            channel.setVibrationPattern(null);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * 构建前台服务通知
     *
     * @return Notification 对象
     */
    protected Notification buildNotification() {
        return new NotificationCompat.Builder(this, getNotificationChannelId())
                .setSmallIcon(getNotificationIconResId())
                .setContentTitle(getNotificationTitle())
                .setContentText(getNotificationContent())
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setSilent(true)
                .build();
    }

    /**
     * 更新前台通知内容
     *
     * @param title   新标题
     * @param content 新内容
     */
    protected void updateNotification(String title, String content) {
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            Notification notification = new NotificationCompat.Builder(this, getNotificationChannelId())
                    .setSmallIcon(getNotificationIconResId())
                    .setContentTitle(title)
                    .setContentText(content)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOngoing(true)
                    .setSilent(true)
                    .build();
            notificationManager.notify(getNotificationId(), notification);
        }
    }
}
