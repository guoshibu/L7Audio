package com.aug32.l7audio.domain.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import com.aug32.l7audio.data.local.AppConfig;
import com.aug32.l7audio.utils.AppLog;

import java.util.Locale;

/**
 * TTS语音播报管理器
 *
 * 职责：
 * - 文本转语音播报
 * - 语速/音调设置
 * - 车内外音频输出模式切换
 *
 * 目标 SDK：Android 11 (API 30)
 */
public class TTSManager implements TextToSpeech.OnInitListener {
    private static final String TAG = "TTSManager";

    /** TTS播报进度监听器接口 */
    public interface TTSProgressListener {
        void onTTSStart();
        void onTTSDone();
        void onTTSError();
        void onTTSProgress(int progress);
    }

    private final Context context;
    private final AudioOutputManager audioOutputManager;
    private final AppConfig appConfig;

    private TextToSpeech textToSpeech;
    private boolean isInitialized = false;
    private float speechRate = 1.0f;
    private float pitch = 1.0f;
    private TTSProgressListener progressListener;
    private android.os.Handler progressHandler;
    private Runnable progressRunnable;
    private int currentProgress = 0;
    private boolean isSpeaking = false;
    
    /** 构造函数 */
    public TTSManager(Context context, AudioOutputManager audioOutputManager) {
        this.context = context;
        this.audioOutputManager = audioOutputManager;
        this.appConfig = new AppConfig(context);
        this.speechRate = appConfig.getTTSSpeed();
        this.pitch = appConfig.getTTSPitch();

        textToSpeech = new TextToSpeech(context, this);
        AppLog.d(TAG, "TTSManager initialized");
        
        progressHandler = new android.os.Handler();
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
        
    /** TTS引擎初始化回调 */
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
                textToSpeech.setSpeechRate(speechRate);
                textToSpeech.setPitch(pitch);

                int audioUsage;
                if (audioOutputManager != null) {
                    audioUsage = audioOutputManager.getAudioUsage();
                } else {
                    audioUsage = appConfig.getAudioOutputUsageExternal();
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

    /** 播报文本 */
    public synchronized boolean speak(String text) {
        return speakWithUsage(text, -1);
    }
    
    /** 使用指定的 usage 类型播报文本 */
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
                    audioUsage = appConfig.getAudioOutputUsageExternal();
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

    /** 播报预设文本 */
    public boolean speakPreset(int presetId) {
        String text = getPresetText(presetId);
        return speak(text);
    }

    /** 获取预设文本 */
    private String getPresetText(int presetId) {
        switch (presetId) {
            case 1:
                return "您好，欢迎使用吉利银河 L7 音频工具";
            case 2:
                return "请注意，车辆即将启动";
            case 3:
                return "车辆已到达目的地";
            case 4:
                return "请注意周边环境，安全驾驶";
            case 5:
                return "感谢使用，祝您旅途愉快";
            default:
                return "这是一条预设播报内容";
        }
    }

    /** 设置语速 */
    public void setSpeechRate(float rate) {
        if (rate < 0.5f) {
            rate = 0.5f;
        } else if (rate > 3.0f) {
            rate = 3.0f;
        }
        this.speechRate = rate;
        appConfig.setTTSSpeed(rate);
        if (textToSpeech != null) {
            textToSpeech.setSpeechRate(rate);
        }
        AppLog.d(TAG, "Speech rate set to: " + rate);
    }

    /** 设置音调 */
    public void setPitch(float pitch) {
        if (pitch < 0.5f) {
            pitch = 0.5f;
        } else if (pitch > 2.0f) {
            pitch = 2.0f;
        }
        this.pitch = pitch;
        appConfig.setTTSPitch(pitch);
        if (textToSpeech != null) {
            textToSpeech.setPitch(pitch);
        }
        AppLog.d(TAG, "Pitch set to: " + pitch);
    }

    /** 停止播报 */
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

    /** 关闭 TTS 引擎 */
    public synchronized void shutdown() {
        if (textToSpeech != null) {
            textToSpeech.shutdown();
            textToSpeech = null;
            isInitialized = false;
            AppLog.d(TAG, "TTS shutdown");
        }
    }

    /** 检查是否初始化成功 */
    public boolean isInitialized() {
        return isInitialized;
    }

    /** 设置 TTS 进度监听器 */
    public void setProgressListener(TTSProgressListener listener) {
        this.progressListener = listener;
    }
    
    /** 检查是否正在播报 */
    public boolean isSpeaking() {
        return isSpeaking;
    }
}
