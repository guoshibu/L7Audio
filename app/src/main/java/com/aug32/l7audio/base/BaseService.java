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
 * Service 基类，提供应用内所有前台 Service 的通用基础能力。
 *
 * <p>主要职责：
 * <ul>
 *   <li>通知渠道管理：统一创建和管理前台服务通知渠道</li>
 *   <li>前台服务支持：封装 startForeground 逻辑，子类只需配置通知参数</li>
 *   <li>自动重建：返回 START_STICKY 确保服务被系统杀死后自动重建</li>
 *   <li>通知更新：提供运行时更新前台通知内容的方法</li>
 * </ul>
 *
 * <p>设计意图：
 * 抽取所有前台 Service 共有的通知渠道创建、前台服务启动逻辑，
 * 子类通过重写抽象方法提供各自的通知 ID、标题、内容等差异化配置。
 * 使用低优先级通知（IMPORTANCE_LOW）避免频繁打扰用户，同时保证服务在前台运行。
 *
 * <p>目标 SDK：Android 11 (API 30)
 * <br>最低 SDK：Android 11 (API 30)
 */
public abstract class BaseService extends Service {

    /**
     * 获取前台通知渠道 ID。
     * <p>每个 Service 子类应有唯一的通知渠道 ID，便于用户在系统设置中分别管理。
     *
     * @return 通知渠道 ID
     */
    protected abstract String getNotificationChannelId();

    /**
     * 获取前台通知渠道名称。
     * <p>渠道名称会显示在系统通知设置中，应使用用户可理解的描述性名称。
     *
     * @return 通知渠道显示名称
     */
    protected abstract String getNotificationChannelName();

    /**
     * 获取通知渠道描述。
     * <p>描述信息会显示在系统通知设置中，帮助用户了解该渠道的用途。
     *
     * @return 通知渠道描述文本
     */
    protected String getNotificationChannelDescription() {
        return "L7Audio 服务通知";
    }

    /**
     * 获取前台通知 ID。
     * <p>每个 Service 子类应有唯一的通知 ID，避免不同服务的通知相互覆盖。
     *
     * @return 通知 ID
     */
    protected abstract int getNotificationId();

    /**
     * 获取通知小图标资源 ID。
     * <p>默认为系统信息图标，子类可重写返回自定义图标。
     *
     * @return 图标资源 ID
     */
    protected int getNotificationIconResId() {
        return android.R.drawable.ic_dialog_info;
    }

    /**
     * 获取通知标题。
     *
     * @return 通知标题文本
     */
    protected abstract String getNotificationTitle();

    /**
     * 获取通知内容。
     *
     * @return 通知内容文本
     */
    protected abstract String getNotificationContent();

    /**
     * Service 创建时的初始化回调。
     * <p>在服务首次创建时调用，用于创建通知渠道。
     * 通知渠道在 Service 启动前必须已创建，否则 Android O 及以上版本会抛出异常。
     */
    @Override
    public void onCreate() {
        super.onCreate();
        // 先创建通知渠道，再启动前台服务，避免 Android O+ 抛出异常
        createNotificationChannel();
    }

    /**
     * Service 启动命令回调。
     * <p>每次通过 startService 启动时都会调用。
     * 返回 START_STICKY 表示服务被系统杀死后会自动重建，
     * 这对于需要持续运行的音频服务至关重要。
     *
     * @param intent 启动 Intent
     * @param flags 启动标志
     * @param startId 启动 ID
     * @return START_STICKY，确保服务被杀死后自动重建
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 立即启动前台服务，降低服务被系统回收的概率
        startForeground(getNotificationId(), buildNotification());
        return START_STICKY;
    }

    /**
     * Service 绑定回调。
     * <p>默认返回 null，表示该服务不支持绑定，仅通过 startService 方式启动。
     * 子类如需支持绑定，可重写此方法返回有效的 IBinder。
     *
     * @param intent 绑定 Intent
     * @return IBinder 对象，默认返回 null
     */
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * 创建通知渠道（仅 Android O 及以上生效）。
     *
     * <p>Android O (API 26) 引入了通知渠道机制，所有通知必须分配到指定渠道，
     * 否则通知无法显示。使用 IMPORTANCE_LOW 低优先级，确保服务运行时
     * 不会发出提示音或震动，仅在通知栏显示图标，减少对用户的干扰。
     * 同时关闭声音和震动，进一步降低打扰。
     *
     * <p>注：虽然目标 SDK 为 Android 11 (API 30)，但通知渠道机制仍然是必须的。
     */
    protected void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    getNotificationChannelId(),
                    getNotificationChannelName(),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(getNotificationChannelDescription());
            // 关闭通知声音和震动，前台服务通知不应打扰用户
            channel.setSound(null, null);
            channel.setVibrationPattern(null);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * 构建前台服务通知。
     *
     * <p>使用 NotificationCompat.Builder 构建通知，确保兼容性。
     * 设置 ongoing=true 表示通知为持续进行中的，用户不能手动清除，
     * 这是前台服务通知的必要属性。设置 silent=true 确保通知不会发出提示音。
     *
     * @return 构建完成的 Notification 对象
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
     * 更新前台通知内容。
     *
     * <p>在服务运行过程中，当需要更新通知显示的标题或内容时调用此方法。
     * 使用相同的通知 ID 以更新现有通知，而不是创建新通知。
     *
     * @param title   新的通知标题
     * @param content 新的通知内容
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
            // 使用相同的通知 ID，更新现有通知而非创建新通知
            notificationManager.notify(getNotificationId(), notification);
        }
    }
}
