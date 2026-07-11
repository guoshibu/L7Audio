package com.aug32.l7audio.domain.audio.micoutput.processor;

import com.aug32.l7audio.domain.audio.micoutput.AudioProcessor;

public class HighPassFilterProcessor implements AudioProcessor {

    /** 一阶 IIR HPF 反馈系数，对应 @100Hz 截止频率（fs=48000），由 cos(2π * fc / fs) 算得 */
    private static final float B1 = 0.9998f;
    private float prevX = 0.0f;
    private float prevY = 0.0f;
    private boolean enabled = true;

    @Override
    public void process(short[] samples) {
        if (!enabled) return;

        for (int i = 0; i < samples.length; i++) {
            float x = samples[i] / 32768.0f;
            float y = x - prevX + B1 * prevY;
            prevX = x;
            if (y > 1.0f) y = 1.0f;
            else if (y < -1.0f) y = -1.0f;
            prevY = y;
            samples[i] = (short) (y * 32767.0f);
        }
    }

    @Override
    public void reset() {
        prevX = 0.0f;
        prevY = 0.0f;
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
