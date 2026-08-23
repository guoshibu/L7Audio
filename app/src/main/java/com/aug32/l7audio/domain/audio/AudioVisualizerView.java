package com.aug32.l7audio.domain.audio;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.animation.LinearInterpolator;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.aug32.l7audio.R;

/**
 * 音频可视化视图
 *
 * 职责：
 * - 显示音频播放时的柱状可视化动画效果
 * - 通过正弦波和随机抖动模拟音频频谱跳动
 * - 支持动画的启动、停止以及主题更新
 * - 自动管理动画资源生命周期，防止内存泄漏
 *
 * 目标 SDK：Android 11 (API 30)
 */
public class AudioVisualizerView extends View {

    /** 可视化柱状条的数量 */
    private static final int BAR_COUNT = 12;
    /** 动画周期时长，单位毫秒 */
    private static final long ANIMATION_DURATION = 800;
    /** 柱状条最大高度占视图高度的比例 */
    private static final float MAX_BAR_HEIGHT_RATIO = 0.8f;
    /** 柱状条最小高度占视图高度的比例 */
    private static final float MIN_BAR_HEIGHT_RATIO = 0.2f;

    /** 骨架（背景）柱状条的画笔 */
    private Paint skeletonPaint;
    /** 填充（前景）柱状条的画笔，带渐变效果 */
    private Paint fillPaint;
    /** 当前各柱状条的高度比例数组 */
    private float[] barHeights;
    /** 各柱状条目标高度比例数组，用于平滑过渡动画 */
    private float[] targetHeights;
    /** 动画是否正在运行的标志位 */
    private boolean isAnimating = false;
    /** 属性动画器，驱动可视化动画循环播放 */
    private ValueAnimator animator;
    /** 动画当前进度，范围 0f ~ 1f */
    private float animationProgress = 0f;
    /** 骨架柱状条的颜色 */
    private int skeletonColor;
    /** 填充渐变的起始颜色 */
    private int fillColorStart;
    /** 填充渐变的结束颜色 */
    private int fillColorEnd;

    /**
     * 缓存的竖直渐变 Shader，避免 onDraw 每帧、每根柱子都 new LinearGradient 造成的对象分配与 GC 压力。
     * <p>该渐变始终按整视图高度 [0, height] 构建（顶部 fillColorStart → 底部 fillColorEnd），
     * 绘制每根柱子时通过 {@link #gradientMatrix} 的局部矩阵把它映射到柱子的实际竖直范围 [top, height]，
     * 从而在不逐帧分配对象的前提下保持与原逐柱渐变完全一致的视觉效果。
     * 仅在尺寸或颜色变化时重建。
     */
    private LinearGradient fillGradient;
    /** 构建缓存渐变时使用的视图高度，用于检测尺寸变化后重建 Shader */
    private int gradientHeight = 0;
    /** 复用的局部矩阵，把整高渐变缩放/平移到单根柱子的竖直范围，避免每帧新建 Matrix */
    private final android.graphics.Matrix gradientMatrix = new android.graphics.Matrix();

    /**
     * 代码中创建视图时使用的构造函数
     *
     * @param context 上下文环境，用于获取资源和主题
     */
    public AudioVisualizerView(Context context) {
        super(context);
        init();
    }

