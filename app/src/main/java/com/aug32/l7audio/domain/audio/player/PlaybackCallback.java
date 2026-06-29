package com.aug32.l7audio.domain.audio.player;

import com.aug32.l7audio.domain.audio.PlaybackState;

/**
 * 播放状态回调接口
 *
 * PlaybackController 通过此接口通知外部播放状态的变化，
 * 实现此接口可以监听播放器的所有关键事件，用于更新UI或执行其他响应。
 *
 * 回调方法应在主线程中调用，确保可以直接操作UI。
 */
public interface PlaybackCallback {

    /**
     * 播放状态发生变化
     *
     * 当播放器从一种状态切换到另一种状态时调用，例如：
     * 播放→暂停、停止→加载中、正常→错误等。
     *
     * @param state 最新的播放状态，包含当前所有播放信息
     */
    void onStateChanged(PlaybackState state);

    /**
     * 播放进度更新
     *
     * 播放过程中定期调用，用于更新进度条、显示当前播放时间等。
     * 更新频率通常为每秒数次。
     *
     * @param position 当前播放位置，单位毫秒
     * @param duration 歌曲总时长，单位毫秒
     */
    void onProgressChanged(long position, long duration);

    /**
     * 歌曲播放完成
     *
     * 当前歌曲正常播放完毕时调用，不包含因错误或用户操作导致的停止。
     * 通常用于自动播放下一首歌曲。
     */
    void onSongCompleted();

    /**
     * 播放出错
     *
     * 播放器遇到无法恢复的错误时调用，例如：
     * 文件不存在、格式不支持、解码失败等。
     *
     * @param error 错误描述信息
     */
    void onError(String error);
}
