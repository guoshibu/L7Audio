package com.aug32.l7audio.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.aug32.l7audio.data.local.AppConfig;
import com.aug32.l7audio.data.model.TTSItem;
import com.aug32.l7audio.domain.audio.AudioFocusManager;
import com.aug32.l7audio.domain.audio.AudioOutputManager;
import com.aug32.l7audio.domain.audio.AudioServiceLocator;
import com.aug32.l7audio.domain.audio.MusicPlayerManager;
import com.aug32.l7audio.domain.audio.TTSManager;
import com.aug32.l7audio.R;
import com.aug32.l7audio.ui.activity.MainActivity;
import com.aug32.l7audio.utils.AppLog;

/**
 * 悬浮窗服务
 * <p>
 * 职责说明：
 * 以 Android 前台服务形式运行，提供全局悬浮窗功能，包括：
 * - 悬浮球：可拖拽移动，点击展开/收起功能面板
 * - 功能面板：车外喊话、TTS快捷播放、透明度/宽度调节等
 * - TTS播放管理：加载用户配置的TTS项，支持一键播放
 * - 音频焦点管理：TTS/喊话与音乐播放的焦点协调
 * <p>
 * 设计意图：
 * - 前台服务 + 悬浮窗组合：在应用退到后台后仍可操作音频功能
 * - 悬浮球与列表分离：默认显示小球，展开后显示完整功能，减少视觉干扰
 * - 自动隐藏机制：10秒无操作自动收起列表，提升用户体验
 * - 主题适配：支持深色/浅色主题，跟随系统或用户设置
 * - 音频焦点协调：TTS/喊话与音乐互斥，通过AudioFocus自动管理
 *
 * @author L7Audio Team
 */
public class FloatingWindowService extends Service {
    // 日志标签
    private static final String TAG = "FloatingWindowService";
    // 通知渠道ID（Android O及以上必须）
    private static final String CHANNEL_ID = "l7audio_floating_window_service";
    // 前台服务通知ID，唯一标识该通知
    private static final int NOTIFICATION_ID = 2;

    // 窗口管理器，用于添加/移除/更新悬浮窗
    private WindowManager windowManager;
    // 悬浮球视图（收起状态显示）
    private View floatingBallView;
    // 悬浮窗列表视图（展开状态显示）
    private View floatingListView;
    // 应用配置，持久化存储用户设置
    private AppConfig appConfig;
    // 悬浮窗列表是否可见
    private boolean isListViewVisible = false;
    
    // 车外喊话状态
    private boolean isAnnouncing = false;
    // 车外喊话按钮
    private Button announcementBtn;
    // 喊话前保存的输出模式（喊话结束后恢复），-1表示无效值
    private int savedOutputModeBeforeAnnouncement = -1;
    // TTS 播放期间是否暂停了音乐（用于 TTS 结束时决定是否恢复焦点）
    private boolean ttsPausedMusic = false;
    // 音频焦点管理器，管理TTS/喊话与音乐的焦点协调
    private AudioFocusManager audioFocusManager;

    // Audio 组件服务定位器
    private AudioServiceLocator audioServiceLocator;
    
    // 存储当前播放的按钮，用于变色标识播放状态
    private Button currentPlayingBtn = null;

    // 设置面板展开/收起按钮
    private Button btnSettingsToggle;
    // 设置面板容器（包含透明度、宽度调节等）
    private LinearLayout llSettingsPanel;

    // 拖拽起始位置 X（记录按下时的位置）
    private int initialX;
    // 拖拽起始位置 Y
    private int initialY;
    // 手指按下时的屏幕 X 坐标
    private float initialTouchX;
    // 手指按下时的屏幕 Y 坐标
    private float initialTouchY;

    // 主线程 Handler，用于延迟任务和UI操作
    private Handler handler;
    // 自动隐藏悬浮窗列表的 Runnable
    private Runnable autoHideRunnable;
    // 重试设置TTS监听的 Runnable（TTSManager未就绪时延迟重试）
    private Runnable retrySetupTTSRunnable;

    /**
     * 服务创建时调用
     * <p>
     * 生命周期方法：在服务首次创建时执行一次。
     * 主要工作：
     * 1. 初始化配置、音频服务定位器、音频焦点管理器
     * 2. 创建通知渠道并启动前台服务
     * 3. 创建主线程 Handler 用于UI操作和延迟任务
     * 4. 展示悬浮球
     * 5. 设置自动隐藏Runnable和TTS监听重试Runnable
     * 6. 初始化TTS播放监听
     */
    @Override
    public void onCreate() {
        super.onCreate();
        AppLog.d(TAG, "Floating window service created");
        appConfig = new AppConfig(this);
        audioServiceLocator = AudioServiceLocator.getInstance();
        audioServiceLocator.init(this);
        audioFocusManager = audioServiceLocator.getAudioFocusManager();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        // 为什么用主线程Looper：悬浮窗操作必须在主线程，Handler也用于postDelayed延迟任务
        handler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
        showFloatingBall();
        autoHideRunnable = new Runnable() {
            @Override
            public void run() {
                hideListView();
            }
        };
        retrySetupTTSRunnable = new Runnable() {
            @Override
            public void run() {
                setupTTSListener();
            }
        };
        // 立即初始化 TTS 监听（不依赖悬浮窗列表是否打开）
        // 为什么 onCreate 就初始化：TTS播放监听是全局的，悬浮窗列表只是展示入口
        setupTTSListener();
    }

