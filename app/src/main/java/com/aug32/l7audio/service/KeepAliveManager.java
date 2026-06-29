package com.aug32.l7audio.service;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

import com.aug32.l7audio.utils.AppLog;

/**
 * 保活任务管理器
 * <p>
 * 职责说明：
 * 负责管理（启动/停止）基于 WorkManager 的周期性保活任务，
 * 通过定时唤醒应用，检查并重启前台服务，从而提升应用在后台的存活率。
 * <p>
 * 设计意图：
 * - 使用 Android Jetpack WorkManager 调度周期性任务，兼容各系统版本
 * - 任务间隔设置为15分钟（WorkManager 允许的最小间隔），平衡保活效果与电量消耗
 * - 不依赖网络、充电、设备空闲等条件，确保在各种状态下都能执行
 * - 与 AudioForegroundService 形成双重保活机制
 *
 * @author L7Audio Team
 * @see KeepAliveWorker
 * @see AudioForegroundService
 */
public class KeepAliveManager {
    // 日志标签
    private static final String TAG = "KeepAliveManager";
    // 保活任务执行间隔（单位：分钟），WorkManager 最小支持15分钟
    private static final long KEEP_ALIVE_INTERVAL_MINUTES = 15;

    /**
     * 启动周期性保活任务
     * <p>
     * 通过 WorkManager 调度周期性保活 Worker，间隔为15分钟。
     * 任务约束条件设置为无网络要求、不要求电量充足、不要求充电、不要求设备空闲，
     * 以确保在各种设备状态下都能尽可能执行保活逻辑。
     *
     * @param context 上下文，用于获取 WorkManager 实例
     * @see KeepAliveWorker
     */
    public static void startKeepAliveWork(Context context) {
        // 创建保活任务请求
        PeriodicWorkRequest keepAliveRequest = new PeriodicWorkRequest.Builder(
                KeepAliveWorker.class,
                KEEP_ALIVE_INTERVAL_MINUTES,
                TimeUnit.MINUTES
        )
                .setConstraints(new Constraints.Builder()
                        // 无需网络连接，离线也能执行保活
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        // 不要求电池电量充足，低电量也执行
                        .setRequiresBatteryNotLow(false)
                        // 不要求正在充电
                        .setRequiresCharging(false)
                        // 不要求设备空闲
                        .setRequiresDeviceIdle(false)
                        .build())
                .build();

        // 提交任务到 WorkManager 队列
        WorkManager.getInstance(context).enqueue(keepAliveRequest);
        AppLog.d(TAG, "Keep-alive work started with interval: " + KEEP_ALIVE_INTERVAL_MINUTES + " minutes");
    }

    /**
     * 停止所有保活任务
     * <p>
     * 取消通过 WorkManager 提交的所有任务，停止周期性保活检查。
     * 通常在用户主动关闭应用或不需要保活时调用。
     *
     * @param context 上下文，用于获取 WorkManager 实例
     */
    public static void stopKeepAliveWork(Context context) {
        WorkManager.getInstance(context).cancelAllWork();
        AppLog.d(TAG, "Keep-alive work stopped");
    }
}
