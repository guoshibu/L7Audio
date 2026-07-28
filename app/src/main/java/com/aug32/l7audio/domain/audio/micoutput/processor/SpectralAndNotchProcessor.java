package com.aug32.l7audio.domain.audio.micoutput.processor;

import com.aug32.l7audio.domain.audio.micoutput.AudioProcessor;
import com.aug32.l7audio.utils.AppLog;

public class SpectralAndNotchProcessor {

    /** FFT 大小：从 512 降低到 256，平衡频率分辨率和 CPU 性能 */
    static final int FFT_SIZE = 256;
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

    /**
     * 谱减法降噪处理器。
     *
     * <p>512 FFT + Sine 窗 + 50% overlap-add，前 30 帧学习噪声谱，
     * 后续通过 VAD 判断语音活动，非语音时持续更新噪声谱。
     * 谱减后使用 SPECTRAL_FLOOR 保留底噪，避免"音乐噪声"。
     */
    public static class SpectralNoiseReduction implements AudioProcessor {

        private static final float OVER_SUBTRACTION = 1.3f;
        private static final float SPECTRAL_FLOOR = 0.01f;
        /** 噪声学习帧数：从 30 帧延长至 375 帧（约 1 秒），确保噪声模型足够稳定 */
        private static final int NOISE_LEARN_FRAMES = 375;
        private static final float NOISE_UPDATE_RATE = 0.05f;
        private static final float VAD_THRESHOLD = 2.5f;
        /** 噪声谱更新间隔：从每帧改为每 3 帧更新一次，减少计算开销 */
        private static final int NOISE_UPDATE_INTERVAL = 3;

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
        private int frameCount = 0;
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

            int totalInput = n + HALF_FFT;
            if (inputBuf == null || inputBuf.length < totalInput) inputBuf = new float[totalInput];
            if (outputBuf == null || outputBuf.length < n) outputBuf = new float[n];
            float[] input = inputBuf;
            float[] output = outputBuf;

            System.arraycopy(prevInput, 0, input, 0, HALF_FFT);
            for (int i = 0; i < n; i++) {
                input[HALF_FFT + i] = samples[i] / 32768.0f;
            }

            if (n >= HALF_FFT) {
                System.arraycopy(input, n, prevInput, 0, HALF_FFT);
            }

            int numFrames = (n + HALF_FFT - 1) / HALF_FFT;

