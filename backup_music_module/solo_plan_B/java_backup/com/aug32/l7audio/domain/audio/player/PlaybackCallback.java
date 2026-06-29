package com.aug32.l7audio.domain.audio.player;

import com.aug32.l7audio.domain.audio.PlaybackState;

/**
 * 播放状态回调接口
 *
 * PlaybackController 通过此接口通知外部状态变化
 */
public interface PlaybackCallback {
    /** 状态发生变化（播放/暂停/停止/加载中等） */
    void onStateChanged(PlaybackState state);

    /** 播放进度更新 */
    void onProgressChanged(long position, long duration);

    /** 歌曲播放完成 */
    void onSongCompleted();

    /** 播放错误 */
    void onError(String error);
}
