package com.aug32.l7audio.domain.audio;

import android.content.Context;

import com.aug32.l7audio.data.local.AppConfig;
import com.aug32.l7audio.domain.audio.player.PlaybackCallback;
import com.aug32.l7audio.domain.audio.player.PlaybackController;
import com.aug32.l7audio.domain.audio.playlist.PlaylistManager;
import com.aug32.l7audio.domain.audio.playlist.ScannedMusicInfo;
import com.aug32.l7audio.utils.AppExecutors;
import com.aug32.l7audio.utils.AppLog;

import java.io.File;
import java.util.List;

/**
 * 音乐播放器管理器（外观类）
 *
 * 职责：
 * - 整合 PlaylistManager 和 PlaybackController
 * - 提供统一的对外接口
 * - 处理播放列表与播放器的联动（切歌、歌曲结束自动下一首等）
 * - 歌词加载管理
 *
 * 这是音乐模块对外的唯一入口，外部不应该直接访问 PlaylistManager 或 PlaybackController
 */
public class MusicPlayerManager {

    private static final String TAG = "MusicPlayerManager";

    // ========== 循环模式常量 ==========
    public static final int REPEAT_MODE_ALL = PlaylistManager.REPEAT_MODE_ALL;
    public static final int REPEAT_MODE_SHUFFLE = PlaylistManager.REPEAT_MODE_SHUFFLE;
    public static final int REPEAT_MODE_ONE = PlaylistManager.REPEAT_MODE_ONE;
    public static final int REPEAT_MODE_OFF = PlaylistManager.REPEAT_MODE_OFF;

    // ========== 回调接口 ==========
    public interface MusicPlayerCallback {
        void onPlaybackStarted(int index);
        void onPlaybackPaused();
        void onPlaybackStopped();
        void onPlaybackProgress(long current, long duration);
        void onPlaylistChanged();
        void onLyricsLoaded(int index);
        void onError(String error);
    }

    // ========== 扫描结果类（兼容旧接口） ==========
    public static class ScannedMusicInfoExt {
        public String filePath;
        public String contentUri;
        public String title;
        public String artist;
        public String album;
        public long duration;

        public ScannedMusicInfoExt(String filePath, String contentUri, String title,
                                   String artist, String album, long duration) {
            this.filePath = filePath;
            this.contentUri = contentUri;
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.duration = duration;
        }
    }

    // ========== 添加回调 ==========
    public interface AddMusicCallback {
        void onAddComplete(List<MusicItem> addedItems, int startPosition,
                          int skippedExistCount, int skippedFailedCount);
    }

    // ========== 成员变量 ==========
    private final Context context;
    private final AppConfig appConfig;
    private final PlaylistManager playlistManager;
    private final PlaybackController playbackController;
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private MusicPlayerCallback callback;
    private MusicItem currentPlayingItem;

    // ========== 构造函数 ==========
    public MusicPlayerManager(Context context) {
        this.context = context.getApplicationContext();
        this.appConfig = new AppConfig(this.context);
        this.playlistManager = new PlaylistManager(this.context);
        this.playbackController = new PlaybackController(this.context);

        setupListeners();
        AppLog.d(TAG, "MusicPlayerManager initialized");
    }

    private void setupListeners() {
        // 播放列表变化监听
        playlistManager.setPlaylistChangeListener(new PlaylistManager.PlaylistChangeListener() {
            @Override
            public void onPlaylistChanged() {
                if (callback != null) {
                    callback.onPlaylistChanged();
                }
            }

            @Override
            public void onCurrentIndexChanged(int newIndex) {
                // 当前索引变化，但不自动播放
            }
        });

        // 播放状态监听
        playbackController.setCallback(new PlaybackCallback() {
            @Override
            public void onStateChanged(PlaybackState state) {
                handleStateChanged(state);
            }

            @Override
            public void onProgressChanged(long position, long duration) {
                // 保存播放位置
                appConfig.setLastPlayedPosition(position);

                if (callback != null) {
                    callback.onPlaybackProgress(position, duration);
                }
            }

            @Override
            public void onSongCompleted() {
                handleSongCompleted();
            }

            @Override
            public void onError(String error) {
                if (callback != null) {
                    callback.onError(error);
                }
            }
        });
    }

    // ========== 播放控制 ==========

    /**
     * 播放指定索引的歌曲
     */
    public void start(int index, long startPosition) {
        MusicItem item = playlistManager.getItemAt(index);
        if (item == null) {
            AppLog.e(TAG, "start: Invalid index: " + index);
            return;
        }

        AppLog.d(TAG, "start: index=" + index + ", title=" + item.title + ", startPosition=" + startPosition);
        currentPlayingItem = item;
        playlistManager.setCurrentIndex(index);
        appConfig.setLastPlayedIndex(index);

        // 异步加载歌词
        loadLyricsIfNeeded(item);

        playbackController.play(item, startPosition);
    }

    /**
     * 暂停播放
     */
    public void pause() {
        playbackController.pause();
    }

