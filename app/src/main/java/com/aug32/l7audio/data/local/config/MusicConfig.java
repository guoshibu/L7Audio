package com.aug32.l7audio.data.local.config;

import android.content.SharedPreferences;

/**
 * 音乐播放配置
 *
 * 职责：管理音乐播放列表、循环模式、随机模式以及上次播放的位置和索引等状态
 * 通过 SharedPreferences 持久化存储音乐播放相关配置
 */
public class MusicConfig {

    private static final String PREF_MUSIC_PLAYLIST = "music_playlist";
    private static final String PREF_LAST_PLAYED_INDEX = "last_played_index";
    private static final String PREF_LAST_PLAYED_POSITION = "last_played_position";
    private static final String PREF_REPEAT_MODE = "repeat_mode";
    private static final String PREF_SHUFFLE_MODE = "shuffle_mode";

    private final SharedPreferences preferences;

    /**
     * 构造函数
     *
     * @param preferences SharedPreferences 实例，用于持久化存储音乐播放配置
     */
    public MusicConfig(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    public String getMusicPlaylist() {
        return preferences.getString(PREF_MUSIC_PLAYLIST, "");
    }

    public void setMusicPlaylist(String playlistJson) {
        preferences.edit().putString(PREF_MUSIC_PLAYLIST, playlistJson).apply();
    }

    public int getLastPlayedIndex() {
        return preferences.getInt(PREF_LAST_PLAYED_INDEX, -1);
    }

    public void setLastPlayedIndex(int index) {
        preferences.edit().putInt(PREF_LAST_PLAYED_INDEX, index).apply();
    }

    public long getLastPlayedPosition() {
        return preferences.getLong(PREF_LAST_PLAYED_POSITION, 0);
    }

    public void setLastPlayedPosition(long position) {
        preferences.edit().putLong(PREF_LAST_PLAYED_POSITION, position).apply();
    }

    public int getRepeatMode() {
        return preferences.getInt(PREF_REPEAT_MODE, 0);
    }

    public void setRepeatMode(int mode) {
        preferences.edit().putInt(PREF_REPEAT_MODE, mode).apply();
    }

    public boolean isShuffleModeEnabled() {
        return preferences.getBoolean(PREF_SHUFFLE_MODE, false);
    }

    public void setShuffleModeEnabled(boolean enabled) {
        preferences.edit().putBoolean(PREF_SHUFFLE_MODE, enabled).apply();
    }
}
