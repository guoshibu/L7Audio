package com.aug32.l7audio.ui.fragment;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.aug32.l7audio.utils.AppLog;
import com.aug32.l7audio.R;
import com.aug32.l7audio.base.BaseFragment;
import com.aug32.l7audio.ui.activity.MainActivity;

/**
 * 关于页面 Fragment
 *
 * 职责：
 * - 显示应用版本信息
 * - 提供返回/主页按钮
 *
 * 目标 SDK：Android 11 (API 30)
 */
public class AboutFragment extends BaseFragment {

    private static final String TAG = "AboutFragment";

    private Button btnBack;
    private Button btnHome;

    @Override
    protected int getLayoutId() {
        return R.layout.fragment_about;
    }

    @Override
    protected void initViews(View view) {
        btnBack = view.findViewById(R.id.btn_back);
        btnHome = view.findViewById(R.id.btn_home);
        TextView tvVersion = view.findViewById(R.id.tv_version);

        if (tvVersion != null && getSafeActivity() != null) {
            try {
                String versionName = getSafeActivity().getPackageManager()
                        .getPackageInfo(getSafeActivity().getPackageName(), 0).versionName;
                tvVersion.setText("版本 " + versionName);
            } catch (Exception e) {
                AppLog.e(TAG, "Failed to get version name", e);
            }
        }
    }

    @Override
    protected void initData() {
        // 无需加载数据
    }

    @Override
    protected void initListeners() {
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                MainActivity activity = (MainActivity) requireActivity();
                activity.showSettingsFragment();
            });
        }

        if (btnHome != null) {
            btnHome.setOnClickListener(v -> {
                MainActivity activity = (MainActivity) requireActivity();
                activity.showMainInterface();
            });
        }
    }
}
