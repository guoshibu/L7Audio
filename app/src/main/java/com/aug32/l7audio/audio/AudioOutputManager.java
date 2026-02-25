package com.aug32.l7audio.audio;

import android.content.Context;
import com.aug32.l7audio.AppLog;
import com.aug32.l7audio.AppConfig;

/**
 * 音频输出管理器
 * 负责管理车内/车外音频输出模式的切换
 * 核心功能：根据当前模式返回对应的AudioAttributes.Usage值
 */
public class AudioOutputManager {
    private static final String TAG = "AudioOutputManager";

    /**
     * 车内输出模式常量
     */
    public static final int OUTPUT_CAR = 0;
    
    /**
     * 车外输出模式常量
     */
    public static final int OUTPUT_EXTERNAL = 1;

    private final Context context;
    private final AppConfig appConfig;
    
    /**
     * 当前音频输出模式
     */
    private int currentOutputMode = OUTPUT_EXTERNAL;

    /**
     * 构造函数
     * @param context 上下文对象
     */
    public AudioOutputManager(Context context) {
        this.context = context;
        this.appConfig = new AppConfig(context);
        // 从AppConfig加载保存的音频输出模式
        this.currentOutputMode = appConfig.getAudioOutputMode();
        AppLog.d(TAG, "AudioOutputManager initialized with mode: " + modeToString(currentOutputMode));
    }

    /**
     * 设置音频输出模式
     * @param mode 输出模式，OUTPUT_CAR或OUTPUT_EXTERNAL
     */
    public void setOutputMode(int mode) {
        if (mode < OUTPUT_CAR || mode > OUTPUT_EXTERNAL) {
            AppLog.e(TAG, "Invalid output mode: " + mode);
            return;
        }

        currentOutputMode = mode;
        // 保存模式到AppConfig
        appConfig.setAudioOutputMode(mode);
        AppLog.d(TAG, "Output mode set to: " + modeToString(mode));
    }

    /**
     * 获取当前音频输出模式
     * @return 当前输出模式
     */
    public int getOutputMode() {
        return currentOutputMode;// 返回当前音频输出模式
    }

    /**
     * 获取当前音频输出模式（兼容方法）
     * @return 当前输出模式
     */
    public int getAudioOutputMode() {
        return currentOutputMode;// 返回当前音频输出模式
    }

    /**
     * 获取当前模式对应的AudioAttributes.Usage值
     * 这是车内外发声的关键：
     * - 车内模式：返回AppConfig.getAudioOutputUsageCar()，默认值为1（USAGE_MEDIA）
     * - 车外模式：返回AppConfig.getAudioOutputUsageExternal()，默认值为15（bus15 ktvout）
     * @return AudioAttributes.Usage值
     */
    public int getAudioUsage() {
        if (currentOutputMode == OUTPUT_CAR) { // 车内模式
            return appConfig.getAudioOutputUsageCar(); // 车内模式默认值为1（USAGE_MEDIA）
        } else { // 车外模式
            return appConfig.getAudioOutputUsageExternal(); // 车外模式默认值为15（bus15 ktvout）        
        }
    }

    /**
     * 暂停音频输出
     */
    public void pause() {
        AppLog.d(TAG, "Audio output paused");
    }

    /**
     * 恢复音频输出
     */
    public void resume() {
        AppLog.d(TAG, "Audio output resumed");
    }

    /**
     * 将输出模式转换为可读字符串
     * @param mode 输出模式
     * @return 模式描述字符串
     */
    private String modeToString(int mode) {
        switch (mode) {
            case OUTPUT_CAR:
                return "OUTPUT_CAR (仅车内)";
            case OUTPUT_EXTERNAL:
                return "OUTPUT_EXTERNAL (仅车外)";
            default:
                return "UNKNOWN";
        }
    }
}
