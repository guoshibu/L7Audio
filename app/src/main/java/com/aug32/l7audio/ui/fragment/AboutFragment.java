package com.aug32.l7audio.ui.fragment;

import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.aug32.l7audio.base.BaseFragment;
import com.aug32.l7audio.R;
import com.aug32.l7audio.ui.activity.MainActivity;
import com.aug32.l7audio.utils.AppLog;

/**
 * 关于页面 Fragment
 *
 * <p>职责：
 * <ul>
 *   <li>显示应用版本信息</li>
 *   <li>提供返回/主页按钮</li>
 * </ul>
 *
 * <p>目标 SDK：Android 11 (API 30)
 */
public class AboutFragment extends BaseFragment {

    /** 日志标签 */
    private static final String TAG = "AboutFragment";

    /** 返回按钮 */
    private Button btnBack;
    /** 主页按钮 */
    private Button btnHome;

    /**
     * 返回布局资源 ID。
     *
     * @return 关于页面布局资源 ID
     */
    @Override
    protected int getLayoutId() {
        return R.layout.fragment_about;
    }

    /**
     * 初始化视图控件。
     *
     * <p>查找 UI 控件，获取并显示应用版本号。
     *
     * @param view Fragment 根视图
     */
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

    /**
     * 初始化数据。
     *
     * <p>关于页面无需加载额外数据，版本号已在 initViews 中获取。
     */
    @Override
    protected void initData() {
        // 无需加载数据
    }

    /**
     * 初始化事件监听器。
     *
     * <p>为返回按钮和主页按钮设置点击事件，
     * 分别导航到设置页面和主界面。
     */
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
