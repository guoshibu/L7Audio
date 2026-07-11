package com.aug32.l7audio.ui.fragment.settings;

import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.text.HtmlCompat;

import java.util.Locale;

import com.aug32.l7audio.base.BaseFragment;
import com.aug32.l7audio.data.local.AppConfig;
import com.aug32.l7audio.data.local.config.AudioConfig;
import com.aug32.l7audio.data.local.config.floating.FloatingWindowConfig;
import com.aug32.l7audio.data.local.config.micoutput.MicOutputConfig;
import com.aug32.l7audio.data.local.config.ThemeConfig;
import com.aug32.l7audio.data.local.config.tts.TTSConfig;
import com.aug32.l7audio.domain.audio.micoutput.AudioOutputManager;
import com.aug32.l7audio.domain.audio.AudioServiceLocator;
import com.aug32.l7audio.domain.audio.tts.TTSManager;
import com.aug32.l7audio.R;
import com.aug32.l7audio.receiver.boot.BootReceiver;
import com.aug32.l7audio.service.floating.FloatingWindowService;
import com.aug32.l7audio.ui.activity.MainActivity;
import com.aug32.l7audio.utils.AppLog;
import com.aug32.l7audio.utils.ServiceCompat;

/**
 * 设置功能 Fragment
 *
 * <p>职责：
 * <ul>
 *   <li>主题模式设置（跟随系统/浅色/深色）</li>
 *   <li>开机自启动开关</li>
 *   <li>悬浮窗开关</li>
 *   <li>TTS 诊断功能</li>
 *   <li>音频设备参数配置</li>
 *   <li>音频路由调试信息</li>
 * </ul>
 *
 * <p>架构：按领域拆分配置类（ThemeConfig、AudioConfig、MicConfig、TTSConfig、FloatingWindowConfig），
 * 每个配置类负责对应领域的持久化读写，职责清晰。
 *
 * <p>目标 SDK：Android 11 (API 30)
 */
public class SettingsFragment extends BaseFragment {

    /** 日志标签 */
    private static final String TAG = "SettingsFragment";

    // ========== 主题设置 UI ==========
    /** 主题选择单选组 */
    private RadioGroup themeRadioGroup;
    /** 跟随系统主题单选按钮 */
    private RadioButton themeSystemRadio;
    /** 浅色主题单选按钮 */
    private RadioButton themeLightRadio;
    /** 深色主题单选按钮 */
    private RadioButton themeDarkRadio;
    /** 开机自启动开关 */
    private Switch autoStartSwitch;
    /** 悬浮窗开关 */
    private Switch floatingWindowSwitch;
    /** 返回按钮 */
    private Button btnBack;
    /** 主页按钮 */
    private Button btnHome;
    /** 调试音频路由按钮 */
    private Button btnDebugAudioRoutes;
    /** 音频路由信息显示文本 */
    private TextView tvAudioRoutes;

    // ========== TTS 诊断 UI ==========
    /** TTS 状态显示文本 */
    private TextView tvTTSStatus;
    /** 测试 TTS 按钮 */
    private Button btnTestTTS;
    /** 检查 TTS 状态按钮 */
    private Button btnCheckTTSStatus;

    // ========== 车外喊话设置 UI ==========
    /** 防抖间隔输入框 */
    private EditText editDebounceInterval;
    /** 静音检测开关 */
    private Switch swSilenceDetection;
    /** 静音超时输入框 */
    private EditText editSilenceTimeout;
    /** 静音阈值输入框 */
    private EditText editSilenceThreshold;
    /** 保存车外喊话设置按钮 */
    private Button btnSaveAnnouncement;

    // ========== 音频设备设置 UI ==========
    /** 车外音频用途输入框 */
    private EditText editAudioUsageExternal;
    /** 车内音频用途输入框 */
    private EditText editAudioUsageCar;
    /** 音频输入源输入框 */
    private EditText editAudioSource;
    /** 最大放大倍数输入框 */
    private EditText editMaxAmplification;
    /** 放大倍数警告文本 */
    private TextView tvAmplificationWarning;
    /** 枚举麦克风按钮 */
    private Button btnEnumMics;
    /** 枚举输出设备按钮 */
    private Button btnEnumOutputs;
    /** 枚举车内输出设备按钮 */
    private Button btnEnumCarOutputs;
    /** 保存音频设备设置按钮 */
    private Button btnSaveAudioDevice;
    /** 音频设备状态显示文本 */
    private TextView tvAudioDeviceStatus;
    /** 车外喊话状态显示文本 */
    private TextView tvAnnouncementStatus;
    /** 关于页面按钮 */
    private Button btnAbout;
    /** 恢复默认设置按钮 */
    private Button btnRestoreDefaults;

