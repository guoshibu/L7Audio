package com.aug32.l7audio.domain.audio.micoutput;

import com.aug32.l7audio.utils.AppLog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 *   <li>异常自动降级：单个处理器连续异常超过阈值后自动禁用，不影响其他处理器</li>
 * </ul>
 *
 * @author L7Audio
 * @since 1.4.3
 */
public class AudioPipeline {

    private static final String TAG = "AudioPipeline";

    /** 处理器列表（按注册顺序执行） */
    private final List<AudioProcessor> processors = new ArrayList<>();
    
    /** 处理器异常计数器：key 为处理器类名，value 为连续异常次数 */
    private final Map<String, Integer> errorCounters = new HashMap<>();
    
    /** 连续异常阈值：超过此次数自动禁用处理器 */
    private static final int ERROR_THRESHOLD = 5;
    
    /** 被自动禁用的处理器列表（重置时恢复） */
    private final List<String> autoDisabledProcessors = new ArrayList<>();

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
     * 单个处理器连续异常超过 ERROR_THRESHOLD 次后自动禁用，避免影响其他处理器。
     * </p>
     *
     * @param samples 16-bit PCM 采样数组
     */
    public void process(short[] samples) {
        for (AudioProcessor processor : processors) {
            if (!processor.isEnabled()) {
                continue;
            }
            
            String processorName = processor.getClass().getSimpleName();
            
            try {
                processor.process(samples);
                errorCounters.put(processorName, 0);
            } catch (Exception e) {
                int errorCount = errorCounters.getOrDefault(processorName, 0) + 1;
                errorCounters.put(processorName, errorCount);
                
                AppLog.e(TAG, "处理器错误 (" + errorCount + "/" + ERROR_THRESHOLD + "): " + processorName, e);
                
                if (errorCount >= ERROR_THRESHOLD) {
                    processor.setEnabled(false);
                    autoDisabledProcessors.add(processorName);
                    AppLog.w(TAG, "处理器因连续错误自动禁用: " + processorName);
                }
            }
        }
    }

    /**
     * 重置所有处理器状态
     * <p>
     * 在麦克风停止后调用，清除累积的检测状态（如回声能量、啸叫计数器等）。
     * 同时恢复所有被自动禁用的处理器，重置异常计数器。
     * </p>
     */
    public void reset() {
        for (AudioProcessor processor : processors) {
            try {
                processor.reset();
            } catch (Exception e) {
                AppLog.e(TAG, "重置错误: " + processor.getClass().getSimpleName(), e);
            }
        }
        
        // 恢复所有被自动禁用的处理器
        for (String processorName : autoDisabledProcessors) {
            for (AudioProcessor processor : processors) {
                if (processorName.equals(processor.getClass().getSimpleName())) {
                    processor.setEnabled(true);
                    AppLog.i(TAG, "处理器在重置后恢复: " + processorName);
                    break;
                }
            }
        }
        autoDisabledProcessors.clear();
        errorCounters.clear();
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
