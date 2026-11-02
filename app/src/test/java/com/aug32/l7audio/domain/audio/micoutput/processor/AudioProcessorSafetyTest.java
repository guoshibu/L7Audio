package com.aug32.l7audio.domain.audio.micoutput.processor;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 音频处理器安全回归测试（JVM 单测，不需要真机/模拟器）。
 *
 * <p>覆盖两类曾经真实出现过的问题：
 * <ul>
 *   <li>静音输入必须保持静音：IIR 滤波器 / AGC / AFC 不应自激产生噪声</li>
 *   <li>满量程 + 高增益输入必须限幅：不能因 {@code (short)} 强转整数回绕，
 *       把正半周变成负值（听感上是刺耳爆音）</li>
 * </ul>
 *
 * <p>注意：每条用例的处理帧数刻意小于处理器内部的日志间隔，
 * 避免依赖 {@code android.util.Log} 的桩实现（虽然构建已开启 returnDefaultValues）。
 */
public class AudioProcessorSafetyTest {

    /** 构造一段固定幅值的测试信号 */
    private static short[] filled(int length, short value) {
        short[] samples = new short[length];
        for (int i = 0; i < length; i++) {
            samples[i] = value;
        }
        return samples;
    }

    @Test
    public void highPass_silenceStaysSilent() {
        HighPassFilterProcessor processor = new HighPassFilterProcessor();
        short[] samples = new short[128];

        processor.process(samples);

        assertArrayEquals("高通滤波器对全零输入不应产生任何输出", new short[128], samples);
    }

    @Test
    public void lowPass_silenceStaysSilent() {
        LowPassFilterProcessor processor = new LowPassFilterProcessor();
        short[] samples = new short[128];

        processor.process(samples);

        assertArrayEquals("低通滤波器对全零输入不应产生任何输出", new short[128], samples);
    }

    @Test
    public void automaticGainControl_silenceStaysSilent() {
        AutomaticGainControlProcessor processor = new AutomaticGainControlProcessor();
        short[] samples = new short[128];

        processor.process(samples);

        assertArrayEquals("AGC 对全零输入不应产生任何输出", new short[128], samples);
    }

    @Test
    public void feedbackCancellation_silenceStaysSilent() {
        AdaptiveFeedbackCancellationProcessor processor = new AdaptiveFeedbackCancellationProcessor();
        short[] samples = new short[128];

        processor.process(samples);

        assertArrayEquals("AFC 对全零输入不应产生任何输出", new short[128], samples);
    }

    @Test
    public void gainLimiter_fullScaleKeepsPolarity() {
        // 4 倍增益 + 满量程输入：多项式 tanh 近似会发散，
        // 若缺少限幅，(short) 强转会把正半周回绕成负值 —— 这里锁定符号不被反转
        GainLimiterProcessor processor = new GainLimiterProcessor();
        processor.setAmplificationFactor(4.0f);

        short[] positive = filled(64, Short.MAX_VALUE);
        processor.process(positive);
        assertTrue("正满量程经增益限幅后仍应为正", positive[positive.length - 1] > 0);

        short[] negative = filled(64, (short) -Short.MAX_VALUE);
        processor.process(negative);
        assertTrue("负满量程经增益限幅后仍应为负", negative[negative.length - 1] < 0);
    }

    @Test
    public void gainLimiter_silenceStaysSilent() {
        GainLimiterProcessor processor = new GainLimiterProcessor();
        processor.setAmplificationFactor(4.0f);
        short[] samples = new short[64];

        processor.process(samples);

        assertArrayEquals("增益限幅器对全零输入不应产生任何输出", new short[64], samples);
    }

    @Test
    public void disabledFilters_arePassThrough() {
        short[] expected = {1, -2, 300, -4000, 12000};

        HighPassFilterProcessor highPass = new HighPassFilterProcessor();
        highPass.setEnabled(false);
        short[] highPassSamples = expected.clone();
        highPass.process(highPassSamples);
        assertArrayEquals("关闭后的高通滤波器应原样透传", expected, highPassSamples);

        LowPassFilterProcessor lowPass = new LowPassFilterProcessor();
        lowPass.setEnabled(false);
        short[] lowPassSamples = expected.clone();
        lowPass.process(lowPassSamples);
        assertArrayEquals("关闭后的低通滤波器应原样透传", expected, lowPassSamples);
    }
}
