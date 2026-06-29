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
 * Fragment 基类，提供应用内所有 Fragment 的通用基础能力。
 *
 * <p>主要职责：
 * <ul>
 *   <li>统一布局加载：通过模板方法模式规范 Fragment 的初始化流程</li>
 *   <li>视图缓存：维护 rootView 引用，避免重复 inflate</li>
 *   <li>安全访问：提供安全获取 Context、Activity、根视图的方法</li>
 *   <li>生命周期封装：将初始化拆分为 initViews/initData/initListeners 三个阶段</li>
 * </ul>
 *
 * <p>设计意图：
 * 使用模板方法模式定义 Fragment 的初始化骨架，子类只需实现三个抽象方法，
 * 确保所有 Fragment 遵循一致的初始化顺序，减少因初始化顺序不当导致的问题。
 *
 * <p>目标 SDK：Android 11 (API 30)
 * <br>最低 SDK：Android 11 (API 30)
 */
public abstract class BaseFragment extends Fragment {

    /** Fragment 根视图，onCreateView 中 inflate 后缓存，供子类直接使用 */
    protected View rootView;

    /**
     * 创建 Fragment 视图的回调方法。
     *
     * <p>按照固定顺序执行初始化流程：
     * 1. inflate 布局文件，创建根视图
     * 2. 调用 initViews 初始化视图控件
     * 3. 调用 initData 加载数据
     * 4. 调用 initListeners 设置事件监听
     *
     * <p>采用此固定顺序的原因：确保视图先创建、数据后加载、监听最后绑定，
     * 避免因视图未就绪或数据未加载导致的空指针或事件响应异常。
     *
     * @param inflater 布局填充器
     * @param container 父容器
     * @param savedInstanceState 保存的状态
     * @return 创建的根视图
     */
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
