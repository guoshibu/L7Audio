package com.aug32.l7audio.domain.audio.micoutput.processor;

import com.aug32.l7audio.domain.audio.micoutput.AudioProcessor;
import com.aug32.l7audio.utils.AppLog;

public class AdaptiveFeedbackCancellationProcessor implements AudioProcessor {

    private static final String TAG = "AdaptiveFeedbackCancel";

    /** 滤波器长度：从 1024 降低到 512，兼顾回声消除效果和 CPU 性能 */
    private static final int FILTER_LENGTH = 512;
    private static final float MU = 0.05f;
    private static final float DELTA = 0.0001f;
    private static final float LEAKAGE = 0.001f;
    private static final int LOG_INTERVAL = 25;

    // 双讲检测：|mic| > DT_THRESHOLD * |predicted_echo| 时冻结适配
    private static final float DT_THRESHOLD = 1.5f;
    // 最小有效回声阈值：|y| 低于此值时跳过双讲检测，保证初始收敛
    private static final float MIN_ECHO_AMP = 0.001f;
    // 系数归一化阈值：防止滤波器系数无限增长导致数值漂移
    private static final float NORM_THRESHOLD = 10.0f;

    // 滑动窗口 xnorm 优化
    private float xnorm = 0;

    private final float[] w;
    private final float[] xbuf;
    private int xpos = 0;
    private int frameCount = 0;
    private float lastErleDb = 0.0f;
    private boolean enabled = true;

    // 上一帧的能量值，用于当前帧的双讲检测（避免两遍处理的bug）
    private double prevInputEnergy = 0.0;
    private double prevEchoEnergy = 0.0;

    public AdaptiveFeedbackCancellationProcessor() {
        w = new float[FILTER_LENGTH];
        xbuf = new float[FILTER_LENGTH];
    }

    public void setReference(short[] refSamples) {
        for (int i = 0; i < refSamples.length; i++) {
            float xv = refSamples[i] / 32768.0f;
            xnorm -= xbuf[xpos] * xbuf[xpos];
            xbuf[xpos] = xv;
            xnorm += xv * xv;
            xpos = (xpos + 1) % FILTER_LENGTH;
        }
    }

    @Override
    public void process(short[] samples) {
        if (!enabled) return;

        double errorEnergy = 0.0;
        double inputEnergy = 0.0;
        double echoEnergy = 0.0;

        // 根据上一帧的能量判断是否允许适配（第一帧默认为允许）
        boolean adapt = frameCount == 0;
        if (frameCount > 0 && xnorm > 1e-6f) {
            adapt = prevEchoEnergy < MIN_ECHO_AMP * MIN_ECHO_AMP
                    || prevInputEnergy < DT_THRESHOLD * DT_THRESHOLD * prevEchoEnergy;
        }

        float mu = MU / (xnorm + DELTA);

        for (int i = 0; i < samples.length; i++) {
            float d = samples[i] / 32768.0f;
            inputEnergy += d * d;

            float y = 0;
            int idx = xpos;
            // 使用位掩码替代模运算：FILTER_LENGTH=512 是 2 的幂，等价于 idx & 511
            for (int j = 0; j < FILTER_LENGTH; j++) {
                idx = (idx - 1) & (FILTER_LENGTH - 1);
                y += w[j] * xbuf[idx];
            }
            echoEnergy += y * y;

            float e = d - y;
            errorEnergy += e * e;

            // 单遍处理：根据上一帧的双讲检测结果决定是否更新系数
            if (adapt && xnorm > 1e-6f) {
                idx = xpos;
                for (int j = 0; j < FILTER_LENGTH; j++) {
                    idx = (idx - 1) & (FILTER_LENGTH - 1);
                    w[j] = w[j] * (1.0f - LEAKAGE) + mu * e * xbuf[idx];
                }
            }

            samples[i] = (short) (e * 32767.0f);
        }

        // 系数归一化：防止滤波器系数无限增长导致数值漂移
        float wNorm = 0.0f;
        for (int j = 0; j < FILTER_LENGTH; j++) {
            wNorm += w[j] * w[j];
        }
        wNorm = (float) Math.sqrt(wNorm);
        if (wNorm > NORM_THRESHOLD) {
            float scale = NORM_THRESHOLD / wNorm;
            for (int j = 0; j < FILTER_LENGTH; j++) {
                w[j] *= scale;
            }
        }

        // 保存当前帧能量供下一帧使用
        prevInputEnergy = inputEnergy;
        prevEchoEnergy = echoEnergy;

        frameCount++;
        if (frameCount % LOG_INTERVAL == 0) {
            float erleLinear = (float) (inputEnergy / Math.max(errorEnergy, 1e-10));
            lastErleDb = 10.0f * (float) Math.log10(erleLinear);
            AppLog.i(TAG, "ERLE=" + String.format("%.1f", lastErleDb) + "dB, input="
                    + String.format("%.4f", inputEnergy)
                    + ", error=" + String.format("%.4f", errorEnergy));
        }
    }

    @Override
    public void reset() {
        for (int i = 0; i < FILTER_LENGTH; i++) {
            w[i] = 0.0f;
            xbuf[i] = 0.0f;
        }
        xnorm = 0.0f;
        xpos = 0;
        frameCount = 0;
        prevInputEnergy = 0.0;
        prevEchoEnergy = 0.0;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) reset();
    }

    /** 返回最近一次计算的 ERLE（dB），用于文件日志记录 */
    public float getLastErleDb() {
        return lastErleDb;
    }

    /** 返回滤波器系数范数，用于判断滤波器是否收敛 */
    public float getCoefficientNorm() {
        float norm = 0.0f;
        for (float wj : w) {
            norm += wj * wj;
        }
        return (float) Math.sqrt(norm);
    }
}