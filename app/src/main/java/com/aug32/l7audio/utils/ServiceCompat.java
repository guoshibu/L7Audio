package com.aug32.l7audio.utils;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.aug32.l7audio.utils.AppLog;

/**
 * Service 启动兼容工具类。
 *
 * <p>主要职责：
 * <ul>
 *   <li>前台服务启动兼容：统一处理 Android O 及以上版本的 startForegroundService 调用</li>
 *   <li>服务停止：封装停止服务的安全调用</li>
 *   <li>服务状态查询：提供判断服务是否正在运行的方法</li>
 * </ul>
 *
 * <p>设计意图：
 * Android O (API 26) 对后台服务进行了严格限制，启动前台服务必须使用
 * startForegroundService() 而非 startService()，且必须在 5 秒内调用 startForeground()。
 * 此类封装了版本判断逻辑，调用方无需关心系统版本差异，直接调用统一方法即可。
 *
 * <p>目标 SDK：Android 11 (API 30)
 * <br>最低 SDK：Android 11 (API 30)
 */
public final class ServiceCompat {

    /** 日志标签 */
    private static final String TAG = "ServiceCompat";

    /**
     * 私有构造函数，防止实例化。
     * <p>工具类所有方法均为静态方法，无需创建实例。
     */
    private ServiceCompat() {
        // 私有构造函数，防止实例化
    }

    /**
     * 启动前台服务（兼容所有 Android 版本）。
     *
     * <p>Android O (API 26) 及以上使用 startForegroundService()，
     * 调用后 Service 必须在 5 秒内调用 startForeground()，否则会触发 ANR。
     * Android O 以下使用传统的 startService()。
     *
     * <p>注：虽然最低 SDK 为 Android 11，但保留版本判断逻辑以保持代码兼容性。
     *
     * @param context       上下文对象
     * @param serviceIntent 启动 Service 的 Intent
     */
    public static void startForegroundService(Context context, Intent serviceIntent) {
        // 参数校验，避免空指针
        if (context == null || serviceIntent == null) {
            AppLog.e(TAG, "startForegroundService: context or intent is null");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android O 及以上必须使用 startForegroundService 启动前台服务
            context.startForegroundService(serviceIntent);
            AppLog.d(TAG, "Started foreground service (API " + Build.VERSION.SDK_INT + ")");
        } else {
            // Android O 以下使用普通 startService
            context.startService(serviceIntent);
            AppLog.d(TAG, "Started service (API " + Build.VERSION.SDK_INT + ")");
        }
    }

    /**
     * 停止服务。
     *
     * @param context       上下文对象
     * @param serviceIntent 停止 Service 的 Intent
     */
    public static void stopService(Context context, Intent serviceIntent) {
        // 参数校验，避免空指针
        if (context == null || serviceIntent == null) {
            return;
        }
        context.stopService(serviceIntent);
    }
}
