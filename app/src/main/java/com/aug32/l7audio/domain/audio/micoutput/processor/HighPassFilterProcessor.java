package com.aug32.l7audio.domain.audio.micoutput.processor;

import com.aug32.l7audio.domain.audio.micoutput.AudioProcessor;

/**
 * 高通滤波器（HPF）处理器
 *
 * <p>职责：
 * <ul>
 *   <li>二阶 Butterworth IIR 高通，抑制低频胎噪和车身共振</li>
 *   <li>与低通滤波器（{@link LowPassFilterProcessor}）配合组成人声带通（约 100~4000Hz）</li>
 * </ul>
 *
 * <p>线程安全：截止频率可在录制过程中由 UI 线程运行时修改（{@link #setCutoffFrequency}），
 * 而 {@link #process} 在录制线程读取系数与历史状态。系数字段用 volatile 保证跨线程可见性；
 * 历史状态（x1/x2/y1/y2）与 enabled 存在"多读多写"依赖关系，volatile 无法保证复合操作原子性，
 * 因此 process/setCutoffFrequency/reset/setEnabled 统一用 synchronized(this) 互斥——
 * 临界区仅几十次浮点运算，对录制线程的阻塞可忽略。
 * 换系数/重新开启时立即 {@link #reset()} 清空滤波器历史状态，
 * 避免新旧系数与残留状态不匹配导致的爆音。
 */
public class HighPassFilterProcessor implements AudioProcessor {

    /** 采样率，与麦克风采集保持一致 */
    private static final int SAMPLE_RATE = 48000;
    /** 默认截止频率（Hz），抑制胎噪和车身共振 */
    private static final float DEFAULT_CUTOFF_FREQ = 100.0f;
    /** 截止频率下限（Hz） */
    private static final float MIN_CUTOFF = 50.0f;
    /** 截止频率上限（Hz） */
    private static final float MAX_CUTOFF = 2000.0f;

    // 双二阶差分方程系数（运行时可重算，volatile 保证录制线程可见）
    private volatile float b0;
    private volatile float b1;
    private volatile float b2;
    private volatile float a1;
    private volatile float a2;

    // 滤波器历史状态与开关（process 在录制线程读写，setter 在 UI 线程写，
    // 统一由 synchronized(this) 互斥，故无需 volatile）
    private float x1 = 0.0f;
    private float x2 = 0.0f;
    private float y1 = 0.0f;
    private float y2 = 0.0f;
    private boolean enabled = true;

    /** 无参构造，使用默认截止频率 100Hz */
    public HighPassFilterProcessor() {
        this(DEFAULT_CUTOFF_FREQ);
    }

    /**
     * @param cutoffHz 初始截止频率（Hz），自动 clamp 到 [50, 2000]
     */
    public HighPassFilterProcessor(float cutoffHz) {
        recalcCoefficients(cutoffHz);
    }

    /**
     * 运行时修改截止频率并即时生效。
     *
     * <p>重算系数后立即清空滤波器历史状态，防止换系数瞬间产生爆音。
     * synchronized 与录制线程的 {@link #process} 互斥，避免改到一半被 process 读到"新系数+旧状态"的混合态。
     *
     * @param cutoffHz 新截止频率（Hz），自动 clamp 到 [50, 2000]
     */
    public synchronized void setCutoffFrequency(float cutoffHz) {
        recalcCoefficients(cutoffHz);
        reset();
    }

    /**
     * 根据截止频率重算二阶高通系数（Q=0.707 Butterworth）。
     *
     * @param cutoffHz 截止频率（Hz），自动 clamp 到合法范围
     */
    private void recalcCoefficients(float cutoffHz) {
        if (cutoffHz < MIN_CUTOFF) cutoffHz = MIN_CUTOFF;
        else if (cutoffHz > MAX_CUTOFF) cutoffHz = MAX_CUTOFF;

        float w0 = 2.0f * (float) Math.PI * cutoffHz / SAMPLE_RATE;
        float cosw0 = (float) Math.cos(w0);
        float sinw0 = (float) Math.sin(w0);
        float alpha = sinw0 * (float) Math.sqrt(2.0) / 2.0f;

        // 高通系数：分子用 (1 + cosw0)，与低通的 (1 - cosw0) 相反
        float nb0 = (1.0f + cosw0) / 2.0f;
        float nb1 = -(1.0f + cosw0);
        float nb2 = (1.0f + cosw0) / 2.0f;
        float na0 = 1.0f + alpha;
        float na1 = -2.0f * cosw0;
        float na2 = 1.0f - alpha;

        b0 = nb0 / na0;
        b1 = nb1 / na0;
        b2 = nb2 / na0;
        a1 = na1 / na0;
        a2 = na2 / na0;
    }

    @Override
    public synchronized void process(short[] samples) {
        if (!enabled) return;

        for (int i = 0; i < samples.length; i++) {
            float x = samples[i] / 32768.0f;
            float y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1;
            x1 = x;
            y2 = y1;
            y1 = y;
            if (y > 1.0f) y = 1.0f;
            else if (y < -1.0f) y = -1.0f;
            samples[i] = (short) (y * 32767.0f);
        }
    }

    @Override
    public synchronized void reset() {
        x1 = 0.0f;
        x2 = 0.0f;
        y1 = 0.0f;
        y2 = 0.0f;
    }

    @Override
    public synchronized boolean isEnabled() {
        return enabled;
    }

    @Override
    public synchronized void setEnabled(boolean enabled) {
        // 重新开启时清空历史状态，避免残留的旧样本造成瞬时爆音/咔哒声
        // （与 LowPassFilterProcessor 行为对齐）
        if (enabled && !this.enabled) {
            reset();
        }
        this.enabled = enabled;
    }
}
