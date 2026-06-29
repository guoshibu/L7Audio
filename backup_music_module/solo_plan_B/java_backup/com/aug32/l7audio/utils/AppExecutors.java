package com.aug32.l7audio.utils;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 统一线程池管理类
 *
 * 提供：
 * - 主线程调度
 * - IO 线程池（用于文件操作、网络请求等轻量 IO）
 * - 计算线程池（用于音频处理等 CPU 密集任务）
 *
 * 目标 SDK：Android 11 (API 30)
 * 最低 SDK：Android 11 (API 30)
 */
public class AppExecutors {

    private static final String TAG = "AppExecutors";

    // 核心线程数
    private static final int CORE_POOL_SIZE = 2;
    // 最大线程数
    private static final int MAX_POOL_SIZE = 4;
    // 空闲线程存活时间
    private static final int KEEP_ALIVE_TIME = 30;

    // 单例实例
    private static volatile AppExecutors instance;

    // 主线程 Handler
    private final Handler mainHandler;

    // IO 线程池（适合文件读写、网络请求等）
    private final ExecutorService ioExecutor;

    // 计算线程池（适合音频处理、图片处理等 CPU 密集任务）
    private final ExecutorService computeExecutor;

    /**
     * 获取单例实例
     *
     * @return AppExecutors 实例
     */
    public static AppExecutors getInstance() {
        if (instance == null) {
            synchronized (AppExecutors.class) {
                if (instance == null) {
                    instance = new AppExecutors();
                }
            }
        }
        return instance;
    }

    /**
     * 私有构造函数
     */
    private AppExecutors() {
        mainHandler = new Handler(Looper.getMainLooper());

        // IO 线程池：核心 2 线程，最大 4 线程，使用 LinkedBlockingQueue
        ioExecutor = new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                KEEP_ALIVE_TIME,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                r -> {
                    Thread t = new Thread(r, "AppExecutors-IO");
                    t.setPriority(Thread.MIN_PRIORITY);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );

        // 计算线程池：核心 2 线程，最大 2 线程（保持 CPU 密集任务不抢占太多资源）
        computeExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "AppExecutors-Compute");
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        });
    }

    /**
     * 获取主线程 Executor
     * 用于将任务调度到主线程执行
     *
     * @return 主线程 Executor
     */
    public Executor mainThread() {
        return mainHandler::post;
    }

    /**
     * 在主线程执行 Runnable
     *
     * @param runnable 要执行的 Runnable
     * @return true=已提交到主线程队列
     */
    public boolean postToMainThread(Runnable runnable) {
        if (runnable == null) {
            return false;
        }
        return mainHandler.post(runnable);
    }

    /**
     * 在主线程延迟执行 Runnable
     *
     * @param runnable   要执行的 Runnable
     * @param delayMillis 延迟毫秒数
     * @return true=已提交到主线程队列
     */
    public boolean postToMainThreadDelayed(Runnable runnable, long delayMillis) {
        if (runnable == null) {
            return false;
        }
        return mainHandler.postDelayed(runnable, delayMillis);
    }

    /**
     * 移除主线程待执行的 Runnable
     *
     * @param runnable 要移除的 Runnable
     */
    public void removeMainThreadCallback(Runnable runnable) {
        if (runnable != null) {
            mainHandler.removeCallbacks(runnable);
        }
    }

    /**
     * 获取 IO 线程池
     * 适合文件读写、网络请求等轻量 IO 操作
     *
     * @return IO Executor
     */
    public ExecutorService ioThread() {
        return ioExecutor;
    }

    /**
     * 在 IO 线程执行任务
     *
     * @param task 要执行的任务
     */
    public void executeOnIOThread(Runnable task) {
        if (task != null) {
            ioExecutor.execute(task);
        }
    }

    /**
     * 在 IO 线程执行任务（带回调）
     *
     * @param task         要执行的任务
     * @param completionCallback 完成后回调（可选，在主线程执行）
     */
    public void executeOnIOThread(Runnable task, Runnable completionCallback) {
        if (task == null) {
            return;
        }

        ioExecutor.execute(() -> {
            task.run();
            if (completionCallback != null) {
                postToMainThread(completionCallback);
            }
        });
    }

    /**
     * 获取计算线程池
     * 适合音频处理、图片处理等 CPU 密集任务
     *
     * @return 计算 Executor
     */
    public ExecutorService computeThread() {
        return computeExecutor;
    }

    /**
     * 在计算线程执行任务
     *
     * @param task 要执行的任务
     */
    public void executeOnComputeThread(Runnable task) {
        if (task != null) {
            computeExecutor.execute(task);
        }
    }

    /**
     * 关闭所有线程池
     * 应用退出时调用
     *
     * @param awaitTerminationMillis 等待线程终止的最大毫秒数，0=不等待
     */
    public void shutdown(long awaitTerminationMillis) {
        ioExecutor.shutdown();
        computeExecutor.shutdown();

        if (awaitTerminationMillis > 0) {
            try {
                if (!ioExecutor.awaitTermination(awaitTerminationMillis, TimeUnit.MILLISECONDS)) {
                    ioExecutor.shutdownNow();
                }
                if (!computeExecutor.awaitTermination(awaitTerminationMillis, TimeUnit.MILLISECONDS)) {
                    computeExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                ioExecutor.shutdownNow();
                computeExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
