package com.aug32.l7audio;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.aug32.l7audio.audio.MicrophoneManager;

public class MicAmplifierFragment extends Fragment {
    private static final String TAG = "MicAmplifierFragment";

    private TextView tvAmplifierStatus;
    private TextView tvAmplificationLevel;
    private SeekBar sbAmplification;
    private Button btnStartStop;
    private Switch swNoiseReduction;
    private Switch swEchoCancellation;
    private Switch swHowlingSuppression;
    private MicrophoneManager microphoneManager;
    private boolean isAmplifying;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_mic_amplifier, container, false);

        // 初始化视图
        tvAmplifierStatus = view.findViewById(R.id.tv_amplifier_status);
        tvAmplificationLevel = view.findViewById(R.id.tv_amplification_level);
        sbAmplification = view.findViewById(R.id.sb_amplification);
        btnStartStop = view.findViewById(R.id.btn_start_stop);
        swNoiseReduction = view.findViewById(R.id.sw_noise_reduction);
        swEchoCancellation = view.findViewById(R.id.sw_echo_cancellation);
        swHowlingSuppression = view.findViewById(R.id.sw_howling_suppression);

        // 初始化麦克风管理器
        if (getActivity() != null) {
            MainActivity mainActivity = MainActivity.getInstance();
            if (mainActivity != null) {
                microphoneManager = mainActivity.getMicrophoneManager();
            }
        }

        // 设置初始状态
        isAmplifying = false;
        updateStatus();

        // 设置放大级别进度条
        if (sbAmplification != null && microphoneManager != null) {
            sbAmplification.setMax(10);
            int currentLevel = microphoneManager.getAmplificationLevel();
            sbAmplification.setProgress(currentLevel); // 从麦克风管理器读取当前级别
            updateAmplificationLevel(currentLevel);
            sbAmplification.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && microphoneManager != null) {
                        microphoneManager.setAmplificationLevel(progress);
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

        // 设置开始/停止按钮点击事件
        if (btnStartStop != null) {
            btnStartStop.setOnClickListener(v -> toggleAmplifier());
        }

        // 设置噪声抑制开关
        if (swNoiseReduction != null && microphoneManager != null) {
            boolean noiseReductionEnabled = microphoneManager.isNoiseReductionEnabled();
            swNoiseReduction.setChecked(noiseReductionEnabled); // 从麦克风管理器读取当前状态
            swNoiseReduction.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (microphoneManager != null) {
                    microphoneManager.setNoiseReductionEnabled(isChecked);
                }
            });
        }

        // 设置回声抑制开关
        if (swEchoCancellation != null && microphoneManager != null) {
            boolean echoCancellationEnabled = microphoneManager.isEchoCancellationEnabled();
            swEchoCancellation.setChecked(echoCancellationEnabled); // 从麦克风管理器读取当前状态
            swEchoCancellation.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (microphoneManager != null) {
                    microphoneManager.setEchoCancellationEnabled(isChecked);
                }
            });
        }

        // 设置啸叫抑制开关
        if (swHowlingSuppression != null && microphoneManager != null) {
            boolean howlingSuppressionEnabled = microphoneManager.isHowlingSuppressionEnabled();
            swHowlingSuppression.setChecked(howlingSuppressionEnabled); // 从麦克风管理器读取当前状态
            swHowlingSuppression.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (microphoneManager != null) {
                    microphoneManager.setHowlingSuppressionEnabled(isChecked);
                }
            });
        }

        return view;
    }

    private void toggleAmplifier() {
        if (microphoneManager == null) {
            Toast.makeText(getActivity(), "麦克风管理器未初始化", Toast.LENGTH_SHORT).show();
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
