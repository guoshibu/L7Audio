package com.aug32.l7audio.domain.audio.playlist;

import java.util.List;

import com.aug32.l7audio.domain.audio.MusicItem;

/**
 * 音乐来源接口
 *
 * 定义音乐列表加载的统一接口，支持多种音乐来源的扩展。
 * 采用策略模式，不同的音乐来源实现此接口即可无缝接入播放列表系统。
 *
 * 预留扩展点：未来可以实现多种音乐来源，如：
 * - MediaStoreMusicSource（系统媒体库扫描）
 * - FilePickerMusicSource（自定义文件选择器）
 * - NetworkMusicSource（网络音乐）
 * - PlaylistFileMusicSource（播放列表文件）
 */
public interface MusicSource {

    /**
     * 加载音乐列表
     *
     * 异步加载音乐列表，加载完成后通过回调返回结果。
     * 实现类应在后台线程执行加载操作，避免阻塞主线程。
     *
     * @param callback 加载回调，加载成功或失败时调用
     */
    void loadMusic(LoadCallback callback);

    /**
     * 获取来源名称
     *
     * 用于日志输出或界面显示，标识音乐的来源渠道。
     *
     * @return 来源名称，例如"系统媒体库"、"本地文件夹"等
     */
    String getName();

    /**
     * 音乐加载回调接口
     *
     * 用于接收音乐列表加载的结果，包括成功和失败两种情况。
     */
    interface LoadCallback {

        /**
         * 加载成功
         *
         * 音乐列表加载完成时调用，返回加载到的所有音乐项。
         *
         * @param items 加载到的音乐项列表，可能为空列表但不为null
         */
        void onSuccess(List<MusicItem> items);

        /**
         * 加载失败
         *
         * 音乐列表加载过程中发生错误时调用。
         *
         * @param error 错误描述信息
         */
        void onError(String error);
    }
}
