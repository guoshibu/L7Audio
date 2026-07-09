package com.aug32.l7audio.domain.audio.micoutput.processor;

import com.aug32.l7audio.domain.audio.micoutput.AudioProcessor;
import com.aug32.l7audio.utils.AppLog;

public class AdaptiveFeedbackCancellationProcessor implements AudioProcessor {

    private static final String TAG = "AdaptiveFeedbackCancel";

    private static final int FILTER_LENGTH = 1024;
    private static final float MU = 0.05f;
    private static final float DELTA = 0.0001f;
    private static final float LEAKAGE = 0.001f;
    private static final int LOG_INTERVAL = 25;

    // 双讲检测：|mic| > DT_THRESHOLD * |predicted_echo| 时冻结适配
    private static final float DT_THRESHOLD = 1.5f;
    // 最小有效回声阈值：|y| 低于此值时跳过双讲检测，保证初始收敛
    private static final float MIN_ECHO_AMP = 0.001f;

    // 滑动窗口 xnorm 优化
    private float xnorm = 0;

    private final float[] w;
    private final float[] xbuf;
    private int xpos = 0;
    private int frameCount = 0;
    private float lastErleDb = 0.0f;
    private boolean enabled = true;

    public AdaptiveFeedbackCancellationProcessor() {
        w = new float[FILTER_LENGTH];
        xbuf = new float[FILTER_LENGTH];
    }

    public void setReference(short[] refSamples) {
        for (int i = 0; i < refSamples.length; i++) {
            float xv = refSamples[i] / 32768.0f;
            // 滑窗 xnorm：减旧值、加新值
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

        for (int i = 0; i < samples.length; i++) {
            float d = samples[i] / 32768.0f;
            inputEnergy += d * d;

            float y = 0;
            int idx = xpos;
            for (int j = 0; j < FILTER_LENGTH; j++) {
                idx = (idx - 1 + FILTER_LENGTH) % FILTER_LENGTH;
                y += w[j] * xbuf[idx];
            }

            float e = d - y;
            errorEnergy += e * e;

            // 双讲检测：近端语音能量远大于预测回声时冻结适配
            // xnorm 很小时（首帧参考信号未就绪）也冻结适配，防止系数炸裂
            // 当预测回声 |y| 较小时跳过双讲检测（滤波器尚未收敛），保证初始快速收敛
            if (xnorm > 1e-6f) {
                boolean adapt = Math.abs(y) < MIN_ECHO_AMP
                        || Math.abs(d) < DT_THRESHOLD * Math.abs(y);
                if (adapt) {
                    float mu = MU / (xnorm + DELTA);
                    idx = xpos;
                    for (int j = 0; j < FILTER_LENGTH; j++) {
                        idx = (idx - 1 + FILTER_LENGTH) % FILTER_LENGTH;
                        w[j] = w[j] * (1.0f - LEAKAGE) + mu * e * xbuf[idx];
                    }
                }
            }

            samples[i] = (short) (e * 32767.0f);

            xpos = (xpos + 1) % FILTER_LENGTH;
        }

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
}
