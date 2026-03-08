package com.aug32.l7audio;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class AboutFragment extends Fragment {
    private static final String TAG = "AboutFragment";

    private Button btnBack;
    private Button btnHome;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_about, container, false);

        // 初始化视图
        btnBack = view.findViewById(R.id.btn_back);
        btnHome = view.findViewById(R.id.btn_home);
        TextView tvVersion = view.findViewById(R.id.tv_version);

        // 设置版本号
        if (tvVersion != null && getActivity() != null) {
            try {
                String versionName = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0).versionName;
                tvVersion.setText("版本 " + versionName);
            } catch (Exception e) {
                AppLog.e(TAG, "Failed to get version name", e);
            }
        }

        // 设置返回按钮点击事件
        btnBack.setOnClickListener(v -> {
            // 返回到设置页面
            MainActivity activity = (MainActivity) requireActivity();
            activity.showSettingsFragment();
        });

        // 设置主页按钮点击事件
        btnHome.setOnClickListener(v -> {
            // 返回到主界面
            MainActivity activity = (MainActivity) requireActivity();
            activity.showMainInterface();
        });

        return view;
    }
}