    // ========== 配置管理器 ==========
    /** 主题配置 */
    private ThemeConfig themeConfig;
    /** 音频配置 */
    private AudioConfig audioConfig;
    /** 麦克风配置 */
    private MicOutputConfig micOutputConfig;
    /** TTS 配置 */
    private TTSConfig ttsConfig;
    /** 悬浮窗配置 */
    private FloatingWindowConfig floatingWindowConfig;
    /** TTS 管理器 */
    private TTSManager ttsManager;
    /** 音频输出管理器 */
    private AudioOutputManager audioOutputManager;

    /**
     * 返回布局资源 ID。
     *
     * @return 设置页面布局资源 ID
     */
    @Override
    protected int getLayoutId() {
        return R.layout.fragment_settings;
    }

    /**
     * 初始化视图控件。
     *
     * <p>查找所有 UI 控件，初始化各领域配置类和音频服务，
     * 加载当前配置值到 UI 控件。
     *
     * @param view Fragment 根视图
     */
    @Override
    protected void initViews(View view) {
        themeRadioGroup = view.findViewById(R.id.theme_radio_group);
        themeSystemRadio = view.findViewById(R.id.theme_system);
        themeLightRadio = view.findViewById(R.id.theme_light);
        themeDarkRadio = view.findViewById(R.id.theme_dark);
        autoStartSwitch = view.findViewById(R.id.auto_start_switch);
        floatingWindowSwitch = view.findViewById(R.id.floating_window_switch);
        btnBack = view.findViewById(R.id.btn_back);
        btnHome = view.findViewById(R.id.btn_home);
        btnDebugAudioRoutes = view.findViewById(R.id.btn_debug_audio_routes);
        tvAudioRoutes = view.findViewById(R.id.tv_audio_routes);

        tvTTSStatus = view.findViewById(R.id.tv_tts_status);
        btnTestTTS = view.findViewById(R.id.btn_test_tts);
        btnCheckTTSStatus = view.findViewById(R.id.btn_check_tts_status);

        editDebounceInterval = view.findViewById(R.id.edit_debounce_interval);
        swSilenceDetection = view.findViewById(R.id.sw_silence_detection);
        editSilenceTimeout = view.findViewById(R.id.edit_silence_timeout);
        editSilenceThreshold = view.findViewById(R.id.edit_silence_threshold);
        btnSaveAnnouncement = view.findViewById(R.id.btn_save_announcement);
        tvAnnouncementStatus = view.findViewById(R.id.tv_announcement_status);

        editAudioUsageExternal = view.findViewById(R.id.edit_audio_usage_external);
        editAudioUsageCar = view.findViewById(R.id.edit_audio_usage_car);
        editAudioSource = view.findViewById(R.id.edit_audio_source);
        editMaxAmplification = view.findViewById(R.id.edit_max_amplification);
        tvAmplificationWarning = view.findViewById(R.id.tv_amplification_warning);
        btnEnumMics = view.findViewById(R.id.btn_enum_mics);
        btnEnumOutputs = view.findViewById(R.id.btn_enum_outputs);
        btnEnumCarOutputs = view.findViewById(R.id.btn_enum_car_outputs);
        btnSaveAudioDevice = view.findViewById(R.id.btn_save_audio_device);
        tvAudioDeviceStatus = view.findViewById(R.id.tv_audio_device_status);

        btnAbout = view.findViewById(R.id.btn_about);
        btnRestoreDefaults = view.findViewById(R.id.btn_restore_defaults);

        tvAudioRoutes.setMovementMethod(new android.text.method.ScrollingMovementMethod());

        // 初始化配置（按领域拆分）
        android.content.SharedPreferences prefs = requireContext().getSharedPreferences(
                requireContext().getPackageName() + "_preferences", android.content.Context.MODE_PRIVATE);
        themeConfig = new ThemeConfig(prefs);
        audioConfig = new AudioConfig(prefs);
        micOutputConfig = new MicOutputConfig(prefs);
        ttsConfig = new TTSConfig(prefs);
        floatingWindowConfig = new FloatingWindowConfig(prefs);

        // 加载车外喊话设置（在 micConfig 初始化之后调用）
        loadAnnouncementSettings();

        AudioServiceLocator locator = AudioServiceLocator.getInstance();
        locator.init(requireContext());
        audioOutputManager = locator.getAudioOutputManager();
        ttsManager = locator.getTTSManager();

        // 设置当前值
        int currentTheme = themeConfig.getThemeMode();
        switch (currentTheme) {
            case AppConfig.THEME_MODE_SYSTEM:
                themeSystemRadio.setChecked(true);
                break;
            case AppConfig.THEME_MODE_LIGHT:
                themeLightRadio.setChecked(true);
                break;
            case AppConfig.THEME_MODE_DARK:
                themeDarkRadio.setChecked(true);
                break;
        }

        autoStartSwitch.setChecked(themeConfig.isAutoStartOnBoot());
        floatingWindowSwitch.setChecked(floatingWindowConfig.isEnabled());

        // 加载音频设备设置
        loadAudioDeviceSettings();
    }

