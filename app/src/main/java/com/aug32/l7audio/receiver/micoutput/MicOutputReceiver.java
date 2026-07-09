package com.aug32.l7audio.receiver.micoutput;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.aug32.l7audio.domain.audio.micoutput.MicOutputController;
import com.aug32.l7audio.utils.AppLog;

/**
 * 车外喊话外部按键广播接收器
 *
 * <p>职责：接收第三方 APP（如 Key Mapper、Button Mapper 等）发送的广播，
 * 触发车外喊话的开启/关闭切换。</p>
 *
 * <p>广播 Action：{@link #ACTION_OUTSIDE_MIC_TOGGLE}</p>
 * <p>触发方式：按一下开启车外喊话，再按一下关闭</p>
 *
 * <p>使用方式（第三方 APP 配置）：
 * <ul>
 *   <li>Action：com.aug32.l7audio.OUTSIDE_MIC_TOGGLE</li>
 *   <li>目标：Broadcast</li>
 * </ul>
 * </p>
 *
 * @author L7Audio
 * @since 1.4.3
 */
public class MicOutputReceiver extends BroadcastReceiver {

    private static final String TAG = "MicOutputReceiver";

    /** 广播 Action：外部按键切换车外喊话 */
    public static final String ACTION_OUTSIDE_MIC_TOGGLE = "com.aug32.l7audio.OUTSIDE_MIC_TOGGLE";

    /**
     * 接收到广播时回调
     * <p>
     * 转发给 MicOutputController.toggle()，强制车外输出。
     * Controller 内部有防抖和静音检测机制。
     * </p>
     *
     * @param context 上下文对象
     * @param intent  接收到的广播 Intent
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_OUTSIDE_MIC_TOGGLE.equals(intent.getAction())) {
            return;
        }

        AppLog.d(TAG, "Received OUTSIDE_MIC_TOGGLE broadcast, toggling announcement");

        // 确保 Controller 已初始化
        MicOutputController controller = MicOutputController.getInstance();
        if (controller != null) {
            // 首次通过广播触发时，Controller 可能未初始化
            // 使用 context.getApplicationContext() 避免持有 Receiver Context 引用
            if (!controller.isInitialized()) {
                controller.init(context.getApplicationContext());
            }
            // 强制车外输出，显示Toast（仅第三方调用时显示）
            controller.toggle(true, true);
        }
    }
}