    /**
     * 恢复播放
     */
    public void resume() {
        AppLog.d(TAG, "resume called, getCurrentIndex=" + playlistManager.getCurrentIndex()
                + ", hasCurrentItem=" + playbackController.getCurrentState().hasCurrentItem());
        // 如果有当前歌曲但播放器未准备好，重新播放
        if (playlistManager.getCurrentIndex() >= 0 && !playbackController.getCurrentState().hasCurrentItem()) {
            MusicItem item = playlistManager.getCurrentItem();
            if (item != null) {
                long pos = appConfig.getLastPlayedPosition();
                AppLog.d(TAG, "resume: currentItem is null in controller, restarting from saved position=" + pos);
                start(playlistManager.getCurrentIndex(), pos);
                return;
            }
        }
        playbackController.resume();
    }

    public void resume(long startPosition) {
        playbackController.resume(startPosition);
    }

    /**
     * 停止播放
     */
    public void stop() {
        playbackController.stop();
    }

    /**
     * 播放/暂停切换
     */
    public void togglePlayPause() {
        AppLog.d(TAG, "togglePlayPause called, isPlaying=" + playbackController.isPlaying()
                + ", currentIndex=" + getCurrentIndex()
                + ", playlistSize=" + playlistManager.getItemCount());
        if (playbackController.isPlaying()) {
            pause();
            return;
        }

        if (getCurrentIndex() < 0 && playlistManager.getItemCount() > 0) {
            AppLog.d(TAG, "togglePlayPause: no current index, start from first song");
            start(0, 0);
        } else {
            AppLog.d(TAG, "togglePlayPause: calling resume");
            resume();
        }
    }

    /**
     * 下一首
     */
    public void playNext() {
        int nextIndex = playlistManager.getNextIndex();
        if (nextIndex >= 0) {
            start(nextIndex, 0);
        }
    }

    /**
     * 上一首
     */
    public void playPrevious() {
        int prevIndex = playlistManager.getPreviousIndex();
        if (prevIndex >= 0) {
            start(prevIndex, 0);
        }
    }

    /**
     * 跳转进度
     * 如果播放器未准备好，自动 start 对应歌曲
     * 如果没有当前歌曲但有播放列表，从第一首开始
     */
    public void seekTo(long position) {
        if (playbackController.getCurrentState().hasCurrentItem()) {
            playbackController.seekTo(position);
            return;
        }

        int index = playlistManager.getCurrentIndex();
        if (index < 0 && playlistManager.getItemCount() > 0) {
            index = 0;
        }
        if (index >= 0) {
            MusicItem item = playlistManager.getItemAt(index);
            if (item != null) {
                AppLog.d(TAG, "seekTo: player not ready, starting index=" + index + " at position=" + position);
                start(index, position);
            }
        }
    }

    /**
     * 设置音量
     */
    public void setVolume(int volumePercent) {
        playbackController.setVolume(volumePercent / 100f);
    }

    /**
     * 更新音频输出（切换车内/车外）
     */
    public void updateAudioOutputUsage(int audioUsage) {
        playbackController.updateAudioUsage(audioUsage);
    }

    // ========== 播放列表操作 ==========

    public List<MusicItem> getMusicItems() {
        return playlistManager.getAllItems();
    }

    public int getMusicItemCount() {
        return playlistManager.getItemCount();
    }

    public MusicItem getCurrentMusicItem() {
        if (currentPlayingItem != null) {
            return currentPlayingItem.copy();
        }
        return playlistManager.getCurrentItem();
    }

    public int getCurrentIndex() {
        return playlistManager.getCurrentIndex();
    }

    public int getIndexOf(String filePath) {
        return playlistManager.getIndexOf(filePath);
    }

    public boolean isPlaying() {
        return playbackController.isPlaying();
    }

    public long getCurrentPosition() {
        long pos = playbackController.getCurrentPosition();
        if (pos == 0 && !playbackController.getCurrentState().hasCurrentItem()) {
            long savedPos = appConfig.getLastPlayedPosition();
            if (savedPos > 0) {
                return savedPos;
            }
        }
        return pos;
    }

    public long getDuration() {
        long dur = playbackController.getDuration();
        if (dur == 0 && !playbackController.getCurrentState().hasCurrentItem()) {
            MusicItem item = playlistManager.getCurrentItem();
            if (item != null && item.duration > 0) {
                return item.duration;
            }
        }
        return dur;
    }

    public PlaybackState getPlaybackState() {
        return playbackController.getCurrentState();
    }

    // ========== 循环模式 ==========

    public int getRepeatMode() {
        return playlistManager.getRepeatMode();
    }

    public void setRepeatMode(int mode) {
        playlistManager.setRepeatMode(mode);
    }

    public void setShuffleModeEnabled(boolean enabled) {
        if (enabled) {
            playlistManager.setRepeatMode(REPEAT_MODE_SHUFFLE);
        } else {
            if (playlistManager.getRepeatMode() == REPEAT_MODE_SHUFFLE) {
                playlistManager.setRepeatMode(REPEAT_MODE_ALL);
            }
        }
    }

