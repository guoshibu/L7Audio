package com.aug32.l7audio.domain.audio.processor;

import com.aug32.l7audio.domain.audio.AudioProcessor;
import com.aug32.l7audio.utils.AppLog;

/**
 * 音频抑制处理器（噪声门、回声消除、啸叫抑制）
 *
 * <p>职责：统一管理三种音频抑制算法，按正确顺序执行：
 * <ol>
 *   <li>噪声门（Noise Gate）：纯噪声帧直接静音，保留语音</li>
 *   <li>回声消除（Echo Cancellation）：互相关法检测回声并衰减</li>
 *   <li>啸叫抑制（Howling Suppression）：动态衰减检测到的啸叫</li>
 * </ol>
 *
 * <p>设计意图：
 * <ul>
 *   <li>三种抑制共享 reset()，一次清零所有状态，杜绝状态泄漏</li>
 *   <li>内部顺序固定，不依赖外部编排</li>
 *   <li>每个抑制算法独立开关，互不影响</li>
 * </ul>
 *
 * @author L7Audio
 * @since 1.4.3
 */
public class AudioSuppressionProcessor implements AudioProcessor {

    private static final String TAG = "AudioSuppressionProcessor";

    // ========== 开关 ==========
    /** 噪声门开关 */
    private boolean noiseGateEnabled = true;
    /** 回声消除开关 */
    private boolean echoCancelEnabled = true;
    /** 啸叫抑制开关 */
    private boolean howlingSuppressionEnabled = true;

    // ========== 噪声门状态 ==========
    /** 上一帧平均音量（用于平滑） */
    private float previousAvgVolume = 0.0f;
    /** 噪声门阈值（RMS） */
    private static final float NOISE_GATE_THRESHOLD = 0.02f;
    /** 过渡区上限（RMS），阈值~上限之间做软衰减 */
    private static final float NOISE_GATE_UPPER = 0.05f;

    // ========== 回声消除状态 ==========
    /** 平滑后的回声能量 */
    private float echoEnergy = 0.0f;
    /** 回声能量衰减因子 */
    private static final float ECHO_DECAY_FACTOR = 0.95f;
    /** 回声检测阈值 */
    private static final float ECHO_THRESHOLD = 0.05f;
    /** 回声最大衰减系数 */
    private static final float MAX_ECHO_ATTENUATION = 0.3f;

    // ========== 啸叫抑制状态 ==========
    /** 平滑后的啸叫能量 */
    private float howlingEnergy = 0.0f;
    /** 啸叫检测计数器 */
    private int howlingCounter = 0;
    /** 啸叫检测次数阈值 */
    private static final int HOWLING_DETECTION_COUNT = 3;
    /** 啸叫能量衰减因子 */
    private static final float HOWLING_DECAY_FACTOR = 0.98f;
    /** 啸叫检测阈值 */
    private static final float HOWLING_THRESHOLD = 0.1f;
    /** 啸叫最大衰减系数 */
    private static final float MAX_HOWLING_ATTENUATION = 0.4f;

    // ========== 开关设置 ==========

    public void setNoiseGateEnabled(boolean enabled) {
        this.noiseGateEnabled = enabled;
    }

    public boolean isNoiseGateEnabled() {
        return noiseGateEnabled;
    }

    public void setEchoCancelEnabled(boolean enabled) {
        this.echoCancelEnabled = enabled;
    }

    public boolean isEchoCancelEnabled() {
        return echoCancelEnabled;
    }

    public void setHowlingSuppressionEnabled(boolean enabled) {
        this.howlingSuppressionEnabled = enabled;
    }

    public boolean isHowlingSuppressionEnabled() {
        return howlingSuppressionEnabled;
    }

    // ========== AudioProcessor 接口实现 ==========

    @Override
    public void process(short[] samples) {
        if (noiseGateEnabled) {
            applyNoiseGate(samples);
        }
        if (echoCancelEnabled) {
            applyEchoCancellation(samples);
        }
        if (howlingSuppressionEnabled) {
            applyHowlingSuppression(samples);
        }
    }

    /**
     * 重置所有状态
     * <p>
     * 在每次麦克风启动时调用，清除上一轮会话的累积状态，
     * 确保每次启动行为一致。这是解决状态泄漏的关键。
     * </p>
     */
    @Override
    public void reset() {
        previousAvgVolume = 0.0f;
        echoEnergy = 0.0f;
        howlingEnergy = 0.0f;
        howlingCounter = 0;
    }

    @Override
    public boolean isEnabled() {
        // 整体启用：任一子开关打开即有效
        return noiseGateEnabled || echoCancelEnabled || howlingSuppressionEnabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.noiseGateEnabled = enabled;
        this.echoCancelEnabled = enabled;
        this.howlingSuppressionEnabled = enabled;
    }

    // ========== 噪声门 ==========

