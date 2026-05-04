package com.aug32.l7audio;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.text.HtmlCompat;
import androidx.fragment.app.Fragment;

import com.aug32.l7audio.audio.AudioOutputManager;
import com.aug32.l7audio.audio.MicrophoneManager;
import com.aug32.l7audio.audio.TTSManager;
import com.aug32.l7audio.service.FloatingWindowService;

public class SettingsFragment extends Fragment {
    private static final String TAG = "SettingsFragment";

    private RadioGroup themeRadioGroup;
    private RadioButton themeSystemRadio;
    private RadioButton themeLightRadio;
    private RadioButton themeDarkRadio;
    private Switch autoStartSwitch;
    private Switch floatingWindowSwitch;
    private Button btnBack;
    private Button btnHome;
    private Button btnDebugAudioRoutes;
    private TextView tvAudioRoutes;
    
    // TTS diagnostics
    private TextView tvTTSStatus;
    private Button btnTestTTS;
    private Button btnCheckTTSStatus;
    
    // 音频设备设置
    private EditText editAudioUsageExternal;
    private EditText editAudioUsageCar;
    private EditText editAudioSource;
    private EditText editMaxAmplification;
    private Button btnEnumMics;
    private Button btnEnumOutputs;
    private Button btnEnumCarOutputs;
    private Button btnSaveAudioDevice;
    private TextView tvAudioDeviceStatus;

    private AppConfig appConfig;
    private TTSManager ttsManager;
    private AudioOutputManager audioOutputManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        // 初始化视图
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
        
        // 初始化TTS诊断组件
        tvTTSStatus = view.findViewById(R.id.tv_tts_status);
        btnTestTTS = view.findViewById(R.id.btn_test_tts);
        btnCheckTTSStatus = view.findViewById(R.id.btn_check_tts_status);
        
        // 初始化音频设备设置组件
        editAudioUsageExternal = view.findViewById(R.id.edit_audio_usage_external);
        editAudioUsageCar = view.findViewById(R.id.edit_audio_usage_car);
        editAudioSource = view.findViewById(R.id.edit_audio_source);
        editMaxAmplification = view.findViewById(R.id.edit_max_amplification);
        btnEnumMics = view.findViewById(R.id.btn_enum_mics);
        btnEnumOutputs = view.findViewById(R.id.btn_enum_outputs);
        btnEnumCarOutputs = view.findViewById(R.id.btn_enum_car_outputs);
        btnSaveAudioDevice = view.findViewById(R.id.btn_save_audio_device);
        tvAudioDeviceStatus = view.findViewById(R.id.tv_audio_device_status);
        
        // 初始化关于按钮
        Button btnAbout = view.findViewById(R.id.btn_about);
        
        // 启用TextView的滚动功能
        tvAudioRoutes.setMovementMethod(new android.text.method.ScrollingMovementMethod());

        // 初始化配置、音频输出管理器和TTS
        appConfig = new AppConfig(requireContext());
        MainActivity mainActivity = MainActivity.getInstance();
        if (mainActivity != null) {
            audioOutputManager = mainActivity.getAudioOutputManager();
            ttsManager = mainActivity.getTTSManager();
        } else {
            audioOutputManager = new AudioOutputManager(requireContext());
            ttsManager = new TTSManager(requireContext(), audioOutputManager);
        }

        // 设置当前值
        int currentTheme = appConfig.getThemeMode();
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

        autoStartSwitch.setChecked(appConfig.isAutoStartOnBoot());
        floatingWindowSwitch.setChecked(appConfig.isFloatingWindowEnabled());

        // 加载并设置音频设备设置
        loadAudioDeviceSettings();

