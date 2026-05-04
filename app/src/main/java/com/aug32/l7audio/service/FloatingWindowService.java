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

import com.aug32.l7audio.AppConfig;
import com.aug32.l7audio.AppLog;
import com.aug32.l7audio.MainActivity;
import com.aug32.l7audio.R;
import com.aug32.l7audio.TTSFragment;
import com.aug32.l7audio.audio.TTSManager;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FloatingWindowService extends Service {
    private static final String TAG = "FloatingWindowService";
    private static final String CHANNEL_ID = "l7audio_floating_window_service";
    private static final int NOTIFICATION_ID = 2;

    private WindowManager windowManager;
    private View floatingBallView;
    private View floatingListView;
    private AppConfig appConfig;
    private boolean isListViewVisible = false;
    
    // 车外喊话状态
    private boolean isAnnouncing = false;
    private Button announcementBtn;
    
    // 存储当前播放的按钮，用于变色
    private Button currentPlayingBtn = null;

    private int initialX;
    private int initialY;
    private float initialTouchX;
    private float initialTouchY;

    private Handler handler;
    private Runnable autoHideRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        AppLog.d(TAG, "Floating window service created");
        appConfig = new AppConfig(this);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
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
    }

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
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
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
    
    // 公共方法，让MainActivity可以通知主题变化
    public static void notifyThemeChanged(Context context) {
        Intent intent = new Intent(context, FloatingWindowService.class);
        intent.setAction("theme_changed");
        context.startService(intent);
    }

    /**
     * 判断当前是否是深色主题
     */
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

    private void showFloatingBall() {
        if (floatingBallView != null) {
            return;
        }

        floatingBallView = LayoutInflater.from(this).inflate(R.layout.view_floating_ball, null);
        
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? 
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : 
                    WindowManager.LayoutParams.TYPE_PHONE,
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
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingBallView, params);
                        return false;
                    case MotionEvent.ACTION_UP:
                        appConfig.setFloatingWindowX(params.x);
                        appConfig.setFloatingWindowY(params.y);
                        return false;
                }
                return false;
            }
        });
    }

    /**
     * 根据主题设置悬浮球的颜色
     */
    private void applyThemeToFloatingBall() {
        if (floatingBallView == null) {
            return;
        }

        Button btnBall = floatingBallView.findViewById(R.id.btn_floating_ball);
        if (btnBall == null) {
            return;
        }

        boolean isDark = isDarkTheme();
        if (isDark) {
            // 深色主题
            btnBall.setBackgroundResource(R.drawable.floating_button_dark_selected);
            btnBall.setTextColor(getResources().getColor(android.R.color.white));
        } else {
            // 浅色主题
            btnBall.setBackgroundResource(R.drawable.floating_button_light);
            btnBall.setTextColor(getResources().getColor(android.R.color.black));
        }
    }

    /**
     * 根据主题设置悬浮窗列表的颜色
     */
    private void applyThemeToListView() {
        if (floatingListView == null) {
            return;
        }

        boolean isDark = isDarkTheme();

        // 设置背景色
        if (isDark) {
            floatingListView.setBackgroundColor(getResources().getColor(R.color.button_background));
        } else {
            floatingListView.setBackgroundColor(getResources().getColor(android.R.color.white));
        }

        // 设置标题文字色
        TextView titleText = floatingListView.findViewById(R.id.tv_title);
        if (titleText != null) {
            titleText.setTextColor(isDark ? getResources().getColor(android.R.color.white) : getResources().getColor(android.R.color.black));
        }

        // 设置关闭按钮
        Button closeBtn = floatingListView.findViewById(R.id.btn_close_list);
        if (closeBtn != null) {
            if (isDark) {
                closeBtn.setBackgroundResource(R.drawable.floating_button_dark);
            } else {
                closeBtn.setBackgroundResource(R.drawable.floating_button_light);
            }
            closeBtn.setTextColor(isDark ? getResources().getColor(android.R.color.white) : getResources().getColor(android.R.color.black));
        }

        // 设置透明度文字
        TextView alphaText = floatingListView.findViewById(R.id.tv_alpha_label);
        if (alphaText != null) {
            alphaText.setTextColor(isDark ? getResources().getColor(android.R.color.white) : getResources().getColor(android.R.color.black));
        }

        // 设置车外喊话按钮
        Button announcementBtn = floatingListView.findViewById(R.id.btn_announcement);
        if (announcementBtn != null) {
            if (isDark) {
                announcementBtn.setBackgroundResource(R.drawable.floating_button_dark_selected);
            } else {
                announcementBtn.setBackgroundResource(R.drawable.floating_button_light);
            }
            announcementBtn.setTextColor(isDark ? getResources().getColor(android.R.color.white) : getResources().getColor(android.R.color.black));
        }

        // 设置选择TTS按钮
        Button selectTtsBtn = floatingListView.findViewById(R.id.btn_select_tts);
        if (selectTtsBtn != null) {
            if (isDark) {
                selectTtsBtn.setBackgroundResource(R.drawable.floating_button_dark);
            } else {
                selectTtsBtn.setBackgroundResource(R.drawable.floating_button_light);
            }
            selectTtsBtn.setTextColor(isDark ? getResources().getColor(android.R.color.white) : getResources().getColor(android.R.color.black));
        }

        // 设置TTS列表标题
        TextView ttsListLabel = floatingListView.findViewById(R.id.tv_tts_list_label);
        if (ttsListLabel != null) {
            ttsListLabel.setTextColor(isDark ? getResources().getColor(android.R.color.white) : getResources().getColor(android.R.color.black));
        }
    }

    private void toggleListView() {
        AppLog.d(TAG, "toggleListView called, isListViewVisible=" + isListViewVisible);
        if (isListViewVisible) {
            hideListView();
        } else {
            showListView();
        }
    }

    private void showListView() {
        AppLog.d(TAG, "showListView called");
        if (floatingListView != null) {
            AppLog.d(TAG, "floatingListView already exists, returning");
            return;
        }

        try {
            floatingListView = LayoutInflater.from(this).inflate(R.layout.view_floating_list, null);
            
            int width = (int) (280 * getResources().getDisplayMetrics().density);
            final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    width,
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

    private void resetAutoHideTimer() {
        if (handler != null && autoHideRunnable != null) {
            handler.removeCallbacks(autoHideRunnable);
            handler.postDelayed(autoHideRunnable, 10000);
        }
    }

    private void cancelAutoHideTimer() {
        if (handler != null && autoHideRunnable != null) {
            handler.removeCallbacks(autoHideRunnable);
        }
    }

    private void setupListView() {
        Button btnClose = floatingListView.findViewById(R.id.btn_close_list);
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideListView();
            }
        });

        SeekBar sbAlpha = floatingListView.findViewById(R.id.sb_alpha);
        sbAlpha.setProgress(appConfig.getFloatingWindowAlpha());
        sbAlpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (floatingListView != null) {
                    floatingListView.setAlpha(progress / 100.0f);
                }
                resetAutoHideTimer();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                cancelAutoHideTimer();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                appConfig.setFloatingWindowAlpha(seekBar.getProgress());
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

    private void setupTTSListener() {
        MainActivity mainActivity = MainActivity.getInstance();
        if (mainActivity != null && mainActivity.getTTSManager() != null) {
            mainActivity.getTTSManager().setProgressListener(new TTSManager.TTSProgressListener() {
                @Override
                public void onTTSStart() {}

                @Override
                public void onTTSDone() {
                    handler.post(() -> {
                        updateTTSButtonColors(null);
                    });
                }

                @Override
                public void onTTSError() {
                    handler.post(() -> {
                        updateTTSButtonColors(null);
                    });
                }

                @Override
                public void onTTSProgress(int progress) {}
            });
        }
    }

    private void toggleAnnouncement() {
        MainActivity mainActivity = MainActivity.getInstance();
        if (mainActivity != null) {
            mainActivity.setAudioOutput(1);
            // 停止TTS播放（互斥）
            if (mainActivity.getTTSManager() != null && mainActivity.getTTSManager().isSpeaking()) {
                mainActivity.getTTSManager().stop();
                updateTTSButtonColors(null);
            }
            if (isAnnouncing) {
                // 停止
                if (mainActivity.getMicrophoneManager() != null) {
                    mainActivity.getMicrophoneManager().stop();
                }
                isAnnouncing = false;
            } else {
                // 开始
                mainActivity.startMicAmplification();
                isAnnouncing = true;
            }
            updateAnnouncementButton();
        }
    }
    
    private void updateAnnouncementButton() {
        if (announcementBtn == null) {
            return;
        }
        
        boolean isDark = isDarkTheme();
        if (isAnnouncing) {
            announcementBtn.setText("正在喊话");
            if (isDark) {
                // 深色主题：使用 Accent 颜色（粉色），就像麦克风页面一样
                announcementBtn.setBackgroundTintList(androidx.core.content.ContextCompat.getColorStateList(this, R.color.colorAccent));
            } else {
                // 浅色主题
                announcementBtn.setBackgroundTintList(androidx.core.content.ContextCompat.getColorStateList(this, R.color.colorAccent));
            }
        } else {
            announcementBtn.setText("车外喊话");
            if (isDark) {
                // 深色主题：使用普通的深色按钮
                announcementBtn.setBackgroundResource(R.drawable.floating_button_dark_selected);
            } else {
                // 浅色主题
                announcementBtn.setBackgroundResource(R.drawable.floating_button_light);
            }
            // 确保清除 tint
            announcementBtn.setBackgroundTintList(null);
        }
    }

    private void openMainActivityToSelectTTS() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("navigate_to_tts", true);
        startActivity(intent);
        hideListView();
    }

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
            List<String> allTexts = gson.fromJson(ttsItemsJson, new TypeToken<List<String>>(){}.getType());
            List<Integer> selectedIndices = gson.fromJson(indicesJson, new TypeToken<List<Integer>>(){}.getType());
            Map<Integer, String> customNames = gson.fromJson(namesJson, new TypeToken<Map<Integer, String>>(){}.getType());

            AppLog.d(TAG, "Parsed allTexts: " + (allTexts != null ? allTexts.size() : "null"));
            AppLog.d(TAG, "Parsed selectedIndices: " + (selectedIndices != null ? selectedIndices.size() : "null"));

            if (allTexts == null || allTexts.isEmpty()) {
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

            AppLog.d(TAG, "Loading " + selectedIndices.size() + " TTS items from " + allTexts.size() + " available");
            
            for (int i = 0; i < selectedIndices.size(); i++) {
                int index = selectedIndices.get(i);
                AppLog.d(TAG, "Processing index " + i + ": value=" + index);
                
                if (index >= 0 && index < allTexts.size()) {
                    String text = allTexts.get(index);
                    String displayName = customNames.containsKey(index) ? customNames.get(index) : text;
                    AppLog.d(TAG, "Adding TTS item: " + displayName);
                    addTTSItemToView(ttsContainer, displayName, index, text);
                } else {
                    AppLog.d(TAG, "Invalid index: " + index + " (valid range: 0-" + (allTexts.size() - 1) + ")");
                }
            }
            
            AppLog.d(TAG, "TTS items added to container: " + ttsContainer.getChildCount());
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to load TTS items", e);
        }
    }

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

    private void playTTS(String text, Button clickedBtn) {
        MainActivity mainActivity = MainActivity.getInstance();
        if (mainActivity != null) {
            mainActivity.setAudioOutput(1);
            // 停止车外喊话（互斥）
            if (isAnnouncing) {
                toggleAnnouncement();
            }
            if (mainActivity.getTTSManager() != null) {
                mainActivity.getTTSManager().speak(text);
            }
        }
        
        // 更新按钮颜色
        updateTTSButtonColors(clickedBtn);
    }
    
    private void updateTTSButtonColors(Button newPlayingBtn) {
        // 恢复原来的按钮
        if (currentPlayingBtn != null && currentPlayingBtn != newPlayingBtn) {
            applyTTSButtonColorToSingle(currentPlayingBtn, false);
        }
        
        // 高亮新按钮
        if (newPlayingBtn != null) {
            applyTTSButtonColorToSingle(newPlayingBtn, true);
        }
        
        currentPlayingBtn = newPlayingBtn;
    }
    
    private void applyTTSButtonColorToSingle(Button button, boolean isPlaying) {
        boolean isDark = isDarkTheme();
        if (isPlaying) {
            // 播放中：用粉色
            button.setBackgroundTintList(androidx.core.content.ContextCompat.getColorStateList(this, R.color.colorAccent));
            button.setTextColor(getResources().getColor(android.R.color.white));
        } else {
            // 未播放：恢复原来的主题色
            if (isDark) {
                button.setBackgroundResource(R.drawable.floating_button_dark_selected);
            } else {
                button.setBackgroundResource(R.drawable.floating_button_light);
            }
            button.setBackgroundTintList(null);
            button.setTextColor(isDark ? getResources().getColor(android.R.color.white) : getResources().getColor(android.R.color.black));
        }
    }
    
    private void applyTTSButtonColor(Button btnPlay, Button btnDelete, boolean isPlaying) {
        boolean isDark = isDarkTheme();
        
        // 播放按钮
        if (isPlaying) {
            btnPlay.setBackgroundTintList(androidx.core.content.ContextCompat.getColorStateList(this, R.color.colorAccent));
        } else {
            if (isDark) {
                btnPlay.setBackgroundResource(R.drawable.floating_button_dark_selected);
            } else {
                btnPlay.setBackgroundResource(R.drawable.floating_button_light);
            }
            btnPlay.setBackgroundTintList(null);
        }
        btnPlay.setTextColor(isDark ? getResources().getColor(android.R.color.white) : getResources().getColor(android.R.color.black));
        
        // 删除按钮
        if (isDark) {
            btnDelete.setBackgroundResource(R.drawable.floating_button_dark);
        } else {
            btnDelete.setBackgroundResource(R.drawable.floating_button_light);
        }
        btnDelete.setTextColor(isDark ? getResources().getColor(android.R.color.white) : getResources().getColor(android.R.color.black));
    }

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

    public static void start(Context context) {
        Intent intent = new Intent(context, FloatingWindowService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
        AppLog.d(TAG, "Floating window service started");
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, FloatingWindowService.class);
        context.stopService(intent);
        AppLog.d(TAG, "Floating window service stopped");
    }
}
