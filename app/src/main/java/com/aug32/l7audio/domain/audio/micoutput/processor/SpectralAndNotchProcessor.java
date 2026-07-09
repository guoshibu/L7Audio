package com.aug32.l7audio.domain.audio.micoutput.processor;

import com.aug32.l7audio.domain.audio.micoutput.AudioProcessor;
import com.aug32.l7audio.utils.AppLog;

public class SpectralAndNotchProcessor {

    static final int FFT_SIZE = 512;
    static final int HALF_FFT = FFT_SIZE / 2;

    static void fft(float[] real, float[] imag) {
        int n = real.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;
            if (i < j) {
                float tr = real[i];
                float ti = imag[i];
                real[i] = real[j];
                imag[i] = imag[j];
                real[j] = tr;
                imag[j] = ti;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            float angle = -2.0f * (float) Math.PI / len;
            float wr = (float) Math.cos(angle);
            float wi = (float) Math.sin(angle);
            for (int i = 0; i < n; i += len) {
                float curR = 1.0f;
                float curI = 0.0f;
                for (int j = 0; j < len / 2; j++) {
                    int idx = i + j;
                    int idx2 = idx + len / 2;
                    float tr = curR * real[idx2] - curI * imag[idx2];
                    float ti = curR * imag[idx2] + curI * real[idx2];
                    real[idx2] = real[idx] - tr;
                    imag[idx2] = imag[idx] - ti;
                    real[idx] += tr;
                    imag[idx] += ti;
                    float nwr = curR * wr - curI * wi;
                    float nwi = curR * wi + curI * wr;
                    curR = nwr;
                    curI = nwi;
                }
            }
        }
    }

    static void ifft(float[] real, float[] imag) {
        int n = real.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;
            if (i < j) {
                float tr = real[i];
                float ti = imag[i];
                real[i] = real[j];
                imag[i] = imag[j];
                real[j] = tr;
                imag[j] = ti;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            float angle = 2.0f * (float) Math.PI / len;
            float wr = (float) Math.cos(angle);
            float wi = (float) Math.sin(angle);
            for (int i = 0; i < n; i += len) {
                float curR = 1.0f;
                float curI = 0.0f;
                for (int j = 0; j < len / 2; j++) {
                    int idx = i + j;
                    int idx2 = idx + len / 2;
                    float tr = curR * real[idx2] - curI * imag[idx2];
                    float ti = curR * imag[idx2] + curI * real[idx2];
                    real[idx2] = real[idx] - tr;
                    imag[idx2] = imag[idx] - ti;
                    real[idx] += tr;
                    imag[idx] += ti;
                    float nwr = curR * wr - curI * wi;
                    float nwi = curR * wi + curI * wr;
                    curR = nwr;
                    curI = nwi;
                }
            }
        }
        float invN = 1.0f / n;
        for (int i = 0; i < n; i++) {
            real[i] *= invN;
            imag[i] *= invN;
        }
    }

    public static class SpectralNoiseReduction implements AudioProcessor {

        private static final float OVER_SUBTRACTION = 1.3f;
        private static final float SPECTRAL_FLOOR = 0.01f;
        private static final int NOISE_LEARN_FRAMES = 30;
        private static final float NOISE_UPDATE_RATE = 0.05f;
        private static final float VAD_THRESHOLD = 2.5f;

        private final float[] sineWindow;
        private final float[] noiseProfile;
        private final float[] prevInput;
        private final float[] overlap;
        private final float[] real;
        private final float[] imag;
        private final float[] magnitude;
        private float[] inputBuf;
        private float[] outputBuf;

        private int noiseUpdateCount = 0;
        private boolean enabled = true;

        public SpectralNoiseReduction() {
            sineWindow = new float[FFT_SIZE];
            noiseProfile = new float[HALF_FFT + 1];
            prevInput = new float[HALF_FFT];
            overlap = new float[HALF_FFT];
            real = new float[FFT_SIZE];
            imag = new float[FFT_SIZE];
            magnitude = new float[HALF_FFT + 1];

            for (int i = 0; i < FFT_SIZE; i++) {
                sineWindow[i] = (float) Math.sin(Math.PI * (i + 0.5) / FFT_SIZE);
            }
        }

