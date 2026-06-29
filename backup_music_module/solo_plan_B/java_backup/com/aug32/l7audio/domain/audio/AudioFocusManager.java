package com.aug32.l7audio.domain.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;

import androidx.annotation.NonNull;

import com.aug32.l7audio.utils.AppLog;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 音频焦点统一管理器。
 *
 * 支持两类焦点申请：
 *   1) playback focus: 用于本应用播放音乐时的焦点申请（GAIN）
 *   2) transient focus: 用于车外喊话 / TTS 播报等短暂独占焦点场景（GAIN_TRANSIENT_EXCLUSIVE）
 *
 * 设计为进程内单例，避免重复创建。监听器用弱引用防止内存泄漏。
 *
 * 目标 SDK：Android 11 (API 30)
 */
public class AudioFocusManager {

    private static final String TAG = "AudioFocus";
    private static AudioFocusManager INSTANCE;

    private final Context appContext;
    private final AudioManager audioManager;

    private AudioFocusRequest playbackFocusRequest;
    private AudioFocusRequest transientFocusRequest;
    private boolean hasPlaybackFocus = false;
    private boolean hasTransientFocus = false;

    private final List<WeakReference<OnAudioFocusChangeListener>> listeners = new ArrayList<>();

    public interface OnAudioFocusChangeListener {
        void onFocusGained();
        void onFocusLostTransient();
        void onFocusLostPermanent();
    }

    /** 获取/创建单例实例 */
    public static synchronized AudioFocusManager from(@NonNull Context ctx) {
        if (INSTANCE == null) {
            INSTANCE = new AudioFocusManager(ctx.getApplicationContext());
        }
        return INSTANCE;
    }

    /** 构造函数 */
    public AudioFocusManager(Context appContext) {
        this.appContext = appContext;
        this.audioManager = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
    }

    /** 当前是否持有任何类型的音频焦点 */
    public synchronized boolean hasAnyFocus() {
        return hasPlaybackFocus || hasTransientFocus;
    }

    /** 当前是否持有短暂独占焦点 */
    public synchronized boolean hasTransientFocus() {
        return hasTransientFocus;
    }

    /** 当前是否持有音乐播放焦点 */
    public synchronized boolean hasPlaybackFocus() {
        return hasPlaybackFocus;
    }

    /** 注册音频焦点变化监听器（弱引用） */
    public synchronized void addFocusChangeListener(@NonNull OnAudioFocusChangeListener listener) {
        Iterator<WeakReference<OnAudioFocusChangeListener>> it = listeners.iterator();
        while (it.hasNext()) {
            OnAudioFocusChangeListener l = it.next().get();
            if (l == null) {
                it.remove();
            } else if (l == listener) {
                return;
            }
        }
        listeners.add(new WeakReference<>(listener));
    }

    /** 移除音频焦点变化监听器 */
    public synchronized void removeFocusChangeListener(@NonNull OnAudioFocusChangeListener listener) {
        Iterator<WeakReference<OnAudioFocusChangeListener>> it = listeners.iterator();
        while (it.hasNext()) {
            OnAudioFocusChangeListener l = it.next().get();
            if (l == null || l == listener) {
                it.remove();
            }
        }
    }

    /** 分发焦点获取事件 */
    private void dispatchGained() {
        Iterator<WeakReference<OnAudioFocusChangeListener>> it = listeners.iterator();
        while (it.hasNext()) {
            OnAudioFocusChangeListener l = it.next().get();
            if (l == null) {
                it.remove();
                continue;
            }
            try {
                l.onFocusGained();
            } catch (Throwable ignore) { /* defensive */ }
        }
    }

    /** 分发短暂失去焦点事件 */
    private void dispatchLostTransient() {
        Iterator<WeakReference<OnAudioFocusChangeListener>> it = listeners.iterator();
        while (it.hasNext()) {
            OnAudioFocusChangeListener l = it.next().get();
            if (l == null) {
                it.remove();
                continue;
            }
            try {
                l.onFocusLostTransient();
            } catch (Throwable ignore) { /* defensive */ }
        }
    }

