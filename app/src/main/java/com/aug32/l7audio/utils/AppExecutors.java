package com.aug32.l7audio.utils;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 统一线程池管理类。
 *
 * <p>主要职责：
 * <ul>
 *   <li>主线程调度：封装主线程 Handler，提供主线程执行能力</li>
 *   <li>IO 线程池：用于文件操作、网络请求等轻量 IO 密集型任务</li>
 *   <li>计算线程池：用于音频处理、编解码等 CPU 密集型任务</li>
 *   <li>生命周期管理：统一管理线程池的创建与关闭</li>
 * </ul>
 *
 * <p>设计意图：
 * 使用单例模式全局管理线程池，避免各模块自行创建线程导致的资源浪费和不可控。
 * 区分 IO 线程池和计算线程池，针对不同任务类型采用不同的线程数量和优先级策略：
 * IO 密集型任务使用更多线程但更低优先级，计算密集型任务使用较少线程避免 CPU 过载。
 *
 * <p>目标 SDK：Android 11 (API 30)
 * <br>最低 SDK：Android 11 (API 30)
 */
public class AppExecutors {

    /** 日志标签 */
    private static final String TAG = "AppExecutors";

    /** IO 线程池核心线程数 */
    private static final int CORE_POOL_SIZE = 2;
    /** IO 线程池最大线程数 */
    private static final int MAX_POOL_SIZE = 4;
    /** 空闲线程存活时间（秒） */
    private static final int KEEP_ALIVE_TIME = 30;

    /** 单例实例，使用 volatile 保证多线程可见性 */
    private static volatile AppExecutors instance;

    /** 主线程 Handler，用于将任务调度到主线程执行 */
    private final Handler mainHandler;

    /** IO 线程池，适合文件读写、网络请求等 IO 密集型任务 */
    private final ExecutorService ioExecutor;

    /** 计算线程池，适合音频处理、图片处理等 CPU 密集型任务 */
    private final ExecutorService computeExecutor;

    /**
     * 获取单例实例。
     *
     * <p>使用双重检查锁定（Double-Checked Locking）模式实现线程安全的懒汉式单例。
     * 配合 volatile 关键字禁止指令重排序，确保多线程环境下 instance 的可见性。
     *
     * @return AppExecutors 单例实例
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
     * 私有构造函数，初始化各线程池。
     *
     * <p>IO 线程池配置：
     * - 核心线程 2 个，最大线程 4 个，应对突发 IO 任务
     * - 空闲线程 30 秒后回收，平衡资源占用与响应速度
     * - 使用 LinkedBlockingQueue 无界队列，确保任务不丢失
     * - 线程优先级设为最低，避免抢占 UI 线程资源
     * - 使用 AbortPolicy 拒绝策略，队列满时抛出异常便于发现问题
     *
     * <p>计算线程池配置：
     * - 固定 2 个线程，避免 CPU 密集任务过多导致系统卡顿
     * - 线程优先级设为默认，保证计算任务的执行效率
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
                    // IO 线程使用最低优先级，避免影响 UI 响应
                    t.setPriority(Thread.MIN_PRIORITY);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );

        // 计算线程池：核心 2 线程，最大 2 线程（保持 CPU 密集任务不抢占太多资源）
        computeExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "AppExecutors-Compute");
            // 计算线程使用默认优先级，平衡执行效率与系统响应
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        });
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
     * 在计算线程执行任务
     *
     * @param task 要执行的任务
     */
    public void executeOnComputeThread(Runnable task) {
        if (task != null) {
            computeExecutor.execute(task);
        }
    }
}