        @Override
        public void process(short[] samples) {
            if (!enabled) return;

            int n = samples.length;
            if (n <= 0) return;

            int numFrames = n / HALF_FFT;
            if (numFrames <= 0) return;

            int totalInput = n + HALF_FFT;
            if (inputBuf == null || inputBuf.length < totalInput) inputBuf = new float[totalInput];
            if (outputBuf == null || outputBuf.length < n) outputBuf = new float[n];
            float[] input = inputBuf;
            float[] output = outputBuf;
            System.arraycopy(prevInput, 0, input, 0, HALF_FFT);
            for (int i = 0; i < n; i++) {
                input[HALF_FFT + i] = samples[i] / 32768.0f;
            }

            System.arraycopy(input, n, prevInput, 0, HALF_FFT);

            for (int f = 0; f < numFrames; f++) {
                int base = f * HALF_FFT;

                for (int i = 0; i < FFT_SIZE; i++) {
                    real[i] = input[base + i] * sineWindow[i];
                    imag[i] = 0.0f;
                }

                fft(real, imag);

                for (int i = 0; i <= HALF_FFT; i++) {
                    magnitude[i] = (float) Math.sqrt(real[i] * real[i] + imag[i] * imag[i]);
                }

                boolean voiceActive = isVoiceActive(magnitude);

                if (noiseUpdateCount < NOISE_LEARN_FRAMES) {
                    for (int i = 0; i <= HALF_FFT; i++) {
                        noiseProfile[i] = (noiseProfile[i] * noiseUpdateCount + magnitude[i])
                                / (noiseUpdateCount + 1);
                    }
                    noiseUpdateCount++;
                } else if (!voiceActive) {
                    for (int i = 0; i <= HALF_FFT; i++) {
                        noiseProfile[i] = (1.0f - NOISE_UPDATE_RATE) * noiseProfile[i]
                                + NOISE_UPDATE_RATE * magnitude[i];
                    }
                }

                if (noiseUpdateCount >= NOISE_LEARN_FRAMES) {
                    for (int i = 0; i <= HALF_FFT; i++) {
                        float subtracted = magnitude[i] - OVER_SUBTRACTION * noiseProfile[i];
                        if (subtracted < 0) subtracted = SPECTRAL_FLOOR * magnitude[i];
                        float gain = (magnitude[i] > 0.0001f) ? subtracted / magnitude[i] : 0.0f;
                        gain = Math.min(gain, 1.0f);
                        real[i] *= gain;
                        imag[i] *= gain;
                    }

                    for (int i = HALF_FFT + 1; i < FFT_SIZE; i++) {
                        int mirror = FFT_SIZE - i;
                        real[i] = real[mirror];
                        imag[i] = -imag[mirror];
                    }
                }

                ifft(real, imag);

                for (int i = 0; i < FFT_SIZE; i++) {
                    real[i] *= sineWindow[i];
                }

                for (int i = 0; i < HALF_FFT; i++) {
                    float val = overlap[i] + real[i];
                    int outIdx = f * HALF_FFT + i;
                    if (outIdx < n) {
                        output[outIdx] = val;
                    }
                    overlap[i] = real[HALF_FFT + i];
                }
            }

            for (int i = 0; i < n; i++) {
                float val = output[i];
                if (val > 1.0f) val = 1.0f;
                else if (val < -1.0f) val = -1.0f;
                samples[i] = (short) (val * 32767.0f);
            }
        }

        private boolean isVoiceActive(float[] mag) {
            float currentEnergy = 0.0f;
            float noiseEnergy = 0.0f;
            for (int i = 0; i <= HALF_FFT; i++) {
                currentEnergy += mag[i] * mag[i];
                noiseEnergy += noiseProfile[i] * noiseProfile[i];
            }
            return currentEnergy > VAD_THRESHOLD * noiseEnergy;
        }

