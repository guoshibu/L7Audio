package com.aug32.l7audio;

import android.app.Application;

import com.aug32.l7audio.domain.audio.micoutput.MicOutputController;
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
        // 初始化车外喊话控制器（统一管理喊话/麦克风放大的开启关闭）
        MicOutputController.getInstance().init(this);

        // 注册进程级前后台观察者：App 退后台时降低进度更新频率，减少后台无效 CPU。
        // onStart/onStop 在主线程回调，直接透传前后台状态给音乐播放管理器。
        androidx.lifecycle.ProcessLifecycleOwner.get().getLifecycle().addObserver(
                new androidx.lifecycle.DefaultLifecycleObserver() {
                    @Override
                    public void onStart(@androidx.annotation.NonNull androidx.lifecycle.LifecycleOwner owner) {
                        com.aug32.l7audio.domain.audio.player.MusicPlayerManager m =
                                AudioServiceLocator.getInstance().getMusicPlayerManager();
                        if (m != null) m.setForeground(true);
                    }

                    @Override
                    public void onStop(@androidx.annotation.NonNull androidx.lifecycle.LifecycleOwner owner) {
                        com.aug32.l7audio.domain.audio.player.MusicPlayerManager m =
                                AudioServiceLocator.getInstance().getMusicPlayerManager();
                        if (m != null) m.setForeground(false);
                    }
                });
    }
}
