package com.aug32.l7audio.ui.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.navigation.NavigationView;

import com.aug32.l7audio.data.local.AppConfig;
import com.aug32.l7audio.utils.AppLog;
import com.aug32.l7audio.R;
import com.aug32.l7audio.base.BaseActivity;
import com.aug32.l7audio.domain.audio.AudioFocusManager;
import com.aug32.l7audio.domain.audio.AudioOutputManager;
import com.aug32.l7audio.domain.audio.AudioServiceLocator;
import com.aug32.l7audio.domain.audio.MicrophoneManager;
import com.aug32.l7audio.domain.audio.MusicPlayerManager;
import com.aug32.l7audio.domain.audio.TTSManager;
import com.aug32.l7audio.service.AudioForegroundService;
import com.aug32.l7audio.service.FloatingWindowService;
import com.aug32.l7audio.ui.fragment.AboutFragment;
import com.aug32.l7audio.ui.fragment.FileBrowserFragment;
import com.aug32.l7audio.ui.fragment.MicAmplifierFragment;
import com.aug32.l7audio.ui.fragment.MusicPlayerFragment;
import com.aug32.l7audio.ui.fragment.SettingsFragment;
import com.aug32.l7audio.ui.fragment.TTSFragment;
import com.aug32.l7audio.utils.ServiceCompat;

/**
 * L7Audio 主界面 Activity
 *
 * 职责：
 * - 权限请求与管理
 * - 音频管理器初始化
 * - Fragment 导航管理
 * - 前台服务启停
 * - 主题/状态栏设置
 *
 * 目标 SDK：Android 11 (API 30)
 * 最低 SDK：Android 11 (API 30)
 *
 * 已继承 BaseActivity，公共方法见父类：
 * - applyThemeMode() 主题模式设置
 * - setupStatusBar() 状态栏设置
 * - applyFontScale() 字体缩放
 * - isDarkTheme() 判断深色主题
 */
public class MainActivity extends BaseActivity {

    /** 日志标签 */
    private static final String TAG = "MainActivity";
    /** 权限请求码 */
    private static final int REQUEST_PERMISSIONS = 100;

    // UI 控件
    /** 侧边抽屉布局 */
    private DrawerLayout drawerLayout;
    /** 导航视图 */
    private NavigationView navigationView;
    /** 主界面布局 */
    private View mainLayout;
    /** Fragment 容器 */
    private View fragmentContainer;

    /** 菜单按钮、退出按钮 */
    private Button btnMenu, btnExit;
    /** 应用标题文本 */
    private TextView tvAppTitle;
    /** 功能按钮：麦克风放大、TTS、音乐播放器 */
    private Button btnMicAmplifier, btnTTS, btnMusicPlayer;
    /** 音频输出模式按钮：车内、车外 */
    private Button btnOutputCar, btnOutputExternal;

    // 音频管理器
    /** 音频输出管理器，负责切换车内/车外输出 */
    private AudioOutputManager audioOutputManager;
    /** 麦克风管理器，负责麦克风采集与放大 */
    private MicrophoneManager microphoneManager;
    /** TTS 语音合成管理器 */
    private TTSManager ttsManager;
    /** 音乐播放器管理器 */
    private MusicPlayerManager musicPlayerManager;
    /** 音频焦点管理器，协调各音频模块的焦点竞争 */
    private AudioFocusManager audioFocusManager;

    /** 当前功能页面标识：-1无、0麦克风、1TTS、2音乐 */
    private int currentFunction = -1;

    /** 外部存储管理权限的 ActivityResult 启动器 */
    private ActivityResultLauncher<Intent> manageExternalStorageLauncher;

    // ==================== 生命周期 ====================

