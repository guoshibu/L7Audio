package com.aug32.l7audio.domain.audio.micoutput.processor;

import com.aug32.l7audio.BuildConfig;
import com.aug32.l7audio.domain.audio.micoutput.AudioProcessor;
import com.aug32.l7audio.utils.AppLog;

import java.util.Locale;

public class AutomaticGainControlProcessor implements AudioProcessor {

    private static final float TARGET_RMS = 0.3f;
    private static final float MAX_GAIN = 2.0f;
    private static final float MIN_GAIN = 0.5f;
    private static final float GAIN_CHANGE_LIMIT = 0.05f;
    private static final float SMOOTH_FACTOR = 0.05f;
    private static final int GAIN_UPDATE_INTERVAL = 10;

    private float currentGain = 1.0f;
    private float smoothedRms = 0.0f;
    private int frameCount = 0;
    private boolean enabled = true;

    @Override
    public void process(short[] samples) {
        if (!enabled) return;

        frameCount++;
        if (frameCount % GAIN_UPDATE_INTERVAL == 0) {
            float rms = calculateRms(samples);
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
            normalized = (float) Math.tanh(normalized);
            samples[i] = (short) (normalized * 32767.0f);
        }
    }

    private float calculateRms(short[] samples) {
        double sum = 0.0;
        for (short sample : samples) {
            float normalized = sample / 32768.0f;
            sum += normalized * normalized;
        }
        return (float) Math.sqrt(sum / samples.length);
    }

    public float getCurrentGain() {
        return currentGain;
    }

    @Override
    public void reset() {
        currentGain = 1.0f;
        smoothedRms = 0.0f;
        frameCount = 0;
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
