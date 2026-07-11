package com.aug32.l7audio.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import com.aug32.l7audio.data.local.config.AudioConfig;
import com.aug32.l7audio.data.local.config.ThemeConfig;
import com.aug32.l7audio.data.local.config.floating.FloatingWindowConfig;
import com.aug32.l7audio.data.local.config.micoutput.MicOutputConfig;
import com.aug32.l7audio.data.local.config.player.MusicConfig;
import com.aug32.l7audio.data.local.config.tts.TTSConfig;

/**
 * 应用全局配置管理门面类
 *
 * 职责：
 * - 统一管理 SharedPreferences 读写（底层委托给各领域 Config）
 * - 保持原有 API 兼容，内部委托给拆分的 Config 子类
 *
 * 架构说明：
 * 各领域配置已拆分到 data/local/config/ 包下，AppConfig 作为门面统一暴露，
 * 避免大规模修改现有代码。推荐逐步迁移到各 Config 子类直接使用。
 *
 * 目标 SDK：Android 11 (API 30)
 */
public class AppConfig {
    // 主题模式常量（保持向后兼容）
    /** 跟随系统主题 */
    public static final int THEME_MODE_SYSTEM = 0;
    /** 浅色主题 */
    public static final int THEME_MODE_LIGHT = 1;
    /** 深色主题 */
    public static final int THEME_MODE_DARK = 2;

    private final SharedPreferences preferences;

    // 领域配置实例
    private ThemeConfig themeConfig;
    private AudioConfig audioConfig;
    private MicOutputConfig micOutputConfig;
    private TTSConfig ttsConfig;
    private MusicConfig musicConfig;
    private FloatingWindowConfig floatingWindowConfig;

    /**
     * 构造函数，使用应用默认的 SharedPreferences
     *
     * @param context 应用上下文，用于获取 SharedPreferences 实例
     */
    public AppConfig(Context context) {
        this.preferences = context.getSharedPreferences(
                context.getPackageName() + "_preferences", Context.MODE_PRIVATE);
        initSubConfigs();
    }

    /**
     * 内部构造函数（直接传入 SharedPreferences，用于子类）
     *
     * @param preferences SharedPreferences 实例
     */
    AppConfig(SharedPreferences preferences) {
        this.preferences = preferences;
        initSubConfigs();
    }

    private void initSubConfigs() {
        themeConfig = new ThemeConfig(preferences);
        audioConfig = new AudioConfig(preferences);
        micOutputConfig = new MicOutputConfig(preferences);
        ttsConfig = new TTSConfig(preferences);
        musicConfig = new MusicConfig(preferences);
        floatingWindowConfig = new FloatingWindowConfig(preferences);
    }

    // ==================== 门面方法（保持向后兼容，内部委托）====================

    // 主题
    public int getThemeMode() { return themeConfig.getThemeMode(); }
    public void setThemeMode(int mode) { themeConfig.setThemeMode(mode); }

    // 开机自启动
    public boolean isAutoStartOnBoot() { return themeConfig.isAutoStartOnBoot(); }
    public void setAutoStartOnBoot(boolean autoStart) { themeConfig.setAutoStartOnBoot(autoStart); }

    // 音频输出模式
    public int getAudioOutputMode() { return audioConfig.getOutputMode(); }
    public void setAudioOutputMode(int mode) { audioConfig.setOutputMode(mode); }

    // 音频使用类型
    public int getAudioOutputUsageExternal() { return audioConfig.getUsageExternal(); }
    public void setAudioOutputUsageExternal(int usageType) { audioConfig.setUsageExternal(usageType); }
    public int getAudioOutputUsageCar() { return audioConfig.getUsageCar(); }
    public void setAudioOutputUsageCar(int usageType) { audioConfig.setUsageCar(usageType); }
    public int getAudioInputSource() { return audioConfig.getAudioInputSource(); }
    public void setAudioInputSource(int sourceType) { audioConfig.setAudioInputSource(sourceType); }

    // 音量
    public int getCarVolume() { return audioConfig.getCarVolume(); }
    public void setCarVolume(int volume) { audioConfig.setCarVolume(volume); }
    public int getExternalVolume() { return audioConfig.getExternalVolume(); }
    public void setExternalVolume(int volume) { audioConfig.setExternalVolume(volume); }

    // 当前功能
    public int getCurrentFunction() { return audioConfig.getCurrentFunction(); }
    public void setCurrentFunction(int function) { audioConfig.setCurrentFunction(function); }

