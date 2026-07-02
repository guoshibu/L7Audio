package com.aug32.l7audio.domain.audio.processor;

import com.aug32.l7audio.domain.audio.AudioProcessor;

/**
 * 增益放大 + 软限幅处理器
 *
 * <p>职责：
 * <ul>
 *   <li>根据放大级别对音频采样进行增益放大</li>
 *   <li>使用 tanh 软限幅防止溢出，避免削波失真</li>
 * </ul>
 *
 * <p>处理顺序：在管线中最先执行，让后续处理器有更大的动态范围。
 *
 * <p>软限幅 vs 硬限幅（clamp）：
 * <ul>
 *   <li>硬限幅：超过范围直接截断 → 产生谐波失真</li>
 *   <li>软限幅：使用 tanh 平滑压缩 → 保留波形，声音更自然</li>
 * </ul>
 *
 * @author L7Audio
 * @since 1.4.3
 */
public class GainLimiterProcessor implements AudioProcessor {

    /** 增益倍数 */
    private float amplificationFactor = 1.0f;
    /** 是否启用 */
    private boolean enabled = true;

    /**
     * 设置增益倍数
     * <p>
     * 由 MicrophoneManager 根据放大级别和最大放大倍率计算。
     * </p>
     *
     * @param factor 增益倍数，1.0 表示不放大
     */
    public void setAmplificationFactor(float factor) {
        this.amplificationFactor = factor;
    }

    @Override
    public void process(short[] samples) {
        for (int i = 0; i < samples.length; i++) {
            // 归一化到 [-1, 1]
            float normalized = samples[i] / 32768.0f;
            // 增益放大
            normalized *= amplificationFactor;
            // 软限幅：仅在溢出时使用 tanh 压缩，正常范围直通
            // 为什么不在正常范围用 tanh：tanh(0.5)≈0.46, tanh(1.0)≈0.76，
            // 即使增益为1.0x也会把信号压到76%，导致声音极小不可用
            if (normalized > 1.0f || normalized < -1.0f) {
                normalized = (float) Math.tanh(normalized);
            }
            // 归一化回 short 范围
            samples[i] = (short) (normalized * 32767.0f);
        }
    }

    /**
     * 重置处理器状态
     * <p>
     * 增益处理器无状态，无需重置。
     * </p>
     */
    @Override
    public void reset() {
        // 无状态，无需重置
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}