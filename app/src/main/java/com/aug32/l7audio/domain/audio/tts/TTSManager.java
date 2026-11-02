package com.aug32.l7audio.domain.audio.tts;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

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
     * <p>用于监听TTS播报的开始、完成和错误事件。
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
    }

    /** 上下文对象 */
    private final Context context;
    /** 音频输出管理器 */
    private final AudioOutputManager audioOutputManager;

    /** TTS引擎实例 */
    private TextToSpeech textToSpeech;
    /** TTS引擎是否已初始化（onInit 在引擎绑定线程写，业务线程读，需跨线程可见） */
    private volatile boolean isInitialized = false;
    /**
     * 进度监听器集合（多槽位）。
     * <p>
     * 修复（监听器互踩）：原实现为单槽位 setProgressListener，TTSFragment 与
     * FloatingWindowService 互相覆盖——Fragment 打开时覆盖 Service 的监听器，
     * 销毁时又一并置 null。后果：悬浮窗 TTS 播完收不到 onTTSDone 回调，
     * 瞬态焦点永不释放 → 音乐永久暂停。
     * 改为注册表模式后，各方独立注册/注销，互不影响。
     * CopyOnWriteArrayList：读远多于写（每次播报回调遍历，注册/注销极少），
     * 且回调线程与注册线程不同，写时复制保证遍历安全。
     */
    private final CopyOnWriteArrayList<TTSProgressListener> progressListeners = new CopyOnWriteArrayList<>();
    /** 是否正在播报（utterance 回调在引擎 Binder 线程写，业务线程读，需跨线程可见） */
    private volatile boolean isSpeaking = false;

    /**
     * 构造函数
     *
     * <p>初始化TTS引擎，使用默认语速和音调（1.0f）。
     *
     * @param context            上下文对象
     * @param audioOutputManager 音频输出管理器，用于获取音频输出模式
     */
    public TTSManager(Context context, AudioOutputManager audioOutputManager) {
        this.context = context;
        this.audioOutputManager = audioOutputManager;

        textToSpeech = new TextToSpeech(context, this);
        AppLog.d(TAG, "TTSManager initialized");
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

            // 修复（语言误判）：判定用 >= LANG_AVAILABLE 而非 ==。
            // setLanguage 返回值：LANG_AVAILABLE(0)=完全可用、LANG_COUNTRY_AVAILABLE(1)=
            // 国家变体可用、LANG_COUNTRY_VAR_AVAILABLE(2)=地区变体可用，三者均为成功；
            // 负值（LANG_MISSING_DATA=-1 / LANG_NOT_SUPPORTED=-2）才是失败。
            // 原实现 == LANG_AVAILABLE 会拒绝返回 1/2 的引擎（如部分车载中文引擎），
            // 逐级回退后错误地落到英文
            if (!languageSet) {
                int result = textToSpeech.setLanguage(Locale.SIMPLIFIED_CHINESE);
                if (result >= TextToSpeech.LANG_AVAILABLE) {
                    AppLog.d(TAG, "Simplified Chinese language set successfully (level=" + result + ")");
                    languageSet = true;
                } else {
                    AppLog.w(TAG, "Simplified Chinese not available, result: " + result);
                }
            }

            if (!languageSet) {
                int result = textToSpeech.setLanguage(Locale.CHINA);
                if (result >= TextToSpeech.LANG_AVAILABLE) {
                    AppLog.d(TAG, "Chinese (China) language set successfully (level=" + result + ")");
                    languageSet = true;
                } else {
                    AppLog.w(TAG, "Chinese (China) not available, result: " + result);
                }
            }

            if (!languageSet) {
                int result = textToSpeech.setLanguage(Locale.TRADITIONAL_CHINESE);
                if (result >= TextToSpeech.LANG_AVAILABLE) {
                    AppLog.d(TAG, "Traditional Chinese language set successfully (level=" + result + ")");
                    languageSet = true;
                } else {
                    AppLog.w(TAG, "Traditional Chinese not available, result: " + result);
                }
            }

            if (!languageSet) {
                int result = textToSpeech.setLanguage(Locale.ENGLISH);
                if (result >= TextToSpeech.LANG_AVAILABLE) {
                    AppLog.d(TAG, "English language set successfully as fallback (level=" + result + ")");
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
                        notifyTTSStart();
                    }

                    @Override
                    public void onDone(String utteranceId) {
                        AppLog.d(TAG, "TTS done: " + utteranceId);
                        isSpeaking = false;
                        notifyTTSDone();
                    }

                    // onError(String) 仍是抽象方法（必须实现），但其自身被标记为废弃；
                    // 实现委托给新版本重载，避免逻辑重复，并在本地抑制废弃警告
                    @Override
                    @SuppressWarnings("deprecation")
                    public void onError(String utteranceId) {
                        onError(utteranceId, TextToSpeech.ERROR);
                    }

                    @Override
                    public void onError(String utteranceId, int errorCode) {
                        AppLog.e(TAG, "TTS error: " + utteranceId + ", code: " + errorCode);
                        isSpeaking = false;
                        notifyTTSError();
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

            // 修复（竞态）：不再用 textToSpeech.isSpeaking() 做播报前置判断。
            // stop() 是异步生效的，刚 stop 完引擎内部状态可能仍报 speaking，
            // 原实现此时直接返回 false → 用户看到"播放失败"提示但引擎实际正常。
            // QUEUE_FLUSH 本身就是"清空队列、打断当前、立即播报"的语义，直接播即可
            int result = textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId);
            boolean success = (result == TextToSpeech.SUCCESS);
            AppLog.d(TAG, "Speak text: " + text + ", result: " + success + ", AudioUsage: " + audioUsage);
            return success;
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to speak text", e);
            return false;
        }
    }

    /**
     * 停止播报
     *
     * <p>立即停止当前正在进行的TTS播报。
     */
    public synchronized void stop() {
        if (textToSpeech != null && isInitialized) {
            boolean wasSpeaking = isSpeaking;
            textToSpeech.stop();
            isSpeaking = false;
            // 修复（stop 无回调）：部分 TTS 引擎对被 stop() 中断的 utterance
            // 不会补发 onDone/onError，监听方（悬浮窗）苦等 onTTSDone 释放瞬态
            // 焦点将永远等不到 → 音乐永久暂停。这里主动补发一次错误回调
            // （语义=播报被取消）；各监听方对已释放状态均幂等，引擎若再补发
            // 一次原生回调也不会产生副作用
            if (wasSpeaking) {
                notifyTTSError();
            }
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
        boolean wasSpeaking = isSpeaking;
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
        isInitialized = false;
        isSpeaking = false;
        // 播报过程中销毁引擎必须补发一次错误回调（与 stop() 同理）：
        // 引擎不会对被 shutdown 的 utterance 回调 onDone/onError，监听方（悬浮窗）
        // 永远等不到结束事件 → 瞬态焦点不释放、音乐永久暂停
        if (wasSpeaking) {
            notifyTTSError();
        }
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
     * 注册 TTS 进度监听器
     *
     * <p>多槽位注册表：各使用方（TTS 页面、悬浮窗服务）独立注册/注销，互不覆盖。
     * 同一实例重复注册会被忽略。
     *
     * @param listener 进度监听器，非 null
     */
    public void addProgressListener(TTSProgressListener listener) {
        if (listener != null) {
            progressListeners.addIfAbsent(listener);
        }
    }

    /**
     * 注销 TTS 进度监听器
     *
     * @param listener 之前注册的监听器实例
     */
    public void removeProgressListener(TTSProgressListener listener) {
        if (listener != null) {
            progressListeners.remove(listener);
        }
    }

    // 以下三个分发方法统一遍历监听器集合；单个监听器抛异常不影响其余监听器
    private void notifyTTSStart() {
        for (TTSProgressListener l : progressListeners) {
            try {
                l.onTTSStart();
            } catch (Exception e) {
                AppLog.e(TAG, "TTSProgressListener.onTTSStart error", e);
            }
        }
    }

    private void notifyTTSDone() {
        for (TTSProgressListener l : progressListeners) {
            try {
                l.onTTSDone();
            } catch (Exception e) {
                AppLog.e(TAG, "TTSProgressListener.onTTSDone error", e);
            }
        }
    }

    private void notifyTTSError() {
        for (TTSProgressListener l : progressListeners) {
            try {
                l.onTTSError();
            } catch (Exception e) {
                AppLog.e(TAG, "TTSProgressListener.onTTSError error", e);
            }
        }
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
