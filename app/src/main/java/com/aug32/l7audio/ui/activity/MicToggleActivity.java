package com.aug32.l7audio.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.aug32.l7audio.domain.audio.AudioServiceLocator;
import com.aug32.l7audio.domain.audio.micoutput.MicOutputController;
import com.aug32.l7audio.utils.AppLog;

/**
 * 车外喊话 Intent 触发入口 Activity
 *
 * <p>职责：提供第三方 APP 通过 Intent 方式触发车外喊话功能的入口。
 * 相比广播方式，通过 startActivity 触发能保证应用进程完整初始化，
 * 避免 AudioServiceLocator 内部各 Manager 为 null 导致的静默失败。</p>
 *
 * <p>使用方式（第三方 APP 配置）：
 * <ul>
 *   <li>Action：{@link #ACTION_TOGGLE_MIC}</li>
 *   <li>目标：Activity</li>
 *   <li>包名：com.aug32.l7audio（可选，建议添加以精确匹配）</li>
 * </ul>
 * </p>
 *
 * <p>示例代码：
 * <pre>
 * Intent intent = new Intent("com.aug32.l7audio.ACTION_TOGGLE_MIC");
 * intent.setPackage("com.aug32.l7audio");
 * intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
 * context.startActivity(intent);
 * </pre>
 * </p>
 *
 * <p>设计特点：
 * <ul>
 *   <li>透明无 UI Activity，用户无感知</li>
 *   <li>自动完成 AudioServiceLocator 和 MicOutputController 的初始化</li>
 *   <li>调用 toggle() 后立即 finish()，无界面停留</li>
 *   <li>配置 excludeFromRecents，不显示在最近任务列表</li>
 * </ul>
 * </p>
 *
 * @author L7Audio
 * @since 1.5.7
 */
public class MicToggleActivity extends AppCompatActivity {

    private static final String TAG = "MicToggleActivity";

    /** Intent Action：触发车外喊话开关切换 */
    public static final String ACTION_TOGGLE_MIC = "com.aug32.l7audio.ACTION_TOGGLE_MIC";

    /**
     * Activity 创建时回调
     * <p>
     * 执行流程：
     * 1. 获取 ApplicationContext
     * 2. 初始化 AudioServiceLocator（确保各 Manager 可用）
     * 3. 初始化 MicOutputController
     * 4. 调用 toggle() 切换喊话状态
     * 5. 立即 finish() 结束 Activity
     * </p>
     *
     * @param savedInstanceState 保存的实例状态
     */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppLog.d(TAG, "onCreate: received intent, action=" + getIntent().getAction());

        Context appContext = getApplicationContext();

        AudioServiceLocator locator = AudioServiceLocator.getInstance();
        locator.init(appContext);

        MicOutputController controller = MicOutputController.getInstance();
        if (!controller.isInitialized()) {
            controller.init(appContext);
        }

        controller.toggle(true, true);

        finish();
    }

    /**
     * 启动 MicToggleActivity（内部调用）
     *
     * @param context 上下文对象
     */
    public static void launch(Context context) {
        Intent intent = new Intent(context, MicToggleActivity.class);
        intent.setAction(ACTION_TOGGLE_MIC);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_ANIMATION
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        context.startActivity(intent);
    }
}