    /**
     * XML 布局文件中使用的构造函数
     *
     * @param context 上下文环境，用于获取资源和主题
     * @param attrs   XML 中定义的属性集合
     */
    public AudioVisualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * 支持样式属性的构造函数
     *
     * @param context      上下文环境，用于获取资源和主题
     * @param attrs        XML 中定义的属性集合
     * @param defStyleAttr 默认样式属性引用
     */
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
        // 颜色可能变化（主题切换），使缓存的渐变失效，下次绘制时按新颜色重建
        fillGradient = null;
    }

    /**
     * 按当前视图高度与填充颜色构建并缓存竖直渐变 Shader。
     * <p>仅在缓存为空或高度发生变化时创建，避免 onDraw 逐帧分配对象。
     * 渐变覆盖整视图高度 [0, height]，绘制时用局部矩阵映射到单根柱子的竖直范围。
     *
     * @param height 当前视图高度（像素）
     */
    private void ensureGradient(int height) {
        if (fillGradient == null || gradientHeight != height) {
            gradientHeight = height;
            fillGradient = new LinearGradient(
                    0, 0, 0, height,
                    fillColorStart, fillColorEnd,
                    Shader.TileMode.CLAMP
            );
        }
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

    /**
     * 更新各柱状条的目标高度
     * 使用正弦波叠加随机抖动的方式，模拟音频频谱的自然跳动效果
     */
    private void updateTargetHeights() {
        for (int i = 0; i < BAR_COUNT; i++) {
            // 为每个柱状条设置不同的相位偏移，形成波浪式错落效果
            float phase = (float) i / BAR_COUNT * 2f * (float) Math.PI;
            // 正弦波基础波形，决定整体律动节奏
            float sineWave = (float) Math.sin(animationProgress * 2f * (float) Math.PI + phase);
            // 随机抖动，范围 -0.15 ~ 0.15，增加视觉随机性，避免过于机械
            float randomJitter = (float) (Math.random() * 0.3f - 0.15f);
            // 将正弦波从 [-1, 1] 归一化到 [0, 1] 区间
            float normalizedHeight = (sineWave + 1f) / 2f;
            // 计算目标高度：最小高度 + 归一化高度 * 高度范围 + 随机抖动
            targetHeights[i] = MIN_BAR_HEIGHT_RATIO + normalizedHeight * (MAX_BAR_HEIGHT_RATIO - MIN_BAR_HEIGHT_RATIO) + randomJitter;
            // 限制目标高度在合法范围内，防止抖动越界
            targetHeights[i] = Math.max(MIN_BAR_HEIGHT_RATIO, Math.min(MAX_BAR_HEIGHT_RATIO, targetHeights[i]));
        }
    }

    /**
     * 线性插值平滑过渡当前柱状条高度到目标高度
     * 使用 0.3 的插值系数，兼顾响应速度和视觉流畅度
     */
    private void interpolateHeights() {
        for (int i = 0; i < BAR_COUNT; i++) {
            // 阻尼插值：当前值向目标值靠近 30%，实现平滑缓动效果
            barHeights[i] += (targetHeights[i] - barHeights[i]) * 0.3f;
        }
    }

    /**
     * 绘制可视化视图
     * 先绘制骨架柱状条作为背景，动画运行时再叠加渐变填充柱状条
     *
     * @param canvas 用于绘制的画布对象
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        // 视图尺寸为 0 时跳过绘制，避免除零或无效绘制
        if (width == 0 || height == 0) return;

        // 柱状条宽度占单份宽度的 60%
        float barWidth = (float) width / BAR_COUNT * 0.6f;
        // 柱状条间距占单份宽度的 40%
        float gap = (float) width / BAR_COUNT * 0.4f;
        // 所有柱状条加间距的总宽度
        float totalWidth = BAR_COUNT * barWidth + (BAR_COUNT - 1) * gap;
        // 水平居中起始 X 坐标
        float startX = (width - totalWidth) / 2f;

        // 动画运行时，先插值更新高度再绘制
        if (isAnimating) {
            interpolateHeights();
            // 确保缓存的整高渐变已按当前高度/颜色构建，避免下面逐柱重复 new LinearGradient
            ensureGradient(height);
        }

        for (int i = 0; i < BAR_COUNT; i++) {
            float x = startX + i * (barWidth + gap);
            float barHeight = height * barHeights[i];
            float top = height - barHeight;

            // 始终绘制骨架（背景）柱状条
            canvas.drawRect(x, top, x + barWidth, height, skeletonPaint);

            // 动画运行时，叠加绘制渐变填充柱状条
            if (isAnimating) {
                // 复用缓存的整高渐变（[0, height] 竖直方向 start→end）。
                // 通过局部矩阵把 [0, height] 线性映射到当前柱子的 [top, height]：
                // 缩放系数 barHeight/height，再平移 top，使柱顶取 fillColorStart、柱底取 fillColorEnd，
                // 与原来"每柱单独 new LinearGradient(x, top, x, height,...)"的效果完全一致，且不再逐帧分配对象。
                // 竖直渐变颜色只随 Y 变化、与 X 无关，故原本各柱不同的 x 起点不影响配色，可安全共用同一 Shader。
                gradientMatrix.reset();
                gradientMatrix.setScale(1f, barHeight / (float) height);
                gradientMatrix.postTranslate(0f, top);
                fillGradient.setLocalMatrix(gradientMatrix);
                fillPaint.setShader(fillGradient);
                canvas.drawRect(x, top, x + barWidth, height, fillPaint);
            }
        }
    }

    /**
     * 启动音频可视化动画
     * 重新加载颜色和画笔设置，启动属性动画器并触发重绘
     * 若动画已在运行中，则不重复执行
     */
    public void startAnimation() {
        if (!isAnimating) {
            isAnimating = true;
            // 重新加载颜色和画笔，确保主题颜色为最新
            loadColors();
            setupPaints();
            if (animator != null) {
                animator.start();
            }
            invalidate();
        }
    }

    /**
     * 停止音频可视化动画
     * 取消属性动画器，将所有柱状条重置为最小高度，并触发重绘
     * 若动画未在运行，则不执行操作
     */
    public void stopAnimation() {
        if (isAnimating) {
            isAnimating = false;
            // 取消动画器，停止动画回调
            if (animator != null && animator.isRunning()) {
                animator.cancel();
            }
            // 重置所有柱状条高度到最小值，恢复初始状态
            for (int i = 0; i < BAR_COUNT; i++) {
                barHeights[i] = MIN_BAR_HEIGHT_RATIO;
                targetHeights[i] = MIN_BAR_HEIGHT_RATIO;
            }
            invalidate();
        }
    }

    /**
     * 更新视图主题
     * 先停止当前动画，重新加载颜色资源和画笔设置，然后触发重绘
     * 用于应用主题切换时更新可视化视图的配色
     */
    public void updateTheme() {
        // 先停止动画，避免在旧颜色状态下继续动画
        stopAnimation();
        // 重新加载最新的主题颜色
        loadColors();
        setupPaints();
        invalidate();
    }

    /**
     * 视图从窗口分离时的回调
     * 停止动画并移除所有动画更新监听器，防止内存泄漏
     */
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // 停止动画，释放动画资源
        stopAnimation();
        // 移除所有更新监听器，避免动画器持有视图引用导致内存泄漏
        if (animator != null) {
            animator.removeAllUpdateListeners();
        }
    }
}
