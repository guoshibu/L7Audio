package com.aug32.l7audio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.aug32.l7audio.AppLog;
import com.aug32.l7audio.service.AudioForegroundService;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

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

        // 处理开机完成广播
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                "android.intent.action.QUICKBOOT_POWERON".equals(action) ||
                "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)) {

            handleBootCompleted(context);
        }
    }

    /**
     * 处理开机完成事件
     */
    private void handleBootCompleted(Context context) {
        AppLog.d(TAG, "Handling boot completed event");

        // 检查是否开启了开机自启动
        AppConfig appConfig = new AppConfig(context);
        if (appConfig.isAutoStartOnBoot()) {
            AppLog.d(TAG, "Auto start on boot is enabled, starting app");

            // 启动前台服务
            AudioForegroundService.start(context);

            // 启动主 Activity
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
     * 启用开机自启动
     */
    public static void enable(Context context) {
        AppConfig appConfig = new AppConfig(context);
        appConfig.setAutoStartOnBoot(true);

        // 启用广播接收器
        AppLog.d(TAG, "Boot receiver enabled");
    }

    /**
     * 禁用开机自启动
     */
    public static void disable(Context context) {
        AppConfig appConfig = new AppConfig(context);
        appConfig.setAutoStartOnBoot(false);

        // 禁用广播接收器
        AppLog.d(TAG, "Boot receiver disabled");
    }
}