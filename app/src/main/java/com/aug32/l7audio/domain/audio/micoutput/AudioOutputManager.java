package com.aug32.l7audio.domain.audio.micoutput;

import android.content.Context;

import com.aug32.l7audio.data.local.AppConfig;
import com.aug32.l7audio.utils.AppLog;

/**
 * 音频输出管理器
 *
 * 职责：
 * - 管理车内/车外音频输出模式切换
 * - 提供对应的 AudioAttributes.Usage 值
 *
 * 目标 SDK：Android 11 (API 30)
 */
public class AudioOutputManager {

    private static final String TAG = "AudioOutputManager";

    /** 车内输出模式 */
    public static final int OUTPUT_CAR = 0;

    /** 车外输出模式 */
    public static final int OUTPUT_EXTERNAL = 1;

    // 上下文对象
    private final Context context;
    // 应用配置
    private final AppConfig appConfig;
    // 当前音频输出模式（volatile 保证多线程可见性）
    private volatile int currentOutputMode = OUTPUT_EXTERNAL;

    /**
     * 构造函数
     * <p>
     * 初始化时从 AppConfig 读取上次保存的输出模式。
     * </p>
     *
     * @param context 上下文对象
     */
    public AudioOutputManager(Context context) {
        this.context = context;
        this.appConfig = new AppConfig(context);
        this.currentOutputMode = appConfig.getAudioOutputMode();
        AppLog.i(TAG, "音频输出管理器初始化，模式: " + modeToString(currentOutputMode));
    }

    /**
     * 设置音频输出模式
     * <p>
     * 设置后会自动保存到 AppConfig 持久化存储。
     * 模式必须是 OUTPUT_CAR 或 OUTPUT_EXTERNAL，无效值会被忽略。
     * </p>
     *
     * @param mode 音频输出模式，取值为 {@link #OUTPUT_CAR} 或 {@link #OUTPUT_EXTERNAL}
     */
    public synchronized void setOutputMode(int mode) {
        // 边界检查：确保模式在有效范围内
        if (mode < OUTPUT_CAR || mode > OUTPUT_EXTERNAL) {
            AppLog.e(TAG, "无效输出模式: " + mode);
            return;
        }
        currentOutputMode = mode;
        appConfig.setAudioOutputMode(mode);
        AppLog.i(TAG, "输出模式设置为: " + modeToString(mode));
    }

    /**
     * 获取当前音频输出模式
     *
     * @return 当前输出模式，{@link #OUTPUT_CAR} 或 {@link #OUTPUT_EXTERNAL}
     */
    public synchronized int getOutputMode() {
        return currentOutputMode;
    }

    /**
     * 获取当前 AudioAttributes.Usage 值
     * <p>
     * 根据当前输出模式返回对应的 Usage 值，用于 AudioTrack、TTS 等音频播放。
     * </p>
     *
     * @return AudioAttributes.Usage 值
     */
    public synchronized int getAudioUsage() {
        int usage;
        if (currentOutputMode == OUTPUT_CAR) {
            usage = appConfig.getAudioOutputUsageCar();
        } else {
            usage = appConfig.getAudioOutputUsageExternal();
        }
        AppLog.i(TAG, "获取音频用法: mode=" + currentOutputMode + " → usage=" + usage);
        return usage;
    }

    /**
     * 获取车外 AudioAttributes.Usage 值
     *
     * @return 车外用法常量，如 {@link android.media.AudioAttributes#USAGE_NOTIFICATION_RINGTONE}
     */
    public int getExternalAudioUsage() {
        return appConfig.getAudioOutputUsageExternal();
    }

    /**
     * 获取车内 AudioAttributes.Usage 值
     *
     * @return 车内用法常量，如 {@link android.media.AudioAttributes#USAGE_MEDIA}
     */
    public int getCarAudioUsage() {
        return appConfig.getAudioOutputUsageCar();
    }

    /**
     * 恢复音频输出
     * <p>
     * 预留方法，用于音频输出暂停后恢复。
     * </p>
     */
    public void resume() {
        AppLog.d(TAG, "音频输出已恢复");
    }

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