        @Override
        public void reset() {
            noiseUpdateCount = 0;
            for (int i = 0; i < HALF_FFT + 1; i++) noiseProfile[i] = 0.0f;
            for (int i = 0; i < HALF_FFT; i++) prevInput[i] = 0.0f;
            for (int i = 0; i < HALF_FFT; i++) overlap[i] = 0.0f;
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

    public static class HowlingNotchFilter implements AudioProcessor {

        private static final String TAG = "HowlingNotchFilter";
        private static final int SAMPLE_RATE = 48000;
        private static final float PEAK_RATIO = 3.0f;
        private static final float HOWLING_AMP_THRESHOLD = 0.01f;
        private static final int DETECTION_COUNT = 3;
        private static final int MAX_NOTCHES = 3;
        private static final float NOTCH_Q = 30.0f;
        private static final int RELEASE_FRAMES = 10;
        private static final int MIN_FREQ_BIN = 500 * FFT_SIZE / SAMPLE_RATE;
        private static final int MAX_FREQ_BIN = Math.min(8000 * FFT_SIZE / SAMPLE_RATE, HALF_FFT - 1);

        private final float[] window;
        private final int[] howlingCounters;
        private final float[] real;
        private final float[] imag;
        private final float[] magnitude;
        private final NotchFilter[] activeNotches;
        private int notchCount = 0;
        private boolean enabled = true;

        private static class NotchFilter {
            float b0, b1, b2, a1, a2;
            float x1, x2, y1, y2;
            float freq;
            int releaseCounter;

            NotchFilter(float freqHz) {
                this.freq = freqHz;
                this.releaseCounter = RELEASE_FRAMES;
                float w0 = 2.0f * (float) Math.PI * freqHz / SAMPLE_RATE;
                float alpha = (float) Math.sin(w0) / (2.0f * NOTCH_Q);
                float cosw0 = (float) Math.cos(w0);
                b0 = 1.0f;
                b1 = -2.0f * cosw0;
                b2 = 1.0f;
                float a0 = 1.0f + alpha;
                a1 = -2.0f * cosw0;
                a2 = 1.0f - alpha;
                b0 /= a0;
                b1 /= a0;
                b2 /= a0;
                a1 /= a0;
                a2 /= a0;
            }

            float process(float x) {
                float y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
                x2 = x1;
                x1 = x;
                y2 = y1;
                y1 = y;
                return y;
            }
        }

        public HowlingNotchFilter() {
            window = new float[FFT_SIZE];
            howlingCounters = new int[HALF_FFT];
            real = new float[FFT_SIZE];
            imag = new float[FFT_SIZE];
            magnitude = new float[HALF_FFT];
            activeNotches = new NotchFilter[MAX_NOTCHES];

            for (int i = 0; i < FFT_SIZE; i++) {
                window[i] = (float) Math.sin(Math.PI * (i + 0.5) / FFT_SIZE);
            }
        }

        @Override
        public void process(short[] samples) {
            if (!enabled) return;

            detectHowling(samples);

            for (int i = 0; i < samples.length; i++) {
                float x = samples[i] / 32768.0f;
                float y = x;
                for (int j = 0; j < notchCount; j++) {
                    y = activeNotches[j].process(y);
                }
                samples[i] = (short) (y * 32767.0f);
            }

            releaseNotches();
        }

        private void detectHowling(short[] samples) {
            int fftLen = Math.min(FFT_SIZE, samples.length);
            for (int i = 0; i < fftLen; i++) {
                real[i] = (samples[i] / 32768.0f) * window[i];
                imag[i] = 0.0f;
            }
            for (int i = fftLen; i < FFT_SIZE; i++) {
                real[i] = 0.0f;
                imag[i] = 0.0f;
            }

            fft(real, imag);

            for (int i = 0; i < HALF_FFT; i++) {
                magnitude[i] = (float) Math.sqrt(real[i] * real[i] + imag[i] * imag[i]);
            }

            for (int i = MIN_FREQ_BIN; i <= MAX_FREQ_BIN && i < HALF_FFT - 1; i++) {
                if (magnitude[i] > HOWLING_AMP_THRESHOLD
                        && magnitude[i] > magnitude[i - 1] * PEAK_RATIO
                        && magnitude[i] > magnitude[i + 1] * PEAK_RATIO) {
                    howlingCounters[i]++;
                    if (howlingCounters[i] >= DETECTION_COUNT) {
                        howlingCounters[i] = 0;
                        float freq = (float) i * SAMPLE_RATE / FFT_SIZE;
                        addNotch(freq);
                    }
                } else {
                    howlingCounters[i] = 0;
                }
            }
        }

        private void addNotch(float freq) {
            for (int i = 0; i < notchCount; i++) {
                if (Math.abs(activeNotches[i].freq - freq) < 20.0f) {
                    activeNotches[i].releaseCounter = RELEASE_FRAMES;
                    return;
                }
            }
            if (notchCount >= MAX_NOTCHES) return;
            AppLog.i(TAG, "Howling notch activated: " + (int) freq + "Hz");
            activeNotches[notchCount++] = new NotchFilter(freq);
        }

        private void releaseNotches() {
            int writeIdx = 0;
            for (int readIdx = 0; readIdx < notchCount; readIdx++) {
                activeNotches[readIdx].releaseCounter--;
                if (activeNotches[readIdx].releaseCounter > 0) {
                    if (writeIdx != readIdx) {
                        activeNotches[writeIdx] = activeNotches[readIdx];
                    }
                    writeIdx++;
                } else {
                    AppLog.i(TAG, "Howling notch released: " + (int) activeNotches[readIdx].freq + "Hz");
                }
            }
            notchCount = writeIdx;
        }

        @Override
        public void reset() {
            for (int i = 0; i < HALF_FFT; i++) {
                howlingCounters[i] = 0;
            }
            notchCount = 0;
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
    }
}