    /**
     * 初始化数据。
     *
     * <p>SettingsFragment 的数据加载已在 initViews 中完成，
     * 此方法仅作为基类抽象方法的空实现。
     */
    @Override
    protected void initData() {
        // 数据已在 initViews 中加载
    }

    /**
     * Fragment 视图销毁时调用。
     *
     * <p>置空所有 View 引用，防止内存泄漏。
     */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 置空主题设置 UI
        themeRadioGroup = null;
        themeSystemRadio = null;
        themeLightRadio = null;
        themeDarkRadio = null;
        autoStartSwitch = null;
        floatingWindowSwitch = null;
        btnBack = null;
        btnHome = null;
        btnDebugAudioRoutes = null;
        tvAudioRoutes = null;
        // 置空 TTS 诊断 UI
        tvTTSStatus = null;
        btnTestTTS = null;
        btnCheckTTSStatus = null;
        // 置空车外喊话设置 UI
        editDebounceInterval = null;
        swSilenceDetection = null;
        editSilenceTimeout = null;
        editSilenceThreshold = null;
        btnSaveAnnouncement = null;
        tvAnnouncementStatus = null;
        // 置空音频设备设置 UI
        editAudioUsageExternal = null;
        editAudioUsageCar = null;
        editAudioSource = null;
        editMaxAmplification = null;
        tvAmplificationWarning = null;
        btnEnumMics = null;
        btnEnumOutputs = null;
        btnEnumCarOutputs = null;
        btnSaveAudioDevice = null;
        tvAudioDeviceStatus = null;
        btnAbout = null;
        btnRestoreDefaults = null;
    }

    /**
     * 初始化事件监听器。
     *
     * <p>为主题切换、自启动、悬浮窗、TTS 设置、音频设备设置等
     * 所有可交互控件设置监听器，用户操作时实时更新配置。
     */
    @Override
    protected void initListeners() {
        // 主题切换
        themeRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (!isAdded()) return;
            int themeMode = AppConfig.THEME_MODE_SYSTEM;
            if (checkedId == R.id.theme_system) {
                themeMode = AppConfig.THEME_MODE_SYSTEM;
            } else if (checkedId == R.id.theme_light) {
                themeMode = AppConfig.THEME_MODE_LIGHT;
            } else if (checkedId == R.id.theme_dark) {
                themeMode = AppConfig.THEME_MODE_DARK;
            }
            themeConfig.setThemeMode(themeMode);
            try {
                FloatingWindowService.notifyThemeChanged(requireContext());
            } catch (Exception e) {
                AppLog.d(TAG, "Failed to notify theme change");
            }
            try {
                Intent intent = new Intent(requireContext(), MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                requireContext().startActivity(intent);
                requireActivity().finishAffinity();
            } catch (Exception e) {
                requireActivity().finishAffinity();
            }
        });

        // 开机自启动开关
        autoStartSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isAdded()) return;
            themeConfig.setAutoStartOnBoot(isChecked);
            BootReceiver.enable(requireContext());
        });

        // 悬浮窗开关
        floatingWindowSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isAdded()) return;
            floatingWindowConfig.setEnabled(isChecked);
            if (isChecked) {
                startFloatingWindowService();
            } else {
                stopFloatingWindowService();
            }
        });

        // 返回按钮
        btnBack.setOnClickListener(v -> {
            if (!isAdded()) return;
            MainActivity activity = (MainActivity) getActivity();
            if (activity != null) activity.showMainInterface();
        });

        // 主页按钮
        btnHome.setOnClickListener(v -> {
            if (!isAdded()) return;
            MainActivity activity = (MainActivity) getActivity();
            if (activity != null) activity.showMainInterface();
        });

        // 调试音频路由
        btnDebugAudioRoutes.setOnClickListener(v -> displayAudioRoutes());

        // TTS 诊断
        btnTestTTS.setOnClickListener(v -> testTTS());
        btnCheckTTSStatus.setOnClickListener(v -> checkTTSStatus());

        // 车外喊话设置
        btnSaveAnnouncement.setOnClickListener(v -> saveAnnouncementSettings());

        // 音频设备设置
        setupAudioDeviceListeners();

        // 关于按钮
        if (btnAbout != null) {
            btnAbout.setOnClickListener(v -> {
                if (!isAdded()) return;
                MainActivity activity = (MainActivity) getActivity();
                if (activity != null) activity.showAboutFragment();
            });
        }

        // 恢复默认设置按钮
        if (btnRestoreDefaults != null) {
            btnRestoreDefaults.setOnClickListener(v -> restoreDefaults());
        }
    }

    /**
     * 显示详细的音频路由信息
     *
     * <p>包含：基本音频信息、设备连接状态、音量设置、
     * 所有音频设备详细列表（输入+输出）、系统信息等。
     */
    private void displayAudioRoutes() {
        if (!isAdded()) return;
        StringBuilder routesInfo = new StringBuilder();
        routesInfo.append("═══════ 音频路由详细信息 ═══════\n\n");

        try {
            android.content.Context ctx = getContext();
            if (ctx == null) return;
            android.media.AudioManager audioManager = (android.media.AudioManager)
                    ctx.getSystemService(android.content.Context.AUDIO_SERVICE);

            // 一、基本音频信息
            routesInfo.append("【一、基本音频信息】\n");
            int mode = audioManager.getMode();
            routesInfo.append("  音频模式：").append(getModeName(mode)).append(" (").append(mode).append(")\n");

            int ringerMode = audioManager.getRingerMode();
            String ringerModeName;
            switch (ringerMode) {
                case android.media.AudioManager.RINGER_MODE_SILENT:
                    ringerModeName = "静音模式";
                    break;
                case android.media.AudioManager.RINGER_MODE_VIBRATE:
                    ringerModeName = "振动模式";
                    break;
                default:
                    ringerModeName = "正常模式";
                    break;
            }
            routesInfo.append("  铃声模式：").append(ringerModeName).append(" (").append(ringerMode).append(")\n");
            routesInfo.append("  蓝牙SCO可用：").append(audioManager.isBluetoothScoAvailableOffCall() ? "是" : "否").append("\n");
            routesInfo.append("\n");

            // 二、音量设置
            routesInfo.append("【二、音量设置】\n");
            int musicVol = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
            int musicMax = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
            routesInfo.append(String.format(Locale.getDefault(),
                    "  音乐流：%d / %d\n", musicVol, musicMax));

            int ringVol = audioManager.getStreamVolume(android.media.AudioManager.STREAM_RING);
            int ringMax = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_RING);
            routesInfo.append(String.format(Locale.getDefault(),
                    "  铃声流：%d / %d\n", ringVol, ringMax));

            int callVol = audioManager.getStreamVolume(android.media.AudioManager.STREAM_VOICE_CALL);
            int callMax = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_VOICE_CALL);
            routesInfo.append(String.format(Locale.getDefault(),
                    "  通话流：%d / %d\n", callVol, callMax));

            int alarmVol = audioManager.getStreamVolume(android.media.AudioManager.STREAM_ALARM);
            int alarmMax = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM);
            routesInfo.append(String.format(Locale.getDefault(),
                    "  闹钟流：%d / %d\n", alarmVol, alarmMax));
            routesInfo.append("\n");

            // 三、音频设备详细列表（通过 getDevices 获取）
            routesInfo.append("【三、系统音频设备列表】\n");
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                try {
                    int GET_DEVICES_ALL = android.media.AudioManager.class
                            .getField("GET_DEVICES_ALL").getInt(null);
                    android.media.AudioDeviceInfo[] allDevices = audioManager.getDevices(GET_DEVICES_ALL);

                    int inputCount = 0;
                    int outputCount = 0;
                    if (allDevices != null) {
                        for (android.media.AudioDeviceInfo device : allDevices) {
                            if (device.isSource()) inputCount++;
                            if (device.isSink()) outputCount++;
                        }
                    }
                    routesInfo.append(String.format(Locale.getDefault(),
                            "  总计：%d 个设备（输入 %d，输出 %d）\n\n",
                            (allDevices != null ? allDevices.length : 0), inputCount, outputCount));

                    if (allDevices != null && allDevices.length > 0) {
                        for (int i = 0; i < allDevices.length; i++) {
                            android.media.AudioDeviceInfo device = allDevices[i];
                            CharSequence productName = device.getProductName();
                            String name = (productName == null || productName.length() == 0)
                                    ? "无名设备" : productName.toString();
                            int type = device.getType();
                            boolean isSource = device.isSource();
                            boolean isSink = device.isSink();
                            String address = device.getAddress();
                            if (address == null || address.isEmpty()) address = "无";

                            String direction;
                            if (isSource && isSink) {
                                direction = "输入+输出";
                            } else if (isSource) {
                                direction = "输入";
                            } else {
                                direction = "输出";
                            }

                            routesInfo.append(String.format(Locale.getDefault(),
                                    "  %d. [%s] %s\n", (i + 1), direction, name));
                            routesInfo.append("     类型：").append(getDeviceTypeName(type)).append("\n");
                            routesInfo.append("     类型ID：").append(type).append("\n");
                            routesInfo.append("     地址：").append(address).append("\n");
                            routesInfo.append("     输入：").append(isSource ? "是" : "否")
                                    .append("  输出：").append(isSink ? "是" : "否").append("\n");
                        }
                    }
                } catch (Exception e) {
                    routesInfo.append("  获取设备列表失败：").append(e.getMessage()).append("\n");

                    // 回退到旧API
                    routesInfo.append("\n  【回退：旧API设备状态】\n");
                    try {
                        routesInfo.append("    扬声器：").append(audioManager.isSpeakerphoneOn() ? "开启" : "关闭").append("\n");
                        routesInfo.append("    蓝牙SCO：").append(audioManager.isBluetoothScoOn() ? "开启" : "关闭").append("\n");
                        routesInfo.append("    有线耳机：").append(audioManager.isWiredHeadsetOn() ? "已连接" : "未连接").append("\n");
                        routesInfo.append("    蓝牙A2DP：").append(audioManager.isBluetoothA2dpOn() ? "已连接" : "未连接").append("\n");
                    } catch (Exception ex) {
                        routesInfo.append("    旧API也失败：").append(ex.getMessage()).append("\n");
                    }
                }
            } else {
                routesInfo.append("  Android 版本较低，无法获取详细设备信息\n");
            }
            routesInfo.append("\n");

            // 四、系统信息
            routesInfo.append("【四、系统信息】\n");
            routesInfo.append("  Android 版本：").append(android.os.Build.VERSION.RELEASE).append("\n");
            routesInfo.append("  API Level：").append(android.os.Build.VERSION.SDK_INT).append("\n");
            routesInfo.append("  设备品牌：").append(android.os.Build.BRAND).append("\n");
            routesInfo.append("  设备型号：").append(android.os.Build.MODEL).append("\n");
            routesInfo.append("  设备厂商：").append(android.os.Build.MANUFACTURER).append("\n");

        } catch (Exception e) {
            routesInfo.append("获取音频路由信息时出错：").append(e.getMessage()).append("\n");
            AppLog.e(TAG, "Error displaying audio routes", e);
        }

        tvAudioRoutes.setText(routesInfo.toString());
        AppLog.d(TAG, "Audio routes info:\n" + routesInfo.toString());
    }

    /** 获取音频模式名称 */
    private String getModeName(int mode) {
        switch (mode) {
            case android.media.AudioManager.MODE_NORMAL:
                return "正常模式";
            case android.media.AudioManager.MODE_RINGTONE:
                return "铃声模式";
            case android.media.AudioManager.MODE_IN_CALL:
                return "通话模式";
            case android.media.AudioManager.MODE_IN_COMMUNICATION:
                return "通信模式";
            default:
                return "未知模式 (" + mode + ")";
        }
    }

    /** 测试 TTS（车外音频输出） */
    private void testTTS() {
        String testMessage = "测试TTS发声功能，这是一条测试消息。";
        if (audioOutputManager == null || ttsManager == null) {
            tvTTSStatus.setText("TTS测试失败：管理器未初始化");
            return;
        }
        try {
            // 使用车外音频输出模式进行测试
            int externalUsage = audioOutputManager.getExternalAudioUsage();
            boolean success = ttsManager.speakWithUsage(testMessage, externalUsage);
            tvTTSStatus.setText(success ? "TTS测试成功！正在车外播放。" : "TTS测试失败。");
        } catch (Exception e) {
            tvTTSStatus.setText("TTS测试出错：" + e.getMessage());
        }
    }

    /** 检查 TTS 状态 */
    private void checkTTSStatus() {
        StringBuilder statusInfo = new StringBuilder();
        statusInfo.append("TTS状态诊断：\n\n");

        try {
            boolean initialized = ttsManager.isInitialized();
            statusInfo.append("初始化状态：").append(initialized ? "已初始化" : "未初始化").append("\n");
            statusInfo.append("Android版本：").append(android.os.Build.VERSION.RELEASE).append("\n");
            statusInfo.append("设备品牌：").append(android.os.Build.BRAND).append("\n");

            if (initialized) {
                statusInfo.append("\nTTS引擎状态：正常\n");
            } else {
                statusInfo.append("\nTTS引擎状态：未初始化\n");
            }

            tvTTSStatus.setText(statusInfo.toString());
        } catch (Exception e) {
            tvTTSStatus.setText("检查TTS状态时出错：" + e.getMessage());
        }
    }

    /** 加载音频设备设置 */
    private void loadAudioDeviceSettings() {
        editAudioUsageExternal.setText(String.valueOf(audioConfig.getUsageExternal()));
        editAudioUsageCar.setText(String.valueOf(audioConfig.getUsageCar()));
        editAudioSource.setText(String.valueOf(audioConfig.getAudioInputSource()));
        editMaxAmplification.setText(String.valueOf(micOutputConfig.getMaxAmplification()));
        updateAmplificationWarning();
    }

    /** 设置音频设备监听器 */
    private void setupAudioDeviceListeners() {
        btnEnumMics.setOnClickListener(v -> enumMicrophones());
        btnEnumOutputs.setOnClickListener(v -> enumOutputDevices());
        btnEnumCarOutputs.setOnClickListener(v -> enumCarOutputDevices());
        btnSaveAudioDevice.setOnClickListener(v -> saveAudioDeviceSettings());

        // 输入过滤器
        android.text.InputFilter inputFilter = (source, start, end, dest, dstart, dend) -> {
            try {
                String newText = dest.subSequence(0, dstart).toString() +
                                source.subSequence(start, end).toString() +
                                dest.subSequence(dend, dest.length()).toString();
                if (newText.isEmpty()) return null;
                int value = Integer.parseInt(newText);
                if (value >= 1 && value <= 20) return null;
                return "";
            } catch (NumberFormatException e) {
                return "";
            }
        };

        editMaxAmplification.setFilters(new android.text.InputFilter[]{
                new android.text.InputFilter.LengthFilter(2),
                inputFilter
        });

        editMaxAmplification.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateAmplificationWarning();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });
    }

    /** 更新放大警告 */
    private void updateAmplificationWarning() {
        if (tvAmplificationWarning == null) return;
        String text = editMaxAmplification.getText().toString().trim();
        if (text.isEmpty()) {
            tvAmplificationWarning.setVisibility(View.GONE);
            return;
        }
        try {
            int value = Integer.parseInt(text);
            tvAmplificationWarning.setVisibility(value > 10 ? View.VISIBLE : View.GONE);
        } catch (NumberFormatException e) {
            tvAmplificationWarning.setVisibility(View.GONE);
        }
    }

    /**
     * 枚举麦克风设备
     *
     * <p>使用反射调用 AudioManager.getDevices(GET_DEVICES_INPUTS) 获取所有输入设备，
     * 简洁显示设备列表，让用户知道车机有几个麦克风。
     */
    private void enumMicrophones() {
        enumAudioDevices(true, "麦克风设备列表");
    }

    /**
     * 枚举输出设备
     *
     * <p>使用反射调用 AudioManager.getDevices(GET_DEVICES_OUTPUTS) 获取所有输出设备，
     * 简洁显示设备列表，让用户知道车机有几个扬声器/输出设备。
     */
    private void enumOutputDevices() {
        enumAudioDevices(false, "输出设备列表");
    }

    /**
     * 枚举车内输出设备
     *
     * <p>与枚举输出设备共用同一逻辑，都是显示所有输出设备，
     * 只是标题不同，方便用户理解。
     */
    private void enumCarOutputDevices() {
        enumAudioDevices(false, "输出设备列表");
    }

    /**
     * 枚举音频设备（共用方法）
     *
     * <p>使用反射调用 AudioManager.getDevices() 获取指定方向的设备列表，
     * 简洁显示：编号 + 设备名 + 设备类型中文名 + 类型ID。
     *
     * @param isInput true=输入设备（麦克风），false=输出设备（扬声器）
     * @param title   标题文字
     */
    private void enumAudioDevices(boolean isInput, String title) {
        if (!isAdded()) return;
        try {
            StringBuilder info = new StringBuilder();

            android.content.Context ctx = getContext();
            if (ctx == null) return;
            android.media.AudioManager audioManager = (android.media.AudioManager)
                    ctx.getSystemService(android.content.Context.AUDIO_SERVICE);

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                try {
                    int flag = isInput
                            ? android.media.AudioManager.class.getField("GET_DEVICES_INPUTS").getInt(null)
                            : android.media.AudioManager.class.getField("GET_DEVICES_OUTPUTS").getInt(null);
                    android.media.AudioDeviceInfo[] devices = audioManager.getDevices(flag);

                    int count = (devices != null) ? devices.length : 0;
                    info.append(title).append(" (共 ").append(count).append(" 个)\n\n");

                    if (devices != null && devices.length > 0) {
                        for (int i = 0; i < devices.length; i++) {
                            android.media.AudioDeviceInfo device = devices[i];
                            CharSequence productName = device.getProductName();
                            String name = (productName == null || productName.length() == 0)
                                    ? "无名设备" : productName.toString();
                            int type = device.getType();

                            info.append((i + 1)).append(". ").append(name).append("\n");
                            String address = device.getAddress();
                            if (address != null && !address.isEmpty()) {
                                info.append("   地址：").append(address).append("\n");
                            }
                            info.append("   类型：").append(getDeviceTypeName(type)).append("\n");
                            info.append("   类型ID：").append(type).append("\n");
                        }
                    } else {
                        info.append("未检测到设备\n");
                    }
                } catch (Exception e) {
                    info.append("获取设备失败: ").append(e.getMessage()).append("\n");
                    AppLog.e(TAG, "Failed to enumerate audio devices", e);
                }
            } else {
                info.append("Android 版本较低，无法获取设备信息\n");
            }

            tvAudioDeviceStatus.setText(info.toString());
            AppLog.d(TAG, "Audio device enumeration completed: " + info.toString());
        } catch (Exception e) {
            tvAudioDeviceStatus.setText("枚举设备时出错：" + e.getMessage());
            AppLog.e(TAG, "Error enumerating audio devices", e);
        }
    }

    /**
     * 获取音频设备类型的名称
     *
     * <p>将系统类型ID映射为中文名称，方便用户识别设备种类。
     * 覆盖了 Android AudioDeviceInfo 的全部常见设备类型。
     *
     * @param deviceType 设备类型ID（AudioDeviceInfo.TYPE_*）
     * @return 设备类型的中文名称
     */
    private String getDeviceTypeName(int deviceType) {
        switch (deviceType) {
            case android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE:
                return "内置听筒 (BUILTIN_EARPIECE)";
            case android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                return "内置扬声器 (BUILTIN_SPEAKER)";
            case android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET:
                return "有线耳机 (WIRED_HEADSET)";
            case android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                return "有线耳机 (WIRED_HEADPHONES)";
            case android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                return "蓝牙SCO (BLUETOOTH_SCO)";
            case android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                return "蓝牙A2DP (BLUETOOTH_A2DP)";
            case android.media.AudioDeviceInfo.TYPE_HDMI:
                return "HDMI (HDMI)";
            case android.media.AudioDeviceInfo.TYPE_USB_DEVICE:
                return "USB设备 (USB_DEVICE)";
            case android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC:
                return "内置麦克风 (BUILTIN_MIC)";
            case android.media.AudioDeviceInfo.TYPE_REMOTE_SUBMIX:
                return "远程混音 (REMOTE_SUBMIX)";
            case android.media.AudioDeviceInfo.TYPE_TELEPHONY:
                return "电话 (TELEPHONY)";
            case android.media.AudioDeviceInfo.TYPE_AUX_LINE:
                return "辅助线 (AUX_LINE)";
            case android.media.AudioDeviceInfo.TYPE_IP:
                return "IP (IP)";
            case android.media.AudioDeviceInfo.TYPE_BUS:
                return "总线 (BUS)";
            case android.media.AudioDeviceInfo.TYPE_USB_ACCESSORY:
                return "USB配件 (USB_ACCESSORY)";
            case android.media.AudioDeviceInfo.TYPE_DOCK:
                return "底座 (DOCK)";
            case android.media.AudioDeviceInfo.TYPE_FM:
                return "FM (FM)";
            case android.media.AudioDeviceInfo.TYPE_BLE_HEADSET:
                return "BLE耳机 (BLE_HEADSET)";
            case android.media.AudioDeviceInfo.TYPE_HEARING_AID:
                return "助听器 (HEARING_AID)";
            case android.media.AudioDeviceInfo.TYPE_USB_HEADSET:
                return "USB耳机 (USB_HEADSET)";
            case android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE:
                return "内置安全扬声器 (BUILTIN_SPEAKER_SAFE)";
            default:
                return "未知设备类型 (" + deviceType + ")";
        }
    }

    /** 加载车外喊话设置 */
    private void loadAnnouncementSettings() {
        editDebounceInterval.setText(String.valueOf(micOutputConfig.getDebounceInterval()));
        swSilenceDetection.setChecked(micOutputConfig.isSilenceDetectionEnabled());
        editSilenceTimeout.setText(String.valueOf(micOutputConfig.getSilenceTimeout()));
        editSilenceThreshold.setText(String.valueOf(micOutputConfig.getSilenceThreshold()));
    }

    /** 保存车外喊话设置 */
    private void saveAnnouncementSettings() {
        try {
            String debounceIntervalStr = editDebounceInterval.getText().toString().trim();
            int debounceInterval = !debounceIntervalStr.isEmpty() ? Integer.parseInt(debounceIntervalStr) : 800;
            if (debounceInterval < 500) debounceInterval = 500;
            else if (debounceInterval > 2000) debounceInterval = 2000;

            boolean silenceDetectionEnabled = swSilenceDetection.isChecked();

            String silenceTimeoutStr = editSilenceTimeout.getText().toString().trim();
            int silenceTimeout = !silenceTimeoutStr.isEmpty() ? Integer.parseInt(silenceTimeoutStr) : 30;
            if (silenceTimeout < 5) silenceTimeout = 5;
            else if (silenceTimeout > 300) silenceTimeout = 300;

            String silenceThresholdStr = editSilenceThreshold.getText().toString().trim();
            float silenceThreshold = !silenceThresholdStr.isEmpty() ? Float.parseFloat(silenceThresholdStr) : 0.05f;
            if (silenceThreshold < 0.03f) silenceThreshold = 0.03f;
            else if (silenceThreshold > 0.3f) silenceThreshold = 0.3f;

            micOutputConfig.setDebounceInterval(debounceInterval);
            micOutputConfig.setSilenceDetectionEnabled(silenceDetectionEnabled);
            micOutputConfig.setSilenceTimeout(silenceTimeout);
            micOutputConfig.setSilenceThreshold(silenceThreshold);

            String message = "车外喊话设置已保存<br>" +
                    "防抖间隔: <font color='#FF0000'>" + debounceInterval + "ms</font><br>" +
                    "静音检测: <font color='#FF0000'>" + (silenceDetectionEnabled ? "开启" : "关闭") + "</font><br>" +
                    "静音超时: <font color='#FF0000'>" + silenceTimeout + "秒</font><br>" +
                    "静音阈值: <font color='#FF0000'>" + silenceThreshold + "</font>";
            tvAnnouncementStatus.setText(HtmlCompat.fromHtml(message, HtmlCompat.FROM_HTML_MODE_LEGACY));

            try {
                ttsManager.speakWithUsage("车外喊话设置已保存", audioOutputManager.getCarAudioUsage());
            } catch (Exception e) {
                AppLog.e(TAG, "Failed to speak", e);
            }

        } catch (NumberFormatException e) {
            tvAudioDeviceStatus.setText("输入值无效，请输入有效的数字");
        }
    }

    /** 保存音频设备设置 */
    private void saveAudioDeviceSettings() {
        try {
            String outputUsageExternalStr = editAudioUsageExternal.getText().toString().trim();
            int audioOutputUsageExternal = !outputUsageExternalStr.isEmpty() ? Integer.parseInt(outputUsageExternalStr) : 9;

            String outputUsageCarStr = editAudioUsageCar.getText().toString().trim();
            int audioOutputUsageCar = !outputUsageCarStr.isEmpty() ? Integer.parseInt(outputUsageCarStr) : 1;

            String inputSourceStr = editAudioSource.getText().toString().trim();
            int audioInputSource = !inputSourceStr.isEmpty() ? Integer.parseInt(inputSourceStr) : 1;

            String maxAmplificationStr = editMaxAmplification.getText().toString().trim();
            int maxAmplification = !maxAmplificationStr.isEmpty() ? Integer.parseInt(maxAmplificationStr) : 2;
            if (maxAmplification < 1) maxAmplification = 1;
            else if (maxAmplification > 20) maxAmplification = 20;

            audioConfig.setUsageExternal(audioOutputUsageExternal);
            audioConfig.setUsageCar(audioOutputUsageCar);
            audioConfig.setAudioInputSource(audioInputSource);
            micOutputConfig.setMaxAmplification(maxAmplification);

            String message = "音频设备设置已保存<br>" +
                    "车外输出: <font color='#FF0000'>" + audioOutputUsageExternal + "</font><br>" +
                    "车内输出: <font color='#FF0000'>" + audioOutputUsageCar + "</font><br>" +
                    "麦克风源: <font color='#FF0000'>" + audioInputSource + "</font><br>" +
                    "最大放大: <font color='#FF0000'>" + maxAmplification + "</font>";
            tvAudioDeviceStatus.setText(HtmlCompat.fromHtml(message, HtmlCompat.FROM_HTML_MODE_LEGACY));

            try {
                ttsManager.speakWithUsage("音频设备设置已保存", audioOutputManager.getCarAudioUsage());
            } catch (Exception e) {
                AppLog.e(TAG, "Failed to speak", e);
            }

        } catch (NumberFormatException e) {
            tvAudioDeviceStatus.setText("输入值无效，请输入有效的数字");
        }
    }

    /**
     * 恢复所有设置为默认值
     *
     * <p>清空 SharedPreferences 中所有配置，重新初始化各 Config 子类，
     * 并刷新 UI 控件显示默认值。恢复后需要重启应用才能完全生效。
     */
    private void restoreDefaults() {
        if (!isAdded()) return;
        android.content.SharedPreferences prefs = requireContext().getSharedPreferences(
                requireContext().getPackageName() + "_preferences", android.content.Context.MODE_PRIVATE);

        // 清空所有配置
        prefs.edit().clear().apply();

        // 重新初始化配置对象
        themeConfig = new ThemeConfig(prefs);
        audioConfig = new AudioConfig(prefs);
        micOutputConfig = new MicOutputConfig(prefs);
        ttsConfig = new TTSConfig(prefs);
        floatingWindowConfig = new FloatingWindowConfig(prefs);

        // 刷新 UI
        loadAnnouncementSettings();
        loadAudioDeviceSettings();

        // 主题恢复为跟随系统
        themeRadioGroup.check(R.id.theme_system);

        // 开机自启恢复为开启
        autoStartSwitch.setChecked(true);

        // 悬浮窗恢复为开启
        floatingWindowSwitch.setChecked(true);

        // 显示提示
        Toast.makeText(requireContext(), "已恢复默认设置，请重启应用以完全生效", Toast.LENGTH_LONG).show();
    }

    /** 启动悬浮窗服务 */
    private void startFloatingWindowService() {
        Intent serviceIntent = new Intent(requireContext(), FloatingWindowService.class);
        ServiceCompat.startForegroundService(requireContext(), serviceIntent);
    }

    /** 停止悬浮窗服务 */
    private void stopFloatingWindowService() {
        Intent serviceIntent = new Intent(requireContext(), FloatingWindowService.class);
        ServiceCompat.stopService(requireContext(), serviceIntent);
    }
}
