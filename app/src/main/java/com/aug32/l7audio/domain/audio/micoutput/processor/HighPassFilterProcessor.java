package com.aug32.l7audio.domain.audio.micoutput.processor;

import com.aug32.l7audio.domain.audio.micoutput.AudioProcessor;

public class HighPassFilterProcessor implements AudioProcessor {

    private static final float B1 = 0.969f;
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