    /**
     * Activity 创建时调用，完成应用核心初始化工作
     *
     * 初始化顺序说明：
     * 1. 初始化日志工具与 ActivityResultLauncher
     * 2. 初始化音频管理器（先于Fragment创建，确保Fragment可访问）
     * 3. 应用主题与字体缩放
     * 4. 加载布局与UI控件绑定
     * 5. 权限检查与请求
     * 6. 启动前台服务（车机必需）
     * 7. 处理开机自启动逻辑
     * 8. 恢复功能页面（权限已授予时）
     *
     * @param savedInstanceState 保存的实例状态
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppLog.init(this);

        // 初始化 ActivityResultLauncher
        initActivityResultLaunchers();

        // 初始化应用配置（BaseActivity 已创建 appConfig）
        // appConfig = new AppConfig(this); // BaseActivity 中已初始化

        // 【修复】先初始化音频管理器，确保 Fragment 中可访问
        // 即使权限未授予也可以安全创建 Manager 实例
        initAudioManagers();

        // 设置主题（使用 BaseActivity 方法）
        applyThemeMode(appConfig.getThemeMode());

        // 设置字体缩放比例（车机专用 1.2 倍）
        applyFontScale(1.2f);

        // 设置布局
        setContentView(R.layout.activity_main);

        // 设置状态栏（使用 BaseActivity 方法）
        setupStatusBarWithTheme(R.color.menu_background);

        initViews();
        setupNavigationDrawer();

        // 权限检查
        if (!checkPermissions()) {
            requestPermissions();
        }

        // 启动前台服务（车机必需，始终开启）
        startForegroundService();

        // 检查是否是开机自启动
        boolean autoStartFromBoot = getIntent().getBooleanExtra("auto_start_from_boot", false);
        if (autoStartFromBoot) {
            getIntent().removeExtra("auto_start_from_boot");
            if (!appConfig.isAutoStartOnBoot()) {
                AppLog.d(TAG, "开机自启动模式：未开启自动功能，移到后台");
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    moveTaskToBack(true);
                }, 500);
            }
        }

        // 如果权限已授予，加载功能页面（优先处理悬浮窗跳转，否则恢复上次状态）
        if (checkPermissions()) {
            loadFunctionPage();
        }

        // 根据配置启动悬浮窗服务
        if (appConfig.isFloatingWindowEnabled()) {
            startFloatingWindowService();
        }
    }

    /**
     * Activity 已在栈顶时收到新 Intent 的回调
     * 主要用于处理悬浮窗跳转等场景，支持跳转到指定功能页面
     *
     * @param intent 新的 Intent 对象，可能包含导航指令
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        AppLog.d(TAG, "onNewIntent: received new intent, action=" + intent.getAction());
        setIntent(intent);
        if (intent.getBooleanExtra("navigate_to_tts", false)) {
            showTTSFragment();
            intent.removeExtra("navigate_to_tts");
        }
    }

    /**
     * Activity 恢复时调用
     * 恢复音频输出管理器并同步输出按钮状态
     */
    @Override
    protected void onResume() {
        super.onResume();
        // 恢复音频输出管理器，重新监听音频路由变化
        if (audioOutputManager != null) {
            audioOutputManager.resume();
        }
        // 同步输出按钮的选中状态
        if (audioOutputManager != null) {
            updateOutputButtons(audioOutputManager.getOutputMode());
        }
    }

    /**
     * Activity 暂停时调用
     * 预留位置，暂无额外清理逻辑
     */
    @Override
    protected void onPause() {
        super.onPause();
    }

