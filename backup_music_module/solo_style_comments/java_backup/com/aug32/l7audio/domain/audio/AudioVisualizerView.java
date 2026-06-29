package com.aug32.l7audio.domain.audio;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.core.content.ContextCompat;

import com.aug32.l7audio.R;

/**
 * 音频可视化视图
 *
 * 职责：
 * - 显示音频播放时的可视化动画
 *
 * 目标 SDK：Android 11 (API 30)
 */
public class AudioVisualizerView extends View {

    private static final int BAR_COUNT = 12;
    private static final long ANIMATION_DURATION = 800;
    private static final float MAX_BAR_HEIGHT_RATIO = 0.8f;
    private static final float MIN_BAR_HEIGHT_RATIO = 0.2f;

    private Paint skeletonPaint;
    private Paint fillPaint;
    private float[] barHeights;
    private float[] targetHeights;
    private boolean isAnimating = false;
    private ValueAnimator animator;
    private float animationProgress = 0f;
    private int skeletonColor;
    private int fillColorStart;
    private int fillColorEnd;

    public AudioVisualizerView(Context context) {
        super(context);
        init();
    }

    public AudioVisualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AudioVisualizerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        loadColors();
        setupPaints();
        setupBarHeights();
        setupAnimator();
    }

    private void loadColors() {
        skeletonColor = ContextCompat.getColor(getContext(), R.color.text_secondary);
        fillColorStart = ContextCompat.getColor(getContext(), R.color.colorAccent);
        fillColorEnd = ContextCompat.getColor(getContext(), R.color.colorPrimary);
    }

    private void setupPaints() {
        skeletonPaint = new Paint();
        skeletonPaint.setColor(skeletonColor);
        skeletonPaint.setStyle(Paint.Style.FILL);
        skeletonPaint.setAntiAlias(true);

        fillPaint = new Paint();
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setAntiAlias(true);
    }

    private void setupBarHeights() {
        barHeights = new float[BAR_COUNT];
        targetHeights = new float[BAR_COUNT];
        for (int i = 0; i < BAR_COUNT; i++) {
            barHeights[i] = MIN_BAR_HEIGHT_RATIO;
            targetHeights[i] = MIN_BAR_HEIGHT_RATIO;
        }
    }

    private void setupAnimator() {
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(ANIMATION_DURATION);
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(animation -> {
            animationProgress = (float) animation.getAnimatedValue();
            updateTargetHeights();
            invalidate();
        });
    }

    private void updateTargetHeights() {
        for (int i = 0; i < BAR_COUNT; i++) {
            float phase = (float) i / BAR_COUNT * 2f * (float) Math.PI;
            float sineWave = (float) Math.sin(animationProgress * 2f * (float) Math.PI + phase);
            float randomJitter = (float) (Math.random() * 0.3f - 0.15f);
            float normalizedHeight = (sineWave + 1f) / 2f;
            targetHeights[i] = MIN_BAR_HEIGHT_RATIO + normalizedHeight * (MAX_BAR_HEIGHT_RATIO - MIN_BAR_HEIGHT_RATIO) + randomJitter;
            targetHeights[i] = Math.max(MIN_BAR_HEIGHT_RATIO, Math.min(MAX_BAR_HEIGHT_RATIO, targetHeights[i]));
        }
    }

    private void interpolateHeights() {
        for (int i = 0; i < BAR_COUNT; i++) {
            barHeights[i] += (targetHeights[i] - barHeights[i]) * 0.3f;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0) return;

        float barWidth = (float) width / BAR_COUNT * 0.6f;
        float gap = (float) width / BAR_COUNT * 0.4f;
        float totalWidth = BAR_COUNT * barWidth + (BAR_COUNT - 1) * gap;
        float startX = (width - totalWidth) / 2f;

        if (isAnimating) {
            interpolateHeights();
        }

        for (int i = 0; i < BAR_COUNT; i++) {
            float x = startX + i * (barWidth + gap);
            float barHeight = height * barHeights[i];
            float top = height - barHeight;

            canvas.drawRect(x, top, x + barWidth, height, skeletonPaint);

            if (isAnimating) {
                LinearGradient gradient = new LinearGradient(
                    x, top, x, height,
                    fillColorStart, fillColorEnd,
                    Shader.TileMode.CLAMP
                );
                fillPaint.setShader(gradient);
                canvas.drawRect(x, top, x + barWidth, height, fillPaint);
            }
        }
    }

    public void startAnimation() {
        if (!isAnimating) {
            isAnimating = true;
            loadColors();
            setupPaints();
            if (animator != null) {
                animator.start();
            }
            invalidate();
        }
    }

    public void stopAnimation() {
        if (isAnimating) {
            isAnimating = false;
            if (animator != null && animator.isRunning()) {
                animator.cancel();
            }
            for (int i = 0; i < BAR_COUNT; i++) {
                barHeights[i] = MIN_BAR_HEIGHT_RATIO;
                targetHeights[i] = MIN_BAR_HEIGHT_RATIO;
            }
            invalidate();
        }
    }

    public void updateTheme() {
        stopAnimation();
        loadColors();
        setupPaints();
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimation();
        if (animator != null) {
            animator.removeAllUpdateListeners();
        }
    }
}
