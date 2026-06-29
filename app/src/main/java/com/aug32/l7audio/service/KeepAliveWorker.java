package com.aug32.l7audio.service;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.aug32.l7audio.utils.AppLog;

/**
 * 保活工作任务
 * <p>
 * 职责说明：
 * 由 WorkManager 调度执行的周期性后台任务，负责检查音频前台服务的运行状态，
 * 若服务已停止则重新启动，从而保证应用在后台持续存活。
 * <p>
 * 设计意图：
 * - 继承自 Worker，由 WorkManager 管理生命周期，无需手动处理线程和唤醒
 * - doWork() 在后台线程执行，可直接进行耗时操作
 * - 每次执行都尝试启动前台服务，利用 Service 的幂等性确保服务存活
 * - 目前 isForegroundServiceRunning() 直接返回 false，简化为每次都尝试启动
 *
 * @author L7Audio Team
 * @see KeepAliveManager
 * @see AudioForegroundService
 */
public class KeepAliveWorker extends Worker {
    // 日志标签
    private static final String TAG = "KeepAliveWorker";

    /**
     * 构造方法
     * <p>
     * 由 WorkManager 在创建 Worker 实例时调用，
     * 将上下文和任务参数传递给父类 Worker。
     *
     * @param context      应用上下文
     * @param workerParams Worker 任务参数
     */
    public KeepAliveWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    /**
     * 执行保活任务
     * <p>
     * 在后台线程中执行，核心逻辑：
     * 1. 检查音频前台服务是否正在运行
     * 2. 若服务未运行，则启动音频前台服务
     * 3. 返回执行结果（成功/失败/重试）
     * <p>
     * 注意：此方法在 WorkManager 提供的后台线程中执行，
     * 不需要额外开启线程。
     *
     * @return 任务执行结果，成功返回 Result.success()
     */
    @NonNull
    @Override
    public Result doWork() {
        AppLog.d(TAG, "Keep-alive work executed");

        // 检查前台服务是否正在运行，如果没有运行则启动
        // 为什么每次都尝试启动：Service 启动是幂等的，重复调用 startService 不会有副作用，
        // 反而能确保服务始终处于运行状态，简化逻辑
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

    /**
     * 检查前台服务是否正在运行
     * <p>
     * 当前实现直接返回 false，简化为每次执行保活任务时都尝试启动服务。
     * 利用 Service 的幂等性（多次 startService 不会重复创建），
     * 以最简单的方式确保服务存活。
     *
     * @return 当前始终返回 false，表示每次都尝试启动服务
     */
    private boolean isForegroundServiceRunning() {
        // 这里可以通过多种方式检查服务是否运行
        // 简单起见，我们直接返回 false，让每次都尝试启动服务
        // 实际应用中可以通过 ActivityManager 来检查服务状态
        return false;
    }
}