    /** 分发永久失去焦点事件 */
    private void dispatchLostPermanent() {
        Iterator<WeakReference<OnAudioFocusChangeListener>> it = listeners.iterator();
        while (it.hasNext()) {
            OnAudioFocusChangeListener l = it.next().get();
            if (l == null) {
                it.remove();
                continue;
            }
            try {
                l.onFocusLostPermanent();
            } catch (Throwable ignore) { /* defensive */ }
        }
    }

    /** 申请音乐播放焦点 */
    public synchronized boolean requestPlaybackFocus() {
        if (hasTransientFocus) {
            AppLog.d(TAG, "Cannot request playback focus while holding transient focus; skip.");
            return false;
        }
        if (hasPlaybackFocus) {
            AppLog.d(TAG, "Already holds playback focus; skip.");
            return true;
        }
        if (playbackFocusRequest == null) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            playbackFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(focusListener)
                    .build();
        }
        int result = audioManager.requestAudioFocus(playbackFocusRequest);
        hasPlaybackFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
        AppLog.d(TAG, "requestPlaybackFocus => granted=" + hasPlaybackFocus);
        return hasPlaybackFocus;
    }

    /** 释放音乐播放焦点 */
    public synchronized void abandonPlaybackFocus() {
        if (!hasPlaybackFocus || playbackFocusRequest == null) {
            return;
        }
        audioManager.abandonAudioFocusRequest(playbackFocusRequest);
        hasPlaybackFocus = false;
        AppLog.d(TAG, "abandonPlaybackFocus");
    }

    /** 申请短暂独占焦点 */
    public synchronized boolean requestTransientFocus() {
        if (hasTransientFocus) {
            return true;
        }
        hasTransientFocus = true;
        if (hasPlaybackFocus && playbackFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(playbackFocusRequest);
            hasPlaybackFocus = false;
            AppLog.d(TAG, "Preemptively abandoned playback focus to grant transient focus.");
        }
        if (transientFocusRequest == null) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            transientFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(attrs)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener(focusListener)
                    .build();
        }
        int result = audioManager.requestAudioFocus(transientFocusRequest);
        boolean granted = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
        if (!granted) {
            hasTransientFocus = false;
            AppLog.w(TAG, "requestTransientFocus => denied");
        } else {
            AppLog.d(TAG, "Manually dispatching focus lost transient after transient focus request");
            dispatchLostTransient();
        }
        return granted;
    }

    /** 释放短暂独占焦点 */
    public synchronized void abandonTransientFocus() {
        if (!hasTransientFocus || transientFocusRequest == null) {
            return;
        }
        audioManager.abandonAudioFocusRequest(transientFocusRequest);
        hasTransientFocus = false;
        AppLog.d(TAG, "Manually dispatching focus gained after transient focus release");
        dispatchGained();
    }

    /** 一次性释放所有焦点 */
    public synchronized void abandonAll() {
        abandonPlaybackFocus();
        abandonTransientFocus();
    }

    private final android.media.AudioManager.OnAudioFocusChangeListener focusListener =
            new android.media.AudioManager.OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int focusChange) {
                    AppLog.d(TAG, "onAudioFocusChange: " + focusChange);
                    switch (focusChange) {
                        case AudioManager.AUDIOFOCUS_GAIN:
                        case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                        case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE:
                        case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                            if (!hasTransientFocus) {
                                hasPlaybackFocus = true;
                                dispatchGained();
                            }
                            break;
                        case AudioManager.AUDIOFOCUS_LOSS:
                            hasPlaybackFocus = false;
                            hasTransientFocus = false;
                            dispatchLostPermanent();
                            break;
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                            if (hasPlaybackFocus) {
                                hasPlaybackFocus = false;
                            }
                            dispatchLostTransient();
                            break;
                    }
                }
            };
}