        // 设置监听器
        themeRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int themeMode = AppConfig.THEME_MODE_SYSTEM;
            if (checkedId == R.id.theme_system) {
                themeMode = AppConfig.THEME_MODE_SYSTEM;
            } else if (checkedId == R.id.theme_light) {
                themeMode = AppConfig.THEME_MODE_LIGHT;
            } else if (checkedId == R.id.theme_dark) {
                themeMode = AppConfig.THEME_MODE_DARK;
            }
            appConfig.setThemeMode(themeMode);
            // 通知悬浮窗服务主题变化
            try {
                com.aug32.l7audio.service.FloatingWindowService.notifyThemeChanged(requireContext());
            } catch (Exception e) {
                AppLog.d(TAG, "Failed to notify theme change to floating window service");
            }
            // 真正重启应用以应用主题
            try {
            Intent intent = new Intent(requireContext(), MainActivity.class);// 1. 创建跳转到MainActivity的Intent
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);// 2. 添加关键Flag，确保启动模式符合预期
            requireContext().startActivity(intent);// 3. 启动MainActivity（requireContext()适配Fragment场景）
            requireActivity().finishAffinity();// 4. 关闭当前Activity所属的所有关联Activity（清空整个任务栈）
            } catch (Exception e) {
            // 捕获启动异常，关闭应用
            requireActivity().finishAffinity();
            }
        });

        autoStartSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            appConfig.setAutoStartOnBoot(isChecked);
            // 更新开机自启动接收器状态
            BootReceiver.enable(requireContext());
        });
        
        floatingWindowSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            appConfig.setFloatingWindowEnabled(isChecked);
            if (isChecked) {
                startFloatingWindowService();
            } else {
                stopFloatingWindowService();
            }
        });

        // 设置返回按钮点击事件
        btnBack.setOnClickListener(v -> {
            // 返回到主界面
            MainActivity activity = (MainActivity) requireActivity();
            activity.showMainInterface();
        });

        // 设置主页按钮点击事件
        btnHome.setOnClickListener(v -> {
            // 返回到主界面
            MainActivity activity = (MainActivity) requireActivity();
            activity.showMainInterface();
        });

        // 设置调试音频路由按钮点击事件
        btnDebugAudioRoutes.setOnClickListener(v -> {
            displayAudioRoutes();
        });

        // 设置TTS诊断按钮点击事件
        btnTestTTS.setOnClickListener(v -> testTTS());
        btnCheckTTSStatus.setOnClickListener(v -> checkTTSStatus());

        // 设置音频设备设置按钮点击事件
        setupAudioDeviceListeners();

        // 设置关于按钮点击事件
        btnAbout.setOnClickListener(v -> {
            // 显示关于页面
            MainActivity activity = (MainActivity) requireActivity();
            activity.showAboutFragment();
        });

        return view;
    }

    /**
     * 显示系统音频路由信息
     */
    private void displayAudioRoutes() {
        StringBuilder routesInfo = new StringBuilder();
        routesInfo.append("# 音频路由信息\n\n");

        try {
            // 获取AudioManager实例
            android.media.AudioManager audioManager = (android.media.AudioManager) requireContext().getSystemService(android.content.Context.AUDIO_SERVICE);

            // 获取当前音频模式
            int mode = audioManager.getMode();
            routesInfo.append("## 基本音频信息\n");
            routesInfo.append("音频模式: " + getModeName(mode) + " (原始值: " + mode + ")\n");

            // 获取当前音频焦点状态
            routesInfo.append("音频焦点状态: 无法获取详细信息\n");

            // 获取当前音频路由
            boolean isSpeakerphoneOn = false;
            boolean isBluetoothScoOn = false;
            boolean isWiredHeadsetOn = false;
            boolean isBluetoothA2dpOn = false;

            // 尝试使用现代API获取音频设备信息
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                try {
                    // 反射检查getDevices方法
                    java.lang.reflect.Method getDevicesMethod = android.media.AudioManager.class.getMethod("getDevices", int.class);
                    int GET_DEVICES_ALL = android.media.AudioManager.class.getField("GET_DEVICES_ALL").getInt(null);
                    
                    // 获取所有设备
                    Object devicesObj = getDevicesMethod.invoke(audioManager, GET_DEVICES_ALL);
                    if (devicesObj != null && devicesObj instanceof android.media.AudioDeviceInfo[]) {
                        android.media.AudioDeviceInfo[] devices = (android.media.AudioDeviceInfo[]) devicesObj;
                        for (android.media.AudioDeviceInfo device : devices) {
                            int type = device.getType();
                            switch (type) {
                                case android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                                    isSpeakerphoneOn = true;
                                    break;
                                case android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                                    isBluetoothScoOn = true;
                                    break;
                                case android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET:
                                case android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                                    isWiredHeadsetOn = true;
                                    break;
                                case android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                                    isBluetoothA2dpOn = true;
                                    break;
                            }
                        }
                    }
                } catch (Exception e) {
                    // 反射失败，使用旧API
                    routesInfo.append("使用getDevices()方法失败，回退到旧API: " + e.getMessage() + "\n");
                    // TODO: AudioManager.isSpeakerphoneOn() 等方法在API 31中已过时
                    // Android 11(API 30)上仍可正常使用，但建议未来迁移到AudioDeviceCallback
                    // 替代方案: 使用AudioManager.getDevices()和AudioDeviceInfo
                    isSpeakerphoneOn = audioManager.isSpeakerphoneOn();
                    isBluetoothScoOn = audioManager.isBluetoothScoOn();
                    isWiredHeadsetOn = audioManager.isWiredHeadsetOn();
                    isBluetoothA2dpOn = audioManager.isBluetoothA2dpOn();
                }
            } else {
                // Android 6.0以下，使用旧API
                // TODO: AudioManager.isSpeakerphoneOn() 等方法在API 31中已过时
                // Android 11(API 30)上仍可正常使用，但建议未来迁移到AudioDeviceCallback
                // 替代方案: 使用AudioManager.getDevices()和AudioDeviceInfo
                isSpeakerphoneOn = audioManager.isSpeakerphoneOn();
                isBluetoothScoOn = audioManager.isBluetoothScoOn();
                isWiredHeadsetOn = audioManager.isWiredHeadsetOn();
                isBluetoothA2dpOn = audioManager.isBluetoothA2dpOn();
            }

            routesInfo.append("扬声器状态: ").append(isSpeakerphoneOn ? "开启" : "关闭").append(" (原始值: " + isSpeakerphoneOn + ")\n");
            routesInfo.append("蓝牙SCO状态: ").append(isBluetoothScoOn ? "开启" : "关闭").append(" (原始值: " + isBluetoothScoOn + ")\n");
            routesInfo.append("有线耳机状态: ").append(isWiredHeadsetOn ? "已连接" : "未连接").append(" (原始值: " + isWiredHeadsetOn + ")\n");
            routesInfo.append("蓝牙A2DP状态: ").append(isBluetoothA2dpOn ? "已连接" : "未连接").append(" (原始值: " + isBluetoothA2dpOn + ")\n");

            // 获取当前音频流的音量
            routesInfo.append("\n## 音量设置\n");
            int streamVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
            int maxStreamVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
            routesInfo.append("音乐流音量: ").append(streamVolume).append("/").append(maxStreamVolume).append("\n");
            
            // 获取其他音频流的音量
            int ringStreamVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_RING);
            int ringMaxStreamVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_RING);
            routesInfo.append("铃声流音量: ").append(ringStreamVolume).append("/").append(ringMaxStreamVolume).append("\n");
            
            int voiceCallStreamVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_VOICE_CALL);
            int voiceCallMaxStreamVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_VOICE_CALL);
            routesInfo.append("通话流音量: ").append(voiceCallStreamVolume).append("/").append(voiceCallMaxStreamVolume).append("\n");
            
            // 增加更多音频相关信息
            routesInfo.append("\n## 音频高级信息\n");
            
            // 尝试获取音频焦点状态
            try {
                // 反射检查音频焦点相关方法
                java.lang.reflect.Method getAudioFocusRequestMethod = null;
                try {
                    getAudioFocusRequestMethod = android.media.AudioManager.class.getMethod("getAudioFocusRequest", int.class);
                    routesInfo.append("AudioManager.getAudioFocusRequest() 方法存在\n");
                } catch (Exception e) {
                    routesInfo.append("AudioManager.getAudioFocusRequest() 方法不存在: " + e.getMessage() + "\n");
                }
            } catch (Exception e) {
                routesInfo.append("获取音频焦点信息失败: " + e.getMessage() + "\n");
            }
            
            // 获取音频模式的详细信息
            int currentMode = audioManager.getMode();
            routesInfo.append("当前音频模式详细信息: " + getModeName(currentMode) + " (原始值: " + currentMode + ")\n");
            
            // 检查音频设备连接状态
            routesInfo.append("蓝牙SCO是否可用: " + audioManager.isBluetoothScoAvailableOffCall() + "\n");
            
            // 获取Ringer模式
            int ringerMode = audioManager.getRingerMode();
            String ringerModeName = "未知";
            switch (ringerMode) {
                case android.media.AudioManager.RINGER_MODE_SILENT:
                    ringerModeName = "静音模式";
                    break;
                case android.media.AudioManager.RINGER_MODE_VIBRATE:
                    ringerModeName = "振动模式";
                    break;
                case android.media.AudioManager.RINGER_MODE_NORMAL:
                    ringerModeName = "正常模式";
                    break;
            }
            routesInfo.append("铃声模式: " + ringerModeName + " (原始值: " + ringerMode + ")\n");

            // 尝试获取更多音频设备信息
            routesInfo.append("\n## 系统信息\n");
            routesInfo.append("Android 版本: " + android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")\n");
            routesInfo.append("设备品牌: " + android.os.Build.BRAND + "\n");
            routesInfo.append("设备型号: " + android.os.Build.MODEL + "\n");
            
            routesInfo.append("\n## 音频设备信息\n");
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                // Android 6.0+ 可以获取更多音频设备信息
                routesInfo.append("### Android 6.0+ 音频设备信息\n");
                routesInfo.append("Android 6.0+ 支持获取更多音频设备信息\n");
                routesInfo.append("当前音频源: VOICE_COMMUNICATION\n");
                routesInfo.append("当前音频输出: VOICE_COMMUNICATION\n");
                
                // 检查AudioManager类是否包含getDevices方法
                try {
                    // 反射检查getDevices方法是否存在
                    java.lang.reflect.Method getDevicesMethod = android.media.AudioManager.class.getMethod("getDevices", int.class);
                    routesInfo.append("AudioManager.getDevices() 方法存在\n");
                    
                    // 尝试获取常量值
                    int GET_DEVICES_ALL = 0;
                    int GET_DEVICES_INPUTS = 0;
                    int GET_DEVICES_OUTPUTS = 0;
                    
                    try {
                        GET_DEVICES_ALL = android.media.AudioManager.class.getField("GET_DEVICES_ALL").getInt(null);
                        routesInfo.append("GET_DEVICES_ALL 常量值: " + GET_DEVICES_ALL + "\n");
                    } catch (Exception e) {
                        routesInfo.append("GET_DEVICES_ALL 常量不存在: " + e.getMessage() + "\n");
                    }
                    
                    try {
                        GET_DEVICES_INPUTS = android.media.AudioManager.class.getField("GET_DEVICES_INPUTS").getInt(null);
                        routesInfo.append("GET_DEVICES_INPUTS 常量值: " + GET_DEVICES_INPUTS + "\n");
                    } catch (Exception e) {
                        routesInfo.append("GET_DEVICES_INPUTS 常量不存在: " + e.getMessage() + "\n");
                    }
                    
                    try {
                        GET_DEVICES_OUTPUTS = android.media.AudioManager.class.getField("GET_DEVICES_OUTPUTS").getInt(null);
                        routesInfo.append("GET_DEVICES_OUTPUTS 常量值: " + GET_DEVICES_OUTPUTS + "\n");
                    } catch (Exception e) {
                        routesInfo.append("GET_DEVICES_OUTPUTS 常量不存在: " + e.getMessage() + "\n");
                    }
                    
                    // 尝试使用不同的方式获取设备
                    android.media.AudioDeviceInfo[] devices = null;
                    
                    // 尝试使用GET_DEVICES_ALL
                    if (GET_DEVICES_ALL != 0) {
                        try {
                            devices = audioManager.getDevices(GET_DEVICES_ALL);
                            routesInfo.append("使用 GET_DEVICES_ALL 获取设备成功\n");
                        } catch (Exception e) {
                            routesInfo.append("使用 GET_DEVICES_ALL 获取设备失败: " + e.getMessage() + "\n");
                        }
                    }
                    
                    // 如果GET_DEVICES_ALL失败，尝试分别获取输入和输出设备
                    if (devices == null && GET_DEVICES_INPUTS != 0 && GET_DEVICES_OUTPUTS != 0) {
                        routesInfo.append("尝试分别获取输入和输出设备\n");
                        try {
                            // 获取输入设备
                            android.media.AudioDeviceInfo[] inputDevices = audioManager.getDevices(GET_DEVICES_INPUTS);
                            routesInfo.append("获取输入设备成功，数量: " + (inputDevices != null ? inputDevices.length : 0) + "\n");
                            
                            // 获取输出设备
                            android.media.AudioDeviceInfo[] outputDevices = audioManager.getDevices(GET_DEVICES_OUTPUTS);
                            routesInfo.append("获取输出设备成功，数量: " + (outputDevices != null ? outputDevices.length : 0) + "\n");
                            
                            // 合并输入和输出设备
                            if (inputDevices != null && outputDevices != null) {
                                devices = new android.media.AudioDeviceInfo[inputDevices.length + outputDevices.length];
                                System.arraycopy(inputDevices, 0, devices, 0, inputDevices.length);
                                System.arraycopy(outputDevices, 0, devices, inputDevices.length, outputDevices.length);
                                routesInfo.append("合并设备成功，总数量: " + devices.length + "\n");
                            } else if (inputDevices != null) {
                                devices = inputDevices;
                                routesInfo.append("使用输入设备作为结果\n");
                            } else if (outputDevices != null) {
                                devices = outputDevices;
                                routesInfo.append("使用输出设备作为结果\n");
                            }
                        } catch (Exception ex) {
                            routesInfo.append("获取设备时出错: " + ex.getMessage() + "\n");
                            routesInfo.append("错误详情: " + ex.toString() + "\n");
                        }
                    }
                    
                    // 显示设备信息
                    if (devices != null && devices.length > 0) {
                        routesInfo.append("\n### 音频设备列表\n");
                        for (int i = 0; i < devices.length; i++) {
                            android.media.AudioDeviceInfo device = devices[i];
                            // 系统原始名称
                            CharSequence productName = null;
                            try {
                                productName = device.getProductName();
                            } catch (Exception e) {
                                routesInfo.append("获取设备名称失败: " + e.getMessage() + "\n");
                            }
                            String name = productName == null ? "无名设备" : productName.toString();
                            
                            // 设备类型（系统原生ID）
                            int type = 0;
                            try {
                                type = device.getType();
                            } catch (Exception e) {
                                routesInfo.append("获取设备类型失败: " + e.getMessage() + "\n");
                            }
                            
                            // 输入还是输出
                            boolean isSource = false;
                            try {
                                isSource = device.isSource();
                            } catch (Exception e) {
                                routesInfo.append("获取设备方向失败: " + e.getMessage() + "\n");
                            }
                            String direction = isSource ? "【输入】" : "【输出】";
                            
                            // 设备地址
                            String address = null;
                            try {
                                address = device.getAddress();
                            } catch (Exception e) {
                                address = "无法获取";
                            }
                            
                            // 设备描述
                            String description = null;
                            try {
                                // 尝试获取设备描述
                                try {
                                    // 尝试调用带Context参数的方法（较新API）
                                    java.lang.reflect.Method getDescriptionMethod = android.media.AudioDeviceInfo.class.getMethod("getDescription", Context.class);
                                    description = (String) getDescriptionMethod.invoke(device, requireContext());
                                } catch (Exception e1) {
                                    // 尝试调用无参数的方法（较旧API）
                                    try {
                                        java.lang.reflect.Method getDescriptionMethod = android.media.AudioDeviceInfo.class.getMethod("getDescription");
                                        description = (String) getDescriptionMethod.invoke(device);
                                    } catch (Exception e2) {
                                        description = "无法获取";
                                    }
                                }
                            } catch (Exception e) {
                                description = "无法获取";
                            }
                            
                            routesInfo.append((i + 1)).append(". ").append(direction).append("\n");
                            routesInfo.append("   设备名：").append(name).append("\n");
                            routesInfo.append("   设备描述：").append(description).append("\n");
                            routesInfo.append("   设备地址：").append(address).append("\n");
                            routesInfo.append("   系统类型ID：").append(type).append("\n");
                            routesInfo.append("   设备类型：").append(getDeviceTypeName(type)).append("\n");
                            routesInfo.append("   是输入设备：").append(isSource).append("\n");
                        }
                    } else {
                        routesInfo.append("未检测到音频设备或获取设备失败\n");
                    }
                } catch (Exception e) {
                    routesInfo.append("AudioManager.getDevices() 方法不存在: " + e.getMessage() + "\n");
                    routesInfo.append("错误详情: " + e.toString() + "\n");
                }
            } else {
                routesInfo.append("Android 版本较低，无法获取详细音频设备信息\n");
            }

            // 添加可能的音频路由选项
            routesInfo.append("\n可能的音频路由选项:\n");
            routesInfo.append("1. 扬声器 (SPEAKER)\n");
            routesInfo.append("2. 听筒 (EARPIECE)\n");
            routesInfo.append("3. 有线耳机 (WIRED_HEADSET)\n");
            routesInfo.append("4. 蓝牙SCO (BLUETOOTH_SCO)\n");
            routesInfo.append("5. 蓝牙A2DP (BLUETOOTH_A2DP)\n");
            routesInfo.append("6. 车载音频 (CAR_AUDIO)\n");

        } catch (Exception e) {
            routesInfo.append("获取音频路由信息时出错: ").append(e.getMessage()).append("\n");
        }

        tvAudioRoutes.setText(routesInfo.toString());
        
        // 输出到日志，方便通过adb查看
        AppLog.d(TAG, "Audio routes info:\n" + routesInfo.toString());
        AppLog.d(TAG, "Audio routes info length: " + routesInfo.length());
    }

    /**
     * 获取音频模式的名称
     */
    private String getModeName(int mode) {
        switch (mode) {
            case android.media.AudioManager.MODE_NORMAL:
                return "正常模式 (MODE_NORMAL)";
            case android.media.AudioManager.MODE_RINGTONE:
                return "铃声模式 (MODE_RINGTONE)";
            case android.media.AudioManager.MODE_IN_CALL:
                return "通话模式 (MODE_IN_CALL)";
            case android.media.AudioManager.MODE_IN_COMMUNICATION:
                return "通信模式 (MODE_IN_COMMUNICATION)";
            default:
                return "未知模式 (" + mode + ")";
        }
    }

    /**
     * 测试TTS发声
     */
    private void testTTS() {
        String testMessage = "测试TTS发声功能，这是一条测试消息。";
        AppLog.d(TAG, "Testing TTS with message: " + testMessage);
        
        try {
            boolean success = ttsManager.speak(testMessage);
            if (success) {
                tvTTSStatus.setText("TTS测试成功！正在播放测试消息。");
                AppLog.d(TAG, "TTS test successful");
            } else {
                tvTTSStatus.setText("TTS测试失败。请检查TTS引擎是否正常初始化。");
                AppLog.w(TAG, "TTS test failed");
            }
        } catch (Exception e) {
            tvTTSStatus.setText("TTS测试出错：" + e.getMessage());
            AppLog.e(TAG, "TTS test error", e);
        }
    }
    
    /**
     * 检查TTS状态
     */
    private void checkTTSStatus() {
        StringBuilder statusInfo = new StringBuilder();
        statusInfo.append("TTS状态诊断：\n\n");
        
        try {
            // 检查TTS初始化状态
            boolean initialized = ttsManager.isInitialized();
            statusInfo.append("初始化状态：" + (initialized ? "已初始化" : "未初始化") + "\n");
            
            // 检查系统信息
            statusInfo.append("Android版本：" + android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")\n");
            statusInfo.append("设备品牌：" + android.os.Build.BRAND + "\n");
            statusInfo.append("设备型号：" + android.os.Build.MODEL + "\n");
            
            // 检查TTS引擎
            if (initialized) {
                statusInfo.append("\nTTS引擎状态：正常\n");
                statusInfo.append("建议：点击'测试TTS发声'按钮测试语音输出。");
            } else {
                statusInfo.append("\nTTS引擎状态：未初始化\n");
                statusInfo.append("建议：检查设备是否安装了TTS引擎和语言包。");
            }
            
            tvTTSStatus.setText(statusInfo.toString());
            AppLog.d(TAG, "TTS status checked: " + statusInfo.toString());
        } catch (Exception e) {
            tvTTSStatus.setText("检查TTS状态时出错：" + e.getMessage());
            AppLog.e(TAG, "Error checking TTS status", e);
        }
    }

    /**
     * 获取音频设备类型的名称
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
    
    /**
     * 加载音频设备设置
     */
    private void loadAudioDeviceSettings() {
        // 加载车外输出设备设置
        int audioOutputUsageExternal = appConfig.getAudioOutputUsageExternal();
        editAudioUsageExternal.setText(String.valueOf(audioOutputUsageExternal));
        
        // 加载车内输出设备设置
        int audioOutputUsageCar = appConfig.getAudioOutputUsageCar();
        editAudioUsageCar.setText(String.valueOf(audioOutputUsageCar));
        
        // 加载麦克风源设置
        int audioInputSource = appConfig.getAudioInputSource();
        editAudioSource.setText(String.valueOf(audioInputSource));
        
        // 加载最大放大倍数设置
        int maxAmplification = appConfig.getMaxAmplification();
        editMaxAmplification.setText(String.valueOf(maxAmplification));
        
        AppLog.d(TAG, "Loaded audio device settings: outputUsageExternal=" + audioOutputUsageExternal + ", outputUsageCar=" + audioOutputUsageCar + ", inputSource=" + audioInputSource + ", maxAmplification=" + maxAmplification);
    }
    
    /**
     * 设置音频设备设置按钮点击事件
     */
    private void setupAudioDeviceListeners() {
        // 枚举麦克风按钮点击事件
        btnEnumMics.setOnClickListener(v -> {
            enumMicrophones();
        });
        
        // 枚举输出设备按钮点击事件
        btnEnumOutputs.setOnClickListener(v -> {
            enumOutputDevices();
        });
        
        // 枚举车内输出设备按钮点击事件
        btnEnumCarOutputs.setOnClickListener(v -> {
            enumCarOutputDevices();
        });
        
        // 保存音频设备设置按钮点击事件
        btnSaveAudioDevice.setOnClickListener(v -> {
            saveAudioDeviceSettings();
        });
        
        // 最大放大倍数输入框过滤 - 限制只能输入1-10
        android.text.InputFilter inputFilter = (source, start, end, dest, dstart, dend) -> {
            try {
                String newText = dest.subSequence(0, dstart).toString() + 
                                source.subSequence(start, end).toString() + 
                                dest.subSequence(dend, dest.length()).toString();
                
                if (newText.isEmpty()) {
                    return null; // 允许空字符串
                }
                
                int value = Integer.parseInt(newText);
                if (value >= 1 && value <= 10) {
                    return null; // 允许输入
                }
                return ""; // 拒绝输入
            } catch (NumberFormatException e) {
                return ""; // 拒绝非数字
            }
        };
        
        editMaxAmplification.setFilters(new android.text.InputFilter[]{ 
            new android.text.InputFilter.LengthFilter(2), 
            inputFilter 
        });
    }
    
    /**
     * 枚举麦克风设备
     */
    private void enumMicrophones() {
        try {
            StringBuilder micInfo = new StringBuilder();
            micInfo.append("麦克风设备列表:\n\n");
            
            // 获取AudioManager实例
            android.media.AudioManager audioManager = (android.media.AudioManager) requireContext().getSystemService(android.content.Context.AUDIO_SERVICE);
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                // Android 6.0+ 可以获取音频设备信息
                try {
                    // 反射检查getDevices方法是否存在
                    java.lang.reflect.Method getDevicesMethod = android.media.AudioManager.class.getMethod("getDevices", int.class);
                    
                    // 获取输入设备
                    int GET_DEVICES_INPUTS = android.media.AudioManager.class.getField("GET_DEVICES_INPUTS").getInt(null);
                    android.media.AudioDeviceInfo[] inputDevices = audioManager.getDevices(GET_DEVICES_INPUTS);
                    
                    if (inputDevices != null && inputDevices.length > 0) {
                        for (int i = 0; i < inputDevices.length; i++) {
                            android.media.AudioDeviceInfo device = inputDevices[i];
                            // 系统原始名称
                            CharSequence productName = device.getProductName();
                            String name = productName == null ? "无名设备" : productName.toString();
                            
                            // 设备类型
                            int type = device.getType();
                            
                            micInfo.append((i + 1)).append(". ").append(name).append("\n");
                            micInfo.append("   设备类型：").append(getDeviceTypeName(type)).append("\n");
                            micInfo.append("   类型ID：").append(type).append("\n");
                        }
                    } else {
                        micInfo.append("未检测到麦克风设备\n");
                    }
                } catch (Exception e) {
                    micInfo.append("获取麦克风设备失败: " ).append(e.getMessage()).append("\n");
                    AppLog.e(TAG, "Failed to enumerate microphones", e);
                }
            } else {
                micInfo.append("Android 版本较低，无法获取详细麦克风设备信息\n");
            }
            
            // 显示麦克风设备列表
            tvAudioDeviceStatus.setText(micInfo.toString());
            AppLog.d(TAG, "Microphone enumeration completed: " + micInfo.toString());
            
        } catch (Exception e) {
            tvAudioDeviceStatus.setText("枚举麦克风时出错：" + e.getMessage());
            AppLog.e(TAG, "Error enumerating microphones", e);
        }
    }
    
    /**
     * 枚举输出设备
     */
    private void enumOutputDevices() {
        try {
            StringBuilder outputInfo = new StringBuilder();
            outputInfo.append("输出设备列表:\n\n");
            
            // 获取AudioManager实例
            android.media.AudioManager audioManager = (android.media.AudioManager) requireContext().getSystemService(android.content.Context.AUDIO_SERVICE);
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                // Android 6.0+ 可以获取音频设备信息
                try {
                    // 反射检查getDevices方法是否存在
                    java.lang.reflect.Method getDevicesMethod = android.media.AudioManager.class.getMethod("getDevices", int.class);
                    
                    // 获取输出设备
                    int GET_DEVICES_OUTPUTS = android.media.AudioManager.class.getField("GET_DEVICES_OUTPUTS").getInt(null);
                    android.media.AudioDeviceInfo[] outputDevices = audioManager.getDevices(GET_DEVICES_OUTPUTS);
                    
                    if (outputDevices != null && outputDevices.length > 0) {
                        for (int i = 0; i < outputDevices.length; i++) {
                            android.media.AudioDeviceInfo device = outputDevices[i];
                            // 系统原始名称
                            CharSequence productName = device.getProductName();
                            String name = productName == null ? "无名设备" : productName.toString();
                            
                            // 设备类型
                            int type = device.getType();
                            
                            outputInfo.append((i + 1)).append(". " ).append(name).append("\n");
                            outputInfo.append("   设备类型：").append(getDeviceTypeName(type)).append("\n");
                            outputInfo.append("   类型ID：").append(type).append("\n");
                        }
                    } else {
                        outputInfo.append("未检测到输出设备\n");
                    }
                } catch (Exception e) {
                    outputInfo.append("获取输出设备失败: " ).append(e.getMessage()).append("\n");
                    AppLog.e(TAG, "Failed to enumerate output devices", e);
                }
            } else {
                outputInfo.append("Android 版本较低，无法获取详细输出设备信息\n");
            }
            
            // 显示输出设备列表
            tvAudioDeviceStatus.setText(outputInfo.toString());
            AppLog.d(TAG, "Output device enumeration completed: " + outputInfo.toString());
            
        } catch (Exception e) {
            tvAudioDeviceStatus.setText("枚举输出设备时出错：" + e.getMessage());
            AppLog.e(TAG, "Error enumerating output devices", e);
        }
    }
    
    /**
     * 枚举车内输出设备
     */
    private void enumCarOutputDevices() {
        try {
            StringBuilder outputInfo = new StringBuilder();
            outputInfo.append("车内输出设备列表:\n\n");
            
            // 获取AudioManager实例
            android.media.AudioManager audioManager = (android.media.AudioManager) requireContext().getSystemService(android.content.Context.AUDIO_SERVICE);
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                // Android 6.0+ 可以获取音频设备信息
                try {
                    // 反射检查getDevices方法是否存在
                    java.lang.reflect.Method getDevicesMethod = android.media.AudioManager.class.getMethod("getDevices", int.class);
                    
                    // 获取输出设备
                    int GET_DEVICES_OUTPUTS = android.media.AudioManager.class.getField("GET_DEVICES_OUTPUTS").getInt(null);
                    android.media.AudioDeviceInfo[] outputDevices = audioManager.getDevices(GET_DEVICES_OUTPUTS);
                    
                    if (outputDevices != null && outputDevices.length > 0) {
                        for (int i = 0; i < outputDevices.length; i++) {
                            android.media.AudioDeviceInfo device = outputDevices[i];
                            // 系统原始名称
                            CharSequence productName = device.getProductName();
                            String name = productName == null ? "无名设备" : productName.toString();
                            
                            // 设备类型
                            int type = device.getType();
                            
                            outputInfo.append((i + 1)).append(". " ).append(name).append("\n");
                            outputInfo.append("   设备类型：").append(getDeviceTypeName(type)).append("\n");
                            outputInfo.append("   类型ID：").append(type).append("\n");
                        }
                    } else {
                        outputInfo.append("未检测到车内输出设备\n");
                    }
                } catch (Exception e) {
                    outputInfo.append("获取车内输出设备失败: " ).append(e.getMessage()).append("\n");
                    AppLog.e(TAG, "Failed to enumerate car output devices", e);
                }
            } else {
                outputInfo.append("Android 版本较低，无法获取详细车内输出设备信息\n");
            }
            
            // 显示车内输出设备列表
            tvAudioDeviceStatus.setText(outputInfo.toString());
            AppLog.d(TAG, "Car output device enumeration completed: " + outputInfo.toString());
            
        } catch (Exception e) {
            tvAudioDeviceStatus.setText("枚举车内输出设备时出错：" + e.getMessage());
            AppLog.e(TAG, "Error enumerating car output devices", e);
        }
    }
    
    /**
     * 保存音频设备设置
     */
    private void saveAudioDeviceSettings() {
        try {
            // 保存车外输出设备设置
            String outputUsageExternalStr = editAudioUsageExternal.getText().toString().trim();
            int audioOutputUsageExternal = 9; // 默认值
            if (!outputUsageExternalStr.isEmpty()) {
                audioOutputUsageExternal = Integer.parseInt(outputUsageExternalStr);
            }
            
            // 保存车内输出设备设置
            String outputUsageCarStr = editAudioUsageCar.getText().toString().trim();
            int audioOutputUsageCar = 1; // 默认值
            if (!outputUsageCarStr.isEmpty()) {
                audioOutputUsageCar = Integer.parseInt(outputUsageCarStr);
            }
            
            // 保存麦克风源设置
            String inputSourceStr = editAudioSource.getText().toString().trim();
            int audioInputSource = 1; // 默认值
            if (!inputSourceStr.isEmpty()) {
                audioInputSource = Integer.parseInt(inputSourceStr);
            }
            
            // 保存最大放大倍数设置
            String maxAmplificationStr = editMaxAmplification.getText().toString().trim();
            int maxAmplification = 2; // 默认值
            if (!maxAmplificationStr.isEmpty()) {
                maxAmplification = Integer.parseInt(maxAmplificationStr);
                // 限制范围1-10倍
                if (maxAmplification < 1) {
                    maxAmplification = 1;
                } else if (maxAmplification > 10) {
                    maxAmplification = 10;
                }
            }
            
            // 保存设置
            appConfig.setAudioOutputUsageExternal(audioOutputUsageExternal);
            appConfig.setAudioOutputUsageCar(audioOutputUsageCar);
            appConfig.setAudioInputSource(audioInputSource);
            appConfig.setMaxAmplification(maxAmplification);
            
            // 显示保存成功消息
            String message = "<font color='#666666'>L6/L7竖屏车外默认9、15、22，麦克风源默认0或1</font><br>" + 
                            "音频设备设置已保存<br>" +
                            "车外输出设备: <font color='#FF0000'>" + audioOutputUsageExternal + 
                            "</font>    车内输出设备: <font color='#FF0000'>" + audioOutputUsageCar + 
                            "</font>    麦克风源: <font color='#FF0000'>" + audioInputSource + 
                            "</font>    最大放大倍数: <font color='#FF0000'>" + maxAmplification + "</font>";
            // 使用HtmlCompat.fromHtml()解析HTML标签
            tvAudioDeviceStatus.setText(HtmlCompat.fromHtml(message, HtmlCompat.FROM_HTML_MODE_LEGACY));
            AppLog.d(TAG, "Saved audio device settings: outputUsageExternal=" + audioOutputUsageExternal + ", outputUsageCar=" + audioOutputUsageCar + ", inputSource=" + audioInputSource + ", maxAmplification=" + maxAmplification);
            
            // 测试TTS发声，强制使用车内模式
            try {
                int carAudioUsage = appConfig.getAudioOutputUsageCar();
                ttsManager.speakWithUsage("音频设备设置已保存", carAudioUsage);
            } catch (Exception e) {
                AppLog.e(TAG, "Failed to speak with TTS", e);
            }
            
        } catch (NumberFormatException e) {
            tvAudioDeviceStatus.setText("输入值无效，请输入有效的数字");
            AppLog.e(TAG, "Invalid input format", e);
        } catch (Exception e) {
            tvAudioDeviceStatus.setText("保存设置时出错：" + e.getMessage());
            AppLog.e(TAG, "Error saving audio device settings", e);
        }
    }
    
    private void startFloatingWindowService() {
        Intent serviceIntent = new Intent(requireContext(), FloatingWindowService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            requireContext().startForegroundService(serviceIntent);
        } else {
            requireContext().startService(serviceIntent);
        }
    }
    
    private void stopFloatingWindowService() {
        Intent serviceIntent = new Intent(requireContext(), FloatingWindowService.class);
        requireContext().stopService(serviceIntent);
    }
}
