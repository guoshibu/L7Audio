package com.aug32.l7audio.ui.fragment.settings;

import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
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
import com.aug32.l7audio.domain.audio.micoutput.MicrophoneManager;
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
    // 字体缩放排查专用 TAG，便于 adb logcat 单独过滤：adb logcat | findstr FontScale
    private static final String FONT_SCALE_TAG = "FontScale";
    // 悬浮窗权限（SYSTEM_ALERT_WINDOW）授权请求码，用于 startActivityForResult 跳转系统授权页后回调识别
    private static final int REQUEST_OVERLAY_PERMISSION = 200;

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
    // 程序化改写悬浮窗开关状态（如无权限弹回）时置 true，令监听器跳过业务逻辑，避免 setChecked 再入导致的启停抖动
    private boolean suppressFloatingSwitchCallback = false;
    /** 返回按钮 */
    private Button btnBack;
    /** 主页按钮 */
    private Button btnHome;
    /** 调试音频路由按钮 */
    private Button btnDebugAudioRoutes;
    /** usage 路由探测按钮（遍历 0~100 的 usage 值，逐个建流并读取实际路由设备） */
    private Button btnProbeUsageRoutes;
    /** 音频路由信息显示文本（显示路由/探测结果，两个按钮共用） */
    private TextView tvAudioRoutes;

    // ========== 字体大小设置 UI ==========
    /** 字体缩放滑动条（0.7×–1.5×，步进 0.05，共 17 档） */
    private SeekBar seekFontScale;
    /** 当前字体缩放系数显示文本 */
    private TextView tvFontScaleValue;
    /** 字体预览标题文本 */
    private TextView tvFontPreviewTitle;
    /** 字体预览正文文本 */
    private TextView tvFontPreviewBody;
    /** 重启应用兜底按钮 */
    private Button btnRestartApp;

    /** 字体预览标题基准字号（sp，缩放系数为 1.0 时的大小） */
    private static final float FONT_PREVIEW_TITLE_SP = 20f;
    /** 字体预览正文基准字号（sp，缩放系数为 1.0 时的大小） */
    private static final float FONT_PREVIEW_BODY_SP = 16f;

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
    /** 最小增益（增益下限）输入框 */
    private EditText editMinGain;
    /** 高通滤波器截止频率输入框（Hz） */
    private EditText editHpfCutoff;
    /** 低通滤波器截止频率输入框（Hz） */
    private EditText editLpfCutoff;
    /** 低通滤波器开关 */
    private Switch swLpfEnabled;
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
        btnProbeUsageRoutes = view.findViewById(R.id.btn_probe_usage_routes);
        tvAudioRoutes = view.findViewById(R.id.tv_audio_routes);

        // 字体大小设置控件
        seekFontScale = view.findViewById(R.id.seek_font_scale);
        tvFontScaleValue = view.findViewById(R.id.tv_font_scale_value);
        tvFontPreviewTitle = view.findViewById(R.id.tv_font_preview_title);
        tvFontPreviewBody = view.findViewById(R.id.tv_font_preview_body);
        btnRestartApp = view.findViewById(R.id.btn_restart_app);

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
        editMinGain = view.findViewById(R.id.edit_min_gain);
        editHpfCutoff = view.findViewById(R.id.edit_hpf_cutoff);
        editLpfCutoff = view.findViewById(R.id.edit_lpf_cutoff);
        swLpfEnabled = view.findViewById(R.id.sw_lpf_enabled);
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

        // 初始化字体大小滑动条与预览
        initFontScaleViews();

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
        btnProbeUsageRoutes = null;
        tvAudioRoutes = null;
        // 置空字体大小设置 UI
        seekFontScale = null;
        tvFontScaleValue = null;
        tvFontPreviewTitle = null;
        tvFontPreviewBody = null;
        btnRestartApp = null;
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
        editMinGain = null;
        editHpfCutoff = null;
        editLpfCutoff = null;
        swLpfEnabled = null;
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
            // 程序化弹回/回填开关时跳过业务逻辑，避免 setChecked 再入引发的启停抖动
            if (suppressFloatingSwitchCallback) return;
            if (isChecked) {
                floatingWindowConfig.setEnabled(true);
                startFloatingWindowService();
            } else {
                floatingWindowConfig.setEnabled(false);
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

        // usage 路由探测（后台线程遍历 0~100 建流并读取实际路由设备）
        btnProbeUsageRoutes.setOnClickListener(v -> probeUsageRoutes());

        // 字体大小设置监听器
        setupFontScaleListeners();

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
     * 初始化字体大小滑动条与预览。
     *
     * <p>从持久化配置读取当前缩放系数，映射为滑动条档位（0..16），
     * 并同步更新数值文本与预览字号。
     */
    private void initFontScaleViews() {
        if (seekFontScale == null) return;
        float scale = themeConfig.getFontScale();
        // 缩放系数 → 滑动条档位：progress = round((scale - 0.7) / 0.05)，并夹紧到 [0, 16]
        int progress = Math.round((scale - AppConfig.FONT_SCALE_MIN) / 0.05f);
        if (progress < 0) progress = 0;
        if (progress > seekFontScale.getMax()) progress = seekFontScale.getMax();
        seekFontScale.setProgress(progress);
        // 以实际档位反算出的系数刷新文本与预览，保证三者一致
        float snappedScale = AppConfig.FONT_SCALE_MIN + progress * 0.05f;
        updateFontScaleValueText(snappedScale);
        updateFontPreview(snappedScale);
    }

    /**
     * 设置字体大小相关监听器：滑动条实时预览 + 松手保存重建，以及重启兜底按钮。
     */
    private void setupFontScaleListeners() {
        if (seekFontScale != null) {
            seekFontScale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                    if (!isAdded()) return;
                    // 档位 → 缩放系数：0.7 + progress * 0.05
                    float scale = AppConfig.FONT_SCALE_MIN + progress * 0.05f;
                    // 仅实时更新数值文本与预览，不持久化、不重建 Activity
                    updateFontScaleValueText(scale);
                    updateFontPreview(scale);
                    // 【字体缩放排查】仅在用户手动拖动时打印，避免代码 setProgress 刷屏
                    if (fromUser) {
                        AppLog.d(FONT_SCALE_TAG, "onProgressChanged: progress=" + progress
                                + " -> scale=" + String.format(Locale.US, "%.2f", scale) + " (预览，未持久化)");
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar sb) {
                    // 【字体缩放排查】开始拖动，记录起始档位与当前已持久化的系数
                    AppLog.d(FONT_SCALE_TAG, "onStartTrackingTouch: startProgress=" + sb.getProgress()
                            + ", 当前已保存 scale=" + String.format(Locale.US, "%.2f", themeConfig.getFontScale()));
                }

                @Override
                public void onStopTrackingTouch(SeekBar sb) {
                    if (!isAdded()) return;
                    int progress = sb.getProgress();
                    float scale = AppConfig.FONT_SCALE_MIN + progress * 0.05f;
                    // 【字体缩放排查】松手 -> 保存 -> recreate 全链路打点，用于定位“界面没刷新”
                    AppLog.d(FONT_SCALE_TAG, "onStopTrackingTouch: 松手 progress=" + progress
                            + " -> 目标 scale=" + String.format(Locale.US, "%.2f", scale));
                    float before = themeConfig.getFontScale();
                    // 松手后保存并重建 Activity，单 Activity 架构下即整个 App 生效
                    themeConfig.setFontScale(scale);
                    float saved = themeConfig.getFontScale();
                    AppLog.d(FONT_SCALE_TAG, "已持久化: before=" + String.format(Locale.US, "%.2f", before)
                            + " -> after(读回)=" + String.format(Locale.US, "%.2f", saved)
                            + (Math.abs(saved - scale) > 0.001f ? "  ⚠️ 读回值与目标不一致（可能被 clamp）" : ""));
                    AppLog.d(FONT_SCALE_TAG, "即将调用 requireActivity().recreate() 重建界面 ...");
                    // 【方案A】标记重建后需返回设置页，避免 recreate 后被 loadFunctionPage 恢复到功能页
                    themeConfig.setPendingReturnToSettings(true);
                    AppLog.d(FONT_SCALE_TAG, "已设置 pendingReturnToSettings=true，recreate 后将回到设置页");
                    requireActivity().recreate();
                    // 注意：recreate() 之后当前 Fragment 实例即将销毁重建，
                    // 此处之后的日志可能不会执行完整，真正“生效验证”看 BaseActivity.attachBaseContext 的日志
                    AppLog.d(FONT_SCALE_TAG, "recreate() 已调用（Activity 将走 finish->重建，实际应用见 attachBaseContext 日志）");
                }
            });
        }

        if (btnRestartApp != null) {
            btnRestartApp.setOnClickListener(v -> restartApp());
        }
    }

    /** 更新“当前：x.xx×”数值文本 */
    private void updateFontScaleValueText(float scale) {
        if (tvFontScaleValue == null) return;
        tvFontScaleValue.setText(String.format(Locale.getDefault(), "当前：%.2f×", scale));
    }

    /**
     * 按目标缩放系数更新预览字号。
     *
     * <p>注意：Fragment 当前的 Context 已经应用了全局 fontScale，若直接用
     * setTextSize(COMPLEX_UNIT_SP, baseSp * targetScale) 会把全局系数再叠加一次（双重缩放）。
     * 因此这里改用 PX 单位：px = baseSp * density * targetScale（density 不含 fontScale），
     * 从而精确呈现“缩放系数 = targetScale”时的真实字号，避免双重应用。
     *
     * @param targetScale 目标缩放系数
     */
    private void updateFontPreview(float targetScale) {
        if (tvFontPreviewTitle == null || tvFontPreviewBody == null) return;
        float density = getResources().getDisplayMetrics().density;
        float titlePx = FONT_PREVIEW_TITLE_SP * density * targetScale;
        float bodyPx = FONT_PREVIEW_BODY_SP * density * targetScale;
        tvFontPreviewTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, titlePx);
        tvFontPreviewBody.setTextSize(TypedValue.COMPLEX_UNIT_PX, bodyPx);
    }

    /**
     * 彻底重启应用（兜底）。
     *
     * <p>当 recreate() 未即时生效时，冷启动整个进程：构建应用启动 Intent，
     * 以 NEW_TASK|CLEAR_TASK 清栈重启，随后结束当前进程确保干净重建。
     */
    private void restartApp() {
        if (!isAdded()) return;
        try {
            android.content.Context ctx = requireContext().getApplicationContext();
            Intent intent = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                ctx.startActivity(intent);
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to restart app", e);
        }
        // 结束进程，触发系统按上面的 Intent 冷启动新进程
        Runtime.getRuntime().exit(0);
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

    /**
     * usage 路由探测器：遍历 0~100 的 AudioAttributes.usage，逐个建一条静音 AudioTrack，
     * 读取系统实际把该 usage 路由到的设备（bus/扬声器等），生成“usage→路由设备”映射表。
     *
     * <p>用途：对标 Hey 项目的做法，直观看出车外喊话/音乐播放应该用哪个 usage 值
     * 才能路由到目标 bus（如亿咖通的 vendor usage 72/73 → bus4 外部喇叭）。
     *
     * <p>线程模型：建 101 条流并逐个 play/read/release 属于重操作，必须放后台线程，
     * 避免阻塞主线程触发 ANR；探测结果回主线程刷新文本框，并通过 AppLog 落盘方便 adb 拉取。
     *
     * <p>安全性：写入的是全 0 静音数据且时长极短，不会真正外放出声；每条流用 try-catch
     * 单独隔离，单个 usage 建流/路由失败不影响其余 usage 的探测。
     */
    private void probeUsageRoutes() {
        if (!isAdded()) return;
        // 先给出即时反馈，避免用户以为按钮没响应（探测全程约数百毫秒~数秒）
        tvAudioRoutes.setText("正在探测 usage 0~100 的路由，请稍候…");
        AppLog.d(TAG, "开始 usage 路由探测（0~100）");

        // 后台线程执行建流探测，结果回主线程更新 UI
        new Thread(() -> {
            String result = buildUsageRouteReport();
            android.app.Activity activity = getActivity();
            if (activity == null) return;
            activity.runOnUiThread(() -> {
                if (!isAdded() || tvAudioRoutes == null) return;
                tvAudioRoutes.setText(result);
            });
            // 落盘（后台线程写文件即可，无需回主线程）
            AppLog.d(TAG, "usage 路由探测结果:\n" + result);
        }, "usage-route-probe").start();
    }

    /**
     * 构建 usage 路由探测报告（在后台线程调用）。
     *
     * @return 格式化后的“usage→路由设备”报告文本
     */
    private String buildUsageRouteReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("═══════ usage 路由探测 (0~100) ═══════\n");
        sb.append("说明：√=可建流并成功播放，×=建流失败/异常\n");
        sb.append("      路由设备为系统实际选择的输出目标\n\n");

        int okCount = 0;
        for (int usage = 0; usage <= 100; usage++) {
            String line = probeSingleUsage(usage);
            if (line.contains("√")) okCount++;
            sb.append(line).append("\n");
        }

        sb.append("\n───────────────────────────\n");
        sb.append("可建流的 usage 数：").append(okCount).append(" / 101\n");
        sb.append("设备型号：").append(android.os.Build.MANUFACTURER)
                .append(" ").append(android.os.Build.MODEL).append("\n");
        return sb.toString();
    }

    /**
     * 探测单个 usage 值的建流与路由情况。
     *
     * <p>用极小缓冲、单声道、16bit PCM 建一条 AudioTrack，写入静音数据后短暂 play，
     * 读取 getRoutedDevice() 拿到系统实际路由的设备，随后立即 stop/release 释放资源。
     * 全程 try-catch，任何异常都转成一行“× + 异常信息”返回，绝不向上抛出中断整轮探测。
     *
     * @param usage 待探测的 AudioAttributes.usage 值
     * @return 该 usage 的单行探测结果
     */
    @SuppressWarnings("deprecation")
    private String probeSingleUsage(int usage) {
        android.media.AudioTrack track = null;
        try {
            // 构建 usage 对应的 AudioAttributes；非法 usage 会在此抛异常，被下方捕获
            android.media.AudioAttributes attrs = new android.media.AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();

            int sampleRate = 44100;
            int channelMask = android.media.AudioFormat.CHANNEL_OUT_MONO;
            int encoding = android.media.AudioFormat.ENCODING_PCM_16BIT;
            // 取系统最小缓冲，尽量减少每次探测的资源与耗时
            int minBuf = android.media.AudioTrack.getMinBufferSize(sampleRate, channelMask, encoding);
            if (minBuf <= 0) minBuf = 4096;

            android.media.AudioFormat format = new android.media.AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(encoding)
                    .setChannelMask(channelMask)
                    .build();

            track = new android.media.AudioTrack.Builder()
                    .setAudioAttributes(attrs)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(minBuf)
                    .setTransferMode(android.media.AudioTrack.MODE_STREAM)
                    .build();

            if (track.getState() != android.media.AudioTrack.STATE_INITIALIZED) {
                return String.format(Locale.getDefault(),
                        "usage %3d  ×  建流失败(未初始化)", usage);
            }

            // 写入静音数据并短暂播放，促使系统完成路由选择
            byte[] silence = new byte[minBuf];
            track.write(silence, 0, silence.length);
            track.play();
            // getRoutedDevice 需在 play 后才返回实际路由；此处不 sleep，避免 101 次累计过久
            android.media.AudioDeviceInfo routed = track.getRoutedDevice();

            String routeDesc;
            if (routed != null) {
                String name = routed.getProductName() != null ? routed.getProductName().toString() : "?";
                String addr = routed.getAddress();
                if (addr == null || addr.isEmpty()) addr = "无";
                routeDesc = getDeviceTypeName(routed.getType()) + " 地址=" + addr + " 名=" + name;
            } else {
                routeDesc = "路由未知(null)";
            }

            return String.format(Locale.getDefault(),
                    "usage %3d  √  → %s", usage, routeDesc);
        } catch (Throwable t) {
            // 用 Throwable 兜底，防止个别 usage 触发的非 Exception 错误中断整轮探测
            return String.format(Locale.getDefault(),
                    "usage %3d  ×  %s", usage, t.getClass().getSimpleName());
        } finally {
            // 无论成功与否都释放资源，避免泄漏 AudioTrack 句柄
            if (track != null) {
                try {
                    track.stop();
                } catch (Throwable ignore) {
                    // stop 在未 play 时可能抛异常，忽略
                }
                track.release();
            }
        }
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
        // 回填滤波器与增益范围配置（借鉴 Hey：截止频率与增益上下限可配）
        editMinGain.setText(String.valueOf(micOutputConfig.getMinGain()));
        editHpfCutoff.setText(String.valueOf(micOutputConfig.getHpfCutoff()));
        editLpfCutoff.setText(String.valueOf(micOutputConfig.getLpfCutoff()));
        swLpfEnabled.setChecked(micOutputConfig.isLpfEnabled());
        updateAmplificationWarning();
    }

    /** 设置音频设备监听器 */
    private void setupAudioDeviceListeners() {
        btnEnumMics.setOnClickListener(v -> enumMicrophones());
        btnEnumOutputs.setOnClickListener(v -> enumOutputDevices());
        btnEnumCarOutputs.setOnClickListener(v -> enumCarOutputDevices());
        btnSaveAudioDevice.setOnClickListener(v -> saveAudioDeviceSettings());

        // 输入过滤器：最大放大倍率范围 1~50（上限从 20 放宽到 50）
        android.text.InputFilter inputFilter = (source, start, end, dest, dstart, dend) -> {
            try {
                String newText = dest.subSequence(0, dstart).toString() +
                                source.subSequence(start, end).toString() +
                                dest.subSequence(dend, dest.length()).toString();
                if (newText.isEmpty()) return null;
                int value = Integer.parseInt(newText);
                if (value >= 1 && value <= 50) return null;
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
            // 上限从 20 放宽到 50，与 MicOutputConfig 的 clamp 范围保持一致
            if (maxAmplification < 1) maxAmplification = 1;
            else if (maxAmplification > 50) maxAmplification = 50;

            // 最小增益（增益下限），范围 0.1~最大放大倍率
            String minGainStr = editMinGain.getText().toString().trim();
            float minGain = !minGainStr.isEmpty() ? Float.parseFloat(minGainStr) : 1.0f;
            if (minGain < 0.1f) minGain = 0.1f;
            else if (minGain > maxAmplification) minGain = maxAmplification;

            // 高通截止频率（Hz），范围 50~2000
            String hpfCutoffStr = editHpfCutoff.getText().toString().trim();
            int hpfCutoff = !hpfCutoffStr.isEmpty() ? Integer.parseInt(hpfCutoffStr) : 100;
            if (hpfCutoff < 50) hpfCutoff = 50;
            else if (hpfCutoff > 2000) hpfCutoff = 2000;

            // 低通截止频率（Hz），范围 200~8000
            String lpfCutoffStr = editLpfCutoff.getText().toString().trim();
            int lpfCutoff = !lpfCutoffStr.isEmpty() ? Integer.parseInt(lpfCutoffStr) : 4000;
            if (lpfCutoff < 200) lpfCutoff = 200;
            else if (lpfCutoff > 8000) lpfCutoff = 8000;

            boolean lpfEnabled = swLpfEnabled.isChecked();

            // 先持久化到 SharedPreferences
            audioConfig.setUsageExternal(audioOutputUsageExternal);
            audioConfig.setUsageCar(audioOutputUsageCar);
            audioConfig.setAudioInputSource(audioInputSource);
            micOutputConfig.setMaxAmplification(maxAmplification);
            micOutputConfig.setMinGain(minGain);
            micOutputConfig.setHpfCutoff(hpfCutoff);
            micOutputConfig.setLpfCutoff(lpfCutoff);
            micOutputConfig.setLpfEnabled(lpfEnabled);

            // 运行时即时生效：若麦克风管理器已创建，直接更新滤波器系数与增益范围，
            // 无需重启应用。滤波器 setter 内部会 reset() 清空历史状态，避免换系数爆音。
            applyMicRuntimeSettings(hpfCutoff, lpfCutoff, lpfEnabled);

            // 回填校验后的值，让用户看到实际生效的数值（可能被 clamp 修正）
            editMaxAmplification.setText(String.valueOf(maxAmplification));
            editMinGain.setText(String.valueOf(minGain));
            editHpfCutoff.setText(String.valueOf(hpfCutoff));
            editLpfCutoff.setText(String.valueOf(lpfCutoff));

            String message = "音频设备设置已保存<br>" +
                    "车外输出: <font color='#FF0000'>" + audioOutputUsageExternal + "</font><br>" +
                    "车内输出: <font color='#FF0000'>" + audioOutputUsageCar + "</font><br>" +
                    "麦克风源: <font color='#FF0000'>" + audioInputSource + "</font><br>" +
                    "最大放大: <font color='#FF0000'>" + maxAmplification + "</font><br>" +
                    "最小增益: <font color='#FF0000'>" + minGain + "</font><br>" +
                    "高通截止: <font color='#FF0000'>" + hpfCutoff + "Hz</font><br>" +
                    "低通截止: <font color='#FF0000'>" + lpfCutoff + "Hz（" + (lpfEnabled ? "开启" : "关闭") + "）</font>";
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
     * 将滤波器与增益范围配置运行时应用到麦克风管线。
     *
     * <p>通过 {@link AudioServiceLocator} 获取全局唯一的麦克风管理器（若已由
     * MainActivity 注册则复用录制实例，否则懒加载创建同一单例），因此运行时
     * 更新一定作用在真正的处理管线上，不会出现"改了配置却没生效"的双实例问题。
     * 滤波器截止频率/开关的 setter 会更新录制线程可见的 volatile 系数，并
     * reset() 清空历史状态；增益范围通过 refreshAmplificationFactor() 以当前
     * 放大级别重新裁剪。所有 setter 均为幂等，重复调用安全。
     *
     * @param hpfCutoff  高通截止频率（Hz）
     * @param lpfCutoff  低通截止频率（Hz）
     * @param lpfEnabled 低通滤波器开关
     */
    private void applyMicRuntimeSettings(int hpfCutoff, int lpfCutoff, boolean lpfEnabled) {
        try {
            MicrophoneManager micManager =
                    AudioServiceLocator.getInstance().getMicrophoneManager();
            if (micManager == null) {
                // context 尚未初始化，管理器无法创建；下次录制会从配置读取新值
                return;
            }
            micManager.setHpfCutoff(hpfCutoff);
            micManager.setLpfCutoff(lpfCutoff);
            micManager.setLowPassEnabled(lpfEnabled);
            micManager.refreshAmplificationFactor();
        } catch (Exception e) {
            // 运行时更新失败不影响配置持久化，下次录制仍会读取新值
            AppLog.e(TAG, "Failed to apply mic runtime settings", e);
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

    /**
     * 启动悬浮窗服务。
     *
     * <p>【三层防御·第一层：入口校验】悬浮窗服务在 onCreate → showFloatingBall → WindowManager.addView
     * 时需要 SYSTEM_ALERT_WINDOW 权限；若未授权，addView 会抛 BadTokenException 导致进程崩溃。
     * 因此在启动服务前先检查 canDrawOverlays：
     * <ul>
     *   <li>未授权：Toast 提示 + 跳转系统授权页 + 把开关弹回 false，绝不启动服务，从源头避免崩溃；</li>
     *   <li>已授权：正常以前台服务方式启动。</li>
     * </ul>
     */
    private void startFloatingWindowService() {
        // 未授予悬浮窗权限时不启动服务，改为引导用户去系统授权页
        if (!Settings.canDrawOverlays(requireContext())) {
            AppLog.w(TAG, "悬浮窗权限未授予，引导用户前往系统授权页，暂不启动服务");
            Toast.makeText(requireContext(), "请先授予悬浮窗权限", Toast.LENGTH_LONG).show();
            // 把开关弹回关闭态，避免用户误以为已开启；同时同步配置为 false
            floatingWindowConfig.setEnabled(false);
            if (floatingWindowSwitch != null) {
                // 用抑制标志包裹程序化弹回，避免再次触发监听器造成的启停抖动
                suppressFloatingSwitchCallback = true;
                floatingWindowSwitch.setChecked(false);
                suppressFloatingSwitchCallback = false;
            }
            // 跳转系统"显示在其他应用上层"授权页
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + requireContext().getPackageName()));
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
            return;
        }
        Intent serviceIntent = new Intent(requireContext(), FloatingWindowService.class);
        ServiceCompat.startForegroundService(requireContext(), serviceIntent);
    }

    /**
     * 悬浮窗授权页返回回调。
     *
     * <p>用户从系统授权页返回后，若此时已授予悬浮窗权限，则自动打开开关并启动服务，
     * 免去用户手动再点一次开关；若仍未授权则保持关闭态。
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY_PERMISSION && isAdded()) {
            // 系统授权页不返回明确 resultCode，需回来后主动复查权限状态
            if (Settings.canDrawOverlays(requireContext())) {
                AppLog.d(TAG, "用户已授予悬浮窗权限，自动开启开关并启动服务");
                floatingWindowConfig.setEnabled(true);
                if (floatingWindowSwitch != null) {
                    floatingWindowSwitch.setChecked(true);
                }
                Intent serviceIntent = new Intent(requireContext(), FloatingWindowService.class);
                ServiceCompat.startForegroundService(requireContext(), serviceIntent);
            } else {
                AppLog.w(TAG, "用户返回后仍未授予悬浮窗权限，保持关闭态");
            }
        }
    }

    /** 停止悬浮窗服务 */
    private void stopFloatingWindowService() {
        Intent serviceIntent = new Intent(requireContext(), FloatingWindowService.class);
        ServiceCompat.stopService(requireContext(), serviceIntent);
    }
}