    /**
     * 处理服务启动命令
     * <p>
     * 每次通过 startService() 启动服务时都会调用此方法。
     * 目前主要用于接收主题变化通知并重新应用主题。
     * 返回 START_STICKY 表示服务被系统意外杀死后系统会自动尝试重建。
     *
     * @param intent  启动服务的 Intent，可能携带 action 用于指令传递
     * @param flags   启动标志位
     * @param startId 启动请求的唯一标识
     * @return 服务被杀死后的重启策略，此处返回 START_STICKY
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        AppLog.d(TAG, "Floating window service started");
        if (intent != null && "theme_changed".equals(intent.getAction())) {
            // 主题变化通知，重新应用
            AppLog.d(TAG, "Received theme changed notification");
            if (floatingListView != null) {
                applyThemeToListView();
                updateAnnouncementButton();
                loadTTSItems();
            }
            if (floatingBallView != null) {
                applyThemeToFloatingBall();
            }
        }
        return START_STICKY;
    }

    /**
     * 服务销毁时调用
     * <p>
     * 生命周期方法：在服务即将被销毁前执行。
     * 主要工作：
     * 1. 移除悬浮球和悬浮窗列表
     * 2. 移除所有延迟任务，防止内存泄漏
     * 3. 兜底停止车外喊话和TTS播放
     * 4. 释放音频焦点
     * <p>
     * 为什么要兜底停止：服务销毁可能由系统触发，此时用户可能处于喊话/TTS状态，
     * 必须确保资源释放，避免麦克风占用或音乐无法恢复。
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        AppLog.d(TAG, "Floating window service destroyed");
        if (floatingBallView != null) {
            windowManager.removeView(floatingBallView);
        }
        if (floatingListView != null) {
            windowManager.removeView(floatingListView);
        }
        if (handler != null && autoHideRunnable != null) {
            handler.removeCallbacks(autoHideRunnable);
        }
        if (handler != null && retrySetupTTSRunnable != null) {
            handler.removeCallbacks(retrySetupTTSRunnable);
        }
        // 兜底：若处于车外喊话/TTS 状态，强制停止并释放焦点
        try {
            if (isAnnouncing && audioServiceLocator.getMicrophoneManager() != null) {
                audioServiceLocator.getMicrophoneManager().stop();
            }
            if (audioServiceLocator.getTTSManager() != null && audioServiceLocator.getTTSManager().isSpeaking()) {
                audioServiceLocator.getTTSManager().stop();
            }
        } catch (Throwable ignore) {}
        ttsPausedMusic = false;
        if (audioFocusManager != null) {
            audioFocusManager.abandonTransientFocus();
        }
    }

    /**
     * 绑定服务接口
     * <p>
     * 此服务不提供绑定模式，仅以 startService 方式启动，
     * 因此直接返回 null 表示不支持绑定。
     *
     * @param intent 绑定服务的 Intent
     * @return 始终返回 null，表示不提供绑定
     */
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * 系统配置变化时调用
     * <p>
     * 当系统配置（如主题、语言、方向等）发生变化时回调，
     * 此处用于在系统主题切换时重新应用悬浮窗的主题样式。
     *
     * @param newConfig 新的配置信息
     */
    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        AppLog.d(TAG, "Configuration changed, reapplying theme to floating window");
        // 重新应用主题
        if (floatingListView != null) {
            applyThemeToListView();
            updateAnnouncementButton();
            // 重新加载TTS项，让它们也应用新主题
            loadTTSItems();
        }
        if (floatingBallView != null) {
            applyThemeToFloatingBall();
        }
    }
    
    /**
     * 通知悬浮窗服务主题已变化
     * <p>
     * 供外部组件（如 MainActivity）调用，通过 startService 发送带有 action 的 Intent，
     * 通知悬浮窗服务重新应用主题样式。
     * 使用 startService 而不是广播或绑定的方式：
     * - 服务本身就是前台服务，通过 startService 通信最简单直接
     * - 不需要建立连接，调用即忘
     *
     * @param context 上下文，用于启动服务
     */
    public static void notifyThemeChanged(Context context) {
        Intent intent = new Intent(context, FloatingWindowService.class);
        intent.setAction("theme_changed");
        context.startService(intent);
    }

    /** 判断当前是否是深色主题（跟随系统） */
    private boolean isDarkTheme() {
        int themeMode = appConfig.getThemeMode();
        if (themeMode == AppConfig.THEME_MODE_DARK) {
            return true;
        } else if (themeMode == AppConfig.THEME_MODE_LIGHT) {
            return false;
        } else {
            // 跟随系统主题
            int nightModeFlags = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            return nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        }
    }

    /** 创建通知渠道（仅 Android O 及以上生效） */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "L7Audio 悬浮窗服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("保持悬浮窗服务活跃");
            channel.setSound(null, null);
            channel.setVibrationPattern(null);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    /** 构建前台服务使用的 Notification */
    private Notification createNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_nav_home)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("悬浮窗服务运行中")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setSilent(true);

        return builder.build();
    }

    /** 展示悬浮球，附带点击切换与拖拽移动逻辑 */
    private void showFloatingBall() {
        if (floatingBallView != null) {
            return;
        }

        floatingBallView = LayoutInflater.from(this).inflate(R.layout.view_floating_ball, null);
        
        // 为什么区分 TYPE_APPLICATION_OVERLAY 和 TYPE_PHONE：
        // Android O (API 26) 开始 TYPE_PHONE 被废弃，必须使用 TYPE_APPLICATION_OVERLAY
        // 才能在其他应用之上显示悬浮窗
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? 
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : 
                    WindowManager.LayoutParams.TYPE_PHONE,
                // FLAG_NOT_FOCUSABLE：悬浮窗不获取焦点，避免抢占输入法等焦点
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = appConfig.getFloatingWindowX();
        params.y = appConfig.getFloatingWindowY();

        // 根据主题设置悬浮球的颜色
        applyThemeToFloatingBall();

        windowManager.addView(floatingBallView, params);

        Button btnBall = floatingBallView.findViewById(R.id.btn_floating_ball);
        btnBall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleListView();
            }
        });

        btnBall.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // 记录按下时的初始位置，用于计算拖拽偏移量
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        // 返回 false：不消费事件，让 onClick 等后续事件继续传递
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        // 根据手指移动距离更新悬浮窗位置
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingBallView, params);
                        return false;
                    case MotionEvent.ACTION_UP:
                        // 松手后保存位置到配置，下次启动时恢复
                        appConfig.setFloatingWindowX(params.x);
                        appConfig.setFloatingWindowY(params.y);
                        return false;
                }
                return false;
            }
        });
    }

    /** 根据主题设置悬浮球的背景色与文字色 */
    private void applyThemeToFloatingBall() {
        if (floatingBallView == null) {
            return;
        }

        Button btnBall = floatingBallView.findViewById(R.id.btn_floating_ball);
        applyButtonTheme(btnBall,
                R.drawable.floating_button_dark_selected,
                R.drawable.floating_button_light);
    }

    /** 根据主题设置悬浮窗列表内各控件的颜色 */
    private void applyThemeToListView() {
        if (floatingListView == null) {
            return;
        }

        boolean isDark = isDarkTheme();

        floatingListView.setBackgroundResource(isDark
                ? R.drawable.floating_list_bg_dark
                : R.drawable.floating_list_bg_light);

        applyTextTheme((TextView) floatingListView.findViewById(R.id.tv_title));
        applyButtonTheme((Button) floatingListView.findViewById(R.id.btn_close_list),
                R.drawable.floating_button_dark, R.drawable.floating_button_light);
        applyButtonTheme((Button) floatingListView.findViewById(R.id.btn_settings_toggle),
                R.drawable.floating_button_dark, R.drawable.floating_button_light);
        applyTextTheme((TextView) floatingListView.findViewById(R.id.tv_alpha_label));
        applyTextTheme((TextView) floatingListView.findViewById(R.id.tv_width_label));
        applyButtonTheme((Button) floatingListView.findViewById(R.id.btn_announcement),
                R.drawable.floating_button_dark_selected, R.drawable.floating_button_light);
        applyButtonTheme((Button) floatingListView.findViewById(R.id.btn_select_tts),
                R.drawable.floating_button_dark, R.drawable.floating_button_light);
        applyTextTheme((TextView) floatingListView.findViewById(R.id.tv_tts_list_label));
    }

    /** 切换悬浮窗列表的显示/隐藏 */
    private void toggleListView() {
        AppLog.d(TAG, "toggleListView called, isListViewVisible=" + isListViewVisible);
        if (isListViewVisible) {
            hideListView();
        } else {
            showListView();
        }
    }

    /** 展示悬浮窗列表，移除悬浮球并加载 TTS 项 */
    private void showListView() {
        AppLog.d(TAG, "showListView called");
        if (floatingListView != null) {
            AppLog.d(TAG, "floatingListView already exists, returning");
            return;
        }

        try {
            floatingListView = LayoutInflater.from(this).inflate(R.layout.view_floating_list, null);

            // 从配置读取宽度（dp），范围 240-480dp，默认 336dp
            final int widthDp = appConfig.getFloatingWindowWidthDp();
            final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    (int) (widthDp * getResources().getDisplayMetrics().density),
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? 
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : 
                        WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            params.y = 100;

            int alpha = appConfig.getFloatingWindowAlpha();
            // alpha取值范围0-100，除以100转换为0.0-1.0的透明度值
            floatingListView.setAlpha(alpha / 100.0f);

            windowManager.addView(floatingListView, params);
            isListViewVisible = true;
            AppLog.d(TAG, "floatingListView added to windowManager");

            floatingListView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            initialX = params.x;
                            initialY = params.y;
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();
                            return false;
                        case MotionEvent.ACTION_MOVE:
                            params.x = initialX + (int) (event.getRawX() - initialTouchX);
                            params.y = initialY + (int) (event.getRawY() - initialTouchY);
                            windowManager.updateViewLayout(floatingListView, params);
                            return false;
                        case MotionEvent.ACTION_UP:
                            return false;
                    }
                    return false;
                }
            });

            // 为什么先加列表再移悬浮球：避免出现短暂的空白期，
            // 让用户感觉是平滑切换，而不是先消失再出现
            if (floatingBallView != null) {
                windowManager.removeView(floatingBallView);
                floatingBallView = null;
                AppLog.d(TAG, "floatingBallView removed");
            }

            setupListView();
            AppLog.d(TAG, "setupListView done");

            // 应用主题颜色
            applyThemeToListView();
            AppLog.d(TAG, "applyThemeToListView done");
            
            // 更新车外喊话按钮状态
            updateAnnouncementButton();

            resetAutoHideTimer();
        } catch (Exception e) {
            AppLog.e(TAG, "Error in showListView", e);
            floatingListView = null;
            isListViewVisible = false;
        }
    }

    /** 隐藏悬浮窗列表并恢复显示悬浮球 */
    private void hideListView() {
        AppLog.d(TAG, "hideListView called");
        try {
            if (floatingListView != null) {
                windowManager.removeView(floatingListView);
                floatingListView = null;
                AppLog.d(TAG, "floatingListView removed");
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Error removing floatingListView", e);
            floatingListView = null;
        }
        
        isListViewVisible = false;
        
        if (handler != null && autoHideRunnable != null) {
            handler.removeCallbacks(autoHideRunnable);
        }
        
        showFloatingBall();
        AppLog.d(TAG, "hideListView done");
    }

    /** 重置自动隐藏悬浮窗列表的定时器 */
    private void resetAutoHideTimer() {
        if (handler != null && autoHideRunnable != null) {
            // 先移除之前的任务，避免多个定时器叠加
            handler.removeCallbacks(autoHideRunnable);
            // 为什么喊话时不自动收起：喊话时用户可能需要操作界面，
            // 自动收起会打断用户操作体验
            if (isAnnouncing) {
                AppLog.d(TAG, "resetAutoHideTimer: 车外喊话中，跳过自动收起");
                return;
            }
            // 10秒无操作自动收起，减少悬浮窗对用户的视觉干扰
            handler.postDelayed(autoHideRunnable, 10000);
        }
    }

    /** 取消自动隐藏悬浮窗列表的定时器 */
    private void cancelAutoHideTimer() {
        if (handler != null && autoHideRunnable != null) {
            handler.removeCallbacks(autoHideRunnable);
        }
    }

    /** 初始化悬浮窗列表的控件点击、透明度控制和 TTS 相关监听 */
    private void setupListView() {
        Button btnClose = floatingListView.findViewById(R.id.btn_close_list);
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideListView();
            }
        });

        // 透明度调节：下限 20%，低于 20 自动回弹到 20
        // 为什么有下限：防止透明度太低导致用户找不到悬浮窗
        SeekBar sbAlpha = floatingListView.findViewById(R.id.sb_alpha);
        int savedAlpha = appConfig.getFloatingWindowAlpha();
        if (savedAlpha < 20) savedAlpha = 20;
        sbAlpha.setProgress(savedAlpha);
        sbAlpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int effectiveProgress = progress;
                if (effectiveProgress < 20) {
                    effectiveProgress = 20;
                    // 为什么只在fromUser时回弹：避免程序设置进度时造成死循环
                    if (fromUser) seekBar.setProgress(20);
                }
                if (floatingListView != null) {
                    floatingListView.setAlpha(effectiveProgress / 100.0f);
                }
                resetAutoHideTimer();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // 开始拖动时取消自动隐藏，避免用户拖到一半悬浮窗消失
                cancelAutoHideTimer();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int finalProgress = seekBar.getProgress();
                if (finalProgress < 20) finalProgress = 20;
                appConfig.setFloatingWindowAlpha(finalProgress);
                resetAutoHideTimer();
            }
        });

        // 设置按钮：展开/收起设置面板（透明度 + 宽度）
        btnSettingsToggle = floatingListView.findViewById(R.id.btn_settings_toggle);
        llSettingsPanel = floatingListView.findViewById(R.id.ll_settings_panel);
        btnSettingsToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (llSettingsPanel != null) {
                    if (llSettingsPanel.getVisibility() == View.VISIBLE) {
                        llSettingsPanel.setVisibility(View.GONE);
                    } else {
                        llSettingsPanel.setVisibility(View.VISIBLE);
                    }
                }
                resetAutoHideTimer();
            }
        });

        // 宽度调节：SeekBar progress 0-240 对应 240-480dp 宽度
        // 为什么是240-480dp：太窄显示不下内容，太宽遮挡屏幕
        final SeekBar sbWidth = floatingListView.findViewById(R.id.sb_width);
        int currentWidth = appConfig.getFloatingWindowWidthDp();
        sbWidth.setProgress(currentWidth - 240); // 默认 336dp → progress=96
        sbWidth.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (floatingListView != null && windowManager != null) {
                    int newWidthDp = 240 + progress;
                    // 为什么要乘以density：LayoutParams.width使用像素单位，需要将dp转换为px
                    WindowManager.LayoutParams layoutParams = (WindowManager.LayoutParams) floatingListView.getLayoutParams();
                    layoutParams.width = (int) (newWidthDp * getResources().getDisplayMetrics().density);
                    windowManager.updateViewLayout(floatingListView, layoutParams);
                }
                resetAutoHideTimer();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // 开始拖动时取消自动隐藏
                cancelAutoHideTimer();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // 松手时才保存配置，避免频繁写入
                appConfig.setFloatingWindowWidthDp(240 + seekBar.getProgress());
                resetAutoHideTimer();
            }
        });

        announcementBtn = floatingListView.findViewById(R.id.btn_announcement);
        announcementBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelAutoHideTimer();
                toggleAnnouncement();
            }
        });

        Button btnSelectTTS = floatingListView.findViewById(R.id.btn_select_tts);
        btnSelectTTS.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetAutoHideTimer();
                openMainActivityToSelectTTS();
            }
        });

        // 设置TTS播放监听
        setupTTSListener();

        loadTTSItems();
    }

    /** 设置 TTS 播放完成/出错时的监听，通过音频焦点自动恢复音乐 */
    private void setupTTSListener() {
        TTSManager ttsManager = audioServiceLocator.getTTSManager();
        if (ttsManager == null) {
            AppLog.w(TAG, "setupTTSListener: TTSManager is null, will retry later");
            // 为什么延迟重试：TTSManager 可能还在初始化中，延迟500ms再试
            // 为什么要先 removeCallbacks：避免多次重试排队，确保只有一个重试任务在等待
            if (handler != null && retrySetupTTSRunnable != null) {
                handler.removeCallbacks(retrySetupTTSRunnable);
                handler.postDelayed(retrySetupTTSRunnable, 500);
            }
            return;
        }
        ttsManager.setProgressListener(new TTSManager.TTSProgressListener() {
            @Override
            public void onTTSStart() {
                AppLog.d(TAG, "TTS started speaking");
            }

            @Override
            public void onTTSDone() {
                AppLog.d(TAG, "TTS finished speaking");
                // 为什么用 handler.post：TTS回调可能在子线程，UI操作必须切回主线程
                handler.post(() -> {
                    updateTTSButtonColors(null);
                    // 仅在 TTS 播放时申请过焦点的情况下释放焦点恢复音乐
                    // 为什么要判断 ttsPausedMusic：如果是车内播放TTS，音乐不会暂停，也就不需要恢复
                    if (ttsPausedMusic && audioFocusManager != null) {
                        audioFocusManager.abandonTransientFocus();
                        ttsPausedMusic = false;
                        AppLog.d(TAG, "Abandoned transient focus after TTS done");
                    }
                    // TTS 完成后重启自动收起定时器
                    resetAutoHideTimer();
                });
            }

            @Override
            public void onTTSError() {
                AppLog.w(TAG, "TTS error");
                handler.post(() -> {
                    updateTTSButtonColors(null);
                    // 出错也要释放焦点，否则音乐可能一直暂停
                    if (ttsPausedMusic && audioFocusManager != null) {
                        audioFocusManager.abandonTransientFocus();
                        ttsPausedMusic = false;
                        AppLog.d(TAG, "Abandoned transient focus after TTS error");
                    }
                    // TTS 出错后也重启自动收起定时器
                    resetAutoHideTimer();
                });
            }

            @Override
            public void onTTSProgress(int progress) {}
        });
        AppLog.d(TAG, "TTS progress listener registered successfully");
    }

    /** 切换车外喊话的开启/关闭，基于音频焦点自动管理音乐暂停恢复 */
    private void toggleAnnouncement() {
        AudioOutputManager outputManager = audioServiceLocator.getAudioOutputManager();
        if (outputManager == null) {
            AppLog.w(TAG, "AudioOutputManager is null, cannot toggle announcement");
            return;
        }

        if (isAnnouncing) {
            // 停止喊话
            try {
                if (audioServiceLocator.getMicrophoneManager() != null) {
                    audioServiceLocator.getMicrophoneManager().stop();
                }
            } catch (Exception e) {
                AppLog.e(TAG, "Failed to stop microphone", e);
            }
            isAnnouncing = false;

            // 为什么先恢复输出模式再释放焦点：
            // 先恢复输出模式，再释放焦点让音乐恢复，确保音乐从正确的输出设备播放
            if (outputManager != null && savedOutputModeBeforeAnnouncement >= 0) {
                outputManager.setOutputMode(savedOutputModeBeforeAnnouncement);
                AppLog.d(TAG, "Restored output mode to: " + savedOutputModeBeforeAnnouncement);
                savedOutputModeBeforeAnnouncement = -1;
            }

            // 释放短暂独占焦点，MusicPlayerManager 通过焦点回调自动恢复音乐
            // 为什么不用手动恢复音乐：通过音频焦点机制统一管理，避免状态不一致
            if (audioFocusManager != null) {
                audioFocusManager.abandonTransientFocus();
                AppLog.d(TAG, "Announcement stopped, abandoned transient focus");
            }

            // 喊话结束后重启自动收起定时器
            resetAutoHideTimer();
        } else {
            // 停止 TTS 播放（互斥）
            // 为什么互斥：车外喊话和TTS都用车外喇叭，同时播放会混乱
            try {
                if (audioServiceLocator.getTTSManager() != null && audioServiceLocator.getTTSManager().isSpeaking()) {
                    audioServiceLocator.getTTSManager().stop();
                    updateTTSButtonColors(null);
                }
            } catch (Exception e) {
                AppLog.e(TAG, "Failed to stop TTS", e);
            }

            // 记录当前输出模式（喊话结束后恢复）
            if (outputManager != null) {
                savedOutputModeBeforeAnnouncement = outputManager.getOutputMode();
            }

            // 申请短暂独占焦点：MusicPlayerManager 通过 onFocusLostTransient 回调自动暂停音乐
            // 为什么用音频焦点而不是直接暂停音乐：统一的焦点管理机制，
            // 避免多个组件同时操作音乐播放状态导致混乱
            if (audioFocusManager != null) {
                boolean granted = audioFocusManager.requestTransientFocus();
                AppLog.d(TAG, "Announcement started, transient focus granted=" + granted);
            }

            // 直接切换输出模式到车外（不调用 setAudioOutput 避免 stop/restart 音乐）
            // 为什么不用 setAudioOutput：setAudioOutput 可能会停止并重启音乐播放，
            // 而我们只想切换输出通道，让音乐通过焦点机制自然暂停
            if (outputManager != null) {
                outputManager.setOutputMode(AudioOutputManager.OUTPUT_EXTERNAL);
                AppLog.d(TAG, "Switched output mode to external");
            }

            try {
                if (audioServiceLocator.getMicrophoneManager() != null) {
                    audioServiceLocator.getMicrophoneManager().start();
                }
            } catch (Exception e) {
                AppLog.e(TAG, "Failed to start mic amplification", e);
            }
            isAnnouncing = true;
        }

        updateAnnouncementButton();
    }
    
    /** 根据当前是否在喊话状态更新喊话按钮的 UI */
    private void updateAnnouncementButton() {
        if (announcementBtn == null) {
            return;
        }

        if (isAnnouncing) {
            announcementBtn.setText("正在喊话");
            applyButtonAccent(announcementBtn);
        } else {
            announcementBtn.setText("车外喊话");
            applyButtonTheme(announcementBtn,
                    R.drawable.floating_button_dark_selected,
                    R.drawable.floating_button_light);
        }
    }

    /** 打开 MainActivity 以便用户选择、编辑 TTS 项 */
    private void openMainActivityToSelectTTS() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("navigate_to_tts", true);
        AppLog.d(TAG, "openMainActivityToSelectTTS: 启动 MainActivity 并导航到 TTS");
        startActivity(intent);
        hideListView();
    }

    /** 从 AppConfig 加载 TTS 项并渲染到悬浮窗列表中 */
    private void loadTTSItems() {
        AppLog.d(TAG, "loadTTSItems called");
        
        if (floatingListView == null) {
            AppLog.d(TAG, "floatingListView is null");
            return;
        }
        
        LinearLayout ttsContainer = floatingListView.findViewById(R.id.ll_tts_items);
        if (ttsContainer == null) {
            AppLog.d(TAG, "ttsContainer is null");
            return;
        }
        
        ttsContainer.removeAllViews();

        String ttsItemsJson = appConfig.getTTSItems();
        String indicesJson = appConfig.getFloatingWindowTTSIndices();
        String namesJson = appConfig.getFloatingWindowTTSNames();

        AppLog.d(TAG, "ttsItemsJson length: " + (ttsItemsJson != null ? ttsItemsJson.length() : "null"));
        AppLog.d(TAG, "indicesJson: " + indicesJson);
        AppLog.d(TAG, "namesJson: " + namesJson);

        if (ttsItemsJson == null || ttsItemsJson.isEmpty()) {
            AppLog.d(TAG, "TTS items is empty, no data to load");
            return;
        }

        if (indicesJson == null || indicesJson.isEmpty() || "[]".equals(indicesJson)) {
            AppLog.d(TAG, "Indices is empty or [], no selected TTS items");
            return;
        }

        try {
            Gson gson = new Gson();
            List<TTSItem> allItems = gson.fromJson(ttsItemsJson, new TypeToken<List<TTSItem>>(){}.getType());
            List<Integer> selectedIndices = gson.fromJson(indicesJson, new TypeToken<List<Integer>>(){}.getType());
            Map<Integer, String> customNames = gson.fromJson(namesJson, new TypeToken<Map<Integer, String>>(){}.getType());

            AppLog.d(TAG, "Parsed allItems: " + (allItems != null ? allItems.size() : "null"));
            AppLog.d(TAG, "Parsed selectedIndices: " + (selectedIndices != null ? selectedIndices.size() : "null"));

            if (allItems == null || allItems.isEmpty()) {
                AppLog.d(TAG, "TTS items list is null or empty after parse");
                return;
            }

            if (selectedIndices == null || selectedIndices.isEmpty()) {
                AppLog.d(TAG, "Selected indices list is null or empty after parse");
                return;
            }

            if (customNames == null) {
                customNames = new HashMap<>();
            }

            AppLog.d(TAG, "Loading " + selectedIndices.size() + " TTS items from " + allItems.size() + " available");
            
            for (int i = 0; i < selectedIndices.size(); i++) {
                int index = selectedIndices.get(i);
                AppLog.d(TAG, "Processing index " + i + ": value=" + index);
                
                if (index >= 0 && index < allItems.size()) {
                    TTSItem item = allItems.get(index);
                    String text = item.text;
                    String customName = customNames.containsKey(index) ? customNames.get(index) : item.customName;
                    String displayName = (customName != null && !customName.isEmpty()) ? customName : item.getDisplayName();
                    AppLog.d(TAG, "Adding TTS item: " + displayName);
                    addTTSItemToView(ttsContainer, displayName, index, text);
                } else {
                    AppLog.d(TAG, "Invalid index: " + index + " (valid range: 0-" + (allItems.size() - 1) + ")");
                }
            }
            
            AppLog.d(TAG, "TTS items added to container: " + ttsContainer.getChildCount());
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to load TTS items", e);
        }
    }

    /** 将单个 TTS 项添加到悬浮窗列表（含播放/删除按钮） */
    private void addTTSItemToView(LinearLayout container, String displayName, final int index, final String originalText) {
        View itemView = LayoutInflater.from(this).inflate(R.layout.item_floating_tts, container, false);
        
        final Button btnPlay = itemView.findViewById(R.id.btn_play_tts);
        btnPlay.setText(displayName);
        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelAutoHideTimer();
                playTTS(originalText, btnPlay);
            }
        });

        Button btnDelete = itemView.findViewById(R.id.btn_delete_tts);
        btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetAutoHideTimer();
                removeTTSIndex(index);
            }
        });

        // 根据主题设置按钮颜色
        applyTTSButtonColor(btnPlay, btnDelete, false);

        container.addView(itemView);
    }

    /** 播放 TTS 文本。智能策略：音乐在车外播放时才暂停音乐，车内播放时不暂停。TTS 始终独立从车外输出。 */
    private void playTTS(String text, Button clickedBtn) {
        AudioOutputManager outputManager = audioServiceLocator.getAudioOutputManager();
        int currentMode = (outputManager != null) ? outputManager.getOutputMode() : AudioOutputManager.OUTPUT_EXTERNAL;
        // 只有音乐在车外模式时才暂停（TTS和音乐都用车外喇叭时会冲突）
        // 为什么车内模式不暂停：车内喇叭和车外喇叭是独立的，TTS从车外输出不会干扰车内音乐
        ttsPausedMusic = (currentMode == AudioOutputManager.OUTPUT_EXTERNAL);

        // 停止车外喊话（互斥）
        // 为什么互斥：都用车外喇叭，同时播放会导致声音重叠混乱
        if (isAnnouncing) {
            toggleAnnouncement();
        }

        // 仅在车外模式时申请焦点暂停音乐；车内模式时不影响音乐播放
        // 为什么用音频焦点而不是直接暂停：通过统一的焦点机制管理，
        // 音乐播放器通过焦点回调处理暂停/恢复，状态更一致
        if (ttsPausedMusic && audioFocusManager != null) {
            boolean granted = audioFocusManager.requestTransientFocus();
            AppLog.d(TAG, "playTTS: currentMode=EXTERNAL, transient focus request granted=" + granted);
        } else {
            AppLog.d(TAG, "playTTS: currentMode=" + currentMode + ", will not pause music");
        }

        // 使用独立的车外 usage 播报 TTS，不影响全局输出模式
        // 为什么不用切换全局输出模式：TTS只是临时播报，
        // 用独立的usage可以直接从车外输出，不改变音乐的输出通道
        try {
            if (audioServiceLocator.getTTSManager() != null) {
                int externalUsage = appConfig.getAudioOutputUsageExternal();
                boolean success = audioServiceLocator.getTTSManager().speakWithUsage(text, externalUsage);
                AppLog.d(TAG, "playTTS: speakWithUsage result=" + success + ", text=\"" + text + "\", usage=" + externalUsage);
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to speak TTS text", e);
            // TTS 失败时，如果之前申请了焦点则释放
            // 为什么必须释放：如果不释放，音乐可能一直处于暂停状态
            if (ttsPausedMusic && audioFocusManager != null) {
                audioFocusManager.abandonTransientFocus();
                ttsPausedMusic = false;
            }
        }

        // 更新按钮颜色
        updateTTSButtonColors(clickedBtn);
    }
    
    /** 更新 TTS 按钮的颜色，高亮当前播放中的按钮 */
    private void updateTTSButtonColors(Button newPlayingBtn) {
        if (currentPlayingBtn != null && currentPlayingBtn != newPlayingBtn) {
            applyTTSPlayButtonTheme(currentPlayingBtn, false);
        }

        if (newPlayingBtn != null) {
            applyTTSPlayButtonTheme(newPlayingBtn, true);
        }

        currentPlayingBtn = newPlayingBtn;
    }

    /** 根据播放状态更新单个 TTS 播放按钮的颜色 */
    private void applyTTSPlayButtonTheme(Button button, boolean isPlaying) {
        if (isPlaying) {
            applyButtonAccent(button);
        } else {
            applyButtonTheme(button,
                    R.drawable.floating_button_dark_selected,
                    R.drawable.floating_button_light);
        }
    }

    /** 根据主题为单个 TTS 项的播放/删除按钮设置颜色 */
    private void applyTTSButtonColor(Button btnPlay, Button btnDelete, boolean isPlaying) {
        applyTTSPlayButtonTheme(btnPlay, isPlaying);
        applyButtonTheme(btnDelete,
                R.drawable.floating_button_dark,
                R.drawable.floating_button_light);
    }

    /** 从配置中移除指定索引的 TTS 项并重新渲染列表 */
    private void removeTTSIndex(int indexToRemove) {
        try {
            String indicesJson = appConfig.getFloatingWindowTTSIndices();
            Gson gson = new Gson();
            List<Integer> selectedIndices = gson.fromJson(indicesJson, new TypeToken<List<Integer>>(){}.getType());
            
            if (selectedIndices != null) {
                selectedIndices.remove(Integer.valueOf(indexToRemove));
                String newIndicesJson = gson.toJson(selectedIndices);
                appConfig.setFloatingWindowTTSIndices(newIndicesJson);
                
                String namesJson = appConfig.getFloatingWindowTTSNames();
                Map<Integer, String> customNames = gson.fromJson(namesJson, new TypeToken<Map<Integer, String>>(){}.getType());
                if (customNames != null) {
                    customNames.remove(indexToRemove);
                    String newNamesJson = gson.toJson(customNames);
                    appConfig.setFloatingWindowTTSNames(newNamesJson);
                }
                
                loadTTSItems();
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to remove TTS index", e);
        }
    }

    /**
     * 启动悬浮窗服务
     * <p>
     * 根据 Android 系统版本自动选择合适的启动方式：
     * - Android O (API 26) 及以上：使用 startForegroundService()
     * - Android O 以下：使用普通的 startService()
     * <p>
     * 为什么需要区分版本：Android O 及以上对后台服务有严格限制，
     * 前台服务必须通过 startForegroundService() 启动，并在5秒内调用 startForeground()，
     * 否则会触发 ANR。
     *
     * @param context 上下文，用于启动服务
     */
    public static void start(Context context) {
        Intent intent = new Intent(context, FloatingWindowService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
        AppLog.d(TAG, "Floating window service started");
    }

    /**
     * 停止悬浮窗服务
     * <p>
     * 通过 stopService() 停止服务，服务销毁时会：
     * 1. 移除所有悬浮窗
     * 2. 停止车外喊话/TTS播放
     * 3. 释放音频焦点
     * 4. 移除所有延迟任务
     *
     * @param context 上下文，用于停止服务
     */
    public static void stop(Context context) {
        Intent intent = new Intent(context, FloatingWindowService.class);
        context.stopService(intent);
        AppLog.d(TAG, "Floating window service stopped");
    }

    // ==================== 主题工具方法 ====================

    /** 为按钮应用主题背景和文字色 */
    private void applyButtonTheme(Button button, int darkBgRes, int lightBgRes) {
        if (button == null) return;
        boolean isDark = isDarkTheme();
        button.setBackgroundResource(isDark ? darkBgRes : lightBgRes);
        button.setBackgroundTintList(null);
        button.setTextColor(isDark
                ? getResources().getColor(android.R.color.white)
                : getResources().getColor(android.R.color.black));
    }

    /** 为按钮设置强调色（播放中/选中状态） */
    private void applyButtonAccent(Button button) {
        if (button == null) return;
        button.setBackgroundTintList(androidx.core.content.ContextCompat.getColorStateList(this, R.color.colorAccent));
        button.setTextColor(getResources().getColor(android.R.color.white));
    }

    /** 为 TextView 应用主题文字色 */
    private void applyTextTheme(TextView textView) {
        if (textView == null) return;
        textView.setTextColor(isDarkTheme()
                ? getResources().getColor(android.R.color.white)
                : getResources().getColor(android.R.color.black));
    }
}
