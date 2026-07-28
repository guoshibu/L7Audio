package com.aug32.l7audio.domain.audio.micoutput.processor;

import com.aug32.l7audio.BuildConfig;
import com.aug32.l7audio.domain.audio.micoutput.AudioProcessor;
import com.aug32.l7audio.utils.AppLog;

import java.util.Locale;

public class AutomaticGainControlProcessor implements AudioProcessor {

    /** 目标 RMS：从 0.3 降至 0.2（约 -14dBFS），减少安静时段背景噪声放大 */
    private static final float TARGET_RMS = 0.2f;
    private static final float MAX_GAIN = 2.0f;
    private static final float MIN_GAIN = 0.5f;
    private static final float GAIN_CHANGE_LIMIT = 0.05f;
    private static final float SMOOTH_FACTOR = 0.05f;
    /** 增益更新间隔：从 10 帧增加到 20 帧，减少计算开销 */
    private static final int GAIN_UPDATE_INTERVAL = 20;

    private float currentGain = 1.0f;
    private float smoothedRms = 0.0f;
    private int frameCount = 0;
    /** 增量 RMS 计算：累积样本平方和，避免每次全帧遍历 */
    private double rmsSum = 0.0;
    private int rmsSampleCount = 0;
    private boolean enabled = true;

    @Override
    public void process(short[] samples) {
        if (!enabled) return;

        // 增量 RMS 计算：累积样本平方和
        for (short sample : samples) {
            float normalized = sample / 32768.0f;
            rmsSum += normalized * normalized;
        }
        rmsSampleCount += samples.length;

        frameCount++;
        if (frameCount % GAIN_UPDATE_INTERVAL == 0) {
            float rms = 0.0f;
            if (rmsSampleCount > 0) {
                rms = (float) Math.sqrt(rmsSum / rmsSampleCount);
            }
            
            // 重置增量累积
            rmsSum = 0.0;
            rmsSampleCount = 0;

            if (smoothedRms == 0.0f) {
                smoothedRms = rms;
            } else {
                smoothedRms = (1.0f - SMOOTH_FACTOR) * smoothedRms + SMOOTH_FACTOR * rms;
            }

            if (smoothedRms > 0.001f) {
                float desiredGain = TARGET_RMS / smoothedRms;
                if (desiredGain > MAX_GAIN) desiredGain = MAX_GAIN;
                if (desiredGain < MIN_GAIN) desiredGain = MIN_GAIN;

                float upper = currentGain * (1.0f + GAIN_CHANGE_LIMIT);
                float lower = currentGain * (1.0f - GAIN_CHANGE_LIMIT);
                if (desiredGain > upper) {
                    currentGain = upper;
                } else if (desiredGain < lower) {
                    currentGain = lower;
                } else {
                    currentGain = desiredGain;
                }
                if (BuildConfig.DEBUG) {
                    AppLog.d("AGC", "gain=" + String.format(Locale.US, "%.4f", currentGain)
                            + " smoothedRms=" + String.format(Locale.US, "%.4f", smoothedRms));
                }
            }
        }

        for (int i = 0; i < samples.length; i++) {
            float normalized = samples[i] / 32768.0f;
            normalized *= currentGain;
            if (normalized > 1.0f) normalized = 1.0f;
            else if (normalized < -1.0f) normalized = -1.0f;
            samples[i] = (short) (normalized * 32767.0f);
        }
    }

    public float getCurrentGain() {
        return currentGain;
    }

    @Override
    public void reset() {
        currentGain = 1.0f;
        smoothedRms = 0.0f;
        frameCount = 0;
        rmsSum = 0.0;
        rmsSampleCount = 0;
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
