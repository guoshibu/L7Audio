package com.aug32.l7audio.domain.audio;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.aug32.l7audio.data.local.AppConfig;
import com.aug32.l7audio.data.local.config.MicConfig;
import com.aug32.l7audio.utils.AppLog;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 车外喊话控制器（单例）
 *
 * <p>职责：
 * <ul>
 *   <li>统一管理车外喊话/麦克风放大的开启/关闭</li>
 *   <li>防抖处理，屏蔽快速连续触发，避免麦克风频繁启停啸叫和硬件损伤</li>
 *   <li>静音检测，无声音输入超时后自动关闭</li>
 *   <li>状态同步，通过观察者模式通知所有注册的 UI（悬浮窗、麦克风页面）</li>
 *   <li>音频输出模式切换：强制车外 或 跟随应用配置</li>
 *   <li>音频焦点管理：喊话时申请短暂独占焦点暂停音乐，关闭时释放焦点恢复音乐</li>
 * </ul>
 *
 * <p>设计意图：
 * <ul>
 *   <li>悬浮窗、麦克风页面、外部广播按键 三个入口统一调用 toggle()</li>
 *   <li>Controller 不依赖 Activity 生命周期，由 AudioServiceLocator 管理管理器实例</li>
 *   <li>状态同步使用 CopyOnWriteArrayList 保证线程安全</li>
 * </ul>
 *
 * @author L7Audio
 * @since 1.4.3
 */
public class AnnouncementController {

    private static final String TAG = "AnnouncementController";

    // ========== 单例 ==========
    private static volatile AnnouncementController instance;

    // ========== 依赖 ==========
    private Context appContext;
    private MicConfig micConfig;
    private AppConfig appConfig;

    // ========== 状态 ==========
    /** 当前是否在喊话 */
    private volatile boolean isAnnouncing = false;
    /** 喊话前的输出模式（用于喊话结束后恢复） */
    private int savedOutputMode = -1;
    /** 上次 toggle 触发时间（用于防抖） */
    private long lastToggleTime = 0;

    // ========== 静音检测 ==========
    /** 静音检测线程 */
    private Thread silenceDetectorThread;
    /** 静音检测停止标记 */
    private volatile boolean stopSilenceDetection = false;

    // ========== UI 回调（主线程） ==========
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    /** 状态监听器列表（线程安全） */
    private final CopyOnWriteArrayList<AnnouncementListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * 喊话状态监听接口
     *
     * <p>所有回调在主线程执行，UI 可安全更新。
     */
    public interface AnnouncementListener {
        /**
         * 喊话状态变化时回调
         *
         * @param isAnnouncing true 表示开始喊话，false 表示停止喊话
         */
        void onAnnouncementStateChanged(boolean isAnnouncing);

        /**
         * 自动关闭时回调（静音超时）
         *
         * @param reason 关闭原因
         */
        void onAnnouncementAutoClosed(String reason);
    }

    /**
     * 私有构造函数
     */
    private AnnouncementController() {
    }

    /**
     * 获取单例实例（DCL 双重检查锁）
     *
     * @return AnnouncementController 实例
     */
    public static AnnouncementController getInstance() {
        if (instance == null) {
            synchronized (AnnouncementController.class) {
                if (instance == null) {
                    instance = new AnnouncementController();
                }
            }
        }
        return instance;
    }

    /**
     * 初始化控制器
     * <p>
     * 在 Application 或首次使用时调用，注入 ApplicationContext。
     * </p>
     *
     * @param context 上下文对象
     */
    public void init(Context context) {
        if (this.appContext != null) {
            AppLog.d(TAG, "Already initialized, skipping");
            return;
        }
        this.appContext = context.getApplicationContext();
        android.content.SharedPreferences prefs = appContext.getSharedPreferences(
                appContext.getPackageName() + "_preferences", Context.MODE_PRIVATE);
        this.micConfig = new MicConfig(prefs);
        this.appConfig = new AppConfig(appContext);
        AppLog.d(TAG, "AnnouncementController initialized");
    }

    /**
     * 是否已初始化
     *
     * @return true 表示已初始化
     */
    public boolean isInitialized() {
        return appContext != null;
    }

