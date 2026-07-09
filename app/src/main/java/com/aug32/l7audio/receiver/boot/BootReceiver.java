package com.aug32.l7audio.receiver.boot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.aug32.l7audio.data.local.AppConfig;
import com.aug32.l7audio.service.player.AudioForegroundService;
import com.aug32.l7audio.ui.activity.MainActivity;
import com.aug32.l7audio.utils.AppLog;

/**
 * 开机自启动广播接收器
 *
 * 职责：
 * - 监听系统开机完成广播（含标准开机和厂商快速开机）
 * - 根据用户配置决定是否自动启动应用
 * - 提供静态方法启用/禁用开机自启动功能
 *
 * 兼容说明：
 * - 支持标准 ACTION_BOOT_COMPLETED 广播
 * - 兼容 HTC 等厂商的 QUICKBOOT_POWERON 快速开机广播
 *
 * 目标 SDK：Android 11 (API 30)
 */
public class BootReceiver extends BroadcastReceiver {

    /** 日志标签 */
    private static final String TAG = "BootReceiver";

    /**
     * 广播接收回调
     * 过滤开机相关广播，收到后根据配置决定是否启动应用
     *
     * @param context 上下文
     * @param intent  广播 Intent
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }

        String action = intent.getAction();
        if (action == null) {
            return;
        }

        AppLog.d(TAG, "Received broadcast: " + action);

        // 兼容标准开机广播和厂商快速开机广播
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                "android.intent.action.QUICKBOOT_POWERON".equals(action) ||
                "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)) {
            handleBootCompleted(context);
        }
    }

    /**
     * 处理开机完成事件
     * 读取配置，若开启了开机自启动则启动前台服务和主Activity
     *
     * @param context 上下文
     */
    private void handleBootCompleted(Context context) {
        AppLog.d(TAG, "Handling boot completed event");

        AppConfig appConfig = new AppConfig(context);
        if (appConfig.isAutoStartOnBoot()) {
            AppLog.d(TAG, "Auto start on boot is enabled, starting app");

            // 先启动前台服务，确保音频功能可用
            AudioForegroundService.start(context);

            // 启动主 Activity，并标记为开机自启动模式
            Intent mainIntent = new Intent(context, MainActivity.class);
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mainIntent.putExtra("auto_start_from_boot", true);
            context.startActivity(mainIntent);

            AppLog.d(TAG, "App started automatically on boot");
        } else {
            AppLog.d(TAG, "Auto start on boot is disabled");
        }
    }

    /**
     * 启用开机自启动功能
     * 修改配置文件中的开机自启动开关为开启状态
     *
     * @param context 上下文
     */
    public static void enable(Context context) {
        AppConfig appConfig = new AppConfig(context);
        appConfig.setAutoStartOnBoot(true);
        AppLog.d(TAG, "Boot receiver enabled");
    }

}
