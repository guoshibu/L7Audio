package com.aug32.l7audio.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;


import com.aug32.l7audio.AppLog;

public class AudioFocusManager {
    private static final String TAG = "AudioFocusManager";

    private final Context context;
    private final AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private volatile boolean hasAudioFocus = false;

    public AudioFocusManager(Context context) {
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        AppLog.d(TAG, "AudioFocusManager initialized");
    }

    /**
     * 请求音频焦点
     */
    public synchronized boolean requestAudioFocus() {
        if (hasAudioFocus) {
            AppLog.d(TAG, "Already has audio focus");
            return true;
        }

        boolean result = false;

        // 使用新的 AudioFocusRequest API
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener(focusChange -> {
                    handleAudioFocusChange(focusChange);
                })
                .build();

        int focusResult = audioManager.requestAudioFocus(audioFocusRequest);
        result = (focusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);

        hasAudioFocus = result;
        AppLog.d(TAG, "Request audio focus: " + result);
        return result;
    }

    /**
     * 放弃音频焦点
     */
    public synchronized void abandonAudioFocus() {
        if (!hasAudioFocus) {
            AppLog.d(TAG, "No audio focus to abandon");
            return;
        }

        if (audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
        }

        hasAudioFocus = false;
        AppLog.d(TAG, "Abandoned audio focus");
    }

    /**
     * 处理音频焦点变化
     */
    private void handleAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                // 获得音频焦点
                hasAudioFocus = true;
                AppLog.d(TAG, "Audio focus gained");
                break;

            case AudioManager.AUDIOFOCUS_LOSS:
                // 失去音频焦点，需要停止播放
                hasAudioFocus = false;
                AppLog.d(TAG, "Audio focus lost");
                // 通知相关组件停止音频播放
                notifyAudioFocusLoss();
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // 暂时失去音频焦点，需要暂停播放
                AppLog.d(TAG, "Audio focus lost transient");
                // 通知相关组件暂停音频播放
                notifyAudioFocusLossTransient();
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // 暂时失去音频焦点，但可以降低音量继续播放
                AppLog.d(TAG, "Audio focus lost transient can duck");
                // 通知相关组件降低音量
                notifyAudioFocusLossTransientCanDuck();
                break;
        }
    }

    /**
     * 通知音频焦点完全失去
     */
    private void notifyAudioFocusLoss() {
        // 这里可以通过回调或广播通知相关组件停止音频播放
        AppLog.d(TAG, "Notify audio focus loss");
    }

    /**
     * 通知音频焦点暂时失去
     */
    private void notifyAudioFocusLossTransient() {
        // 这里可以通过回调或广播通知相关组件暂停音频播放
        AppLog.d(TAG, "Notify audio focus loss transient");
    }

    /**
     * 通知音频焦点暂时失去但可以降低音量
     */
    private void notifyAudioFocusLossTransientCanDuck() {
        // 这里可以通过回调或广播通知相关组件降低音量
        AppLog.d(TAG, "Notify audio focus loss transient can duck");
    }

    /**
     * 检查是否拥有音频焦点
     */
    public boolean hasAudioFocus() {
        return hasAudioFocus;
    }
}