package com.aug32.l7audio.domain.audio.tts;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.Locale;

import android.os.Looper;

import com.aug32.l7audio.domain.audio.micoutput.AudioOutputManager;
import com.aug32.l7audio.utils.AppLog;

/**
 * TTS语音播报管理器
 *
 * <p>职责：
 * <ul>
 *   <li>文本转语音播报</li>
 *   <li>车内外音频输出模式切换</li>
 * </ul>
 *
 * <p>目标 SDK：Android 11 (API 30)
 *
 * @author L7Audio Team
 */
public class TTSManager implements TextToSpeech.OnInitListener {
    private static final String TAG = "TTSManager";

    /** 默认语速 */
    private static final float DEFAULT_SPEECH_RATE = 1.0f;
    /** 默认音调 */
    private static final float DEFAULT_PITCH = 1.0f;

    /**
     * TTS播报进度监听器接口
     *
     * <p>用于监听TTS播报的开始、完成、错误和进度更新事件。
     */
    public interface TTSProgressListener {
        /**
         * TTS开始播报回调
         */
        void onTTSStart();

        /**
         * TTS播报完成回调
         */
        void onTTSDone();

        /**
         * TTS播报错误回调
         */
        void onTTSError();

        /**
         * TTS播报进度更新回调
         *
         * @param progress 当前进度百分比（0-100）
         */
        void onTTSProgress(int progress);
    }

    /** 上下文对象 */
    private final Context context;
    /** 音频输出管理器 */
    private final AudioOutputManager audioOutputManager;

    /** TTS引擎实例 */
    private TextToSpeech textToSpeech;
    /** TTS引擎是否已初始化 */
    private boolean isInitialized = false;
    /** 进度监听器 */
    private TTSProgressListener progressListener;
    /** 进度更新Handler */
    private android.os.Handler progressHandler;
    /** 进度更新Runnable */
    private Runnable progressRunnable;
    /** 当前播报进度（0-100） */
    private int currentProgress = 0;
    /** 是否正在播报 */
    private boolean isSpeaking = false;

