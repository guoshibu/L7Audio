package com.aug32.l7audio.domain.audio.player;

import android.content.Context;

import java.io.File;
import java.util.List;

import com.aug32.l7audio.data.local.AppConfig;
import com.aug32.l7audio.domain.audio.player.PlaybackCallback;
import com.aug32.l7audio.domain.audio.player.PlaybackController;
import com.aug32.l7audio.domain.audio.player.PlaylistManager;
import com.aug32.l7audio.service.player.AudioForegroundService;
import com.aug32.l7audio.utils.AlbumArtCache;
import com.aug32.l7audio.utils.AppExecutors;
import com.aug32.l7audio.utils.AppLog;

/**
 * 音乐播放器管理器（外观类）
 *
 * <p>职责：
 * <ul>
 *   <li>整合 PlaylistManager 和 PlaybackController，提供统一的对外接口</li>
 *   <li>处理播放列表与播放器的联动（切歌、歌曲结束自动下一首等）</li>
 *   <li>歌词加载管理（内嵌歌词 + 同名 .lrc 文件）</li>
 *   <li>保存播放位置，支持断点续播</li>
 * </ul>
 *
 * <p>设计意图：
 * <ul>
 *   <li>采用外观模式（Facade），作为音乐模块对外的唯一入口</li>
 *   <li>封装 PlaylistManager 和 PlaybackController 的交互细节</li>
 *   <li>外部模块不应直接访问 PlaylistManager 或 PlaybackController</li>
 *   <li>通过回调接口向上层通知播放状态变化，实现解耦</li>
 * </ul>
 *
 * <p>这是音乐模块对外的唯一入口，外部不应该直接访问 PlaylistManager 或 PlaybackController
 */
public class MusicPlayerManager {

    // 日志标签
    private static final String TAG = "MusicPlayerManager";

    // ========== 循环模式常量 ==========
    /** 列表循环模式：按顺序循环播放整个列表 */
    public static final int REPEAT_MODE_ALL = PlaylistManager.REPEAT_MODE_ALL;
    /** 随机播放模式：随机选择下一首歌曲 */
    public static final int REPEAT_MODE_SHUFFLE = PlaylistManager.REPEAT_MODE_SHUFFLE;
    /** 单曲循环模式：重复播放当前歌曲 */
    public static final int REPEAT_MODE_ONE = PlaylistManager.REPEAT_MODE_ONE;
    /** 单曲播放模式：当前歌曲播放结束后停止 */
    public static final int REPEAT_MODE_OFF = PlaylistManager.REPEAT_MODE_OFF;

    // ========== 回调接口 ==========
    /**
     * 音乐播放器回调接口
     *
     * <p>用于向外部通知播放状态、进度、播放列表变化等事件。
     */
    public interface MusicPlayerCallback {
        /**
         * 播放开始时回调
         *
         * @param index 当前播放歌曲在播放列表中的索引
         */
        void onPlaybackStarted(int index);

        /**
         * 播放暂停时回调
         */
        void onPlaybackPaused();

        /**
         * 播放停止时回调
         */
        void onPlaybackStopped();

        /**
         * 播放进度更新时回调
         *
         * @param current  当前播放位置（毫秒）
         * @param duration 歌曲总时长（毫秒）
         */
        void onPlaybackProgress(long current, long duration);

        /**
         * 播放列表变化时回调（添加/删除歌曲）
         */
        void onPlaylistChanged();

        /**
         * 歌词加载完成时回调
         *
         * @param index 歌词对应的歌曲索引
         */
        void onLyricsLoaded(int index);

        /**
         * 播放出错时回调
         *
         * @param error 错误信息
         */
        void onError(String error);
    }

    // ========== 添加回调 ==========
    /**
     * 添加音乐完成回调接口
     */
    public interface AddMusicCallback {
        /**
         * 添加完成时回调
         *
         * @param addedItems          成功添加的音乐项列表
         * @param startPosition       添加的起始位置
         * @param skippedExistCount   因已存在而跳过的数量
         * @param skippedFailedCount  因失败而跳过的数量
         */
        void onAddComplete(List<MusicItem> addedItems, int startPosition,
                          int skippedExistCount, int skippedFailedCount);
    }

