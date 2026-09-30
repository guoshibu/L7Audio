package com.aug32.l7audio.domain.audio.micoutput.processor;

import com.aug32.l7audio.domain.audio.micoutput.AudioProcessor;

/**
 * 低通滤波器（LPF）处理器
 *
 * <p>职责：
 * <ul>
 *   <li>二阶 Butterworth IIR 低通，滤除高频段能量</li>
 *   <li>与高通滤波器（{@link HighPassFilterProcessor}）配合组成人声带通（约 100~4000Hz），
 *       砍掉高频啸叫、嘶声与齿音，让车外喊话更清晰、更不易反馈啸叫</li>
 * </ul>
 *
 * <p>与高通的关系：低通与高通差分方程结构完全相同，仅系数公式不同——
 * 高通用 {@code (1+cosw0)/2}，低通用 {@code (1-cosw0)/2}。
 *
 * <p>线程安全：截止频率可在录制过程中由 UI 线程运行时修改（{@link #setCutoffFrequency}），
 * 而 {@link #process} 在录制线程读取系数。系数字段用 volatile 保证跨线程可见性，
 * 单个 float 读写本身原子；换系数时立即 {@link #reset()} 清空滤波器历史状态，
 * 避免新旧系数与残留状态不匹配导致的爆音。
 */
public class LowPassFilterProcessor implements AudioProcessor {

    /** 采样率，与麦克风采集保持一致 */
    private static final int SAMPLE_RATE = 48000;
    /** 默认截止频率（Hz），砍掉 4kHz 以上的高频啸叫/嘶声 */
    private static final float DEFAULT_CUTOFF_FREQ = 4000.0f;
    /** 截止频率下限（Hz） */
    private static final float MIN_CUTOFF = 200.0f;
    /** 截止频率上限（Hz），不超过奈奎斯特一半留足余量 */
    private static final float MAX_CUTOFF = 8000.0f;

    // 双二阶差分方程系数（运行时可重算，volatile 保证录制线程可见）
    private volatile float b0;
    private volatile float b1;
    private volatile float b2;
    private volatile float a1;
    private volatile float a2;

    // 滤波器历史状态（仅录制线程访问）
    private float x1 = 0.0f;
    private float x2 = 0.0f;
    private float y1 = 0.0f;
    private float y2 = 0.0f;
    private boolean enabled = true;

    /** 无参构造，使用默认截止频率 4000Hz */
    public LowPassFilterProcessor() {
        this(DEFAULT_CUTOFF_FREQ);
    }

    /**
     * @param cutoffHz 初始截止频率（Hz），自动 clamp 到 [200, 8000]
     */
    public LowPassFilterProcessor(float cutoffHz) {
        recalcCoefficients(cutoffHz);
    }

    /**
     * 运行时修改截止频率并即时生效。
     *
     * <p>重算系数后立即清空滤波器历史状态，防止换系数瞬间产生爆音。
     *
     * @param cutoffHz 新截止频率（Hz），自动 clamp 到 [200, 8000]
     */
    public void setCutoffFrequency(float cutoffHz) {
        recalcCoefficients(cutoffHz);
        reset();
    }

    /**
     * 根据截止频率重算二阶低通系数（Q=0.707 Butterworth）。
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

        // 低通系数：分子用 (1 - cosw0)，与高通的 (1 + cosw0) 相反
        float nb0 = (1.0f - cosw0) / 2.0f;
        float nb1 = 1.0f - cosw0;
        float nb2 = (1.0f - cosw0) / 2.0f;
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
    public void process(short[] samples) {
        if (!enabled) return;

        for (int i = 0; i < samples.length; i++) {
            float x = samples[i] / 32768.0f;
            float y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1;
            x1 = x;
            y2 = y1;
            y1 = y;
            // 硬限幅防止溢出
            if (y > 1.0f) y = 1.0f;
            else if (y < -1.0f) y = -1.0f;
            samples[i] = (short) (y * 32767.0f);
        }
    }

    @Override
    public void reset() {
        x1 = 0.0f;
        x2 = 0.0f;
        y1 = 0.0f;
        y2 = 0.0f;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        // 重新开启时清空历史状态，避免残留的旧样本造成瞬时爆音/咔哒声
        if (enabled && !this.enabled) {
            reset();
        }
        this.enabled = enabled;
    }
}
