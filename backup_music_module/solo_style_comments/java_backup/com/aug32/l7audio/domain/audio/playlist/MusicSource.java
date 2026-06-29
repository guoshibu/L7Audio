package com.aug32.l7audio.domain.audio.playlist;

import com.aug32.l7audio.domain.audio.MusicItem;

import java.util.List;

/**
 * 音乐来源接口
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
     * @param callback 加载回调
     */
    void loadMusic(LoadCallback callback);

    /**
     * 获取来源名称（用于日志或显示）
     */
    String getName();

    /**
     * 加载回调
     */
    interface LoadCallback {
        /** 加载成功 */
        void onSuccess(List<MusicItem> items);

        /** 加载失败 */
        void onError(String error);
    }
}
