package com.aug32.l7audio.domain.audio;

import com.aug32.l7audio.utils.AppLog;

import java.util.ArrayList;
import java.util.List;

/**
 * 音频处理管线
 *
 * <p>职责：按注册顺序串联执行多个 {@link AudioProcessor}，
 * 支持运行时启用/禁用处理器，以及统一重置所有处理器状态。
 *
 * <p>设计意图：
 * <ul>
 *   <li>解耦 MicrophoneManager 与具体处理算法</li>
 *   <li>处理顺序由 addProcessor 调用顺序决定，一目了然</li>
 *   <li>单线程执行（录制线程），无需同步开销</li>
 * </ul>
 *
 * @author L7Audio
 * @since 1.4.3
 */
public class AudioPipeline {

    private static final String TAG = "AudioPipeline";

    /** 处理器列表（按注册顺序执行） */
    private final List<AudioProcessor> processors = new ArrayList<>();

    /**
     * 注册处理器
     * <p>
     * 处理器按注册顺序执行，先注册的先处理。
     * 建议顺序：增益 → 限幅 → 噪声门 → 回声消除 → 啸叫抑制。
     * </p>
     *
     * @param processor 音频处理器
     */
    public void addProcessor(AudioProcessor processor) {
        if (processor != null) {
            processors.add(processor);
        }
    }

    /**
     * 按注册顺序执行所有启用的处理器
     * <p>
     * 跳过禁用的处理器，仅执行启用的处理器。
     * 每个处理器直接原地修改 samples 数组。
     * </p>
     *
     * @param samples 16-bit PCM 采样数组
     */
    public void process(short[] samples) {
        for (AudioProcessor processor : processors) {
            if (processor.isEnabled()) {
                try {
                    processor.process(samples);
                } catch (Exception e) {
                    AppLog.e(TAG, "Processor error: " + processor.getClass().getSimpleName(), e);
                }
            }
        }
    }

    /**
     * 重置所有处理器状态
     * <p>
     * 在麦克风停止后调用，清除累积的检测状态（如回声能量、啸叫计数器等）。
     * </p>
     */
    public void reset() {
        for (AudioProcessor processor : processors) {
            try {
                processor.reset();
            } catch (Exception e) {
                AppLog.e(TAG, "Reset error: " + processor.getClass().getSimpleName(), e);
            }
        }
    }

    /**
     * 获取指定类型的处理器
     *
     * @param type 处理器类型
     * @param <T>  处理器类型参数
     * @return 匹配的处理器，未找到则返回 null
     */
    @SuppressWarnings("unchecked")
    public <T extends AudioProcessor> T getProcessor(Class<T> type) {
        for (AudioProcessor processor : processors) {
            if (type.isInstance(processor)) {
                return (T) processor;
            }
        }
        return null;
    }
}