    /**
     * 切换喊话状态
     * <p>
     * 所有入口（悬浮窗、麦克风页面、外部广播）统一调用此方法。
     * 内部包含防抖检查，屏蔽快速连续触发。
     * </p>
     *
     * @param forceExternal true=强制车外输出（悬浮窗/外部广播入口），
     *                       false=跟随应用配置（麦克风页面入口）
     * @param showToast true=显示Toast提示（仅第三方广播调用时使用），
     *                   false=不显示Toast（悬浮窗/麦克风页面调用）
     */
    public synchronized void toggle(boolean forceExternal, boolean showToast) {
        if (appContext == null) {
            AppLog.e(TAG, "Controller not initialized, call init() first");
            return;
        }

        long now = System.currentTimeMillis();
        int debounceInterval = micConfig.getDebounceInterval();
        if (now - lastToggleTime < debounceInterval) {
            AppLog.w(TAG, "Toggle ignored: debounce (interval=" + debounceInterval + "ms)");
            return;
        }
        lastToggleTime = now;

        if (isAnnouncing) {
            stopAnnouncement(showToast);
        } else {
            startAnnouncement(forceExternal, showToast);
        }
    }

    /**
     * 切换喊话状态（兼容旧接口，不显示Toast）
     */
    public synchronized void toggle(boolean forceExternal) {
        toggle(forceExternal, false);
    }

