package com.aug32.l7audio.domain.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;

import androidx.annotation.NonNull;

import android.os.Handler;
import android.os.Looper;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.aug32.l7audio.utils.AppLog;

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
    // 单例实例
    private static AudioFocusManager INSTANCE;

    // Application Context
    private final Context appContext;
    // 系统音频管理器
    private final AudioManager audioManager;

    // 音乐播放焦点请求对象
    private AudioFocusRequest playbackFocusRequest;
    // 短暂独占焦点请求对象
    private AudioFocusRequest transientFocusRequest;
    // 是否持有音乐播放焦点（volatile 确保 focusListener 在 Binder 线程的写入对 synchronized 方法可见）
    private volatile boolean hasPlaybackFocus = false;
    // 是否持有短暂独占焦点（同上）
    private volatile boolean hasTransientFocus = false;

    // 音频焦点变化监听器列表（使用弱引用防止内存泄漏）
    private final List<WeakReference<OnAudioFocusChangeListener>> listeners = new ArrayList<>();
    // 主线程 Handler，用于将焦点回调分发切到主线程（避免在 Binder 线程调 ExoPlayer API）
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * 音频焦点变化监听器接口
     * <p>
     * 用于监听音频焦点的获取、短暂丢失和永久丢失事件。
     * </p>
     */
    public interface OnAudioFocusChangeListener {
        /**
         * 焦点获取回调
         */
        void onFocusGained();

        /**
         * 短暂失去焦点回调
         * <p>
         * 焦点短暂丢失后可能会恢复，可暂停播放但无需释放资源。
         * </p>
         */
        void onFocusLostTransient();

        /**
         * 永久失去焦点回调
         * <p>
         * 焦点永久丢失，应停止播放并释放资源。
         * </p>
         */
        void onFocusLostPermanent();
    }

    /**
     * 获取/创建单例实例
     * <p>
     * 首次调用时创建实例，后续调用直接返回已创建的实例。
     * 使用 Application Context 避免持有 Activity 导致内存泄漏。
     * </p>
     *
     * @param ctx 上下文对象，会自动转换为 Application Context
     * @return AudioFocusManager 单例实例
     */
    public static synchronized AudioFocusManager from(@NonNull Context ctx) {
        if (INSTANCE == null) {
            INSTANCE = new AudioFocusManager(ctx.getApplicationContext());
        }
        return INSTANCE;
    }

    /**
     * 构造函数
     *
     * @param appContext Application Context 对象
     */
    public AudioFocusManager(Context appContext) {
        this.appContext = appContext;
        this.audioManager = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
    }

    /**
     * 当前是否持有任何类型的音频焦点
     *
     * @return true 表示持有播放焦点或短暂独占焦点，false 表示未持有任何焦点
     */
    public synchronized boolean hasAnyFocus() {
        return hasPlaybackFocus || hasTransientFocus;
    }

    /**
     * 当前是否持有短暂独占焦点
     *
     * @return true 表示持有短暂独占焦点，false 表示未持有
     */
    public synchronized boolean hasTransientFocus() {
        return hasTransientFocus;
    }

    /**
     * 当前是否持有音乐播放焦点
     *
     * @return true 表示持有音乐播放焦点，false 表示未持有
     */
    public synchronized boolean hasPlaybackFocus() {
        return hasPlaybackFocus;
    }

    /**
     * 注册音频焦点变化监听器
     * <p>
     * 使用弱引用持有监听器，防止内存泄漏。
     * 注册前会自动清理已被回收的弱引用监听器，并避免重复注册。
     * </p>
     *
     * @param listener 音频焦点变化监听器
     */
    public synchronized void addFocusChangeListener(@NonNull OnAudioFocusChangeListener listener) {
        Iterator<WeakReference<OnAudioFocusChangeListener>> it = listeners.iterator();
        while (it.hasNext()) {
            OnAudioFocusChangeListener l = it.next().get();
            // 清理已被回收的弱引用
            if (l == null) {
                it.remove();
            } else if (l == listener) {
                // 已注册则直接返回，避免重复
                return;
            }
        }
        listeners.add(new WeakReference<>(listener));
    }

    /**
     * 移除音频焦点变化监听器
     * <p>
     * 移除指定的监听器，同时清理已被回收的弱引用监听器。
     * </p>
     *
     * @param listener 要移除的音频焦点变化监听器
     */
    public synchronized void removeFocusChangeListener(@NonNull OnAudioFocusChangeListener listener) {
        Iterator<WeakReference<OnAudioFocusChangeListener>> it = listeners.iterator();
        while (it.hasNext()) {
            OnAudioFocusChangeListener l = it.next().get();
            // 移除目标监听器，同时清理已被回收的弱引用
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

    /**
     * 申请音乐播放焦点
     * <p>
     * 申请 AUDIOFOCUS_GAIN 类型的音频焦点，用于音乐播放场景。
     * 若已持有短暂独占焦点则拒绝申请，避免两种焦点冲突。
     * 若已持有播放焦点则直接返回成功。
     * </p>
     *
     * @return true 表示焦点申请成功，false 表示申请失败
     */
    public synchronized boolean requestPlaybackFocus() {
        // 若已持有短暂独占焦点，不允许再申请播放焦点，避免冲突
        if (hasTransientFocus) {
            AppLog.d(TAG, "Cannot request playback focus while holding transient focus; skip.");
            return false;
        }
        // 已持有播放焦点，直接返回成功
        if (hasPlaybackFocus) {
            AppLog.d(TAG, "Already holds playback focus; skip.");
            return true;
        }
        // 首次调用时懒加载创建播放焦点请求对象
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

    /**
     * 释放音乐播放焦点
     * <p>
     * 释放已持有的音乐播放焦点，若未持有则不执行任何操作。
     * </p>
     */
    public synchronized void abandonPlaybackFocus() {
        if (!hasPlaybackFocus || playbackFocusRequest == null) {
            return;
        }
        audioManager.abandonAudioFocusRequest(playbackFocusRequest);
        hasPlaybackFocus = false;
        AppLog.d(TAG, "abandonPlaybackFocus");
    }

    /**
     * 申请短暂独占焦点
     * <p>
     * 申请 AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE 类型的音频焦点，
     * 用于车外喊话、TTS播报等短暂独占场景。
     * 若已持有播放焦点会先释放播放焦点，确保短暂焦点优先级更高。
     * </p>
     *
     * @return true 表示焦点申请成功，false 表示申请失败
     */
    public synchronized boolean requestTransientFocus() {
        // 已持有短暂独占焦点，直接返回成功
        if (hasTransientFocus) {
            return true;
        }
        // 若已持有播放焦点，先主动释放以让短暂独占焦点可以获取
        if (hasPlaybackFocus && playbackFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(playbackFocusRequest);
            hasPlaybackFocus = false;
            AppLog.d(TAG, "Preemptively abandoned playback focus to grant transient focus.");
        }
        // 首次调用时懒加载创建短暂独占焦点请求对象
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
        if (granted) {
            hasTransientFocus = true;
            // 短暂焦点获取后，手动分发短暂失去焦点事件，让音乐播放等组件暂停（切主线程避免 Binder 线程调 ExoPlayer）
            AppLog.d(TAG, "Posting dispatchLostTransient to main thread");
            mainHandler.post(AudioFocusManager.this::dispatchLostTransient);
        } else {
            AppLog.w(TAG, "requestTransientFocus => denied");
        }
        return granted;
    }

    /**
     * 释放短暂独占焦点
     * <p>
     * 释放已持有的短暂独占焦点，若未持有则不执行任何操作。
     * 释放后手动分发焦点获取事件，通知音乐播放等组件恢复。
     * </p>
     */
    public synchronized void abandonTransientFocus() {
        if (!hasTransientFocus || transientFocusRequest == null) {
            return;
        }
        audioManager.abandonAudioFocusRequest(transientFocusRequest);
        hasTransientFocus = false;
        // 短暂焦点释放后，手动分发焦点获取事件，让音乐播放等组件恢复（切主线程）
        AppLog.d(TAG, "Posting dispatchGained to main thread");
        mainHandler.post(AudioFocusManager.this::dispatchGained);
    }

    /**
     * 一次性释放所有焦点
     * <p>
     * 同时释放音乐播放焦点和短暂独占焦点，用于清理资源场景。
     * </p>
     */
    public synchronized void abandonAll() {
        abandonPlaybackFocus();
        abandonTransientFocus();
    }

    // 系统音频焦点变化监听器
    private final android.media.AudioManager.OnAudioFocusChangeListener focusListener =
            new android.media.AudioManager.OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int focusChange) {
                    AppLog.d(TAG, "onAudioFocusChange: " + focusChange);
                    // synchronized 块内更新 flag，与 public synchronized 方法互斥
                    // dispatch* 通过 mainHandler.post 切到主线程，避免在 Binder 线程调 ExoPlayer
                    Runnable dispatchTask;
                    synchronized (AudioFocusManager.this) {
                        switch (focusChange) {
                            case AudioManager.AUDIOFOCUS_GAIN:
                            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE:
                            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                                if (!hasTransientFocus) {
                                    hasPlaybackFocus = true;
                                    dispatchTask = AudioFocusManager.this::dispatchGained;
                                } else {
                                    dispatchTask = null;
                                }
                                break;
                            case AudioManager.AUDIOFOCUS_LOSS:
                                hasPlaybackFocus = false;
                                hasTransientFocus = false;
                                dispatchTask = AudioFocusManager.this::dispatchLostPermanent;
                                break;
                            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                                if (hasPlaybackFocus) {
                                    hasPlaybackFocus = false;
                                }
                                dispatchTask = AudioFocusManager.this::dispatchLostTransient;
                                break;
                            default:
                                dispatchTask = null;
                                break;
                        }
                    }
                    if (dispatchTask != null) {
                        mainHandler.post(dispatchTask);
                    }
                }
            };
}
