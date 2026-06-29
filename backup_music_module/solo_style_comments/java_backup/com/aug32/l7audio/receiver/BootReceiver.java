package com.aug32.l7audio.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.aug32.l7audio.data.local.AppConfig;
import com.aug32.l7audio.utils.AppLog;
import com.aug32.l7audio.service.AudioForegroundService;
import com.aug32.l7audio.ui.activity.MainActivity;

/**
 * 开机自启动广播接收器
 *
 * 职责：
 * - 接收开机完成广播
 * - 根据配置决定是否启动应用
 *
 * 目标 SDK：Android 11 (API 30)
 */
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

        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                "android.intent.action.QUICKBOOT_POWERON".equals(action) ||
                "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)) {
            handleBootCompleted(context);
        }
    }

    /** 处理开机完成事件 */
    private void handleBootCompleted(Context context) {
        AppLog.d(TAG, "Handling boot completed event");

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

    /** 启用开机自启动 */
    public static void enable(Context context) {
        AppConfig appConfig = new AppConfig(context);
        appConfig.setAutoStartOnBoot(true);
        AppLog.d(TAG, "Boot receiver enabled");
    }

    /** 禁用开机自启动 */
    public static void disable(Context context) {
        AppConfig appConfig = new AppConfig(context);
        appConfig.setAutoStartOnBoot(false);
        AppLog.d(TAG, "Boot receiver disabled");
    }
}
