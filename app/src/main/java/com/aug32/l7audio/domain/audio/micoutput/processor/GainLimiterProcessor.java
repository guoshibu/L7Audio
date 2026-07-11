package com.aug32.l7audio.domain.audio.micoutput.processor;

import com.aug32.l7audio.BuildConfig;
import com.aug32.l7audio.domain.audio.micoutput.AudioProcessor;
import com.aug32.l7audio.utils.AppLog;

import java.util.Locale;

/**
 * 增益放大 + 软限幅处理器
 *
 * <p>职责：
 * <ul>
 *   <li>根据放大级别对音频采样进行增益放大</li>
 *   <li>使用 tanh 软限幅防止溢出，避免削波失真</li>
 * </ul>
 *
 * <p>每 {@link #LOG_INTERVAL} 帧输出一次限幅统计日志，方便调优放大倍数。
 *
 * <p>软限幅 vs 硬限幅（clamp）：
 * <ul>
 *   <li>硬限幅：超过范围直接截断 → 产生谐波失真</li>
 *   <li>软限幅：使用 tanh 平滑压缩 → 保留波形，声音更自然</li>
 * </ul>
 */
public class GainLimiterProcessor implements AudioProcessor {

    public GainLimiterProcessor() {
    }

    private static final String TAG = "GainLimiter";
    /** 限幅统计日志输出间隔（帧数） */
    private static final int LOG_INTERVAL = 25;

    /** 增益倍数，由 MicrophoneManager 根据放大级别和最大放大倍率计算 */
    private float amplificationFactor = 1.0f;
    /** 是否启用 */
    private boolean enabled = true;

    /** 已处理帧数 */
    private int frameCount = 0;
    /** 累计的限幅采样数（全生命周期） */
    private int clippedSamplesTotal = 0;
    /** 当前日志窗口内的限幅采样数 */
    private int clippedSamplesInWindow = 0;

    public void setAmplificationFactor(float factor) {
        this.amplificationFactor = factor;
    }

    @Override
    public void process(short[] samples) {
        int localClipCount = 0;
        for (int i = 0; i < samples.length; i++) {
            float normalized = samples[i] / 32768.0f;
            normalized *= amplificationFactor;
            float prev = normalized;
            normalized = (float) Math.tanh(normalized);
            if (Math.abs(prev) > 1.0f) {
                localClipCount++;
            }
            samples[i] = (short) (normalized * 32767.0f);
        }

        clippedSamplesTotal += localClipCount;
        clippedSamplesInWindow += localClipCount;
        frameCount++;

        if (frameCount % LOG_INTERVAL == 0) {
            if (BuildConfig.DEBUG && clippedSamplesInWindow > 0) {
                float pct = clippedSamplesInWindow * 100.0f / (LOG_INTERVAL * samples.length);
                AppLog.i(TAG, "clip=" + String.format(Locale.US, "%.2f", pct) + "% factor=" + String.format(Locale.US, "%.2f", amplificationFactor) + " totalClipped=" + clippedSamplesTotal);
            }
            clippedSamplesInWindow = 0;
        }
    }

    @Override
    public void reset() {
        frameCount = 0;
        clippedSamplesTotal = 0;
        clippedSamplesInWindow = 0;
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