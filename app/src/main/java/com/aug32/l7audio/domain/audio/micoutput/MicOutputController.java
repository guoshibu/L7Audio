package com.aug32.l7audio.domain.audio.micoutput;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.aug32.l7audio.data.local.AppConfig;
import com.aug32.l7audio.data.local.config.micoutput.MicOutputConfig;
import com.aug32.l7audio.domain.audio.AudioServiceLocator;
import com.aug32.l7audio.domain.audio.AudioFocusManager;
import com.aug32.l7audio.utils.AppLog;

import java.util.ArrayList;
import java.util.List;

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
public class MicOutputController {

    private static final String TAG = "MicOutputController";
    private static final String PREF_PREFER_EXTERNAL = "mic_output_prefer_external";

    // ========== 单例 ==========
    private static volatile MicOutputController instance;

    // ========== 依赖 ==========
    private Context appContext;
    private MicOutputConfig micOutputConfig;
    private AppConfig appConfig;

    // ========== 状态 ==========
    /** 当前是否在喊话 */
    private volatile boolean isAnnouncing = false;
    /** 喊话前的输出模式（用于喊话结束后恢复） */
    private int savedOutputMode = -1;
    /** 上次 toggle 触发时间（用于防抖） */
    private long lastToggleTime = 0;
    /** 用户偏好：车外模式（持久化，静音检测停止后恢复时使用） */
    private boolean preferExternal = true;

    // ========== 静音检测 ==========
    /** 静音检测线程 */
    private Thread silenceDetectorThread;
    /** 静音检测停止标记 */
    private volatile boolean stopSilenceDetection = false;

    // ========== UI 回调（主线程） ==========
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    /** 状态监听器列表 */
    private final List<MicOutputListener> listeners = new ArrayList<>();
    /** 输出模式监听器列表 */
    private final List<OutputModeListener> outputModeListeners = new ArrayList<>();

    /**
     * 喊话状态监听接口
     *
     * <p>所有回调在主线程执行，UI 可安全更新。
     */
    public interface MicOutputListener {
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
     * 输出模式变化监听接口
     *
     * <p>用于在喊话启动/停止导致输出模式变化时，同步更新 MainActivity 的车内/车外按钮状态。
     * 所有回调在主线程执行，UI 可安全更新。
     */
    public interface OutputModeListener {
        /**
         * 输出模式变化时回调
         *
         * @param mode 当前输出模式，{@link AudioOutputManager#OUTPUT_CAR} 或 {@link AudioOutputManager#OUTPUT_EXTERNAL}
         */
        void onOutputModeChanged(int mode);
    }

    /**
     * 私有构造函数
     */
    private MicOutputController() {
    }