    /**
     * 构造函数
     *
     * <p>初始化TTS引擎，使用默认语速和音调（1.0f），并启动进度更新定时器。
     *
     * @param context            上下文对象
     * @param audioOutputManager 音频输出管理器，用于获取音频输出模式
     */
    public TTSManager(Context context, AudioOutputManager audioOutputManager) {
        this.context = context;
        this.audioOutputManager = audioOutputManager;

        textToSpeech = new TextToSpeech(context, this);
        AppLog.d(TAG, "TTSManager initialized");

        progressHandler = new android.os.Handler(Looper.getMainLooper());
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (isSpeaking && progressListener != null) {
                    currentProgress += 5;
                    if (currentProgress > 100) {
                        currentProgress = 100;
                    }
                    progressListener.onTTSProgress(currentProgress);
                    if (currentProgress < 100) {
                        progressHandler.postDelayed(this, 200);
                    }
                }
            }
        };
    }

    /**
     * TTS引擎初始化回调
     *
     * <p>初始化成功后，按优先级依次尝试设置语言：简体中文 → 中文(中国) → 繁体中文 → 英语。
     * 多级回退机制确保在各种设备上都能找到可用的语音。
     * 设置成功后配置语速、音调和音频属性，并注册播报进度监听器。
     *
     * @param status 初始化状态，TextToSpeech.SUCCESS 表示成功，其他值表示失败
     */
    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            AppLog.d(TAG, "TTS engine initialized successfully");

            boolean languageSet = false;

            if (!languageSet) {
                int result = textToSpeech.setLanguage(Locale.SIMPLIFIED_CHINESE);
                if (result == TextToSpeech.LANG_AVAILABLE) {
                    AppLog.d(TAG, "Simplified Chinese language set successfully");
                    languageSet = true;
                } else {
                    AppLog.w(TAG, "Simplified Chinese not available, result: " + result);
                }
            }

            if (!languageSet) {
                int result = textToSpeech.setLanguage(Locale.CHINA);
                if (result == TextToSpeech.LANG_AVAILABLE) {
                    AppLog.d(TAG, "Chinese (China) language set successfully");
                    languageSet = true;
                } else {
                    AppLog.w(TAG, "Chinese (China) not available, result: " + result);
                }
            }

            if (!languageSet) {
                int result = textToSpeech.setLanguage(Locale.TRADITIONAL_CHINESE);
                if (result == TextToSpeech.LANG_AVAILABLE) {
                    AppLog.d(TAG, "Traditional Chinese language set successfully");
                    languageSet = true;
                } else {
                    AppLog.w(TAG, "Traditional Chinese not available, result: " + result);
                }
            }

            if (!languageSet) {
                int result = textToSpeech.setLanguage(Locale.ENGLISH);
                if (result == TextToSpeech.LANG_AVAILABLE) {
                    AppLog.d(TAG, "English language set successfully as fallback");
                    languageSet = true;
                } else {
                    AppLog.w(TAG, "English not available, result: " + result);
                }
            }

            if (languageSet) {
                textToSpeech.setSpeechRate(DEFAULT_SPEECH_RATE);
                textToSpeech.setPitch(DEFAULT_PITCH);

                int audioUsage;
                if (audioOutputManager != null) {
                    audioUsage = audioOutputManager.getAudioUsage();
                } else {
                    audioUsage = AudioAttributes.USAGE_MEDIA;
                }
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setUsage(audioUsage)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();
                textToSpeech.setAudioAttributes(audioAttributes);

                textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {
                        AppLog.d(TAG, "TTS started: " + utteranceId);
                        isSpeaking = true;
                        currentProgress = 0;
                        if (progressListener != null) {
                            progressListener.onTTSStart();
                            progressHandler.post(progressRunnable);
                        }
                    }

                    @Override
                    public void onDone(String utteranceId) {
                        AppLog.d(TAG, "TTS done: " + utteranceId);
                        isSpeaking = false;
                        currentProgress = 100;
                        if (progressListener != null) {
                            progressListener.onTTSProgress(100);
                            progressListener.onTTSDone();
                        }
                        progressHandler.removeCallbacks(progressRunnable);
                    }

                    @Override
                    public void onError(String utteranceId) {
                        AppLog.e(TAG, "TTS error: " + utteranceId);
                        isSpeaking = false;
                        currentProgress = 0;
                        if (progressListener != null) {
                            progressListener.onTTSProgress(0);
                            progressListener.onTTSError();
                        }
                        progressHandler.removeCallbacks(progressRunnable);
                    }

                    @Override
                    public void onError(String utteranceId, int errorCode) {
                        AppLog.e(TAG, "TTS error: " + utteranceId + ", code: " + errorCode);
                        isSpeaking = false;
                        currentProgress = 0;
                        if (progressListener != null) {
                            progressListener.onTTSProgress(0);
                            progressListener.onTTSError();
                        }
                        progressHandler.removeCallbacks(progressRunnable);
                    }
                });

                isInitialized = true;
                AppLog.d(TAG, "TTS initialized successfully with language support");
            } else {
                AppLog.e(TAG, "No supported language found");
                isInitialized = false;
            }
        } else {
            AppLog.e(TAG, "Failed to initialize TTS engine, status: " + status);
            isInitialized = false;
        }
    }

    /**
     * 播报文本
     *
     * <p>使用当前默认的音频输出模式播报文本。
     * 若当前正在播报，则不会重复播报。
     *
     * @param text 要播报的文本内容
     * @return true 表示播报成功启动，false 表示播报失败
     */
    public synchronized boolean speak(String text) {
        return speakWithUsage(text, -1);
    }

    /**
     * 使用指定的 usage 类型播报文本
     *
     * <p>支持自定义 AudioAttributes.Usage 类型，用于特殊场景的音频输出。
     * 播报前会先停止当前正在进行的播报。
     *
     * @param text  要播报的文本内容
     * @param usage AudioAttributes.Usage 值，传 -1 表示使用当前输出模式的默认值
     * @return true 表示播报成功启动，false 表示播报失败
     */
    public synchronized boolean speakWithUsage(String text, int usage) {
        if (!isInitialized || textToSpeech == null) {
            AppLog.e(TAG, "TTS not initialized");
            return false;
        }

        if (text == null || text.isEmpty()) {
            AppLog.e(TAG, "Empty text");
            return false;
        }

        try {
            int audioUsage;
            if (usage == -1) {
                if (audioOutputManager != null) {
                    audioUsage = audioOutputManager.getAudioUsage();
                } else {
                    audioUsage = AudioAttributes.USAGE_MEDIA;
                }
            } else {
                audioUsage = usage;
            }
            AppLog.d(TAG, "Using audio usage type: " + audioUsage);

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(audioUsage)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                    .build();
            textToSpeech.setAudioAttributes(audioAttributes);
            AppLog.d(TAG, "Updated TTS AudioAttributes with usage: " + audioUsage);

            textToSpeech.stop();

            Bundle params = new Bundle();
            String utteranceId = "utterance_" + System.currentTimeMillis();
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);

            if (!textToSpeech.isSpeaking()) {
                int result = textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId);
                boolean success = (result == TextToSpeech.SUCCESS);
                AppLog.d(TAG, "Speak text: " + text + ", result: " + success + ", AudioUsage: " + audioUsage);
                return success;
            } else {
                AppLog.w(TAG, "TTS is already speaking");
                return false;
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to speak text", e);
            return false;
        }
    }

    /**
     * 停止播报
     *
     * <p>立即停止当前正在进行的TTS播报，并重置进度状态。
     */
    public synchronized void stop() {
        if (textToSpeech != null && isInitialized) {
            textToSpeech.stop();
            isSpeaking = false;
            currentProgress = 0;
            if (progressListener != null) {
                progressListener.onTTSProgress(0);
            }
            progressHandler.removeCallbacks(progressRunnable);
            AppLog.d(TAG, "TTS stopped");
        }
    }

    /**
     * 释放 TTS 引擎资源
     *
     * <p>调用 TextToSpeech.shutdown() 释放原生资源，清理 Handler 回调。
     * 调用后不应再使用该 TTSManager 实例。
     */
    public synchronized void shutdown() {
        progressHandler.removeCallbacks(progressRunnable);
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
        isInitialized = false;
        isSpeaking = false;
        AppLog.d(TAG, "TTSManager shut down");
    }

    /**
     * 检查是否初始化成功
     *
     * @return true 表示TTS引擎已初始化成功，false 表示未初始化或初始化失败
     */
    public boolean isInitialized() {
        return isInitialized;
    }

    /**
     * 设置 TTS 进度监听器
     *
     * @param listener 进度监听器，传 null 可清除监听器
     */
    public void setProgressListener(TTSProgressListener listener) {
        this.progressListener = listener;
    }

    /**
     * 检查是否正在播报
     *
     * @return true 表示正在播报，false 表示未在播报
     */
    public boolean isSpeaking() {
        return isSpeaking;
    }
}
