package com.aug32.l7audio;

import android.Manifest;
import android.content.Intent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.navigation.NavigationView;
import com.aug32.l7audio.audio.AudioFocusManager;
import com.aug32.l7audio.audio.AudioOutputManager;
import com.aug32.l7audio.audio.MicrophoneManager;
import com.aug32.l7audio.audio.MusicPlayerManager;
import com.aug32.l7audio.audio.TTSManager;
import com.aug32.l7audio.service.AudioForegroundService;
import com.aug32.l7audio.service.FloatingWindowService;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_PERMISSIONS = 100;

    // 静态实例引用（用于外部组件访问）
    private static MainActivity instance;

    // 根据Android版本动态获取需要的权限
    private String[] getRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            return new String[]{
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_MEDIA_AUDIO
            };
        } else {
            // Android 12及以下
            return new String[]{
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            };
        }
    }

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private View mainLayout;
    private View fragmentContainer;

    private Button btnMenu, btnExit;
    private TextView tvAppTitle;
    private Button btnMicAmplifier, btnTTS, btnMusicPlayer;
    private Button btnOutputCar, btnOutputExternal;

    private AudioOutputManager audioOutputManager;
    private MicrophoneManager microphoneManager;
    private TTSManager ttsManager;
    private MusicPlayerManager musicPlayerManager;
    private AudioFocusManager audioFocusManager;

    private AppConfig appConfig;
    private boolean isInBackground = false;
    private int currentFunction = -1; // -1: 无, 0: 麦克风, 1: TTS, 2: 音乐

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
        AppLog.init(this);

        // 初始化ActivityResultLauncher
        initActivityResultLaunchers();

        // 初始化应用配置
        appConfig = new AppConfig(this);

        // 设置主题
        setThemeMode();

        // 设置字体缩放比例
        adjustFontScale(1.2f);

        // 设置布局
        setContentView(R.layout.activity_main);

        // 设置状态栏沉浸式
        setupStatusBar();

        initViews();
        setupNavigationDrawer();

        // 权限检查
        if (!checkPermissions()) {
            requestPermissions();
        } else {
            initAudioManagers();
        }

        // 启动前台服务（车机必需，始终开启）
        startForegroundService();

        // 检查是否是开机自启动
        boolean autoStartFromBoot = getIntent().getBooleanExtra("auto_start_from_boot", false);
        if (autoStartFromBoot) {
            // 清除标志，避免后续重复检测
            getIntent().removeExtra("auto_start_from_boot");

            // 如果未开启自动录制等功能，移到后台
            if (!appConfig.isAutoStartOnBoot()) {
                AppLog.d(TAG, "开机自启动模式：未开启自动功能，移到后台");
                // 延迟移到后台，确保初始化完成
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    moveTaskToBack(true);
                }, 500);
            }
        }
        
        // 如果权限已授予，直接加载保存的功能状态
        if (checkPermissions()) {
            loadSavedFunctionState();
        }
        
        // 处理从悬浮窗导航的intent
        handleFloatingWindowIntent();
        
        // 根据配置启动悬浮窗服务
        if (appConfig.isFloatingWindowEnabled()) {
            startFloatingWindowService();
        }
    }
    
    private void handleFloatingWindowIntent() {
        Intent intent = getIntent();
        if (intent != null && intent.getBooleanExtra("navigate_to_tts", false)) {
            showTTSFragment();
            currentFunction = 1;
            updateFunctionButtons();
            intent.removeExtra("navigate_to_tts");
        }
    }
    
    private void startFloatingWindowService() {
        Intent serviceIntent = new Intent(this, FloatingWindowService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }
    
    private void stopFloatingWindowService() {
        Intent serviceIntent = new Intent(this, FloatingWindowService.class);
        stopService(serviceIntent);
    }

    private void initAudioManagers() {
        // 初始化音频输出管理器
        audioOutputManager = new AudioOutputManager(this);

        // 初始化麦克风管理器
        microphoneManager = new MicrophoneManager(this, audioOutputManager);

        // 初始化TTS管理器
        ttsManager = new TTSManager(this, audioOutputManager);

        // 初始化音乐播放器管理器
        musicPlayerManager = new MusicPlayerManager(this, audioOutputManager);

        // 初始化音频焦点管理器
        audioFocusManager = new AudioFocusManager(this);
    }

    private void startForegroundService() {
        Intent serviceIntent = new Intent(this, AudioForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void adjustFontScale(float scale) {
        android.content.res.Configuration configuration = getResources().getConfiguration();
        configuration.fontScale = scale;
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        // TODO: Resources.updateConfiguration() 在API 25中已过时
        // Android 11(API 30)上仍可正常使用，但建议未来迁移到Context.createConfigurationContext()
        // 替代方案: 使用createConfigurationContext()重新创建Context
        getBaseContext().getResources().updateConfiguration(configuration, metrics);
    }

    private void setupStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // 设置状态栏颜色为菜单栏背景色
            // TODO: Window.setStatusBarColor() 在API 30中已过时
            // Android 11(API 30)上仍可正常使用，但建议未来迁移到WindowInsetsControllerCompat
            // 替代方案: 使用WindowInsetsControllerCompat.setStatusBarAppearance()
            getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.menu_background));

            // 根据当前主题模式设置状态栏图标颜色
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                int nightModeFlags = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
                if (nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                    // 夜间模式：清除浅色状态栏标志，使用深色图标变为浅色图标
                    // TODO: View.setSystemUiVisibility() 在API 30中已过时
                    // Android 11(API 30)上仍可正常使用，但建议未来迁移到WindowInsetsControllerCompat
                    // 替代方案: 使用WindowInsetsControllerCompat.setSystemBarsAppearance()
                    getWindow().getDecorView().setSystemUiVisibility(0);
                } else {
                    // 日间模式：设置状态栏图标为深色（因为背景是浅色）
                    // TODO: View.setSystemUiVisibility() 和 View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR 在API 30中已过时
                    // Android 11(API 30)上仍可正常使用，但建议未来迁移到WindowInsetsControllerCompat
                    // 替代方案: 使用WindowInsetsControllerCompat.setSystemBarsAppearance()
                    getWindow().getDecorView().setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    );
                }
            }
        }
    }

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

        // 初始化按钮和标题
        btnMenu = findViewById(R.id.btn_menu);
        btnExit = findViewById(R.id.btn_exit);
        tvAppTitle = findViewById(R.id.tv_app_title);
        btnMicAmplifier = findViewById(R.id.btn_mic_amplifier);
        btnTTS = findViewById(R.id.btn_tts);
        btnMusicPlayer = findViewById(R.id.btn_music_player);
        btnOutputCar = findViewById(R.id.btn_output_car);
        btnOutputExternal = findViewById(R.id.btn_output_external);

        // 设置按钮点击事件
        if (btnMenu != null) {//
            btnMenu.setOnClickListener(v -> openDrawer());// 打开导航抽屉
        }

        if (btnExit != null) {
            btnExit.setOnClickListener(v -> finish());// 退出应用
        }

        if (btnMicAmplifier != null) {
            btnMicAmplifier.setOnClickListener(v -> {
                showMicAmplifierFragment();// 显示麦克风放大Fragment
                currentFunction = 0;
                updateFunctionButtons();
            });
        }

        if (btnTTS != null) {
            btnTTS.setOnClickListener(v -> {
                showTTSFragment();// 显示TTS Fragment
                currentFunction = 1;
                updateFunctionButtons();
            });
        }

        if (btnMusicPlayer != null) {
            btnMusicPlayer.setOnClickListener(v -> {
                showMusicPlayerFragment();// 显示音乐播放器Fragment
                currentFunction = 2;
                updateFunctionButtons();
            });
        }

        if (btnOutputCar != null) {
            btnOutputCar.setOnClickListener(v -> setAudioOutput(AudioOutputManager.OUTPUT_CAR));// 设置车内播放
        }

        if (btnOutputExternal != null) {
            btnOutputExternal.setOnClickListener(v -> setAudioOutput(AudioOutputManager.OUTPUT_EXTERNAL));// 设置车外播放
        }

       
    }

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

    private void openDrawer() {
        if (drawerLayout != null) {
            drawerLayout.openDrawer(GravityCompat.START);
        }
    }

    public void showMainInterface() {
        if (mainLayout != null) {
            mainLayout.setVisibility(View.VISIBLE);
        }
        if (fragmentContainer != null) {
            fragmentContainer.setVisibility(View.GONE);
        }
    }



    public void showSettingsFragment() {
        if (mainLayout != null) {
            mainLayout.setVisibility(View.GONE);
        }
        if (fragmentContainer != null) {
            fragmentContainer.setVisibility(View.VISIBLE);
        }
        
        // 显示设置Fragment
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container, new SettingsFragment());
        fragmentTransaction.commitAllowingStateLoss();
    }

    public void showAboutFragment() {
        if (mainLayout != null) {
            mainLayout.setVisibility(View.GONE);
        }
        if (fragmentContainer != null) {
            fragmentContainer.setVisibility(View.VISIBLE);
        }
        
        // 显示关于Fragment
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container, new AboutFragment());
        fragmentTransaction.commitAllowingStateLoss();
    }

    public void showMicAmplifierFragment() {
        // 更新标题栏文本
        updateAppTitle("L7 Audio - 麦克风放大");
        // 暂停音乐播放
        if (musicPlayerManager != null && musicPlayerManager.isPlaying()) {
            musicPlayerManager.pause();
        }
        // 显示麦克风放大Fragment
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.function_content, new MicAmplifierFragment());
        fragmentTransaction.commitAllowingStateLoss();
        // 保存当前功能状态
        saveCurrentFunctionState(0);
    }
    
    public void startMicAmplification() {
        if (microphoneManager != null && !microphoneManager.isRecording()) {
            microphoneManager.start();
        }
    }

    private void showTTSFragment() {
        // 更新标题栏文本
        updateAppTitle("L7 Audio - TTS播报");
        // 暂停音乐播放
        if (musicPlayerManager != null && musicPlayerManager.isPlaying()) {
            musicPlayerManager.pause();
        }
        // 显示TTS Fragment
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.function_content, new TTSFragment());
        fragmentTransaction.commitAllowingStateLoss();
        // 保存当前功能状态
        saveCurrentFunctionState(1);
    }

    private void showMusicPlayerFragment() {
        // 更新标题栏文本
        updateAppTitle("L7 Audio - 本地音乐");
        // 显示音乐播放器Fragment
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.function_content, new MusicPlayerFragment());
        fragmentTransaction.commitAllowingStateLoss();
        // 保存当前功能状态
        saveCurrentFunctionState(2);
    }

    /**
     * 加载保存的功能状态
     */
    private void loadSavedFunctionState() {
        int tempFunction = appConfig.getCurrentFunction();
        // 如果是第一次安装（tempFunction为-1），默认进入麦克风放大功能
        if (tempFunction == -1) {
            tempFunction = 0; // 0: 麦克风放大功能
            appConfig.setCurrentFunction(tempFunction);
        }
        
        final int savedFunction = tempFunction;
        
        // 延迟加载，确保UI已初始化
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            switch (savedFunction) {
                case 0:
                    showMicAmplifierFragment();
                    currentFunction = 0;
                    break;
                case 1:
                    showTTSFragment();
                    currentFunction = 1;
                    break;
                case 2:
                    showMusicPlayerFragment();
                    currentFunction = 2;
                    break;
            }
            updateFunctionButtons();
        }, 500);
    }

    /**
     * 保存当前功能状态
     */
    private void saveCurrentFunctionState(int function) {
        currentFunction = function;
        appConfig.setCurrentFunction(function);
    }

    /**
     * 更新功能按钮状态，显示当前正在使用的功能
     */
    private void updateFunctionButtons() {
        if (btnMicAmplifier != null) {
            if (currentFunction == 0) {
                btnMicAmplifier.setBackgroundColor(ContextCompat.getColor(this, R.color.button_background_selected));
            } else {
                btnMicAmplifier.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary));
            }
        }
        
        if (btnTTS != null) {
            if (currentFunction == 1) {
                btnTTS.setBackgroundColor(ContextCompat.getColor(this, R.color.button_background_selected));
            } else {
                btnTTS.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary));
            }
        }
        
        if (btnMusicPlayer != null) {
            if (currentFunction == 2) {
                btnMusicPlayer.setBackgroundColor(ContextCompat.getColor(this, R.color.button_background_selected));
            } else {
                btnMusicPlayer.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary));
            }
        }
    }

    /**
     * 设置音频输出模式
     * 如果麦克风正在放大，会先停止再重新启动，确保模式切换立即生效
     * @param outputMode 音频输出模式，可选值：OUTPUT_CAR（车内）、OUTPUT_EXTERNAL（车外）
     */
    public void setAudioOutput(int outputMode) {
        if (audioOutputManager == null) {// 如果音频输出管理器未初始化
            Toast.makeText(this, "音频输出管理器未初始化", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean wasAmplifying = false;// 是否正在放大
        boolean musicWasPlaying = false;// 是否正在播放音乐
        int currentMusicIndex = -1;// 当前播放的音乐索引
       
        if (microphoneManager != null && microphoneManager.isRecording()) {// 如果麦克风正在放大
            wasAmplifying = true;// 麦克风正在放大
            microphoneManager.stop();// 停止麦克风放大
        }

        long currentMusicPosition = 0;// 当前播放位置
        
        if (musicPlayerManager != null && musicPlayerManager.isPlaying()) {// 如果音乐正在播放
            musicWasPlaying = true;// 音乐正在播放
            currentMusicIndex = musicPlayerManager.getCurrentIndex();// 记录当前播放索引
            currentMusicPosition = musicPlayerManager.getCurrentPosition();// 记录当前播放位置
            musicPlayerManager.stop();// 停止音乐播放
        }
       
        audioOutputManager.setOutputMode(outputMode);// 设置音频输出模式

        if (wasAmplifying && microphoneManager != null) {// 如果麦克风正在放大
            microphoneManager.start();// 启动麦克风放大
        }

        if (musicWasPlaying && musicPlayerManager != null && currentMusicIndex >= 0) {// 如果音乐正在播放且索引有效
            musicPlayerManager.start(currentMusicIndex, currentMusicPosition);// 重新启动音乐播放，从之前的位置继续
        }

        updateOutputButtons(outputMode);//

        enableFunctionButtons();

        String outputText = "";
        switch (outputMode) {
            case AudioOutputManager.OUTPUT_CAR:
                outputText = "仅车内播放";
                break;
            case AudioOutputManager.OUTPUT_EXTERNAL:
                outputText = "仅车外播放";
                break;
        }
        Toast.makeText(this, "音频输出已设置为：" + outputText, Toast.LENGTH_SHORT).show();
    }

    /**
     * 根据音频输出模式更新输出按钮的显示状态
     * @param outputMode 当前的音频输出模式
     *                   - AudioOutputManager.OUTPUT_CAR: 车内输出
     *                   - AudioOutputManager.OUTPUT_EXTERNAL: 车外输出
     */
    private void updateOutputButtons(int outputMode) {
        if (btnOutputCar != null) {
            if (outputMode == AudioOutputManager.OUTPUT_CAR) {
                btnOutputCar.setBackgroundColor(ContextCompat.getColor(this, R.color.button_background_selected));
            } else {
                btnOutputCar.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary));
            }
        }
        if (btnOutputExternal != null) {
            if (outputMode == AudioOutputManager.OUTPUT_EXTERNAL) {
                btnOutputExternal.setBackgroundColor(ContextCompat.getColor(this, R.color.button_background_selected));
            } else {
                btnOutputExternal.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary));
            }
        }
    }

    /**
     * 启用功能按钮，当用户选择了音频输出模式后
     */
    private void enableFunctionButtons() {
        if (btnMicAmplifier != null) {
            btnMicAmplifier.setEnabled(true);
        }
        if (btnTTS != null) {
            btnTTS.setEnabled(true);
        }
        if (btnMusicPlayer != null) {
            btnMusicPlayer.setEnabled(true);
        }
    }

    /**
     * 更新应用标题栏文本
     */
    private void updateAppTitle(String title) {
        if (tvAppTitle != null) {
            tvAppTitle.setText(title);
        }
    }

    private boolean checkPermissions() {
        for (String permission : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, getRequiredPermissions(), REQUEST_PERMISSIONS);
    }

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

    @Override
    protected void onResume() {
        super.onResume();
        isInBackground = false;

        // 恢复音频服务
        if (audioOutputManager != null) {
            audioOutputManager.resume();
        }

        // 更新输出按钮状态，显示当前保存的模式
        if (audioOutputManager != null) {
            updateOutputButtons(audioOutputManager.getOutputMode());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isInBackground = true;

        // 不暂停音频服务，确保后台播放正常
        // if (audioOutputManager != null) {
        //     audioOutputManager.pause();
        // }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // 停止所有音频服务
        if (microphoneManager != null) {
            microphoneManager.stop();
        }
        if (ttsManager != null) {
            ttsManager.shutdown();
        }
        // 不停止音乐播放，确保后台播放正常
        // if (musicPlayerManager != null) {
        //     musicPlayerManager.stop();
        // }
        if (audioFocusManager != null) {
            audioFocusManager.abandonAudioFocus();
        }

        // 不停止前台服务，确保后台播放正常
        // stopService(new Intent(this, AudioForegroundService.class));
    }

    private void setThemeMode() {
        int themeMode = appConfig.getThemeMode();
        switch (themeMode) {
            case AppConfig.THEME_MODE_SYSTEM:
                // 跟随系统主题
                getDelegate().setLocalNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
            case AppConfig.THEME_MODE_LIGHT:
                // 强制使用浅色主题
                getDelegate().setLocalNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case AppConfig.THEME_MODE_DARK:
                // 强制使用深色主题
                getDelegate().setLocalNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
                break;
        }
    }

    public static MainActivity getInstance() {
        return instance;
    }

    public TTSManager getTTSManager() {
        return ttsManager;
    }

    public MicrophoneManager getMicrophoneManager() {
        return microphoneManager;
    }

    public AudioOutputManager getAudioOutputManager() {
        return audioOutputManager;
    }

    public MusicPlayerManager getMusicPlayerManager() {
        return musicPlayerManager;
    }

    public boolean isInBackground() {
        return isInBackground;
    }

    private ActivityResultLauncher<Intent> manageExternalStorageLauncher;

    private void initActivityResultLaunchers() {
        // 初始化MANAGE_EXTERNAL_STORAGE权限请求
        manageExternalStorageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            if (Environment.isExternalStorageManager()) {
                                // 用户授予了MANAGE_EXTERNAL_STORAGE权限
                                initAudioManagers();
                                // 初始化完成后加载保存的功能状态
                                loadSavedFunctionState();
                            } else {
                                // 用户拒绝了MANAGE_EXTERNAL_STORAGE权限
                                Toast.makeText(MainActivity.this, "存储权限被拒绝，部分功能可能无法使用", Toast.LENGTH_LONG).show();
                            }
                        }
                    }
                }
        );
    }

    private void checkManageExternalStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                // 没有MANAGE_EXTERNAL_STORAGE权限，引导用户到设置页面
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                manageExternalStorageLauncher.launch(intent);
            } else {
                // 已有权限，初始化音频管理器
                initAudioManagers();
                // 初始化完成后加载保存的功能状态
                loadSavedFunctionState();
            }
        } else {
            // Android R以下不需要这个权限
            initAudioManagers();
            // 初始化完成后加载保存的功能状态
            loadSavedFunctionState();
        }
    }
}