    /**
     * 启动喊话
     * <p>
     * 流程：停止TTS → 记录输出模式 → 申请焦点 → 切换输出 → 启动麦克风 → 静音检测 → 通知UI
     * </p>
     *
     * @param forceExternal true=强制车外，false=跟随配置
     * @param showToast true=显示Toast，false=不显示
     */
    private void startAnnouncement(boolean forceExternal, boolean showToast) {
        AudioServiceLocator locator = AudioServiceLocator.getInstance();
        AudioOutputManager outputManager = locator.getAudioOutputManager();
        AudioFocusManager focusManager = locator.getAudioFocusManager();
        MicrophoneManager micManager = locator.getMicrophoneManager();

        if (outputManager == null || focusManager == null || micManager == null) {
            AppLog.e(TAG, "Required manager is null, cannot start announcement");
            return;
        }

        // 停止 TTS 播放（互斥）
        try {
            if (locator.getTTSManager() != null && locator.getTTSManager().isSpeaking()) {
                locator.getTTSManager().stop();
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to stop TTS", e);
        }

        // 记录当前输出模式（喊话结束后恢复）
        savedOutputMode = outputManager.getOutputMode();

        // 申请短暂独占焦点，音乐通过焦点回调自动暂停
        boolean granted = focusManager.requestTransientFocus();
        AppLog.d(TAG, "Announcement started, transient focus granted=" + granted);

        // 切换输出模式：强制车外 或 跟随配置
        if (forceExternal) {
            outputManager.setOutputMode(AudioOutputManager.OUTPUT_EXTERNAL);
            AppLog.d(TAG, "Switched output mode to external (forced)");
        }

        // 启动麦克风
        boolean started = micManager.start();
        if (!started) {
            AppLog.e(TAG, "Microphone start failed, aborting announcement");
            // 回滚：恢复输出模式和焦点
            if (savedOutputMode >= 0) {
                outputManager.setOutputMode(savedOutputMode);
                savedOutputMode = -1;
            }
            focusManager.abandonTransientFocus();
            return;
        }

        isAnnouncing = true;

        // 启动静音检测
        if (micConfig.isSilenceDetectionEnabled()) {
            startSilenceDetection();
        }

        // 通知 UI
        notifyStateChanged(true);
        if (showToast) {
            showToast("车外喊话已开启");
        }
    }

    /**
     * 停止喊话
     * <p>
     * 流程：停止麦克风 → 停止静音检测 → 恢复输出模式 → 释放焦点 → 通知UI
     * </p>
     *
     * @param showToast true=显示Toast，false=不显示
     */
    private void stopAnnouncement(boolean showToast) {
        stopSilenceDetection();

        AudioServiceLocator locator = AudioServiceLocator.getInstance();
        AudioOutputManager outputManager = locator.getAudioOutputManager();
        AudioFocusManager focusManager = locator.getAudioFocusManager();
        MicrophoneManager micManager = locator.getMicrophoneManager();

        // 停止麦克风
        try {
            if (micManager != null) {
                micManager.stop();
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to stop microphone", e);
        }

        isAnnouncing = false;

        // 恢复输出模式（先恢复输出，再释放焦点让音乐从正确设备播放）
        if (outputManager != null && savedOutputMode >= 0) {
            outputManager.setOutputMode(savedOutputMode);
            AppLog.d(TAG, "Restored output mode to: " + savedOutputMode);
            savedOutputMode = -1;
        }

        // 释放短暂独占焦点，音乐通过焦点回调自动恢复
        if (focusManager != null) {
            focusManager.abandonTransientFocus();
            AppLog.d(TAG, "Announcement stopped, abandoned transient focus");
        }

        // 通知 UI
        notifyStateChanged(false);
        if (showToast) {
            showToast("车外喊话已关闭");
        }
    }

    /**
     * 启动静音检测线程
     * <p>
     * 定期检查麦克风 RMS 值，低于阈值持续超过超时时间则自动关闭喊话。
     * </p>
     */
    private void startSilenceDetection() {
        stopSilenceDetection = false;
        silenceDetectorThread = new Thread(() -> {
            int timeoutMs = micConfig.getSilenceTimeout() * 1000;
            float threshold = micConfig.getSilenceThreshold();
            long silenceStartTime = 0;
            int checkIntervalMs = 500;

            AppLog.d(TAG, "Silence detection started: timeout=" + timeoutMs + "ms, threshold=" + threshold);

            while (!stopSilenceDetection && isAnnouncing) {
                AudioServiceLocator locator = AudioServiceLocator.getInstance();
                MicrophoneManager micManager = locator.getMicrophoneManager();
                if (micManager == null || !micManager.isRecording()) {
                    try { Thread.sleep(checkIntervalMs); } catch (InterruptedException ignored) { break; }
                    continue;
                }

                float rms = micManager.getCurrentRms();
                if (rms < threshold) {
                    // 静音
                    if (silenceStartTime == 0) {
                        silenceStartTime = System.currentTimeMillis();
                    } else if (System.currentTimeMillis() - silenceStartTime >= timeoutMs) {
                        AppLog.w(TAG, "Silence timeout reached, auto stop announcement");
                        mainHandler.post(() -> {
                            stopAnnouncement(false);
                            notifyAutoClosed("长时间无声音输入");
                        });
                        break;
                    }
                } else {
                    // 有声音输入，重置计时
                    silenceStartTime = 0;
                }

                try { Thread.sleep(checkIntervalMs); } catch (InterruptedException ignored) { break; }
            }
        }, "SilenceDetectionThread");
        silenceDetectorThread.start();
    }

    /**
     * 停止静音检测线程
     */
    private void stopSilenceDetection() {
        stopSilenceDetection = true;
        if (silenceDetectorThread != null) {
            silenceDetectorThread.interrupt();
            silenceDetectorThread = null;
        }
    }

    /**
     * 获取当前是否在喊话
     *
     * @return true 表示正在喊话，false 表示未在喊话
     */
    public boolean isAnnouncing() {
        return isAnnouncing;
    }

    /**
     * 注册状态监听器
     *
     * @param listener 监听器
     */
    public void addListener(AnnouncementListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * 移除状态监听器
     *
     * @param listener 监听器
     */
    public void removeListener(AnnouncementListener listener) {
        listeners.remove(listener);
    }

    /**
     * 通知所有监听器状态变化（主线程）
     *
     * @param announcing true=开始喊话，false=停止喊话
     */
    private void notifyStateChanged(boolean announcing) {
        mainHandler.post(() -> {
            for (AnnouncementListener l : listeners) {
                try {
                    l.onAnnouncementStateChanged(announcing);
                } catch (Throwable t) {
                    AppLog.e(TAG, "Listener callback error", t);
                }
            }
        });
    }

    /**
     * 通知所有监听器自动关闭事件（主线程）
     *
     * @param reason 关闭原因
     */
    private void notifyAutoClosed(String reason) {
        mainHandler.post(() -> {
            for (AnnouncementListener l : listeners) {
                try {
                    l.onAnnouncementAutoClosed(reason);
                } catch (Throwable t) {
                    AppLog.e(TAG, "Listener callback error", t);
                }
            }
        });
        showToast("车外喊话已关闭：" + reason);
    }

    /**
     * 显示 Toast（主线程）
     *
     * @param message 消息内容
     */
    private void showToast(String message) {
        mainHandler.post(() -> {
            if (appContext != null) {
                Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
