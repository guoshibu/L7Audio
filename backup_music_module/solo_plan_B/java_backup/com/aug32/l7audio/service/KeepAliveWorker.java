package com.aug32.l7audio.service;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.aug32.l7audio.utils.AppLog;

public class KeepAliveWorker extends Worker {
    private static final String TAG = "KeepAliveWorker";

    /** 构造 Worker，由 WorkManager 调用 */
    public KeepAliveWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    /** 执行保活任务：检查并在必要时启动音频前台服务 */
    @NonNull
    @Override
    public Result doWork() {
        AppLog.d(TAG, "Keep-alive work executed");

        // 检查前台服务是否正在运行，如果没有运行则启动
        if (!isForegroundServiceRunning()) {
            AppLog.d(TAG, "Foreground service not running, starting it");
            AudioForegroundService.start(getApplicationContext());
        }

        // 这里可以添加其他保活逻辑，例如：
        // 1. 检查应用是否被系统杀死
        // 2. 重启必要的服务
        // 3. 发送心跳包等

        return Result.success();
    }

    /** 检查前台服务是否正在运行（目前直接返回 false 以便每次尝试启动服务 */
    private boolean isForegroundServiceRunning() {
        // 这里可以通过多种方式检查服务是否运行
        // 简单起见，我们直接返回 false，让每次都尝试启动服务
        // 实际应用中可以通过 ActivityManager 来检查服务状态
        return false;
    }
}