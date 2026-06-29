package com.aug32.l7audio.domain.audio.player;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.OptIn;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;

import com.aug32.l7audio.domain.audio.AudioFocusManager;
import com.aug32.l7audio.domain.audio.AudioOutputManager;
import com.aug32.l7audio.domain.audio.MusicItem;
import com.aug32.l7audio.domain.audio.PlaybackState;
import com.aug32.l7audio.utils.AppLog;

import java.io.File;

/**
 * 播放控制器
 *
 * 职责：
 * - 封装 ExoPlayer，只负责单曲播放控制
 * - 管理音频焦点
 * - 管理进度更新
 * - 通过 PlaybackCallback 通知外部状态变化
 *
 * 设计原则：
 * - 只处理单曲播放，不管理播放列表（由 PlaylistManager 负责）
 * - 所有操作都在主线程执行
 * - 状态变化通过回调通知
 */
@OptIn(markerClass = UnstableApi.class)
public class PlaybackController {

    private static final String TAG = "PlaybackController";
    private static final long PROGRESS_UPDATE_INTERVAL_MS = 500;
    private static final long POSITION_SAVE_INTERVAL_MS = 1000;

    private final Context context;
    private ExoPlayer exoPlayer;
    private PlaybackCallback callback;
    private PlaybackState currentState;

    private AudioFocusManager audioFocusManager;
    private boolean wasPlayingBeforeFocusLoss = false;
    private final AudioFocusManager.OnAudioFocusChangeListener focusChangeListener =
            new AudioFocusManager.OnAudioFocusChangeListener() {
                @Override
                public void onFocusGained() {
                    if (isInternalFocusChange) return;
                    AppLog.d(TAG, "onFocusGained, wasPlaying=" + wasPlayingBeforeFocusLoss);
                    if (wasPlayingBeforeFocusLoss) {
                        wasPlayingBeforeFocusLoss = false;
                        resumeInternal();
                    }
                }

                @Override
                public void onFocusLostTransient() {
                    if (isInternalFocusChange) return;
                    AppLog.d(TAG, "onFocusLostTransient, isPlaying=" + isPlaying());
                    if (isPlaying()) {
                        wasPlayingBeforeFocusLoss = true;
                        pauseInternal();
                    }
                }

                @Override
                public void onFocusLostPermanent() {
                    if (isInternalFocusChange) return;
                    AppLog.d(TAG, "onFocusLostPermanent");
                    wasPlayingBeforeFocusLoss = false;
                    stopInternal();
                }
            };

    private final android.os.Handler progressHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private long lastSavedPositionTime = 0;
    private boolean isInternalFocusChange = false;

    public PlaybackController(Context context) {
        this.context = context.getApplicationContext();
        this.currentState = PlaybackState.builder()
                .state(PlaybackState.State.IDLE)
                .build();
        initPlayer();
        initAudioFocus();
    }

    private void initPlayer() {
        try {
            exoPlayer = new ExoPlayer.Builder(context).build();

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(getDefaultAudioUsage())
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build();
            exoPlayer.setAudioAttributes(audioAttributes, false);

            exoPlayer.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    handlePlaybackStateChanged(playbackState);
                }

                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    handleIsPlayingChanged(isPlaying);
                }