    /**
     * 噪声门处理
     * <p>
     * 根据当前帧的 RMS 值判断是否纯噪声：
     * <ul>
     *   <li>RMS < 阈值（0.02）：纯噪声，完全静音</li>
     *   <li>阈值 ≤ RMS < 上限（0.05）：过渡区，软衰减</li>
     *   <li>RMS ≥ 上限：语音，不处理</li>
     * </ul>
     * 使用平滑音量避免单帧抖动导致的断续。
     * </p>
     */
    private void applyNoiseGate(short[] samples) {
        // 计算当前帧 RMS
        float rms = calculateRms(samples);

        // 平滑音量：70% 当前帧 + 30% 历史帧，避免单帧抖动
        float smoothed = rms * 0.7f + previousAvgVolume * 0.3f;
        previousAvgVolume = smoothed;

        if (smoothed < NOISE_GATE_THRESHOLD) {
            // 纯噪声，完全静音
            for (int i = 0; i < samples.length; i++) {
                samples[i] = 0;
            }
        } else if (smoothed < NOISE_GATE_UPPER) {
            // 过渡区，软衰减
            float ratio = (smoothed - NOISE_GATE_THRESHOLD) / (NOISE_GATE_UPPER - NOISE_GATE_THRESHOLD);
            for (int i = 0; i < samples.length; i++) {
                samples[i] = (short) (samples[i] * ratio);
            }
        }
        // RMS >= 上限：语音，不处理
    }

    /** 计算 RMS 值 */
    private float calculateRms(short[] samples) {
        double sum = 0.0;
        for (short sample : samples) {
            float normalized = sample / 32768.0f;
            sum += normalized * normalized;
        }
        return (float) Math.sqrt(sum / samples.length);
    }

    // ========== 回声消除 ==========

    /**
     * 回声消除（互相关法）
     * <p>
     * 通过比较当前帧能量与历史平滑能量的比值检测回声：
     * <ul>
     *   <li>能量比在 0.5~2.0 之间，且样本能量 > 阈值 → 疑似回声</li>
     *   <li>能量比越接近 1.0，衰减越大</li>
     * </ul>
     * 车内场景麦克风和扬声器距离固定，回声延迟相对稳定，
     * 互相关法比自适应滤波器更适合这个场景。
     * </p>
     */
    private void applyEchoCancellation(short[] samples) {
        // 计算当前帧能量
        float frameEnergy = calculateEnergy(samples);

        // 平滑回声能量
        echoEnergy = ECHO_DECAY_FACTOR * echoEnergy + (1.0f - ECHO_DECAY_FACTOR) * frameEnergy;

        // 能量比：1.0 表示与历史完全一致，典型回声特征
        float echoRatio = echoEnergy > 0.0001f ? frameEnergy / echoEnergy : 1.0f;

        // 能量比在合理范围内才判断为回声
        if (echoRatio <= 0.5f || echoRatio >= 2.0f) {
            return;
        }

        // 能量比越接近 1.0，衰减越大
        float ratioFactor = 1.0f - Math.abs(echoRatio - 1.0f);
        float attenuation = 1.0f - ratioFactor * (1.0f - MAX_ECHO_ATTENUATION);
        // 衰减范围：0.3 ~ 1.0

        for (int i = 0; i < samples.length; i++) {
            float normalized = Math.abs(samples[i]) / 32768.0f;
            if (normalized > ECHO_THRESHOLD) {
                samples[i] = (short) (samples[i] * attenuation);
            }
        }
    }

    // ========== 啸叫抑制 ==========

    /**
     * 啸叫抑制（动态衰减）
     * <p>
     * 检测能量持续高于历史平均值 1.8 倍以上的帧，连续 3 帧判定为啸叫。
     * 衰减强度根据啸叫强度动态调整：啸叫越强，衰减越大。
     * 啸叫是声反馈回路产生的高能量窄带信号，与普通语音的特征不同。
     * </p>
     */
    private void applyHowlingSuppression(short[] samples) {
        float frameEnergy = calculateEnergy(samples);

        // 平滑啸叫能量
        howlingEnergy = HOWLING_DECAY_FACTOR * howlingEnergy + (1.0f - HOWLING_DECAY_FACTOR) * frameEnergy;

        // 检测：当前能量 > 历史 1.8 倍 且 > 阈值
        boolean isHowling = false;
        if (frameEnergy > howlingEnergy * 1.8f && frameEnergy > HOWLING_THRESHOLD) {
            howlingCounter++;
            if (howlingCounter >= HOWLING_DETECTION_COUNT) {
                isHowling = true;
            }
        } else {
            howlingCounter = Math.max(0, howlingCounter - 1);
        }

        if (isHowling) {
            AppLog.d(TAG, "Howling detected! Energy: " + frameEnergy + ", Avg: " + howlingEnergy);

            // 动态衰减：啸叫强度越高，衰减越大
            float intensity = Math.min((frameEnergy - howlingEnergy) / howlingEnergy, 2.0f);
            float attenuation = MAX_HOWLING_ATTENUATION + (1.0f - MAX_HOWLING_ATTENUATION) * (1.0f - intensity * 0.3f);
            attenuation = Math.max(MAX_HOWLING_ATTENUATION, attenuation);

            for (int i = 0; i < samples.length; i++) {
                samples[i] = (short) (samples[i] * attenuation);
            }
        }
    }

    // ========== 工具方法 ==========

    /** 计算帧能量 */
    private float calculateEnergy(short[] samples) {
        float energy = 0.0f;
        for (short sample : samples) {
            float normalized = sample / 32768.0f;
            energy += normalized * normalized;
        }
        return energy / samples.length;
    }
}