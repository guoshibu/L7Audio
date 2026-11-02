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

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
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
public class MicOutputController {

    private static final String TAG = "MicOutputController";
    private static final String PREF_PREFER_EXTERNAL = "mic_output_prefer_external";
    /** 静音检测轮询间隔（毫秒） */
    private static final int SILENCE_CHECK_INTERVAL_MS = 500;
    /** 静音检测启动宽限期（毫秒），此期间内录制未就绪不触发超时 */
    private static final long STARTUP_GRACE_MS = 3000;

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
    /** 用户偏好：车外模式（持久化，静音检测停止后恢复时使用）。
     *  默认 false（车内）：与主页默认显示保持一致，init() 会按真实输出模式或持久化值覆盖此值。 */
    private boolean preferExternal = false;

    // ========== 静音检测 ==========
    /** 静音检测线程 */
    private Thread silenceDetectorThread;
    /** 静音检测停止标记 */
    private volatile boolean stopSilenceDetection = false;

    // ========== UI 回调（主线程） ==========
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    /** RMS 值格式化器，复用避免每次创建 */
    private static final DecimalFormat RMS_FORMAT = new DecimalFormat("0.000000");
    /** 状态监听器列表（使用 CopyOnWriteArrayList 避免同步和副本创建） */
    private final List<MicOutputListener> listeners = new CopyOnWriteArrayList<>();
    /** 输出模式监听器列表（使用 CopyOnWriteArrayList） */
    private final List<OutputModeListener> outputModeListeners = new CopyOnWriteArrayList<>();

