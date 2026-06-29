package com.aug32.l7audio.base;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * Fragment 基类，提供通用功能：
 * - 统一的布局 inflate 方法
 * - Context 安全获取
 * - 公共初始化入口
 *
 * 目标 SDK：Android 11 (API 30)
 * 最低 SDK：Android 11 (API 30)
 */
public abstract class BaseFragment extends Fragment {

    protected View rootView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(getLayoutId(), container, false);
        initViews(rootView);
        initData();
        initListeners();
        return rootView;
    }

    /**
     * 返回布局资源 ID
     *
     * @return 布局 XML 资源 ID
     */
    protected abstract int getLayoutId();

    /**
     * 初始化视图控件
     * 在 onCreateView 中调用，此时 rootView 已创建
     *
     * @param view rootView
     */
    protected abstract void initViews(View view);

    /**
     * 初始化数据
     * 在视图初始化后调用，用于加载数据等
     */
    protected abstract void initData();

    /**
     * 初始化事件监听
     * 在数据初始化后调用，用于设置点击事件等
     */
    protected abstract void initListeners();

    /**
     * 安全获取 Context
     * 优先返回非空 Context，避免空指针
     *
     * @return Context 或 null（仅在 Fragment 未 attached 时）
     */
    protected Context getSafeContext() {
        if (getActivity() != null && isAdded()) {
            return requireContext();
        }
        return null;
    }

    /**
     * 安全获取 Activity
     *
     * @return Activity 或 null
     */
    protected androidx.appcompat.app.AppCompatActivity getSafeActivity() {
        if (getActivity() instanceof androidx.appcompat.app.AppCompatActivity) {
            return (androidx.appcompat.app.AppCompatActivity) getActivity();
        }
        return null;
    }

    /**
     * 安全获取根视图
     *
     * @return rootView 或 null
     */
    protected View getRootView() {
        return rootView;
    }

    /**
     * 根据资源 ID 查找视图（空安全）
     *
     * @param resId 视图资源 ID
     * @param <T>   视图类型
     * @return 查找到的视图或 null
     */
    @Nullable
    protected <T extends View> T findViewById(int resId) {
        if (rootView != null) {
            return rootView.findViewById(resId);
        }
        return null;
    }
}
