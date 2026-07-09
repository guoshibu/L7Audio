package com.aug32.l7audio.domain.audio.micoutput;

/**
 * 音频处理器接口
 *
 * <p>职责：定义音频处理器的统一契约，每个实现类负责一种音频处理算法。
 * 由 {@link AudioPipeline} 按注册顺序串联执行。
 *
 * <p>设计意图：
 * <ul>
 *   <li>单一职责：每个处理器只做一种音频处理</li>
 *   <li>可替换：算法改进只需替换实现类，不影响其他模块</li>
 *   <li>可测试：每个处理器可独立单元测试</li>
 *   <li>线程安全：{@link #process} 在录制线程中单线程调用，无需加锁</li>
 * </ul>
 *
 * @author L7Audio
 * @since 1.4.3
 */
public interface AudioProcessor {

    /**
     * 处理一帧音频数据
     * <p>
     * 在当前录制线程中调用，原地修改 samples 数组。
     * 实现应避免创建对象，以减少 GC 压力。
     * </p>
     *
     * @param samples 16-bit PCM 采样数组，处理结果原地写入
     */
    void process(short[] samples);

    /**
     * 重置处理器状态
     * <p>
     * 在每次 {@link MicrophoneManager#start()} 时调用，
     * 清除上一轮会话的累积状态，确保每次启动行为一致。
     * </p>
     */
    void reset();

    /**
     * 是否启用
     *
     * @return true 表示启用，false 表示禁用
     */
    boolean isEnabled();

    /**
     * 设置启用状态
     *
     * @param enabled true 启用，false 禁用
     */
    void setEnabled(boolean enabled);
}
