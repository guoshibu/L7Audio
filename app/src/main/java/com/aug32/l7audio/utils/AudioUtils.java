package com.aug32.l7audio.utils;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;

import com.aug32.l7audio.utils.AppLog;

/**
 * 音频相关工具类
 *
 * 提供：
 * - AudioAttributes 统一创建
 * - 音频参数计算
 * - 权限检查等公共方法
 *
 * 目标 SDK：Android 11 (API 30)
 * 最低 SDK：Android 11 (API 30)
 */
public final class AudioUtils {

    private static final String TAG = "AudioUtils";

    // 采样率常量
    public static final int SAMPLE_RATE_44100 = 44100;
    public static final int SAMPLE_RATE_48000 = 48000;

    // 音频格式常量
    public static final int CHANNEL_IN_MONO = AudioFormat.CHANNEL_IN_MONO;
    public static final int CHANNEL_OUT_MONO = AudioFormat.CHANNEL_OUT_MONO;
    public static final int AUDIO_FORMAT_PCM_16BIT = AudioFormat.ENCODING_PCM_16BIT;

    private AudioUtils() {
        // 私有构造函数，防止实例化
    }

    /**
     * 创建音频属性（用于 AudioTrack / TextToSpeech 等）
     *
     * @param usageType      AudioAttributes.USAGE_* 常量
     * @param contentType    AudioAttributes.CONTENT_TYPE_* 常量
     * @return 配置好的 AudioAttributes
     */
    public static AudioAttributes buildAudioAttributes(int usageType, int contentType) {
        return new AudioAttributes.Builder()
                .setUsage(usageType)
                .setContentType(contentType)
                .build();
    }

    /**
     * 创建语音播报用 AudioAttributes
     *
     * @param usageType AudioAttributes.USAGE_* 常量
     * @return 配置好的 AudioAttributes
     */
    public static AudioAttributes buildSpeechAudioAttributes(int usageType) {
        return new AudioAttributes.Builder()
                .setUsage(usageType)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
    }

    /**
     * 创建音乐播放用 AudioAttributes
     *
     * @param usageType AudioAttributes.USAGE_* 常量
     * @return 配置好的 AudioAttributes
     */
    public static AudioAttributes buildMusicAudioAttributes(int usageType) {
        return new AudioAttributes.Builder()
                .setUsage(usageType)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
    }

    /**
     * 创建录音用 AudioFormat
     *
     * @param sampleRate 采样率（默认 44100）
     * @return 配置好的 AudioFormat
     */
    public static AudioFormat buildRecordAudioFormat(int sampleRate) {
        return new AudioFormat.Builder()
                .setSampleRate(sampleRate > 0 ? sampleRate : SAMPLE_RATE_44100)
                .setEncoding(AUDIO_FORMAT_PCM_16BIT)
                .setChannelMask(CHANNEL_IN_MONO)
                .build();
    }

    /**
     * 创建播放用 AudioFormat
     *
     * @param sampleRate 采样率（默认 44100）
     * @return 配置好的 AudioFormat
     */
    public static AudioFormat buildPlayAudioFormat(int sampleRate) {
        return new AudioFormat.Builder()
                .setSampleRate(sampleRate > 0 ? sampleRate : SAMPLE_RATE_44100)
                .setEncoding(AUDIO_FORMAT_PCM_16BIT)
                .setChannelMask(CHANNEL_OUT_MONO)
                .build();
    }

    /**
     * 计算 AudioRecord 最小缓冲区大小
     *
     * @param sampleRate 采样率
     * @return 最小缓冲区大小（字节）
     */
    public static int calculateMinBufferSize(int sampleRate) {
        int minBufferSize = AudioRecord.getMinBufferSize(
                sampleRate > 0 ? sampleRate : SAMPLE_RATE_44100,
                CHANNEL_IN_MONO,
                AUDIO_FORMAT_PCM_16BIT
        );
        return minBufferSize > 0 ? minBufferSize * 2 : 0;
    }

    /**
     * 判断是否为有效的音频采样率
     *
     * @param sampleRate 待检测采样率
     * @return true=有效
     */
    public static boolean isValidSampleRate(int sampleRate) {
        int[] validRates = {8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000};
        for (int rate : validRates) {
            if (rate == sampleRate) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取录音音频源的中文名称
     *
     * @param audioSource MediaRecorder.AudioSource.* 常量
     * @return 中文名称
     */
    public static String getAudioSourceName(int audioSource) {
        switch (audioSource) {
            case MediaRecorder.AudioSource.MIC:
                return "麦克风";
            case MediaRecorder.AudioSource.DEFAULT:
                return "默认";
            case MediaRecorder.AudioSource.VOICE_CALL:
                return "语音通话";
            case MediaRecorder.AudioSource.VOICE_DOWNLINK:
                return "语音下行";
            case MediaRecorder.AudioSource.VOICE_UPLINK:
                return "语音上行";
            case MediaRecorder.AudioSource.CAMCORDER:
                return "摄像机";
            case MediaRecorder.AudioSource.VOICE_RECOGNITION:
                return "语音识别";
            case MediaRecorder.AudioSource.VOICE_COMMUNICATION:
                return "语音通信";
            default:
                return "未知(" + audioSource + ")";
        }
    }

    /**
     * 将音量百分比（0-100）转换为 AudioTrack 音量（0.0-1.0）
     *
     * @param volumePercent 百分比音量
     * @return AudioTrack 音量值
     */
    public static float percentToVolume(int volumePercent) {
        if (volumePercent <= 0) return 0f;
        if (volumePercent >= 100) return 1f;
        return volumePercent / 100f;
    }

    /**
     * 将 AudioTrack 音量（0.0-1.0）转换为百分比（0-100）
     *
     * @param volume AudioTrack 音量值
     * @return 百分比音量
     */
    public static int volumeToPercent(float volume) {
        return Math.round(volume * 100);
    }

    /**
     * 判断指定音频源是否可用
     *
     * @param audioSource 音频源常量
     * @return true=可用
     */
    public static boolean isAudioSourceAvailable(int audioSource) {
        try {
            AudioRecord recorder = new AudioRecord(
                    audioSource,
                    SAMPLE_RATE_44100,
                    CHANNEL_IN_MONO,
                    AUDIO_FORMAT_PCM_16BIT,
                    1024
            );
            boolean available = recorder.getState() == AudioRecord.STATE_INITIALIZED;
            recorder.release();
            return available;
        } catch (Exception e) {
            AppLog.e(TAG, "Audio source " + audioSource + " not available", e);
            return false;
        }
    }

    /**
     * 判断 AudioTrack 是否支持指定格式
     *
     * @param sampleRate 采样率
     * @param channelMask 通道掩码
     * @param audioFormat 编码格式
     * @return true=支持
     */
    public static boolean isAudioTrackFormatSupported(int sampleRate, int channelMask, int audioFormat) {
        int minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelMask, audioFormat);
        return minBufferSize > 0;
    }
}