    public boolean isShuffleModeEnabled() {
        return playlistManager.isShuffleMode();
    }

    // ========== 添加音乐 ==========

    /**
     * 添加音乐文件（通过文件路径，兼容旧接口）
     */
    public void addMusicFilesWithUris(List<String> filePaths, List<String> contentUris, AddMusicCallback callback) {
        playlistManager.addFromFilePaths(filePaths, new PlaylistManager.AddCallback() {
            @Override
            public void onAddComplete(List<MusicItem> addedItems, int startPosition,
                                     int skippedExistCount, int skippedFailedCount) {
                if (callback != null) {
                    callback.onAddComplete(addedItems, startPosition, skippedExistCount, skippedFailedCount);
                }
            }
        });
    }

    /**
     * 从扫描结果添加音乐（MediaStore 扫描结果）
     */
    public void addScannedMusicItems(List<ScannedMusicInfo> scannedList, AddMusicCallback callback) {
        playlistManager.addFromScannedInfo(scannedList, new PlaylistManager.AddCallback() {
            @Override
            public void onAddComplete(List<MusicItem> addedItems, int startPosition,
                                     int skippedExistCount, int skippedFailedCount) {
                if (callback != null) {
                    callback.onAddComplete(addedItems, startPosition, skippedExistCount, skippedFailedCount);
                }
            }
        });
    }

    /**
     * 移除指定位置的音乐
     */
    public void removeMusicItems(List<Integer> positions) {
        playlistManager.removeItems(positions);
        // 如果当前播放的歌曲被移除了，停止播放
        if (playlistManager.getCurrentIndex() < 0) {
            stop();
        }
    }

    // ========== 回调设置 ==========

    public void setCallback(MusicPlayerCallback callback) {
        this.callback = callback;
    }

    // ========== 内部方法 ==========

    private void handleStateChanged(PlaybackState state) {
        if (callback == null) return;

        try {
            switch (state.getState()) {
                case PLAYING:
                    callback.onPlaybackStarted(playlistManager.getCurrentIndex());
                    break;
                case PAUSED:
                    callback.onPlaybackPaused();
                    break;
                case STOPPED:
                case IDLE:
                    callback.onPlaybackStopped();
                    break;
                case ERROR:
                    callback.onError(state.getErrorMessage());
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Error in handleStateChanged", e);
        }
    }

    private void handleSongCompleted() {
        int nextIndex = playlistManager.getNextIndexOnSongEnd();
        if (nextIndex >= 0) {
            start(nextIndex, 0);
        } else {
            // 播放结束
            stop();
        }
    }

    private void loadLyricsIfNeeded(final MusicItem item) {
        if (item == null || item.filePath == null) return;
        if (item.lyrics != null && !item.lyrics.isEmpty()) return;

        AppExecutors.getInstance().executeOnComputeThread(() -> {
            String lyrics = loadEmbeddedLyrics(item.filePath);
            if (lyrics == null || lyrics.isEmpty()) {
                lyrics = loadLyricsFromLrcFile(item.filePath);
            }
            if (lyrics != null && !lyrics.isEmpty()) {
                item.lyrics = lyrics;
                item.lyricsModified = new File(item.filePath).lastModified();
                if (callback != null) {
                    mainHandler.post(() -> {
                        if (callback != null && currentPlayingItem != null
                                && currentPlayingItem.filePath.equals(item.filePath)) {
                            callback.onLyricsLoaded(playlistManager.getCurrentIndex());
                        }
                    });
                }
            }
        });
    }

    private String loadEmbeddedLyrics(String musicFilePath) {
        if (musicFilePath == null || musicFilePath.isEmpty()) return null;
        android.media.MediaMetadataRetriever retriever = null;
        try {
            retriever = new android.media.MediaMetadataRetriever();
            retriever.setDataSource(musicFilePath);
            int keyCode;
            try {
                java.lang.reflect.Field field = android.media.MediaMetadataRetriever.class
                        .getDeclaredField("METADATA_KEY_LYRICS");
                keyCode = field.getInt(null);
            } catch (Exception e) {
                return null;
            }
            String lyrics = retriever.extractMetadata(keyCode);
            if (lyrics != null && !lyrics.isEmpty()) {
                return lyrics;
            }
        } catch (Exception e) {
            AppLog.d(TAG, "Failed to load embedded lyrics: " + musicFilePath);
        } finally {
            if (retriever != null) {
                try {
                    retriever.release();
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private String loadLyricsFromLrcFile(String musicFilePath) {
        if (musicFilePath == null || musicFilePath.isEmpty()) return null;

        String lrcPath = musicFilePath.substring(0, musicFilePath.lastIndexOf('.')) + ".lrc";
        File lrcFile = new File(lrcPath);

        if (!lrcFile.exists()) {
            return null;
        }

        try {
            StringBuilder sb = new StringBuilder();
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(
                            new java.io.FileInputStream(lrcFile), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            AppLog.d(TAG, "Failed to load lrc: " + lrcPath);
            return null;
        }
    }

    // ========== 释放资源 ==========

    public void release() {
        playbackController.release();
    }
}