    /**
     * 获取单例实例（DCL 双重检查锁）
     *
     * @return MicOutputController 实例
     */
    public static MicOutputController getInstance() {
        if (instance == null) {
            synchronized (MicOutputController.class) {
                if (instance == null) {
                    instance = new MicOutputController();
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
        android.content.Context appCtx = context.getApplicationContext();
        android.content.SharedPreferences prefs = appCtx.getSharedPreferences(
                appCtx.getPackageName() + "_preferences", Context.MODE_PRIVATE);
        this.micOutputConfig = new MicOutputConfig(prefs);
        this.appConfig = new AppConfig(appCtx);
        this.appContext = appCtx;
        this.preferExternal = prefs.getBoolean(PREF_PREFER_EXTERNAL, true);
        AppLog.d(TAG, "MicOutputController initialized, preferExternal=" + preferExternal);
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
     * 设置用户对外部模式的偏好（持久化）
     * <p>
     * 用户点击"仅车外"按钮时设为 true，"仅车内"时设为 false。
     * 静音检测停止后重新 amplify 时参考此偏好。
     * </p>
     */
    public void setPreferExternalMode(boolean prefer) {
        this.preferExternal = prefer;
        if (appContext != null) {
            SharedPreferences prefs = appContext.getSharedPreferences(
                    appContext.getPackageName() + "_preferences", Context.MODE_PRIVATE);
            prefs.edit().putBoolean(PREF_PREFER_EXTERNAL, prefer).apply();
        }
        AppLog.i(TAG, "preferExternal set to " + prefer);
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
        int debounceInterval = micOutputConfig.getDebounceInterval();
        if (now - lastToggleTime < debounceInterval) {
            AppLog.w(TAG, "Toggle ignored: debounce (interval=" + debounceInterval + "ms)");
            return;
        }
        lastToggleTime = now;

        // 强制车外时同步记录用户偏好
        if (forceExternal) {
            setPreferExternalMode(true);
        }

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
        AppLog.i(TAG, "startAnnouncement: savedOutputMode=" + savedOutputMode + ", forceExternal=" + forceExternal);

        // 申请短暂独占焦点，音乐通过焦点回调自动暂停
        boolean granted = focusManager.requestTransientFocus();
        AppLog.d(TAG, "Announcement started, transient focus granted=" + granted);

        // forceExternal=true 强制车外；否则按用户偏好（MainActivity 的"仅车外/仅车内"）
        if (forceExternal || preferExternal) {
            outputManager.setOutputMode(AudioOutputManager.OUTPUT_EXTERNAL);
            AppLog.i(TAG, "Switched output mode to external (forceExternal=" + forceExternal + ", preferExternal=" + preferExternal + "), AudioTrack will use usage=" + outputManager.getAudioUsage());
            notifyOutputModeChanged(AudioOutputManager.OUTPUT_EXTERNAL);
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
        if (micOutputConfig.isSilenceDetectionEnabled()) {
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
     * 流程：停止麦克风 → 停止静音检测 → 释放焦点 → 通知UI
     * </p>
     *
     * @param showToast true=显示Toast，false=不显示
     */
    private void stopAnnouncement(boolean showToast) {
        stopSilenceDetection();

        AudioServiceLocator locator = AudioServiceLocator.getInstance();
        AudioFocusManager focusManager = locator.getAudioFocusManager();
        MicrophoneManager micManager = locator.getMicrophoneManager();

        // 停止麦克风，输出模式保持喊话期间的状态不动
        try {
            if (micManager != null) {
                micManager.stop();
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to stop microphone", e);
        }

        isAnnouncing = false;
        savedOutputMode = -1;

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
            int timeoutMs = micOutputConfig.getSilenceTimeout() * 1000;
            float threshold = micOutputConfig.getSilenceThreshold();
            long silenceStartTime = 0;
            int checkIntervalMs = 500;
            // 启动宽限期：前 3 秒不计静音，等麦克风信号稳定后再开始检测
            long detectionStartTime = System.currentTimeMillis();
            long startupGraceMs = 3000;

            AppLog.d(TAG, "Silence detection started: timeout=" + timeoutMs + "ms, threshold=" + threshold + " gracePeriod=" + startupGraceMs + "ms");

            while (!stopSilenceDetection && isAnnouncing) {
                AudioServiceLocator locator = AudioServiceLocator.getInstance();
                MicrophoneManager micManager = locator.getMicrophoneManager();
                if (micManager == null || !micManager.isRecording()) {
                    try { Thread.sleep(checkIntervalMs); } catch (InterruptedException ignored) { break; }
                    continue;
                }

                float rms = micManager.getPostProcessRms();
                if (rms < threshold) {
                    // 宽限期内不计静音
                    if (System.currentTimeMillis() - detectionStartTime < startupGraceMs) {
                        silenceStartTime = 0;
                    } else if (silenceStartTime == 0) {
                        silenceStartTime = System.currentTimeMillis();
                    } else if (System.currentTimeMillis() - silenceStartTime >= timeoutMs) {
                        AppLog.w(TAG, "Silence timeout reached, auto stop announcement. lastRms=" + String.format(java.util.Locale.US, "%.4f", rms));
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
        AppLog.d(TAG, "Stopping silence detection");
        stopSilenceDetection = true;
        if (silenceDetectorThread != null) {
            silenceDetectorThread.interrupt();
            silenceDetectorThread = null;
        }
        AppLog.d(TAG, "Silence detection stopped");
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
    public void addListener(MicOutputListener listener) {
        if (listener != null) {
            synchronized (listeners) {
                if (!listeners.contains(listener)) {
                    listeners.add(listener);
                }
            }
        }
    }

    /**
     * 移除状态监听器
     *
     * @param listener 监听器
     */
    public void removeListener(MicOutputListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    /**
     * 注册输出模式监听器
     *
     * @param listener 监听器
     */
    public void addOutputModeListener(OutputModeListener listener) {
        if (listener != null) {
            synchronized (outputModeListeners) {
                if (!outputModeListeners.contains(listener)) {
                    outputModeListeners.add(listener);
                }
            }
        }
    }

    /**
     * 移除输出模式监听器
     *
     * @param listener 监听器
     */
    public void removeOutputModeListener(OutputModeListener listener) {
        synchronized (outputModeListeners) {
            outputModeListeners.remove(listener);
        }
    }

    /**
     * 通知所有输出模式监听器（主线程）
     *
     * @param mode 当前输出模式
     */
    private void notifyOutputModeChanged(int mode) {
        mainHandler.post(() -> {
            List<OutputModeListener> snapshot;
            synchronized (outputModeListeners) {
                snapshot = new ArrayList<>(outputModeListeners);
            }
            for (OutputModeListener l : snapshot) {
                try {
                    l.onOutputModeChanged(mode);
                } catch (Throwable t) {
                    AppLog.e(TAG, "OutputModeListener callback error", t);
                }
            }
        });
    }

    /**
     * 通知所有监听器状态变化（主线程）
     *
     * @param announcing true=开始喊话，false=停止喊话
     */
    private void notifyStateChanged(boolean announcing) {
        mainHandler.post(() -> {
            List<MicOutputListener> snapshot;
            synchronized (listeners) {
                snapshot = new ArrayList<>(listeners);
            }
            for (MicOutputListener l : snapshot) {
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
            List<MicOutputListener> snapshot;
            synchronized (listeners) {
                snapshot = new ArrayList<>(listeners);
            }
            for (MicOutputListener l : snapshot) {
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