                @Override
                public void onPlayerError(androidx.media3.common.PlaybackException error) {
                    handleError(error);
                }
            });

            AppLog.d(TAG, "ExoPlayer initialized");
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to initialize ExoPlayer", e);
            setState(PlaybackState.builder()
                    .state(PlaybackState.State.ERROR)
                    .errorMessage("初始化播放器失败: " + e.getMessage())
                    .build());
        }
    }

    private void initAudioFocus() {
        try {
            audioFocusManager = AudioFocusManager.from(context);
            audioFocusManager.addFocusChangeListener(focusChangeListener);
            AppLog.d(TAG, "Audio focus listener registered");
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to init audio focus", e);
        }
    }

    private int getDefaultAudioUsage() {
        return android.media.AudioAttributes.USAGE_MEDIA;
    }

    // ========== 公共 API ==========

    public void setCallback(PlaybackCallback callback) {
        this.callback = callback;
    }

    public PlaybackState getCurrentState() {
        return currentState;
    }

    public boolean isPlaying() {
        return exoPlayer != null && exoPlayer.isPlaying();
    }

    public long getCurrentPosition() {
        if (exoPlayer == null) return 0;
        try {
            long pos = exoPlayer.getCurrentPosition();
            return (pos == C.TIME_UNSET || pos < 0) ? currentState.getCurrentPosition() : pos;
        } catch (Exception e) {
            return currentState.getCurrentPosition();
        }
    }

    public long getDuration() {
        if (exoPlayer == null) return 0;
        try {
            long dur = exoPlayer.getDuration();
            if (dur == C.TIME_UNSET || dur <= 0) {
                return currentState.getDuration();
            }
            return dur;
        } catch (Exception e) {
            return currentState.getDuration();
        }
    }

    /**
     * 播放指定歌曲
     */
    public void play(MusicItem item, long startPosition) {
        if (exoPlayer == null || item == null) {
            AppLog.e(TAG, "play: exoPlayer=" + exoPlayer + ", item=" + item);
            return;
        }

        AppLog.d(TAG, ">>> play() called: title=" + item.title + ", path=" + item.filePath + ", startPosition=" + startPosition);

        try {
            AppLog.d(TAG, "play: step1 - requesting focus");
            isInternalFocusChange = true;
            boolean focusGranted = requestFocus();
            isInternalFocusChange = false;
            AppLog.d(TAG, "play: step1 - focus granted=" + focusGranted);

            AppLog.d(TAG, "play: step2 - building media item");
            MediaItem mediaItem = buildMediaItem(item);
            AppLog.d(TAG, "play: step2 - mediaItem built, uri=" + mediaItem.localConfiguration.uri);

            AppLog.d(TAG, "play: step3 - setMediaItem, currentCount=" + exoPlayer.getMediaItemCount());
            exoPlayer.setMediaItem(mediaItem);

            AppLog.d(TAG, "play: step4 - prepare, currentState=" + exoPlayer.getPlaybackState());
            exoPlayer.prepare();

            if (startPosition > 0) {
                AppLog.d(TAG, "play: step5 - seekTo " + startPosition);
                exoPlayer.seekTo(startPosition);
            }

            AppLog.d(TAG, "play: step6 - setPlayWhenReady(true), state before=" + exoPlayer.getPlaybackState());
            exoPlayer.setPlayWhenReady(true);

            setState(currentState.buildUpon()
                    .state(PlaybackState.State.LOADING)
                    .currentItem(item)
                    .currentPosition(startPosition)
                    .build());

            AppLog.d(TAG, ">>> play() completed, final state=" + exoPlayer.getPlaybackState() + ", isPlaying=" + exoPlayer.isPlaying());
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to play", e);
            setState(currentState.buildUpon()
                    .state(PlaybackState.State.ERROR)
                    .errorMessage("播放失败: " + e.getMessage())
                    .build());
            if (callback != null) {
                callback.onError("播放失败: " + e.getMessage());
            }
        }
    }

    /**
     * 暂停播放（保持音频焦点，无需放弃）
     */
    public void pause() {
        pauseInternal();
    }

    /**
     * 恢复播放
     */
    public void resume() {
        resume(getCurrentPosition());
    }

    public void resume(long position) {
        if (exoPlayer == null) {
            AppLog.e(TAG, "resume: exoPlayer is null");
            return;
        }

        AppLog.d(TAG, ">>> resume() called: position=" + position + ", currentCount=" + exoPlayer.getMediaItemCount()
                + ", currentState=" + exoPlayer.getPlaybackState() + ", currentItem=" + currentState.getCurrentItem());

        isInternalFocusChange = true;
        boolean focusGranted = requestFocus();
        isInternalFocusChange = false;
        AppLog.d(TAG, "resume: focus granted=" + focusGranted);

        boolean needPrepare = false;
        if (exoPlayer.getMediaItemCount() == 0 && currentState.getCurrentItem() != null) {
            AppLog.d(TAG, "resume: no media items, rebuilding from currentState");
            exoPlayer.setMediaItem(buildMediaItem(currentState.getCurrentItem()));
            needPrepare = true;
        }

        int playbackState = exoPlayer.getPlaybackState();
        AppLog.d(TAG, "resume: playbackState=" + playbackState + " (IDLE=" + Player.STATE_IDLE + ", ENDED=" + Player.STATE_ENDED + ")");
        if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
            needPrepare = true;
        }

        if (needPrepare) {
            AppLog.d(TAG, "resume: calling prepare(), needPrepare=true");
            exoPlayer.prepare();
        } else {
            AppLog.d(TAG, "resume: skipping prepare, needPrepare=false");
        }

        if (position >= 0 && Math.abs(exoPlayer.getCurrentPosition() - position) > 100) {
            AppLog.d(TAG, "resume: seeking to " + position + " (current=" + exoPlayer.getCurrentPosition() + ")");
            exoPlayer.seekTo(position);
        }

        AppLog.d(TAG, "resume: setPlayWhenReady(true), state before=" + exoPlayer.getPlaybackState());
        exoPlayer.setPlayWhenReady(true);

        AppLog.d(TAG, ">>> resume() completed, final state=" + exoPlayer.getPlaybackState() + ", isPlaying=" + exoPlayer.isPlaying());
    }

    /**
     * 停止播放
     */
    public void stop() {
        stopInternal();
        abandonFocus();
    }

    /**
     * 跳转进度
     */
    public void seekTo(long position) {
        if (exoPlayer != null) {
            exoPlayer.seekTo(position);
            updateProgressState();
        }
    }

    /**
     * 设置音量
     */
    public void setVolume(float volume) {
        if (exoPlayer != null) {
            exoPlayer.setVolume(Math.max(0f, Math.min(1f, volume)));
        }
    }

    /**
     * 更新音频输出用法（切换车内/车外）
     */
    public void updateAudioUsage(int audioUsage) {
        if (exoPlayer == null) return;
        try {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(audioUsage)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build();
            exoPlayer.setAudioAttributes(audioAttributes, true);
            AppLog.d(TAG, "Audio usage updated: " + audioUsage);
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to update audio usage", e);
        }
    }

    /**
     * 释放资源
     */
    public void release() {
        stopProgressUpdates();
        if (audioFocusManager != null) {
            audioFocusManager.removeFocusChangeListener(focusChangeListener);
            audioFocusManager = null;
        }
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
        setState(currentState.buildUpon()
                .state(PlaybackState.State.IDLE)
                .build());
        AppLog.d(TAG, "PlaybackController released");
    }

    // ========== 内部方法 ==========

    private void pauseInternal() {
        if (exoPlayer != null && exoPlayer.isPlaying()) {
            exoPlayer.pause();
        }
    }

    private void resumeInternal() {
        if (exoPlayer == null) return;
        if (exoPlayer.getMediaItemCount() == 0 && currentState.getCurrentItem() != null) {
            exoPlayer.setMediaItem(buildMediaItem(currentState.getCurrentItem()));
        }
        exoPlayer.prepare();
        exoPlayer.setPlayWhenReady(true);
    }

    private void stopInternal() {
        if (exoPlayer != null) {
            exoPlayer.stop();
            stopProgressUpdates();
            setState(currentState.buildUpon()
                    .state(PlaybackState.State.STOPPED)
                    .build());
        }
    }

    private boolean requestFocus() {
        if (audioFocusManager != null) {
            boolean granted = audioFocusManager.requestPlaybackFocus();
            AppLog.d(TAG, "requestPlaybackFocus granted=" + granted);
            return granted;
        }
        AppLog.w(TAG, "requestPlaybackFocus: audioFocusManager is null");
        return false;
    }

    private void abandonFocus() {
        if (audioFocusManager != null) {
            audioFocusManager.abandonPlaybackFocus();
        }
    }

    private void handlePlaybackStateChanged(int playbackState) {
        try {
            if (playbackState == Player.STATE_ENDED) {
                handleSongEnded();
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Error in onPlaybackStateChanged", e);
        }
    }

    private void handleIsPlayingChanged(boolean isPlaying) {
        try {
            AppLog.d(TAG, ">>> handleIsPlayingChanged: isPlaying=" + isPlaying + ", currentState=" + currentState.getState());
            if (isPlaying) {
                setState(currentState.buildUpon()
                        .state(PlaybackState.State.PLAYING)
                        .build());
                startProgressUpdates();
                AppLog.d(TAG, "handleIsPlayingChanged: started playing, state updated to PLAYING");
            } else {
                // 如果不是因为播放结束而暂停，才更新为 PAUSED
                if (currentState.getState() != PlaybackState.State.STOPPED
                        && currentState.getState() != PlaybackState.State.IDLE) {
                    setState(currentState.buildUpon()
                            .state(PlaybackState.State.PAUSED)
                            .build());
                    AppLog.d(TAG, "handleIsPlayingChanged: paused, state updated to PAUSED");
                }
                stopProgressUpdates();
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Error in onIsPlayingChanged", e);
        }
    }

    private void handleSongEnded() {
        AppLog.d(TAG, "Song ended");
        stopProgressUpdates();
        if (callback != null) {
            callback.onSongCompleted();
        }
    }

    private void handleError(androidx.media3.common.PlaybackException error) {
        AppLog.e(TAG, "Player error: " + error.getMessage());
        setState(currentState.buildUpon()
                .state(PlaybackState.State.ERROR)
                .errorMessage(error.getMessage())
                .build());
        stopProgressUpdates();
        if (callback != null) {
            callback.onError("播放错误: " + error.getMessage());
        }
    }

    private void setState(PlaybackState newState) {
        if (this.currentState != null && this.currentState.getState() == newState.getState()
                && this.currentState.getCurrentItem() == newState.getCurrentItem()
                && this.currentState.getErrorMessage() == newState.getErrorMessage()) {
            return;
        }
        this.currentState = newState;
        if (callback != null) {
            callback.onStateChanged(newState);
        }
    }

    private void startProgressUpdates() {
        stopProgressUpdates();
        progressHandler.postDelayed(progressRunnable, PROGRESS_UPDATE_INTERVAL_MS);
    }

    private void stopProgressUpdates() {
        progressHandler.removeCallbacks(progressRunnable);
    }

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            updateProgressState();
            if (isPlaying()) {
                progressHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS);
            }
        }
    };

    private void updateProgressState() {
        if (exoPlayer == null) return;
        long pos = getCurrentPosition();
        long dur = getDuration();

        setState(currentState.buildUpon()
                .currentPosition(pos)
                .duration(dur)
                .build());

        if (callback != null) {
            callback.onProgressChanged(pos, dur);
        }

        // 定期保存位置（每秒一次）
        long now = System.currentTimeMillis();
        if (now - lastSavedPositionTime >= POSITION_SAVE_INTERVAL_MS) {
            lastSavedPositionTime = now;
        }
    }

    private MediaItem buildMediaItem(MusicItem item) {
        String uriString = item.contentUri != null && !item.contentUri.isEmpty()
                ? item.contentUri : item.filePath;
        Uri uri;
        if (uriString.startsWith("content://") || uriString.startsWith("file://")) {
            uri = Uri.parse(uriString);
        } else {
            File file = new File(uriString);
            uri = file.exists() ? Uri.fromFile(file) : Uri.parse(uriString);
        }
        MediaMetadata metadata = new MediaMetadata.Builder()
                .setTitle(item.title)
                .setArtist(item.artist)
                .setAlbumTitle(item.album)
                .build();
        return new MediaItem.Builder()
                .setUri(uri)
                .setMediaId(item.filePath)
                .setMediaMetadata(metadata)
                .build();
    }
}