    // 麦克风放大
    public int getMicAmplificationLevel() { return micOutputConfig.getAmplificationLevel(); }
    public void setMicAmplificationLevel(int level) { micOutputConfig.setAmplificationLevel(level); }
    public boolean isNoiseReductionEnabled() { return micOutputConfig.isNoiseReductionEnabled(); }
    public void setNoiseReductionEnabled(boolean enabled) { micOutputConfig.setNoiseReductionEnabled(enabled); }
    public boolean isEchoCancellationEnabled() { return micOutputConfig.isEchoCancellationEnabled(); }
    public void setEchoCancellationEnabled(boolean enabled) { micOutputConfig.setEchoCancellationEnabled(enabled); }
    public boolean isHowlingSuppressionEnabled() { return micOutputConfig.isHowlingSuppressionEnabled(); }
    public void setHowlingSuppressionEnabled(boolean enabled) { micOutputConfig.setHowlingSuppressionEnabled(enabled); }
    public boolean isAgcEnabled() { return micOutputConfig.isAgcEnabled(); }
    public void setAgcEnabled(boolean enabled) { micOutputConfig.setAgcEnabled(enabled); }
    public int getMaxAmplification() { return micOutputConfig.getMaxAmplification(); }
    public void setMaxAmplification(int maxAmplification) { micOutputConfig.setMaxAmplification(maxAmplification); }

    // TTS
    public String getTTSItems() { return ttsConfig.getTTSItems(); }
    public void setTTSItems(String ttsItemsJson) { ttsConfig.setTTSItems(ttsItemsJson); }

    // 音乐
    public String getMusicPlaylist() { return musicConfig.getMusicPlaylist(); }
    public void setMusicPlaylist(String playlistJson) { musicConfig.setMusicPlaylist(playlistJson); }
    public int getLastPlayedIndex() { return musicConfig.getLastPlayedIndex(); }
    public void setLastPlayedIndex(int index) { musicConfig.setLastPlayedIndex(index); }
    public long getLastPlayedPosition() { return musicConfig.getLastPlayedPosition(); }
    public void setLastPlayedPosition(long position) { musicConfig.setLastPlayedPosition(position); }
    public int getRepeatMode() { return musicConfig.getRepeatMode(); }
    public void setRepeatMode(int mode) { musicConfig.setRepeatMode(mode); }
    public void setShuffleModeEnabled(boolean enabled) { musicConfig.setShuffleModeEnabled(enabled); }
    public boolean isShuffleModeEnabled() { return musicConfig.isShuffleModeEnabled(); }

    // 悬浮窗
    public boolean isFloatingWindowEnabled() { return floatingWindowConfig.isEnabled(); }
    public void setFloatingWindowEnabled(boolean enabled) { floatingWindowConfig.setEnabled(enabled); }
    public int getFloatingWindowX() { return floatingWindowConfig.getX(); }
    public void setFloatingWindowX(int x) { floatingWindowConfig.setX(x); }
    public int getFloatingWindowY() { return floatingWindowConfig.getY(); }
    public void setFloatingWindowY(int y) { floatingWindowConfig.setY(y); }
    public int getFloatingWindowAlpha() { return floatingWindowConfig.getAlpha(); }
    public void setFloatingWindowAlpha(int alpha) { floatingWindowConfig.setAlpha(alpha); }
    public int getFloatingWindowWidthDp() { return floatingWindowConfig.getWidthDp(); }
    public void setFloatingWindowWidthDp(int widthDp) { floatingWindowConfig.setWidthDp(widthDp); }
    public String getFloatingWindowTTSIndices() { return floatingWindowConfig.getTTSIndices(); }
    public void setFloatingWindowTTSIndices(String indicesJson) { floatingWindowConfig.setTTSIndices(indicesJson); }
    public String getFloatingWindowTTSNames() { return floatingWindowConfig.getTTSNames(); }
    public void setFloatingWindowTTSNames(String namesJson) { floatingWindowConfig.setTTSNames(namesJson); }
    public int getFloatingWindowAutoHideTimeoutSec() { return floatingWindowConfig.getAutoHideTimeoutSec(); }
    public void setFloatingWindowAutoHideTimeoutSec(int seconds) { floatingWindowConfig.setAutoHideTimeoutSec(seconds); }

    // ==================== TTS 悬浮窗（uid 方案） ====================
    public String getFloatingWindowTTSSelectedUids() { return floatingWindowConfig.getTTSSelectedUids(); }
    public void setFloatingWindowTTSSelectedUids(String uidsJson) { floatingWindowConfig.setTTSSelectedUids(uidsJson); }
    public String getFloatingWindowTTSNamesByUid() { return floatingWindowConfig.getTTSNamesByUid(); }
    public void setFloatingWindowTTSNamesByUid(String namesJson) { floatingWindowConfig.setTTSNamesByUid(namesJson); }

    // ==================== 全局操作 ====================
}