    // ========== 成员变量 ==========
    // 应用上下文
    private final Context context;
    // 应用配置管理器，用于持久化播放位置等配置
    private final AppConfig appConfig;
    // 播放列表管理器
    private final PlaylistManager playlistManager;
    // 播放控制器
    private final PlaybackController playbackController;
    // 主线程 Handler，用于将回调切换到主线程执行
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    // 播放器回调接口
    private MusicPlayerCallback callback;
    // 当前正在播放的音乐项（用于歌词加载等场景）
    private MusicItem currentPlayingItem;

    // ========== 构造函数 ==========
    /**
     * 构造函数，初始化音乐播放器管理器
     *
     * @param context 上下文对象，内部会自动转换为 ApplicationContext 以避免内存泄漏
     */
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
                // 转发播放列表变化事件给外部回调
                if (callback != null) {
                    callback.onPlaylistChanged();
                }
            }

            @Override
            public void onCurrentIndexChanged(int newIndex) {
                // 当前索引变化，但不自动播放
                // 原因：索引变化可能由用户手动触发，是否播放由上层决定
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
                // 保存播放位置，用于下次恢复播放
                appConfig.setLastPlayedPosition(position);

                // 转发进度变化事件
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
                // 转发错误事件
                if (callback != null) {
                    callback.onError(error);
                }
            }
        });
    }

    // ========== 播放控制 ==========

    /**
     * 播放指定索引的歌曲
     *
     * <p>播放流程：获取音乐项 → 更新当前索引 → 加载歌词 → 调用播放器播放。
     *
     * @param index         歌曲在播放列表中的索引
     * @param startPosition 起始播放位置（毫秒），为 0 时从头开始
     */
    public void start(int index, long startPosition) {
        MusicItem item = playlistManager.getItemAt(index);
        // 索引无效时直接返回
        if (item == null) {
            AppLog.e(TAG, "start: Invalid index: " + index);
            return;
        }

        AppLog.d(TAG, "start: index=" + index + ", title=" + item.title + ", startPosition=" + startPosition);
        currentPlayingItem = item;
        // 更新播放列表当前索引
        playlistManager.setCurrentIndex(index);
        // 持久化保存当前播放索引
        appConfig.setLastPlayedIndex(index);

        // 异步加载歌词，不阻塞播放流程
        loadLyricsIfNeeded(item);

        // 扫描时不再提取封面，延迟到播放时按需提取
        ensureAlbumArt(item);

        playbackController.play(item, startPosition);

        // 同步更新 MediaSession（歌曲信息 + 播放状态）
        notifyMediaSession(item, true, startPosition);

        // 通知前台服务更新通知栏
        AudioForegroundService.notifyUpdate(context);
    }

    /**
     * 暂停播放
     */
    public void pause() {
        playbackController.pause();
        // 同步更新 MediaSession 播放状态
        notifyMediaSessionPlaybackState(false, playbackController.getCurrentPosition());
        // 通知前台服务更新通知栏
        AudioForegroundService.notifyUpdate(context);
    }

    /**
     * 恢复播放
     *
     * <p>如果播放器未准备好但有当前歌曲，会重新从上次保存的位置开始播放。
     */
    public void resume() {
        AppLog.d(TAG, "resume called, getCurrentIndex=" + playlistManager.getCurrentIndex()
                + ", hasCurrentItem=" + playbackController.getCurrentState().hasCurrentItem());
        // 如果有当前歌曲但播放器未准备好（如 stop 后 resume），重新播放
        // 原因：播放器 stop 后媒体项会被清除，需要重新设置
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
        // 同步更新 MediaSession 播放状态
        notifyMediaSessionPlaybackState(true, playbackController.getCurrentPosition());
        // 通知前台服务更新通知栏
        AudioForegroundService.notifyUpdate(context);
    }

    /**
     * 停止播放
     */
    public void stop() {
        playbackController.stop();
        // 同步更新 MediaSession 播放状态
        notifyMediaSessionPlaybackState(false, 0);
        // 通知前台服务更新通知栏
        AudioForegroundService.notifyUpdate(context);
    }

    /**
     * 播放/暂停切换
     *
     * <p>逻辑：
     * <ul>
     *   <li>正在播放 → 暂停</li>
     *   <li>未播放且无当前歌曲但有列表 → 从第一首开始</li>
     *   <li>未播放且有当前歌曲 → 恢复播放</li>
     * </ul>
     */
    public void togglePlayPause() {
        AppLog.d(TAG, "togglePlayPause called, isPlaying=" + playbackController.isPlaying()
                + ", currentIndex=" + getCurrentIndex()
                + ", playlistSize=" + playlistManager.getItemCount());
        // 正在播放则暂停
        if (playbackController.isPlaying()) {
            pause();
            return;
        }

        // 无当前播放但有播放列表 → 从第一首开始
        if (getCurrentIndex() < 0 && playlistManager.getItemCount() > 0) {
            AppLog.d(TAG, "togglePlayPause: no current index, start from first song");
            start(0, 0);
        } else {
            // 有当前播放 → 恢复播放
            AppLog.d(TAG, "togglePlayPause: calling resume");
            resume();
        }
    }

    /**
     * 播放下一首
     *
     * <p>根据当前循环模式计算下一首索引并播放。
     */
    public void playNext() {
        int nextIndex = playlistManager.getNextIndex();
        if (nextIndex >= 0) {
            start(nextIndex, 0);
        }
    }

    /**
     * 播放上一首
     *
     * <p>根据当前循环模式计算上一首索引并播放。
     */
    public void playPrevious() {
        int prevIndex = playlistManager.getPreviousIndex();
        if (prevIndex >= 0) {
            start(prevIndex, 0);
        }
    }

    /**
     * 跳转到指定播放位置
     *
     * <p>处理多种场景：
     * <ul>
     *   <li>播放器已准备好 → 直接跳转</li>
     *   <li>播放器未准备好但有当前歌曲 → 重新播放并跳转</li>
     *   <li>无当前歌曲但有播放列表 → 从第一首开始播放并跳转</li>
     * </ul>
     *
     * @param position 目标播放位置（毫秒）
     */
    public void seekTo(long position) {
        // 播放器已准备好 → 直接跳转
        if (playbackController.getCurrentState().hasCurrentItem()) {
            playbackController.seekTo(position);
            return;
        }

        // 播放器未准备好 → 需要先启动播放
        int index = playlistManager.getCurrentIndex();
        // 无当前索引但有播放列表 → 从第一首开始
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
     *
     * @param volumePercent 音量百分比，范围 [0, 100]
     */
    public void setVolume(int volumePercent) {
        playbackController.setVolume(volumePercent / 100f);
    }

    /**
     * 更新音频输出用法（切换车内/车外音频通道）
     *
     * @param audioUsage 音频用法常量
     */
    public void updateAudioOutputUsage(int audioUsage) {
        playbackController.updateAudioUsage(audioUsage);
    }

    // ========== 播放列表操作 ==========

    /**
     * 获取所有音乐项
     *
     * @return 所有音乐项的副本列表
     */
    public List<MusicItem> getMusicItems() {
        return playlistManager.getAllItems();
    }

    /**
     * 获取当前播放的音乐项
     *
     * <p>优先返回当前正在播放的音乐项，如果没有则从播放列表中获取。
     *
     * @return 当前播放的音乐项副本，无当前播放时返回 null
     */
    public MusicItem getCurrentMusicItem() {
        // 优先使用 currentPlayingItem，确保返回的是当前正在播放的歌曲
        // 原因：播放列表的 currentIndex 可能与实际播放的歌曲不同步（如刚切换时）
        if (currentPlayingItem != null) {
            return currentPlayingItem.copy();
        }
        return playlistManager.getCurrentItem();
    }

    /**
     * 获取当前播放索引
     *
     * @return 当前播放索引，无当前播放时返回 -1
     */
    public int getCurrentIndex() {
        return playlistManager.getCurrentIndex();
    }

    /**
     * 判断是否正在播放
     *
     * @return true 表示正在播放，false 表示未播放
     */
    public boolean isPlaying() {
        return playbackController.isPlaying();
    }

    /**
     * 获取当前播放位置
     *
     * <p>当播放器未准备好时，回退使用保存的播放位置。
     *
     * @return 当前播放位置（毫秒）
     */
    public long getCurrentPosition() {
        long pos = playbackController.getCurrentPosition();
        // 播放器未准备好时，使用保存的播放位置
        // 原因：播放器未初始化时位置为0，但用户可能期望看到上次播放的位置
        if (pos == 0 && !playbackController.getCurrentState().hasCurrentItem()) {
            long savedPos = appConfig.getLastPlayedPosition();
            if (savedPos > 0) {
                return savedPos;
            }
        }
        return pos;
    }

    /**
     * 获取当前歌曲的总时长
     *
     * <p>当播放器未准备好时，回退使用播放列表中的时长信息。
     *
     * @return 歌曲总时长（毫秒）
     */
    public long getDuration() {
        long dur = playbackController.getDuration();
        // 播放器未准备好时，使用播放列表中保存的时长
        // 原因：播放器未初始化时时长为0，但UI可能需要展示歌曲时长
        if (dur == 0 && !playbackController.getCurrentState().hasCurrentItem()) {
            MusicItem item = playlistManager.getCurrentItem();
            if (item != null && item.duration > 0) {
                return item.duration;
            }
        }
        return dur;
    }

    // ========== 循环模式 ==========

    /**
     * 获取当前循环模式
     *
     * @return 循环模式常量，参见 REPEAT_MODE_* 常量
     */
    public int getRepeatMode() {
        return playlistManager.getRepeatMode();
    }

    /**
     * 设置循环模式
     *
     * @param mode 循环模式常量，参见 REPEAT_MODE_* 常量
     */
    public void setRepeatMode(int mode) {
        playlistManager.setRepeatMode(mode);
    }

    // ========== 添加音乐 ==========

    /**
     * 添加音乐文件（通过文件路径，兼容旧接口）
     *
     * @param filePaths   音乐文件路径列表
     * @param contentUris 音乐内容 URI 列表（当前未使用，保留兼容）
     * @param callback    添加完成回调，可为 null
     */
    public void addMusicFilesWithUris(List<String> filePaths, List<String> contentUris, AddMusicCallback callback) {
        playlistManager.addFromFilePaths(filePaths, new PlaylistManager.AddCallback() {
            @Override
            public void onAddComplete(List<MusicItem> addedItems, int startPosition,
                                     int skippedExistCount, int skippedFailedCount) {
                // 转发添加完成回调
                if (callback != null) {
                    callback.onAddComplete(addedItems, startPosition, skippedExistCount, skippedFailedCount);
                }
            }
        });
    }

    /**
     * 移除指定位置的音乐
     *
     * <p>如果当前播放的歌曲被移除，会自动停止播放。
     *
     * @param positions 要删除的位置列表
     */
    public void removeMusicItems(List<Integer> positions) {
        playlistManager.removeItems(positions);
        // 如果当前播放的歌曲被移除了，停止播放
        // 原因：当前歌曲已不存在，无法继续播放
        if (playlistManager.getCurrentIndex() < 0) {
            stop();
        }
    }

    // ========== 回调设置 ==========

    /**
     * 设置播放器回调接口
     *
     * @param callback 播放器回调实例
     */
    public void setCallback(MusicPlayerCallback callback) {
        this.callback = callback;
    }

    // ========== 内部方法 ==========

    private void handleStateChanged(PlaybackState state) {
        if (callback == null) return;

        try {
            // 根据播放状态分发对应的回调事件
            switch (state.getState()) {
                case PLAYING:
                    callback.onPlaybackStarted(playlistManager.getCurrentIndex());
                    notifyMediaSessionPlaybackState(true, playbackController.getCurrentPosition());
                    AudioForegroundService.notifyUpdate(context);
                    break;
                case PAUSED:
                    callback.onPlaybackPaused();
                    notifyMediaSessionPlaybackState(false, playbackController.getCurrentPosition());
                    AudioForegroundService.notifyUpdate(context);
                    break;
                case STOPPED:
                case IDLE:
                    // STOPPED 和 IDLE 都视为停止状态
                    callback.onPlaybackStopped();
                    break;
                case ERROR:
                    callback.onError(state.getErrorMessage());
                    break;
                default:
                    // 其他状态（如 LOADING）不触发回调，等待最终状态
                    break;
            }
        } catch (Exception e) {
            // 捕获回调异常，避免上层异常导致播放功能异常
            AppLog.e(TAG, "Error in handleStateChanged", e);
        }
    }

    private void handleSongCompleted() {
        // 根据循环模式决定下一首
        int nextIndex = playlistManager.getNextIndexOnSongEnd();
        if (nextIndex >= 0) {
            // 有下一首则播放
            start(nextIndex, 0);
        } else {
            // 没有下一首则停止播放（单曲播放模式）
            stop();
        }
    }

    private void loadLyricsIfNeeded(final MusicItem item) {
        // 参数有效性检查
        if (item == null || item.filePath == null) return;
        // 已有歌词则不重复加载
        if (item.lyrics != null && !item.lyrics.isEmpty()) return;

        // 在计算线程中加载歌词，避免阻塞主线程
        AppExecutors.getInstance().executeOnComputeThread(() -> {
            // 优先加载内嵌歌词，其次加载同名 .lrc 文件
            String lyrics = loadEmbeddedLyrics(item.filePath);
            if (lyrics == null || lyrics.isEmpty()) {
                lyrics = loadLyricsFromLrcFile(item.filePath);
            }
            if (lyrics != null && !lyrics.isEmpty()) {
                item.lyrics = lyrics;
                item.lyricsModified = new File(item.filePath).lastModified();
                // 切回主线程回调，并检查当前播放歌曲是否匹配
                // 原因：可能在加载过程中用户切换了歌曲
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
                // 通过反射获取 METADATA_KEY_LYRICS 常量
                // 原因：该常量在部分 Android 版本中可能不存在或被隐藏
                java.lang.reflect.Field field = android.media.MediaMetadataRetriever.class
                        .getDeclaredField("METADATA_KEY_LYRICS");
                keyCode = field.getInt(null);
            } catch (Exception e) {
                // 反射失败说明不支持内嵌歌词，直接返回 null
                return null;
            }
            String lyrics = retriever.extractMetadata(keyCode);
            if (lyrics != null && !lyrics.isEmpty()) {
                return lyrics;
            }
        } catch (Exception e) {
            // 加载内嵌歌词失败不影响播放，仅记录日志
            AppLog.d(TAG, "Failed to load embedded lyrics: " + musicFilePath);
        } finally {
            // 确保释放 MediaMetadataRetriever 资源
            if (retriever != null) {
                try {
                    retriever.release();
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private void ensureAlbumArt(MusicItem item) {
        if (item.albumArt != null) return;
        android.media.MediaMetadataRetriever retriever = null;
        try {
            retriever = new android.media.MediaMetadataRetriever();
            if (item.contentUri != null && item.contentUri.startsWith("content://")) {
                retriever.setDataSource(context, android.net.Uri.parse(item.contentUri));
            } else {
                retriever.setDataSource(item.filePath);
            }
            byte[] art = retriever.getEmbeddedPicture();
            if (art != null && art.length > 0) {
                item.albumArt = art;
                AlbumArtCache.getInstance(context).put(item.filePath, art);
            }
        } catch (Exception e) {
            AppLog.d(TAG, "Failed to extract album art for: " + item.filePath);
        } finally {
            if (retriever != null) {
                try { retriever.release(); } catch (Exception ignored) {}
            }
        }
    }

    private String loadLyricsFromLrcFile(String musicFilePath) {
        if (musicFilePath == null || musicFilePath.isEmpty()) return null;

        // 同名 .lrc 文件路径：将音乐文件的扩展名替换为 .lrc
        String lrcPath = musicFilePath.substring(0, musicFilePath.lastIndexOf('.')) + ".lrc";
        File lrcFile = new File(lrcPath);

        // .lrc 文件不存在则返回 null
        if (!lrcFile.exists()) {
            return null;
        }

        java.io.BufferedReader reader = null;
        try {
            StringBuilder sb = new StringBuilder();
            // 使用 UTF-8 编码读取，确保中文歌词正常显示
            reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(
                            new java.io.FileInputStream(lrcFile), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            // 加载歌词文件失败不影响播放，仅记录日志
            AppLog.d(TAG, "Failed to load lrc: " + lrcPath);
            return null;
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
        }
    }

    // ========== 释放资源 ==========

    /**
     * 释放播放器资源
     *
     * <p>释放 PlaybackController 等资源，调用后应不再使用该实例。
     */
    public void release() {
        playbackController.release();
    }

    // ========== MediaSession 同步 ==========

    /**
     * 同步歌曲信息到 MediaSession
     *
     * <p>更新 MediaSession 的元数据，包括歌曲标题、艺术家、专辑、封面等。
     * 同时更新播放状态。
     *
     * @param item      当前播放的音乐项
     * @param isPlaying 是否正在播放
     * @param position  当前播放位置
     */
    private void notifyMediaSession(MusicItem item, boolean isPlaying, long position) {
        try {
            MediaSessionManager.getInstance().updateMetadata(item);
            MediaSessionManager.getInstance().updatePlaybackState(isPlaying, position);
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to notify MediaSession", e);
        }
    }

    /**
     * 同步播放状态到 MediaSession
     *
     * <p>仅更新播放状态，不更新元数据。
     *
     * @param isPlaying 是否正在播放
     * @param position  当前播放位置
     */
    private void notifyMediaSessionPlaybackState(boolean isPlaying, long position) {
        try {
            MediaSessionManager.getInstance().updatePlaybackState(isPlaying, position);
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to notify MediaSession playback state", e);
        }
    }
}
