package com.aug32.l7audio;

import android.app.Application;

import com.aug32.l7audio.domain.audio.AudioServiceLocator;

/**
 * L7Audio 应用全局 Application 类
 *
 * 职责：
 * - 应用启动时初始化全局服务定位器
 * - 提供应用级上下文给各业务模块使用
 */
public class L7AudioApp extends Application {

    /**
     * 应用创建时的回调
     * 在应用进程启动时调用，初始化音频服务定位器等全局资源
     */
    @Override
    public void onCreate() {
        super.onCreate();
        // 初始化音频服务定位器，传入 Application 上下文供全局使用
        AudioServiceLocator.getInstance().init(this);
    }
}
