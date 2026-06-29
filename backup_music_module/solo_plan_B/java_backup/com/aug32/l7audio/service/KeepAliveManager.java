package com.aug32.l7audio.service;

import android.content.Context;
import android.os.Build;

import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.aug32.l7audio.utils.AppLog;

import java.util.concurrent.TimeUnit;

public class KeepAliveManager {
    private static final String TAG = "KeepAliveManager";
    private static final long KEEP_ALIVE_INTERVAL_MINUTES = 15;

    /** 启动周期保活 Worker，以维持音频前台服务存活 */
    public static void startKeepAliveWork(Context context) {
        // 创建保活任务请求
        PeriodicWorkRequest keepAliveRequest = new PeriodicWorkRequest.Builder(
                KeepAliveWorker.class,
                KEEP_ALIVE_INTERVAL_MINUTES,
                TimeUnit.MINUTES
        )
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .setRequiresBatteryNotLow(false)
                        .setRequiresCharging(false)
                        .setRequiresDeviceIdle(false)
                        .build())
                .build();

        // 提交任务
        WorkManager.getInstance(context).enqueue(keepAliveRequest);
        AppLog.d(TAG, "Keep-alive work started with interval: " + KEEP_ALIVE_INTERVAL_MINUTES + " minutes");
    }

    /** 停止所有周期保活 Worker */
    public static void stopKeepAliveWork(Context context) {
        WorkManager.getInstance(context).cancelAllWork();
        AppLog.d(TAG, "Keep-alive work stopped");
    }
}