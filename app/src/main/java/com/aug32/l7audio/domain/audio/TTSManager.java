package com.aug32.l7audio.domain.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.Locale;

import com.aug32.l7audio.data.local.AppConfig;
import com.aug32.l7audio.utils.AppLog;

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

    /**
     * TTS播报进度监听器接口
     * <p>
     * 用于监听TTS播报的开始、完成、错误和进度更新事件。
     * </p>
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

    // 上下文对象
    private final Context context;
    // 音频输出管理器
    private final AudioOutputManager audioOutputManager;
    // 应用配置
    private final AppConfig appConfig;

    // TTS引擎实例
    private TextToSpeech textToSpeech;
    // TTS引擎是否已初始化
    private boolean isInitialized = false;
    // 语速（默认1.0f）
    private float speechRate = 1.0f;
    // 音调（默认1.0f）
    private float pitch = 1.0f;
    // 进度监听器
    private TTSProgressListener progressListener;
    // 进度更新Handler
    private android.os.Handler progressHandler;
    // 进度更新Runnable
    private Runnable progressRunnable;
    // 当前播报进度（0-100）
    private int currentProgress = 0;
    // 是否正在播报
    private boolean isSpeaking = false;
    
    /**
     * 构造函数
     * <p>
     * 初始化TTS引擎，从配置中读取语速和音调设置，并启动进度更新定时器。
     * </p>
     *
     * @param context             上下文对象
     * @param audioOutputManager  音频输出管理器，用于获取音频输出模式
     */
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
                // 模拟进度更新：每200ms增加5%，用于UI显示进度
                if (isSpeaking && progressListener != null) {
                    currentProgress += 5;
                    // 进度上限保护，避免超过100%
                    if (currentProgress > 100) {
                        currentProgress = 100;
                    }
                    progressListener.onTTSProgress(currentProgress);
                    // 未达到100%时继续定时更新
                    if (currentProgress < 100) {
                        progressHandler.postDelayed(this, 200);
                    }
                }
            }
        };
    }
        
    /**
     * TTS引擎初始化回调
     * <p>
     * 初始化成功后，按优先级依次尝试设置语言：简体中文 → 中文(中国) → 繁体中文 → 英语。
     * 多级回退机制确保在各种设备上都能找到可用的语音。
     * 设置成功后配置语速、音调和音频属性，并注册播报进度监听器。
     * </p>
     *
     * @param status 初始化状态，TextToSpeech.SUCCESS 表示成功，其他值表示失败
     */
    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            AppLog.d(TAG, "TTS engine initialized successfully");
            
            boolean languageSet = false;
            
            // 第一优先级：简体中文
            if (!languageSet) {
                int result = textToSpeech.setLanguage(Locale.SIMPLIFIED_CHINESE);
                if (result == TextToSpeech.LANG_AVAILABLE) {
                    AppLog.d(TAG, "Simplified Chinese language set successfully");
                    languageSet = true;
                } else {
                    AppLog.w(TAG, "Simplified Chinese not available, result: " + result);
                }
            }
            
            // 第二优先级：中文(中国)
            if (!languageSet) {
                int result = textToSpeech.setLanguage(Locale.CHINA);
                if (result == TextToSpeech.LANG_AVAILABLE) {
                    AppLog.d(TAG, "Chinese (China) language set successfully");
                    languageSet = true;
                } else {
                    AppLog.w(TAG, "Chinese (China) not available, result: " + result);
                }
            }
            
            // 第三优先级：繁体中文
            if (!languageSet) {
                int result = textToSpeech.setLanguage(Locale.TRADITIONAL_CHINESE);
                if (result == TextToSpeech.LANG_AVAILABLE) {
                    AppLog.d(TAG, "Traditional Chinese language set successfully");
                    languageSet = true;
                } else {
                    AppLog.w(TAG, "Traditional Chinese not available, result: " + result);
                }
            }
            
            // 第四优先级（兜底）：英语
            if (!languageSet) {
                int result = textToSpeech.setLanguage(Locale.ENGLISH);
                if (result == TextToSpeech.LANG_AVAILABLE) {
                    AppLog.d(TAG, "English language set successfully as fallback");
                    languageSet = true;
                } else {
                    AppLog.w(TAG, "English not available, result: " + result);
                }
            }

            // 语言设置成功后，配置TTS参数
            if (languageSet) {
                textToSpeech.setSpeechRate(speechRate);
                textToSpeech.setPitch(pitch);

                // 获取音频输出模式：优先从AudioOutputManager获取，兜底用配置中的车外模式
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

                // 注册播报进度监听器，用于回调UI更新
                textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {
                        AppLog.d(TAG, "TTS started: " + utteranceId);
                        isSpeaking = true;
                        currentProgress = 0;
                        if (progressListener != null) {
                            progressListener.onTTSStart();
                            // 启动模拟进度更新
                            progressHandler.post(progressRunnable);
                        }
                    }

                    @Override
                    public void onDone(String utteranceId) {
                        AppLog.d(TAG, "TTS done: " + utteranceId);
                        isSpeaking = false;
                        currentProgress = 100;
                        if (progressListener != null) {
                            // 确保进度到达100%再回调完成
                            progressListener.onTTSProgress(100);
                            progressListener.onTTSDone();
                        }
                        // 移除进度更新回调，避免内存泄漏
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
                        // 移除进度更新回调，避免内存泄漏
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
                        // 移除进度更新回调，避免内存泄漏
                        progressHandler.removeCallbacks(progressRunnable);
                    }
                });

                isInitialized = true;
                AppLog.d(TAG, "TTS initialized successfully with language support");
            } else {
                // 所有语言都不可用，标记初始化失败
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
     * <p>
     * 使用当前默认的音频输出模式播报文本。
     * 若当前正在播报，则不会重复播报。
     * </p>
     *
     * @param text 要播报的文本内容
     * @return true 表示播报成功启动，false 表示播报失败
     */
    public synchronized boolean speak(String text) {
        return speakWithUsage(text, -1);
    }
    
    /**
     * 使用指定的 usage 类型播报文本
     * <p>
     * 支持自定义 AudioAttributes.Usage 类型，用于特殊场景的音频输出。
     * 播报前会先停止当前正在进行的播报。
     * </p>
     *
     * @param text  要播报的文本内容
     * @param usage AudioAttributes.Usage 值，传 -1 表示使用当前输出模式的默认值
     * @return true 表示播报成功启动，false 表示播报失败
     */
    public synchronized boolean speakWithUsage(String text, int usage) {
        // 未初始化直接返回失败
        if (!isInitialized || textToSpeech == null) {
            AppLog.e(TAG, "TTS not initialized");
            return false;
        }

        // 空文本不播报
        if (text == null || text.isEmpty()) {
            AppLog.e(TAG, "Empty text");
            return false;
        }

        try {
            // 确定音频输出模式：-1 表示使用默认模式
            int audioUsage;
            if (usage == -1) {
                // 优先从AudioOutputManager获取，兜底用配置中的车外模式
                if (audioOutputManager != null) {
                    audioUsage = audioOutputManager.getAudioUsage();
                } else {
                    audioUsage = appConfig.getAudioOutputUsageExternal();
                }
            } else {
                audioUsage = usage;
            }
            AppLog.d(TAG, "Using audio usage type: " + audioUsage);
            
            // 构建音频属性，设置FLAG_AUDIBILITY_ENFORCED确保声音可听
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(audioUsage)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                    .build();
            textToSpeech.setAudioAttributes(audioAttributes);
            AppLog.d(TAG, "Updated TTS AudioAttributes with usage: " + audioUsage);
            
            // 先停止当前播报，确保新的播报可以立即开始
            textToSpeech.stop();
            
            Bundle params = new Bundle();
            String utteranceId = "utterance_" + System.currentTimeMillis();
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
            
            // 双重检查：确认TTS不在播报状态后再启动
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
            // 捕获所有异常，避免TTS崩溃导致应用崩溃
            AppLog.e(TAG, "Failed to speak text", e);
            return false;
        }
    }

    /**
     * 播报预设文本
     * <p>
     * 根据预设ID播报预定义的文本内容。
     * </p>
     *
     * @param presetId 预设文本ID，取值范围 1-5，其他值使用默认文本
     * @return true 表示播报成功启动，false 表示播报失败
     */
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

    /**
     * 设置语速
     * <p>
     * 语速范围限制在 0.5f - 3.0f 之间，超出范围会被自动截断。
     * 设置后会自动保存到配置中，并同步到TTS引擎。
     * </p>
     *
     * @param rate 语速值，范围 0.5f - 3.0f，1.0f 为正常语速
     */
    public void setSpeechRate(float rate) {
        // 边界处理：限制语速在有效范围内
        if (rate < 0.5f) {
            rate = 0.5f;
        } else if (rate > 3.0f) {
            rate = 3.0f;
        }
        this.speechRate = rate;
        appConfig.setTTSSpeed(rate);
        // TTS引擎已初始化则同步更新
        if (textToSpeech != null) {
            textToSpeech.setSpeechRate(rate);
        }
        AppLog.d(TAG, "Speech rate set to: " + rate);
    }

    /**
     * 设置音调
     * <p>
     * 音调范围限制在 0.5f - 2.0f 之间，超出范围会被自动截断。
     * 设置后会自动保存到配置中，并同步到TTS引擎。
     * </p>
     *
     * @param pitch 音调值，范围 0.5f - 2.0f，1.0f 为正常音调
     */
    public void setPitch(float pitch) {
        // 边界处理：限制音调在有效范围内
        if (pitch < 0.5f) {
            pitch = 0.5f;
        } else if (pitch > 2.0f) {
            pitch = 2.0f;
        }
        this.pitch = pitch;
        appConfig.setTTSPitch(pitch);
        // TTS引擎已初始化则同步更新
        if (textToSpeech != null) {
            textToSpeech.setPitch(pitch);
        }
        AppLog.d(TAG, "Pitch set to: " + pitch);
    }

    /**
     * 停止播报
     * <p>
     * 立即停止当前正在进行的TTS播报，并重置进度状态。
     * </p>
     */
    public synchronized void stop() {
        if (textToSpeech != null && isInitialized) {
            textToSpeech.stop();
            isSpeaking = false;
            currentProgress = 0;
            // 通知监听器进度重置
            if (progressListener != null) {
                progressListener.onTTSProgress(0);
            }
            // 移除进度更新回调，避免内存泄漏
            progressHandler.removeCallbacks(progressRunnable);
            AppLog.d(TAG, "TTS stopped");
        }
    }

    /**
     * 关闭 TTS 引擎
     * <p>
     * 释放TTS引擎资源，调用后需重新创建才能使用。
     * </p>
     */
    public synchronized void shutdown() {
        if (textToSpeech != null) {
            textToSpeech.shutdown();
            textToSpeech = null;
            isInitialized = false;
            AppLog.d(TAG, "TTS shutdown");
        }
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