            for (int f = 0; f < numFrames; f++) {
                int base = f * HALF_FFT;

                for (int i = 0; i < FFT_SIZE; i++) {
                    int inputIdx = base + i;
                    if (inputIdx < totalInput) {
                        real[i] = input[inputIdx] * sineWindow[i];
                    } else {
                        real[i] = 0.0f;
                    }
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
                } else if (!voiceActive && frameCount % NOISE_UPDATE_INTERVAL == 0) {
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

        frameCount++;
    }

    /** 谱平坦度阈值：语音信号谱平坦度低（有明显峰值），噪声信号谱平坦度高 */
        private static final float FLATNESS_THRESHOLD = 0.4f;

        private boolean isVoiceActive(float[] mag) {
            float currentEnergy = 0.0f;
            float noiseEnergy = 0.0f;
            float sumMag = 0.0f;
            float productMag = 1.0f;
            int validBins = 0;

            for (int i = 0; i <= HALF_FFT; i++) {
                currentEnergy += mag[i] * mag[i];
                noiseEnergy += noiseProfile[i] * noiseProfile[i];
                if (mag[i] > 0.0001f) {
                    sumMag += mag[i];
                    productMag *= mag[i];
                    validBins++;
                }
            }

            boolean energyCondition = currentEnergy > VAD_THRESHOLD * noiseEnergy;

            // 谱平坦度检测：语音信号有明显峰值，谱平坦度低；噪声信号平坦，谱平坦度高
            float spectralFlatness = 0.0f;
            if (validBins > 0 && sumMag > 0) {
                float geometricMean = (float) Math.pow(productMag, 1.0f / validBins);
                float arithmeticMean = sumMag / validBins;
                spectralFlatness = geometricMean / arithmeticMean;
            }

            // 双重条件：能量超过阈值 AND 谱平坦度低于阈值（有峰值特征）
            return energyCondition && spectralFlatness < FLATNESS_THRESHOLD;
        }

        @Override
        public void reset() {
            noiseUpdateCount = 0;
            frameCount = 0;
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

    /**
     * 啸叫检测与 IIR 陷波滤波器。
     *
     * <p>512 FFT 谱峰检测（频率分辨率 93.75Hz），寻找频率峰值超过周围 3 倍且连续出现 3 次的频点，
     * 添加 IIR 双二阶陷波器（Q=30, -12dB）进行抑制，最多同时抑制 3 个频点。
     * 啸叫消失 10 帧后自动释放陷波器。
     * </p>
     *
     * <p>与降噪处理器独立使用不同的 FFT 大小：降噪使用 256 点（平衡性能），
     * 啸叫检测使用 512 点（提高频率分辨率，确保陷波器频率定位准确）。
     * </p>
     */
    public static class HowlingNotchFilter implements AudioProcessor {

        private static final String TAG = "HowlingNotchFilter";
        private static final int SAMPLE_RATE = 48000;
        /** 啸叫检测独立使用 512 点 FFT，频率分辨率 93.75Hz，确保陷波器频率定位准确 */
        private static final int HOWLING_FFT_SIZE = 512;
        private static final int HOWLING_HALF_FFT = HOWLING_FFT_SIZE / 2;
        private static final float PEAK_RATIO = 3.0f;
        private static final float HOWLING_AMP_THRESHOLD = 0.01f;
        private static final int DETECTION_COUNT = 3;
        private static final int MAX_NOTCHES = 3;
        private static final float NOTCH_Q = 30.0f;
        private static final int RELEASE_FRAMES = 10;
        private static final int MIN_FREQ_BIN = 500 * HOWLING_FFT_SIZE / SAMPLE_RATE;
        private static final int MAX_FREQ_BIN = Math.min(8000 * HOWLING_FFT_SIZE / SAMPLE_RATE, HOWLING_HALF_FFT - 1);
        /** 陷波器频率锁定范围：已激活的陷波器附近的频点不再检测，避免抖动 */
        private static final float LOCKED_FREQ_RANGE = 50.0f;

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
            window = new float[HOWLING_FFT_SIZE];
            howlingCounters = new int[HOWLING_HALF_FFT];
            real = new float[HOWLING_FFT_SIZE];
            imag = new float[HOWLING_FFT_SIZE];
            magnitude = new float[HOWLING_HALF_FFT];
            activeNotches = new NotchFilter[MAX_NOTCHES];

            for (int i = 0; i < HOWLING_FFT_SIZE; i++) {
                window[i] = (float) Math.sin(Math.PI * (i + 0.5) / HOWLING_FFT_SIZE);
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
            int n = samples.length;
            if (n <= 0) return;

            int numChunks = (n + HOWLING_HALF_FFT - 1) / HOWLING_HALF_FFT;

            for (int chunk = 0; chunk < numChunks; chunk++) {
                int base = chunk * HOWLING_HALF_FFT;
                int fftLen = Math.min(HOWLING_FFT_SIZE, n - base);

                for (int i = 0; i < fftLen; i++) {
                    real[i] = (samples[base + i] / 32768.0f) * window[i];
                    imag[i] = 0.0f;
                }
                for (int i = fftLen; i < HOWLING_FFT_SIZE; i++) {
                    real[i] = 0.0f;
                    imag[i] = 0.0f;
                }

                fft(real, imag);

                for (int i = 0; i < HOWLING_HALF_FFT; i++) {
                    magnitude[i] = (float) Math.sqrt(real[i] * real[i] + imag[i] * imag[i]);
                }

                float avgMagnitude = 0.0f;
                for (int i = MIN_FREQ_BIN; i <= MAX_FREQ_BIN && i < HOWLING_HALF_FFT; i++) {
                    avgMagnitude += magnitude[i];
                }
                avgMagnitude /= (MAX_FREQ_BIN - MIN_FREQ_BIN + 1);
                float adaptiveThreshold = Math.max(HOWLING_AMP_THRESHOLD, avgMagnitude * 0.5f);

                for (int i = MIN_FREQ_BIN; i <= MAX_FREQ_BIN && i < HOWLING_HALF_FFT - 1; i++) {
                    float freq = (float) i * SAMPLE_RATE / HOWLING_FFT_SIZE;
                    
                    boolean freqLocked = false;
                    for (int j = 0; j < notchCount; j++) {
                        if (Math.abs(activeNotches[j].freq - freq) < LOCKED_FREQ_RANGE) {
                            freqLocked = true;
                            break;
                        }
                    }
                    
                    if (freqLocked) {
                        continue;
                    }
                    
                    if (magnitude[i] > adaptiveThreshold
                            && magnitude[i] > magnitude[i - 1] * PEAK_RATIO
                            && magnitude[i] > magnitude[i + 1] * PEAK_RATIO) {
                        howlingCounters[i]++;
                        if (howlingCounters[i] >= DETECTION_COUNT) {
                            howlingCounters[i] = 0;
                            addNotch(freq);
                        }
                    } else {
                        howlingCounters[i] = 0;
                    }
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
            if (notchCount >= MAX_NOTCHES) {
                int oldestIdx = 0;
                int minCounter = activeNotches[0].releaseCounter;
                for (int i = 1; i < notchCount; i++) {
                    if (activeNotches[i].releaseCounter < minCounter) {
                        minCounter = activeNotches[i].releaseCounter;
                        oldestIdx = i;
                    }
                }
                AppLog.i(TAG, "啸叫陷波器替换: " + (int) activeNotches[oldestIdx].freq + "Hz -> " + (int) freq + "Hz");
                activeNotches[oldestIdx] = new NotchFilter(freq);
                return;
            }
            AppLog.i(TAG, "啸叫陷波器激活: " + (int) freq + "Hz");
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
                    AppLog.i(TAG, "啸叫陷波器释放: " + (int) activeNotches[readIdx].freq + "Hz");
                }
            }
            notchCount = writeIdx;
        }

        @Override
        public void reset() {
            for (int i = 0; i < HOWLING_HALF_FFT; i++) {
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
