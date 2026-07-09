package com.aug32.l7audio.ui.fragment.micoutput;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.aug32.l7audio.base.BaseFragment;
import com.aug32.l7audio.data.local.config.micoutput.MicOutputConfig;
import com.aug32.l7audio.domain.audio.AudioServiceLocator;
import com.aug32.l7audio.domain.audio.micoutput.MicOutputController;
import com.aug32.l7audio.domain.audio.micoutput.MicrophoneManager;
import com.aug32.l7audio.R;

/**
 * 麦克风放大功能 Fragment
 *
 * <p>职责：
 * <ul>
 *   <li>显示麦克风放大控制界面</li>
 *   <li>管理放大级别、噪声抑制、回声消除、啸叫抑制开关</li>
 * </ul>
 *
 * <p>架构：直接使用 MicOutputConfig 管理配置持久化（简单读写场景，无需 ViewModel）。
 * 配置变更时立即写入持久化存储，确保下次启动时恢复用户设置。
 *
 * <p>目标 SDK：Android 11 (API 30)
 */
public class MicOutputFragment extends BaseFragment {

    /** 日志标签 */
    private static final String TAG = "MicOutputFragment";

    /** 放大器状态文本 */
    private TextView tvAmplifierStatus;
    /** 放大级别数值显示 */
    private TextView tvAmplificationLevel;
    /** 放大级别调节滑块 */
    private SeekBar sbAmplification;
    /** 开始/停止放大按钮 */
    private Button btnStartStop;
    /** 噪声抑制开关 */
    private Switch swNoiseReduction;
    /** 回声消除开关 */
    private Switch swEchoCancellation;
    /** 啸叫抑制开关 */
    private Switch swHowlingSuppression;
    /** 自动增益控制开关 */
    private Switch swAgc;
    /** 麦克风管理器（音频处理核心） */
    private MicrophoneManager microphoneManager;
    /** 麦克风配置（持久化存储） */
    private MicOutputConfig micOutputConfig;
    /** 是否正在放大的状态标志（与Controller同步） */
    private boolean isAmplifying;
    /** 喊话状态监听器，用于同步 UI */
    private MicOutputController.MicOutputListener micOutputListener = new MicOutputController.MicOutputListener() {
        @Override
        public void onAnnouncementStateChanged(boolean isAnnouncing) {
            isAmplifying = isAnnouncing;
            updateStatus();
        }

        @Override
        public void onAnnouncementAutoClosed(String reason) {
            isAmplifying = false;
            updateStatus();
        }
    };

    /**
     * 返回布局资源 ID。
     *
     * @return 麦克风放大页面布局资源 ID
     */
    @Override
    protected int getLayoutId() {
        return R.layout.fragment_mic_amplifier;
    }

    /**
     * 初始化视图控件。
     *
     * <p>查找所有 UI 控件，初始化麦克风管理器和配置对象，
     * 并刷新初始状态显示。
     *
     * @param view Fragment 根视图
     */
    @Override
    protected void initViews(View view) {
        tvAmplifierStatus = view.findViewById(R.id.tv_amplifier_status);
        tvAmplificationLevel = view.findViewById(R.id.tv_amplification_level);
        sbAmplification = view.findViewById(R.id.sb_amplification);
        btnStartStop = view.findViewById(R.id.btn_start_stop);
        swNoiseReduction = view.findViewById(R.id.sw_noise_reduction);
        swEchoCancellation = view.findViewById(R.id.sw_echo_cancellation);
        swHowlingSuppression = view.findViewById(R.id.sw_howling_suppression);
        swAgc = view.findViewById(R.id.sw_agc);

        AudioServiceLocator locator = AudioServiceLocator.getInstance();
        locator.init(requireContext());
        microphoneManager = locator.getMicrophoneManager();
        SharedPreferences prefs = requireContext().getSharedPreferences(
                requireContext().getPackageName() + "_preferences", Context.MODE_PRIVATE);
        micOutputConfig = new MicOutputConfig(prefs);

        isAmplifying = false;
        updateStatus();
    }