    /**
     * 喊话启停串行执行器。
     *
     * <p>启停会创建/释放 AudioRecord、AudioTrack 并初始化硬件 3A 效果器，
     * 属于百毫秒级重操作。统一放到单线程执行器上：
     * <ul>
     *   <li>悬浮窗按钮、麦克风页面点击等主线程入口不再同步初始化音频设备，避免 ANR</li>
     *   <li>串行化保证 start/stop 不会交错执行，防抖顺序与状态读写保持一致</li>
     * </ul>
     * 守护线程：进程退出时不需要显式关闭。
     */
    private final java.util.concurrent.ExecutorService toggleExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "MicOutputToggle");
                t.setDaemon(true);
                return t;
            });

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
     * 初始化控制器（线程安全，幂等）
     * <p>
     * 在 Application 或首次使用时调用，注入 ApplicationContext。
     * </p>
     *
     * @param context 上下文对象
     */
    public synchronized void init(Context context) {
        if (this.appContext != null) {
            AppLog.d(TAG, "已初始化，跳过");
            return;
        }
        android.content.Context appCtx = context.getApplicationContext();
        android.content.SharedPreferences prefs = appCtx.getSharedPreferences(
                appCtx.getPackageName() + "_preferences", Context.MODE_PRIVATE);
        this.micOutputConfig = new MicOutputConfig(prefs);
        this.appConfig = new AppConfig(appCtx);
        this.appContext = appCtx;
        // 修复首次安装状态不一致：喊话偏好（preferExternal）应与主页显示的车内/车外一致。
        // 首次安装时用户从未点过"仅车内/仅车外"，PREF_PREFER_EXTERNAL 尚未持久化；
        // 若此时硬编码默认车外，会导致"界面显示车内、点放大却切到车外"的错位。
        // 因此当偏好从未被写入时，从真实输出模式（AudioOutputManager，与主页按钮同源）派生偏好。
        if (prefs.contains(PREF_PREFER_EXTERNAL)) {
            // 用户此前已显式设置过偏好，直接读取持久化值
            this.preferExternal = prefs.getBoolean(PREF_PREFER_EXTERNAL, false);
        } else {
            // 首次安装/从未设置：以真实输出模式为准，避免与 UI 显示相互矛盾
            AudioOutputManager outputManager =
                    AudioServiceLocator.getInstance().getAudioOutputManager();
            int mode = (outputManager != null)
                    ? outputManager.getOutputMode()
                    : AudioOutputManager.OUTPUT_CAR;
            this.preferExternal = (mode == AudioOutputManager.OUTPUT_EXTERNAL);
        }
        AppLog.d(TAG, "车外喊话控制器初始化完成，preferExternal=" + preferExternal);
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
        AppLog.i(TAG, "preferExternal 设置为 " + prefer);
    }

    /**
     * 放大进行中时，同步"喊话结束后要恢复的输出模式"（savedOutputMode）
     * <p>
     * 场景：喊话（放大）启动瞬间会用 savedOutputMode 记下"放大前的输出模式"，
     * 用于结束后恢复。若用户在放大进行中又在主页手动切换了车内/车外，
     * 而 savedOutputMode 仍停留在启动时的旧快照，停止放大时就会用旧值把
     * 用户的新选择覆盖掉。此方法让主页切换时把最新选择同步进恢复目标。
     * </p>
     * <p>
     * 仅在正在放大（isAnnouncing）时生效；未放大时不做任何事，
     * 避免干扰下一次放大的正常快照流程。与 toggle/startAnnouncement/stopAnnouncement
     * 同为 synchronized，保证对 savedOutputMode 的读写互斥。
     * </p>
     *
     * @param mode 用户新选择的输出模式，{@link AudioOutputManager#OUTPUT_CAR} 或 {@link AudioOutputManager#OUTPUT_EXTERNAL}
     */
    public synchronized void syncSavedOutputMode(int mode) {
        if (isAnnouncing) {
            savedOutputMode = mode;
            AppLog.i(TAG, "放大进行中，恢复目标 savedOutputMode 同步为 " + mode);
        }
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
    public void toggle(boolean forceExternal, boolean showToast) {
        if (appContext == null) {
            AppLog.e(TAG, "控制器未初始化，请先调用 init()");
            return;
        }
        // 只做入队，调用线程（可能是主线程）不被音频设备初始化阻塞
        toggleExecutor.execute(() -> toggleInternal(forceExternal, showToast));
    }

    /**
     * 喊话状态切换的实际执行体（运行在串行执行器线程）
     *
     * <p>synchronized 与 {@link #syncSavedOutputMode(int)} 等主线程读写互斥，
     * 保证 savedOutputMode 状态一致；串行执行器则保证 start/stop 不交错。
     * </p>
     */
    private synchronized void toggleInternal(boolean forceExternal, boolean showToast) {
        long now = System.currentTimeMillis();
        int debounceInterval = micOutputConfig.getDebounceInterval();
        if (now - lastToggleTime < debounceInterval) {
            AppLog.w(TAG, "切换被忽略：防抖中 (interval=" + debounceInterval + "ms)");
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
     * 重置防抖时间戳，允许用户立即重试。
     *
     * <p>修复（P2-27）：toggle() 在通过防抖检查后立即消耗了 lastToggleTime，
     * 但启动喊话可能失败（管理器为空、麦克风启动失败等）。失败后用户想立刻重试，
     * 却被自己刚才那次失败请求"吃掉"了防抖窗口，按键 800ms 内毫无反馈。
     * 失败路径回滚时间戳，保证失败不惩罚用户。
     */
    private void resetDebounce() {
        lastToggleTime = 0L;
    }

    /**
     * 切换喊话状态（兼容旧接口，不显示Toast）
     */
    public void toggle(boolean forceExternal) {
        toggle(forceExternal, false);
    }

    /**
     * 立即停止喊话（不受防抖限制，同步执行）
     *
     * <p>供 TTS 播报、悬浮窗销毁等"必须立刻互斥"的场景使用：
     * 这些场景需要"停止完成后再继续"，走 {@link #toggle(boolean, boolean)} 的
     * 异步队列会与后续动作重叠（例如 TTS 已开始播报、麦克风还在外放）。
     * 方法返回时麦克风已停止、输出模式已恢复、焦点已释放。
     * </p>
     */
    public synchronized void stopImmediately() {
        if (!isAnnouncing) {
            return;
        }
        stopAnnouncement(false);
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

        AppLog.i(TAG, "========== 启动喊话() ==========");
        AppLog.i(TAG, "forceExternal=" + forceExternal + ", showToast=" + showToast);

        if (outputManager == null || focusManager == null || micManager == null) {
            AppLog.e(TAG, "必需的管理器为空，无法启动喊话");
            AppLog.e(TAG, "  outputManager=" + outputManager + ", focusManager=" + focusManager + ", micManager=" + micManager);
            // 修复（P2-27）：启动失败回滚防抖时间戳，允许用户立即重试
            resetDebounce();
            return;
        }

        // 停止 TTS 播放（互斥）
        try {
            if (locator.getTTSManager() != null && locator.getTTSManager().isSpeaking()) {
                locator.getTTSManager().stop();
                AppLog.d(TAG, "喊话前停止 TTS");
            }
        } catch (Exception e) {
            AppLog.e(TAG, "停止 TTS 失败", e);
        }

        // 记录当前输出模式（喊话结束后恢复）
        savedOutputMode = outputManager.getOutputMode();
        AppLog.i(TAG, "启动喊话: 保存输出模式=" + savedOutputMode + ", forceExternal=" + forceExternal);

        // 申请短暂独占焦点，音乐通过焦点回调自动暂停
        boolean granted = focusManager.requestTransientFocus();
        AppLog.d(TAG, "喊话启动，短暂焦点申请结果=" + granted);

        // forceExternal=true 强制车外；否则按用户偏好（MainActivity 的"仅车外/仅车内"）
        if (forceExternal || preferExternal) {
            outputManager.setOutputMode(AudioOutputManager.OUTPUT_EXTERNAL);
            AppLog.i(TAG, "切换输出模式为车外 (forceExternal=" + forceExternal + ", preferExternal=" + preferExternal + "), AudioTrack 将使用 usage=" + outputManager.getAudioUsage());
            notifyOutputModeChanged(AudioOutputManager.OUTPUT_EXTERNAL);
        }

        // 启动麦克风
        boolean started = micManager.start();
        if (!started) {
            AppLog.e(TAG, "麦克风启动失败，取消喊话");
            // 回滚：恢复输出模式和焦点
            if (savedOutputMode >= 0) {
                outputManager.setOutputMode(savedOutputMode);
                // 回滚必须对外通知，否则界面/悬浮窗仍显示"车外"，
                // 用户需要再手动切一次才能与实际输出方向一致
                notifyOutputModeChanged(savedOutputMode);
                savedOutputMode = -1;
            }
            focusManager.abandonTransientFocus();
            // 修复（P2-27）：麦克风启动失败回滚防抖时间戳，允许用户立即重试
            resetDebounce();
            // 修复（首次使用"点了没反应"）：原实现只写日志就 return，而悬浮窗入口
            // toggle(true) 的 showToast=false，用户完全看不到失败，且悬浮窗的自动收起
            // 定时器在点击时已被取消、不会再恢复。这里失败一律给可见反馈，并通知监听方
            // （notifyStateChanged(false) 会让悬浮窗重新武装自动收起定时器）
            notifyStateChanged(false);
            showToast("车外喊话启动失败：麦克风不可用，请检查车机麦克风权限/隐私开关后重试");
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
     * 流程：停止麦克风 → 停止静音检测 → 恢复输出模式 → 释放焦点 → 通知UI
     * </p>
     *
     * @param showToast true=显示Toast，false=不显示
     */
    private synchronized void stopAnnouncement(boolean showToast) {
        stopSilenceDetection();

        AudioServiceLocator locator = AudioServiceLocator.getInstance();
        AudioFocusManager focusManager = locator.getAudioFocusManager();
        MicrophoneManager micManager = locator.getMicrophoneManager();
        AudioOutputManager outputManager = locator.getAudioOutputManager();

        // 停止麦克风
        try {
            if (micManager != null) {
                micManager.stop();
            }
        } catch (Exception e) {
            AppLog.e(TAG, "停止麦克风失败", e);
        }

        isAnnouncing = false;

        // 恢复输出模式到喊话前的状态，并同步更新 UI 和音乐播放器
        if (outputManager != null && savedOutputMode >= 0) {
            outputManager.setOutputMode(savedOutputMode);
            AppLog.i(TAG, "停止喊话: 恢复输出模式为 " + savedOutputMode);
            notifyOutputModeChanged(savedOutputMode);
            try {
                com.aug32.l7audio.domain.audio.player.MusicPlayerManager musicManager = locator.getMusicPlayerManager();
                if (musicManager != null) {
                    musicManager.updateAudioOutputUsage(outputManager.getAudioUsage());
                }
            } catch (Exception e) {
                AppLog.e(TAG, "更新音乐播放器音频用法失败", e);
            }
            savedOutputMode = -1;
        }

        // 释放短暂独占焦点，音乐通过焦点回调自动恢复
        if (focusManager != null) {
            focusManager.abandonTransientFocus();
            AppLog.d(TAG, "喊话停止，释放短暂焦点");
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
            int checkIntervalMs = SILENCE_CHECK_INTERVAL_MS;
            long detectionStartTime = System.currentTimeMillis();
            long startupGraceMs = STARTUP_GRACE_MS;

            AppLog.d(TAG, "静音检测启动: timeout=" + timeoutMs + "ms, threshold=" + threshold + " gracePeriod=" + startupGraceMs + "ms");

            try {
                while (!stopSilenceDetection && isAnnouncing && !Thread.interrupted()) {
                    AudioServiceLocator locator = AudioServiceLocator.getInstance();
                    MicrophoneManager micManager = locator.getMicrophoneManager();
                    if (micManager == null) {
                        Thread.sleep(checkIntervalMs);
                        continue;
                    }
                    if (!micManager.isRecording()) {
                        // 修复（C4 兜底）：录制线程异常死亡时 isRecording 已被复位，
                        // 不能再 sleep+continue 空转（原实现下喊话永久假活：
                        // UI 显示喊话中、焦点与车外输出都被挂着，实际无声）。
                        // 宽限期内视为启动尚未就绪继续观察；宽限期外确认死亡 → 自动关闭
                        if (System.currentTimeMillis() - detectionStartTime < startupGraceMs) {
                            Thread.sleep(checkIntervalMs);
                            continue;
                        }
                        AppLog.w(TAG, "检测到录制已停止（录制线程异常退出），自动关闭喊话");
                        mainHandler.post(() -> {
                            // 先判空再调用：若用户已手动停止，不重复关闭、不弹"自动关闭"提示；
                            // stopImmediately 内部还有一次 synchronized 校验，防止与串行执行器上的 toggle 交错
                            if (!isAnnouncing) {
                                return;
                            }
                            stopImmediately();
                            notifyAutoClosed("录制异常终止");
                        });
                        break;
                    }

                    // 用区间峰值而不是瞬时平均：说话能量是稀疏尖峰（实测 p99=0.0435，
                    // 单帧峰值可达 0.32），10 帧平均会把"正在说话"稀释到阈值以下导致误关
                    float peakRms = micManager.getAndResetPeakRms();
                    long now = System.currentTimeMillis();
                    long elapsedSinceStart = now - detectionStartTime;
                    boolean inGracePeriod = elapsedSinceStart < startupGraceMs;

                    if (peakRms < threshold) {
                        if (inGracePeriod) {
                            silenceStartTime = 0;
                            AppLog.d(TAG, "静音检测: 峰值RMS=" + RMS_FORMAT.format(peakRms) + " < threshold=" + threshold + ", 在宽限期内 (" + (startupGraceMs - elapsedSinceStart) + "ms 剩余), 忽略");
                        } else if (silenceStartTime == 0) {
                            silenceStartTime = now;
                            AppLog.d(TAG, "静音检测: 峰值RMS=" + RMS_FORMAT.format(peakRms) + " < threshold=" + threshold + ", 静音开始, 将在 " + timeoutMs + "ms 后超时");
                        } else {
                            long silenceDuration = now - silenceStartTime;
                            long remainingMs = timeoutMs - silenceDuration;
                            if (remainingMs <= 0) {
                                AppLog.w(TAG, "静音超时，自动停止喊话。峰值RMS=" + RMS_FORMAT.format(peakRms) + ", silenceDuration=" + silenceDuration + "ms, timeout=" + timeoutMs + "ms");
                                mainHandler.post(() -> {
                                    // 修复：补上 isAnnouncing 校验。原实现无条件 stopAnnouncement，
                                    // 用户手动停止后延时任务仍会再停一次，导致输出模式二次恢复、焦点重复释放
                                    if (!isAnnouncing) {
                                        return;
                                    }
                                    stopImmediately();
                                    notifyAutoClosed("长时间无声音输入");
                                });
                                break;
                            } else if (remainingMs <= STARTUP_GRACE_MS || remainingMs % 2000 == 0) {  // 每 2s 打印一次日志
                                AppLog.d(TAG, "静音检测: 峰值RMS=" + RMS_FORMAT.format(peakRms) + " < threshold=" + threshold + ", elapsed=" + silenceDuration + "ms, remaining=" + remainingMs + "ms");
                            }
                        }
                    } else {
                        long previousDuration = silenceStartTime > 0 ? now - silenceStartTime : 0;
                        silenceStartTime = 0;
                        if (previousDuration > 0) {
                            AppLog.d(TAG, "静音检测: 峰值RMS=" + RMS_FORMAT.format(peakRms) + " >= threshold=" + threshold + ", 静音结束 (持续 " + previousDuration + "ms), 计时器重置");
                        }
                    }

                    Thread.sleep(checkIntervalMs);
                }
            } catch (InterruptedException e) {
                AppLog.d(TAG, "静音检测线程被中断");
                Thread.currentThread().interrupt();
            } finally {
                AppLog.d(TAG, "静音检测线程退出");
            }
        }, "SilenceDetectionThread");
        silenceDetectorThread.start();
    }

    /**
     * 停止静音检测线程
     */
    private void stopSilenceDetection() {
        AppLog.d(TAG, "停止静音检测");
        stopSilenceDetection = true;
        if (silenceDetectorThread != null) {
            silenceDetectorThread.interrupt();
            silenceDetectorThread = null;
        }
        AppLog.d(TAG, "静音检测已停止");
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
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * 移除状态监听器
     *
     * @param listener 监听器
     */
    public void removeListener(MicOutputListener listener) {
        listeners.remove(listener);
    }

    /**
     * 注册输出模式监听器
     *
     * @param listener 监听器
     */
    public void addOutputModeListener(OutputModeListener listener) {
        if (listener != null && !outputModeListeners.contains(listener)) {
            outputModeListeners.add(listener);
        }
    }

    /**
     * 移除输出模式监听器
     *
     * @param listener 监听器
     */
    public void removeOutputModeListener(OutputModeListener listener) {
        outputModeListeners.remove(listener);
    }

    /**
     * 对外广播"输出模式已变化"（供主页用户主动切换车内/车外时调用）
     * <p>
     * 修复缺陷：此前只有喊话导致的<b>被动</b>切换（start/stopAnnouncement）会 notify，
     * 而主页用户<b>主动</b>点"仅车内/仅车外"只刷新自身按钮、未对外通知，
     * 导致其它注册了 {@link OutputModeListener} 的界面无法同步（目前无可见影响，属一致性欠债）。
     * 本方法让主动切换与被动切换共用同一条同步通道，保证未来新增方向显示的界面也能一致更新。
     * </p>
     *
     * @param mode 当前输出模式，{@link AudioOutputManager#OUTPUT_CAR} 或 {@link AudioOutputManager#OUTPUT_EXTERNAL}
     */
    public void broadcastOutputModeChanged(int mode) {
        notifyOutputModeChanged(mode);
    }

    /**
     * 通知所有输出模式监听器（主线程）
     *
     * @param mode 当前输出模式
     */
    private void notifyOutputModeChanged(int mode) {
        mainHandler.post(() -> {
            for (OutputModeListener l : outputModeListeners) {
                try {
                    l.onOutputModeChanged(mode);
                } catch (Throwable t) {
                    AppLog.e(TAG, "输出模式监听器回调错误", t);
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
            for (MicOutputListener l : listeners) {
                try {
                    l.onAnnouncementStateChanged(announcing);
                } catch (Throwable t) {
                    AppLog.e(TAG, "监听器回调错误", t);
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
            for (MicOutputListener l : listeners) {
                try {
                    l.onAnnouncementAutoClosed(reason);
                } catch (Throwable t) {
                    AppLog.e(TAG, "监听器回调错误", t);
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
