package com.aug32.l7audio.domain.audio.micoutput.processor;

import com.aug32.l7audio.domain.audio.micoutput.AudioProcessor;

public class HighPassFilterProcessor implements AudioProcessor {

    private static final int SAMPLE_RATE = 48000;
    /** 截止频率从 80Hz 提升至 100Hz，增强胎噪和车身共振抑制效果 */
    private static final float CUTOFF_FREQ = 100.0f;

    private static final float B0;
    private static final float B1;
    private static final float B2;
    private static final float A1;
    private static final float A2;

    static {
        float w0 = 2.0f * (float) Math.PI * CUTOFF_FREQ / SAMPLE_RATE;
        float cosw0 = (float) Math.cos(w0);
        float sinw0 = (float) Math.sin(w0);
        float alpha = sinw0 * (float) Math.sqrt(2.0) / 2.0f;

        float b0 = (1.0f + cosw0) / 2.0f;
        float b1 = -(1.0f + cosw0);
        float b2 = (1.0f + cosw0) / 2.0f;
        float a0 = 1.0f + alpha;
        float a1 = -2.0f * cosw0;
        float a2 = 1.0f - alpha;

        B0 = b0 / a0;
        B1 = b1 / a0;
        B2 = b2 / a0;
        A1 = a1 / a0;
        A2 = a2 / a0;
    }

    private float x1 = 0.0f;
    private float x2 = 0.0f;
    private float y1 = 0.0f;
    private float y2 = 0.0f;
    private boolean enabled = true;

    @Override
    public void process(short[] samples) {
        if (!enabled) return;

        for (int i = 0; i < samples.length; i++) {
            float x = samples[i] / 32768.0f;
            float y = B0 * x + B1 * x1 + B2 * x2 - A1 * y1 - A2 * y2;
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
        this.enabled = enabled;
    }
}