    /**
     * Activity 销毁时调用，释放音频相关资源
     * 按顺序停止麦克风、TTS、音频焦点，并注销服务定位器中的管理器
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (microphoneManager != null) {
            microphoneManager.stop();
        }
        if (ttsManager != null) {
            ttsManager.shutdown();
        }
        if (audioFocusManager != null) {
            audioFocusManager.abandonAll();
        }
        // 注销 ServiceLocator
        AudioServiceLocator.getInstance().unregisterManagers();
    }

    // ==================== 权限管理 ====================

    /** 根据 Android 版本动态获取需要的权限 */
    private String[] getRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            return new String[]{
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_MEDIA_AUDIO
            };
        } else {
            // Android 12 及以下
            return new String[]{
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            };
        }
    }

    /** 检查当前所需权限是否已全部授予 */
    private boolean checkPermissions() {
        for (String permission : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /** 请求运行时权限 */
    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, getRequiredPermissions(), REQUEST_PERMISSIONS);
    }

    /**
     * 处理运行时权限请求结果
     * 全部授权则继续检查 MANAGE_EXTERNAL_STORAGE 权限，否则提示用户
     *
     * @param requestCode  请求码
     * @param permissions  权限列表
     * @param grantResults 授权结果列表
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                checkManageExternalStoragePermission();
            } else {
                Toast.makeText(this, "权限被拒绝，部分功能可能无法使用", Toast.LENGTH_LONG).show();
            }
        }
    }

    /** 初始化 ActivityResultLauncher */
    private void initActivityResultLaunchers() {
        manageExternalStorageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            if (Environment.isExternalStorageManager()) {
                                loadFunctionPage();
                            } else {
                                Toast.makeText(MainActivity.this, "存储权限被拒绝，部分功能可能无法使用", Toast.LENGTH_LONG).show();
                            }
                        }
                    }
                }
        );
    }

    /** 检查并请求 MANAGE_EXTERNAL_STORAGE 权限（Android R 及以上） */
    private void checkManageExternalStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                manageExternalStorageLauncher.launch(intent);
            } else {
                loadFunctionPage();
            }
        } else {
            loadFunctionPage();
        }
    }

    // ==================== 音频管理器 ====================

    /** 初始化各类音频管理器 */
    private void initAudioManagers() {
        AudioServiceLocator locator = AudioServiceLocator.getInstance();

        audioOutputManager = new AudioOutputManager(this);
        microphoneManager = new MicrophoneManager(this, audioOutputManager);
        ttsManager = new TTSManager(this, audioOutputManager);
        audioFocusManager = AudioFocusManager.from(this);

        musicPlayerManager = locator.getMusicPlayerManager();

        locator.registerManagers(
                audioOutputManager,
                microphoneManager,
                ttsManager,
                musicPlayerManager,
                audioFocusManager);
    }

    /**
     * 获取音乐播放器管理器实例
     * 供 Fragment 等组件获取音乐播放控制能力
     *
     * @return MusicPlayerManager 实例，可能为 null（初始化前）
     */
    public MusicPlayerManager getMusicPlayerManager() {
        return musicPlayerManager;
    }

    /** 启动前台服务 */
    private void startForegroundService() {
        Intent serviceIntent = new Intent(this, AudioForegroundService.class);
        ServiceCompat.startForegroundService(this, serviceIntent);
    }

    // ==================== UI 初始化 ====================

    /** 初始化视图控件 */
    private void initViews() {
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        mainLayout = findViewById(R.id.main);
        fragmentContainer = findViewById(R.id.fragment_container);

        // 设置导航头部版本号
        if (navigationView != null) {
            View headerView = navigationView.getHeaderView(0);
            if (headerView != null) {
                TextView versionText = headerView.findViewById(R.id.nav_header_version);
                if (versionText != null) {
                    try {
                        String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                        versionText.setText(getString(R.string.app_name) + " v" + versionName);
                    } catch (PackageManager.NameNotFoundException e) {
                        AppLog.e(TAG, "Failed to get version name", e);
                    }
                }
            }
        }

        // 初始化按钮
        btnMenu = findViewById(R.id.btn_menu);
        btnExit = findViewById(R.id.btn_exit);
        tvAppTitle = findViewById(R.id.tv_app_title);
        btnMicAmplifier = findViewById(R.id.btn_mic_amplifier);
        btnTTS = findViewById(R.id.btn_tts);
        btnMusicPlayer = findViewById(R.id.btn_music_player);
        btnOutputCar = findViewById(R.id.btn_output_car);
        btnOutputExternal = findViewById(R.id.btn_output_external);

        // 设置按钮点击事件
        if (btnMenu != null) {
            btnMenu.setOnClickListener(v -> openDrawer());
        }
        if (btnExit != null) {
            btnExit.setOnClickListener(v -> finish());
        }
        if (btnMicAmplifier != null) {
            btnMicAmplifier.setOnClickListener(v -> {
                showMicAmplifierFragment();
                currentFunction = 0;
                updateFunctionButtons();
            });
        }
        if (btnTTS != null) {
            btnTTS.setOnClickListener(v -> {
                showTTSFragment();
                currentFunction = 1;
                updateFunctionButtons();
            });
        }
        if (btnMusicPlayer != null) {
            btnMusicPlayer.setOnClickListener(v -> {
                showMusicPlayerFragment();
                currentFunction = 2;
                updateFunctionButtons();
            });
        }
        if (btnOutputCar != null) {
            btnOutputCar.setOnClickListener(v -> setAudioOutput(AudioOutputManager.OUTPUT_CAR));
        }
        if (btnOutputExternal != null) {
            btnOutputExternal.setOnClickListener(v -> setAudioOutput(AudioOutputManager.OUTPUT_EXTERNAL));
        }
    }

    /** 设置侧边导航抽屉 */
    private void setupNavigationDrawer() {
        if (navigationView != null) {
            navigationView.setNavigationItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_home) {
                    showMainInterface();
                } else if (id == R.id.nav_settings) {
                    showSettingsFragment();
                } else if (id == R.id.nav_about) {
                    showAboutFragment();
                }
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            });
        }
    }

    // ==================== 悬浮窗 ====================

    /** 启动悬浮窗服务 */
    private void startFloatingWindowService() {
        Intent serviceIntent = new Intent(this, FloatingWindowService.class);
        ServiceCompat.startForegroundService(this, serviceIntent);
    }

    /** 停止悬浮窗服务 */
    private void stopFloatingWindowService() {
        Intent serviceIntent = new Intent(this, FloatingWindowService.class);
        ServiceCompat.stopService(this, serviceIntent);
    }

    // ==================== Fragment 导航 ====================

    /** 打开左侧导航抽屉 */
    private void openDrawer() {
        if (drawerLayout != null) {
            drawerLayout.openDrawer(GravityCompat.START);
        }
    }

    /**
     * 显示主界面（功能按钮区域），隐藏设置/关于等二级页面
     * 从侧边栏点击"首页"时调用
     */
    public void showMainInterface() {
        if (mainLayout != null) {
            mainLayout.setVisibility(View.VISIBLE);
        }
        if (fragmentContainer != null) {
            fragmentContainer.setVisibility(View.GONE);
        }
    }

    /**
     * 显示设置 Fragment 页面
     * 隐藏主界面布局，将设置页替换到 fragment 容器中
     * 使用 commitAllowingStateLoss 避免状态保存后提交导致的崩溃
     */
    public void showSettingsFragment() {
        if (mainLayout != null) {
            mainLayout.setVisibility(View.GONE);
        }
        if (fragmentContainer != null) {
            fragmentContainer.setVisibility(View.VISIBLE);
        }
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, new SettingsFragment());
        // 使用 commitAllowingStateLoss 防止在 Activity 状态保存后调用 commit 导致崩溃
        transaction.commitAllowingStateLoss();
    }

    /**
     * 显示关于 Fragment 页面
     * 隐藏主界面布局，将关于页替换到 fragment 容器中
     * 使用 commitAllowingStateLoss 避免状态保存后提交导致的崩溃
     */
    public void showAboutFragment() {
        if (mainLayout != null) {
            mainLayout.setVisibility(View.GONE);
        }
        if (fragmentContainer != null) {
            fragmentContainer.setVisibility(View.VISIBLE);
        }
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, new AboutFragment());
        // 使用 commitAllowingStateLoss 防止在 Activity 状态保存后调用 commit 导致崩溃
        transaction.commitAllowingStateLoss();
    }

    /**
     * 显示麦克风放大 Fragment 页面
     * 若音乐正在播放则先暂停音乐，并保存当前功能状态以便下次恢复
     */
    public void showMicAmplifierFragment() {
        updateAppTitle("L7 Audio - 麦克风放大");
        if (musicPlayerManager != null && musicPlayerManager.isPlaying()) {
            musicPlayerManager.pause();
        }
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.function_content, new MicAmplifierFragment());
        transaction.commitAllowingStateLoss();
        saveCurrentFunctionState(0);
    }

    /**
     * 启动麦克风放大功能
     * 仅在麦克风管理器已初始化且未在录音时才启动，避免重复启动
     */
    public void startMicAmplification() {
        if (microphoneManager != null && !microphoneManager.isRecording()) {
            microphoneManager.start();
        }
    }

    /** 显示 TTS Fragment */
    private void showTTSFragment() {
        updateAppTitle("L7 Audio - TTS播报");
        if (musicPlayerManager != null && musicPlayerManager.isPlaying()) {
            musicPlayerManager.pause();
        }
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.function_content, new TTSFragment());
        transaction.commitAllowingStateLoss();
        saveCurrentFunctionState(1);
    }

    /** 显示音乐播放器 Fragment */
    private void showMusicPlayerFragment() {
        updateAppTitle("L7 Audio - 本地音乐");
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.function_content, new MusicPlayerFragment());
        transaction.commitAllowingStateLoss();
        saveCurrentFunctionState(2);
    }

    /** 加载功能页面：优先处理悬浮窗跳转，否则恢复上次保存的状态 */
    private void loadFunctionPage() {
        if (getIntent() != null && getIntent().getBooleanExtra("navigate_to_tts", false)) {
            AppLog.d(TAG, "loadFunctionPage: 从悬浮窗跳转，显示TTS页面");
            showTTSFragment();
            getIntent().removeExtra("navigate_to_tts");
            return;
        }
        int savedFunction = appConfig.getCurrentFunction();
        if (savedFunction == -1) {
            savedFunction = 0;
            appConfig.setCurrentFunction(savedFunction);
        }
        switch (savedFunction) {
            case 0:
                showMicAmplifierFragment();
                break;
            case 1:
                showTTSFragment();
                break;
            case 2:
                showMusicPlayerFragment();
                break;
        }
        // 同步 currentFunction 字段，确保与实际显示的 Fragment 一致
        currentFunction = savedFunction;
        AppLog.d(TAG, "loadFunctionPage: 恢复上次页面，function=" + savedFunction);
    }

    /** 保存当前功能状态 */
    private void saveCurrentFunctionState(int function) {
        currentFunction = function;
        appConfig.setCurrentFunction(function);
    }

    // ==================== UI 更新 ====================

    /** 更新功能按钮高亮状态 */
    private void updateFunctionButtons() {
        if (btnMicAmplifier != null) {
            btnMicAmplifier.setBackgroundColor(ContextCompat.getColor(this,
                    currentFunction == 0 ? R.color.button_background_selected : R.color.colorPrimary));
        }
        if (btnTTS != null) {
            btnTTS.setBackgroundColor(ContextCompat.getColor(this,
                    currentFunction == 1 ? R.color.button_background_selected : R.color.colorPrimary));
        }
        if (btnMusicPlayer != null) {
            btnMusicPlayer.setBackgroundColor(ContextCompat.getColor(this,
                    currentFunction == 2 ? R.color.button_background_selected : R.color.colorPrimary));
        }
    }

    /** 更新输出按钮状态 */
    private void updateOutputButtons(int outputMode) {
        if (btnOutputCar != null) {
            btnOutputCar.setBackgroundColor(ContextCompat.getColor(this,
                    outputMode == AudioOutputManager.OUTPUT_CAR ? R.color.button_background_selected : R.color.colorPrimary));
        }
        if (btnOutputExternal != null) {
            btnOutputExternal.setBackgroundColor(ContextCompat.getColor(this,
                    outputMode == AudioOutputManager.OUTPUT_EXTERNAL ? R.color.button_background_selected : R.color.colorPrimary));
        }
    }

    /** 启用所有功能按钮 */
    private void enableFunctionButtons() {
        if (btnMicAmplifier != null) btnMicAmplifier.setEnabled(true);
        if (btnTTS != null) btnTTS.setEnabled(true);
        if (btnMusicPlayer != null) btnMusicPlayer.setEnabled(true);
    }

    /** 更新标题栏文本 */
    private void updateAppTitle(String title) {
        if (tvAppTitle != null) {
            tvAppTitle.setText(title);
        }
    }

    // ==================== 音频输出控制 ====================

    /**
     * 设置音频输出模式（车内/车外）
     *
     * 切换逻辑说明：
     * 1. 若麦克风正在放大，先停止再切换（避免音频路由变化导致异常）
     * 2. 调用音频输出管理器切换输出模式
     * 3. 更新音乐播放器的音频使用属性（无需停止播放，可热切换）
     * 4. 若之前在放大麦克风，切换完成后重新启动
     * 5. 更新UI按钮状态并启用功能按钮
     *
     * @param outputMode 输出模式，取值为 AudioOutputManager.OUTPUT_CAR 或 AudioOutputManager.OUTPUT_EXTERNAL
     */
    public void setAudioOutput(int outputMode) {
        if (audioOutputManager == null) {
            Toast.makeText(this, "音频输出管理器未初始化", Toast.LENGTH_SHORT).show();
            return;
        }

        // 记录切换前麦克风是否正在放大，切换完成后恢复
        boolean wasAmplifying = false;

        try {
            // 麦克风正在录音时先停止，避免音频路由切换过程中出现异常
            if (microphoneManager != null && microphoneManager.isRecording()) {
                wasAmplifying = true;
                microphoneManager.stop();
            }

            audioOutputManager.setOutputMode(outputMode);

            // 更新音乐播放器的音频输出属性（无需停止播放）
            if (musicPlayerManager != null) {
                musicPlayerManager.updateAudioOutputUsage(audioOutputManager.getAudioUsage());
            }

            // 切换完成后恢复麦克风放大
            if (wasAmplifying && microphoneManager != null) {
                microphoneManager.start();
            }

            updateOutputButtons(outputMode);
            enableFunctionButtons();

            String outputText = outputMode == AudioOutputManager.OUTPUT_CAR ? "仅车内播放" : "仅车外播放";
            Toast.makeText(this, "音频输出已设置为：" + outputText, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            AppLog.e(TAG, "setAudioOutput failed", e);
            Toast.makeText(this, "音频输出切换失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ==================== 文件浏览器 ====================

    /**
     * 显示文件浏览器 Fragment
     *
     * <p>使用 fragment_container 容器显示，覆盖在当前页面之上。
     * 返回时通过 closeFileBrowserFragment() 关闭并恢复原页面。
     *
     * @param mode     浏览模式，FileBrowserFragment.MODE_DIRECTORY 或 MODE_FILE
     * @param callback 选择结果回调
     */
    public void showFileBrowserFragment(int mode, FileBrowserFragment.FileBrowserCallback callback) {
        FileBrowserFragment fragment = FileBrowserFragment.newInstance(mode);
        fragment.setCallback(callback);

        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, fragment);
        transaction.addToBackStack(null);
        transaction.commitAllowingStateLoss();

        if (fragmentContainer != null) {
            fragmentContainer.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 关闭文件浏览器 Fragment
     *
     * <p>弹出返回栈中的文件浏览器 Fragment，并恢复原页面显示状态。
     */
    public void closeFileBrowserFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (fragmentManager.getBackStackEntryCount() > 0) {
            fragmentManager.popBackStack();
        }
        // 如果返回后栈为空，隐藏 fragment_container
        fragmentManager.executePendingTransactions();
        if (fragmentManager.getBackStackEntryCount() == 0 && fragmentContainer != null) {
            fragmentContainer.setVisibility(View.GONE);
        }
    }
}