    /**
     * 初始化数据。
     *
     * <p>从 MicOutputConfig 读取保存的配置，包括放大级别和各开关状态，
     * 并应用到 UI 控件上，确保用户看到上次保存的设置。
     */
    @Override
    protected void initData() {
        if (sbAmplification != null && microphoneManager != null) {
            sbAmplification.setMax(10);
            int level = micOutputConfig.getAmplificationLevel();
            sbAmplification.setProgress(level);
            updateAmplificationLevel(level);
        }

        if (swNoiseReduction != null && microphoneManager != null) {
            swNoiseReduction.setChecked(micOutputConfig.isNoiseReductionEnabled());
        }
        if (swEchoCancellation != null && microphoneManager != null) {
            swEchoCancellation.setChecked(micOutputConfig.isEchoCancellationEnabled());
        }
        if (swHowlingSuppression != null && microphoneManager != null) {
            swHowlingSuppression.setChecked(micOutputConfig.isHowlingSuppressionEnabled());
        }
        if (swAgc != null && microphoneManager != null) {
            swAgc.setChecked(micOutputConfig.isAgcEnabled());
        }
    }

    /**
     * 初始化事件监听器。
     *
     * <p>为放大级别滑块、开始/停止按钮、各功能开关设置监听器，
     * 用户操作时实时更新麦克风管理器状态并保存配置。
     */
    @Override
    protected void initListeners() {
        // 放大级别 SeekBar 监听
        if (sbAmplification != null && microphoneManager != null) {
            sbAmplification.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && microphoneManager != null) {
                        microphoneManager.setAmplificationLevel(progress);
                        micOutputConfig.setAmplificationLevel(progress);
                        updateAmplificationLevel(progress);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
        }

        // 开始/停止按钮
        if (btnStartStop != null) {
            btnStartStop.setOnClickListener(v -> toggleAmplifier());
        }

        // 噪声抑制开关
        if (swNoiseReduction != null && microphoneManager != null) {
            swNoiseReduction.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (microphoneManager != null) {
                    microphoneManager.setNoiseReductionEnabled(isChecked);
                    micOutputConfig.setNoiseReductionEnabled(isChecked);
                }
            });
        }

        // 回声消除开关
        if (swEchoCancellation != null && microphoneManager != null) {
            swEchoCancellation.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (microphoneManager != null) {
                    microphoneManager.setEchoCancellationEnabled(isChecked);
                    micOutputConfig.setEchoCancellationEnabled(isChecked);
                }
            });
        }

        // 啸叫抑制开关
        if (swHowlingSuppression != null && microphoneManager != null) {
            swHowlingSuppression.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (microphoneManager != null) {
                    microphoneManager.setHowlingSuppressionEnabled(isChecked);
                    micOutputConfig.setHowlingSuppressionEnabled(isChecked);
                }
            });
        }

        // 自动增益控制开关
        if (swAgc != null && microphoneManager != null) {
            swAgc.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (microphoneManager != null) {
                    microphoneManager.setAgcEnabled(isChecked);
                    micOutputConfig.setAgcEnabled(isChecked);
                }
            });
        }
    }

    /** 切换麦克风放大的启动/停止状态（通过MicOutputController统一管理） */
    private void toggleAmplifier() {
        MicOutputController.getInstance().toggle(false);
    }

    /** 根据 isAmplifying 状态刷新界面 */
    private void updateStatus() {
        if (tvAmplifierStatus != null) {
            tvAmplifierStatus.setText(isAmplifying ? "状态: 正在放大" : "状态: 已停止");
        }

        if (btnStartStop != null) {
            if (isAmplifying) {
                btnStartStop.setText("停止放大");
                btnStartStop.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.colorAccent));
            } else {
                btnStartStop.setText("开始放大");
                btnStartStop.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.colorPrimary));
            }
        }
    }

    /** 更新显示的放大级别数值 */
    private void updateAmplificationLevel(int level) {
        if (tvAmplificationLevel != null) {
            tvAmplificationLevel.setText(String.valueOf(level));
        }
    }

    /**
     * Fragment 视图创建完成时调用
     * <p>
     * 注册喊话状态监听器，确保视图可见时能同步状态。
     */
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MicOutputController.getInstance().addListener(micOutputListener);
        isAmplifying = MicOutputController.getInstance().isAnnouncing();
        updateStatus();
    }

    /**
     * Fragment 视图销毁时的回调。
     * <p>
     * 注销喊话状态监听器，避免内存泄漏。
     * 麦克风停止由 MicOutputController 统一管理。
     */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        MicOutputController.getInstance().removeListener(micOutputListener);
    }

    /**
     * Fragment 销毁时的回调。
     * <p>
     * 麦克风停止由 MicOutputController 统一管理，此处不做操作。
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
