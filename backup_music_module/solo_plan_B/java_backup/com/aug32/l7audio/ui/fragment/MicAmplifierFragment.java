package com.aug32.l7audio.ui.fragment;

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

import com.aug32.l7audio.R;
import com.aug32.l7audio.base.BaseFragment;
import com.aug32.l7audio.data.local.config.MicConfig;
import com.aug32.l7audio.domain.audio.AudioServiceLocator;
import com.aug32.l7audio.domain.audio.MicrophoneManager;

/**
 * 麦克风放大功能 Fragment
 *
 * 职责：
 * - 显示麦克风放大控制界面
 * - 管理放大级别、噪声抑制、回声消除、啸叫抑制开关
 *
 * 架构：直接使用 MicConfig 管理配置持久化（简单读写场景，无需 ViewModel）
 *
 * 目标 SDK：Android 11 (API 30)
 */
public class MicAmplifierFragment extends BaseFragment {

    private static final String TAG = "MicAmplifierFragment";

    private TextView tvAmplifierStatus;
    private TextView tvAmplificationLevel;
    private SeekBar sbAmplification;
    private Button btnStartStop;
    private Switch swNoiseReduction;
    private Switch swEchoCancellation;
    private Switch swHowlingSuppression;
    private MicrophoneManager microphoneManager;
    private MicConfig micConfig;
    private boolean isAmplifying;

    @Override
    protected int getLayoutId() {
        return R.layout.fragment_mic_amplifier;
    }

    @Override
    protected void initViews(View view) {
        tvAmplifierStatus = view.findViewById(R.id.tv_amplifier_status);
        tvAmplificationLevel = view.findViewById(R.id.tv_amplification_level);
        sbAmplification = view.findViewById(R.id.sb_amplification);
        btnStartStop = view.findViewById(R.id.btn_start_stop);
        swNoiseReduction = view.findViewById(R.id.sw_noise_reduction);
        swEchoCancellation = view.findViewById(R.id.sw_echo_cancellation);
        swHowlingSuppression = view.findViewById(R.id.sw_howling_suppression);

        AudioServiceLocator locator = AudioServiceLocator.getInstance();
        locator.init(requireContext());
        microphoneManager = locator.getMicrophoneManager();
        SharedPreferences prefs = requireContext().getSharedPreferences(
                requireContext().getPackageName() + "_preferences", Context.MODE_PRIVATE);
        micConfig = new MicConfig(prefs);

        isAmplifying = false;
        updateStatus();
    }

    @Override
    protected void initData() {
        if (sbAmplification != null && microphoneManager != null) {
            sbAmplification.setMax(10);
            int level = micConfig.getAmplificationLevel();
            sbAmplification.setProgress(level);
            updateAmplificationLevel(level);
        }

        if (swNoiseReduction != null && microphoneManager != null) {
            swNoiseReduction.setChecked(micConfig.isNoiseReductionEnabled());
        }
        if (swEchoCancellation != null && microphoneManager != null) {
            swEchoCancellation.setChecked(micConfig.isEchoCancellationEnabled());
        }
        if (swHowlingSuppression != null && microphoneManager != null) {
            swHowlingSuppression.setChecked(micConfig.isHowlingSuppressionEnabled());
        }
    }

    @Override
    protected void initListeners() {
        // 放大级别 SeekBar 监听
        if (sbAmplification != null && microphoneManager != null) {
            sbAmplification.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && microphoneManager != null) {
                        microphoneManager.setAmplificationLevel(progress);
                        micConfig.setAmplificationLevel(progress);
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
                    micConfig.setNoiseReductionEnabled(isChecked);
                }
            });
        }

        // 回声消除开关
        if (swEchoCancellation != null && microphoneManager != null) {
            swEchoCancellation.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (microphoneManager != null) {
                    microphoneManager.setEchoCancellationEnabled(isChecked);
                    micConfig.setEchoCancellationEnabled(isChecked);
                }
            });
        }

        // 啸叫抑制开关
        if (swHowlingSuppression != null && microphoneManager != null) {
            swHowlingSuppression.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (microphoneManager != null) {
                    microphoneManager.setHowlingSuppressionEnabled(isChecked);
                    micConfig.setHowlingSuppressionEnabled(isChecked);
                }
            });
        }
    }

    /** 切换麦克风放大的启动/停止状态 */
    private void toggleAmplifier() {
        if (microphoneManager == null) {
            Toast.makeText(getSafeContext(), "麦克风管理器未初始化", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isAmplifying) {
            microphoneManager.stop();
            isAmplifying = false;
        } else {
            microphoneManager.start();
            isAmplifying = true;
        }

        updateStatus();
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (microphoneManager != null) {
            microphoneManager.stop();
        }
    }
}
