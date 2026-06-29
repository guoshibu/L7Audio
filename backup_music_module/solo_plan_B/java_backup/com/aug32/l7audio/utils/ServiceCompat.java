package com.aug32.l7audio.utils;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.aug32.l7audio.utils.AppLog;

/**
 * Service 启动兼容工具类
 *
 * 统一处理 Android O (API 26) 及以上的 startForegroundService 兼容逻辑
 *
 * 目标 SDK：Android 11 (API 30)
 * 最低 SDK：Android 11 (API 30)
 */
public final class ServiceCompat {

    private static final String TAG = "ServiceCompat";

    private ServiceCompat() {
        // 私有构造函数，防止实例化
    }

    /**
     * 启动前台服务（兼容所有 Android 版本）
     * Android O 及以上使用 startForegroundService，然后必须在 5 秒内调用 startForeground
     * Android 11 (API 30) 仍然需要此兼容逻辑
     *
     * @param context     Context
     * @param serviceIntent Service 的 Intent
     */
    public static void startForegroundService(Context context, Intent serviceIntent) {
        if (context == null || serviceIntent == null) {
            AppLog.e(TAG, "startForegroundService: context or intent is null");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android O 及以上
            context.startForegroundService(serviceIntent);
            AppLog.d(TAG, "Started foreground service (API " + Build.VERSION.SDK_INT + ")");
        } else {
            // Android O 以下
            context.startService(serviceIntent);
            AppLog.d(TAG, "Started service (API " + Build.VERSION.SDK_INT + ")");
        }
    }

    /**
     * 停止服务
     *
     * @param context     Context
     * @param serviceIntent Service 的 Intent
     */
    public static void stopService(Context context, Intent serviceIntent) {
        if (context == null || serviceIntent == null) {
            return;
        }
        context.stopService(serviceIntent);
    }

    /**
     * 判断服务是否正在运行
     *
     * @param context      Context
     * @param serviceClass 服务类
     * @return true=正在运行
     */
    public static boolean isServiceRunning(Context context, Class<?> serviceClass) {
        if (context == null || serviceClass == null) {
            return false;
        }

        android.app.ActivityManager activityManager =
                (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) {
            return false;
        }

        for (android.app.ActivityManager.RunningServiceInfo serviceInfo : activityManager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(serviceInfo.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
