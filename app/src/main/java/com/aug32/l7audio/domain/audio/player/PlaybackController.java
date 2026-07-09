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

import java.io.File;

import com.aug32.l7audio.domain.audio.AudioFocusManager;
import com.aug32.l7audio.domain.audio.player.MusicItem;
import com.aug32.l7audio.domain.audio.player.PlaybackState;
import com.aug32.l7audio.utils.AppLog;

/**
 * 播放控制器
 *
 * <p>职责：
 * <ul>
 *   <li>封装 ExoPlayer，只负责单曲播放控制</li>
 *   <li>管理音频焦点（请求/释放/焦点丢失处理）</li>
 *   <li>管理播放进度更新与回调通知</li>
 *   <li>通过 PlaybackCallback 向外部通知播放状态变化</li>
 * </ul>
 *
 * <p>设计意图：
 * <ul>
 *   <li>采用单一职责原则，仅处理单曲播放逻辑，播放列表由 PlaylistManager 管理</li>
 *   <li>所有状态变更通过回调机制通知，实现解耦</li>
 *   <li>内部维护 PlaybackState 状态机，确保状态一致性</li>
 *   <li>封装音频焦点管理逻辑，对外屏蔽焦点获取细节</li>
 * </ul>
 *
 * <p>设计原则：
 * <ul>
 *   <li>只处理单曲播放，不管理播放列表（由 PlaylistManager 负责）</li>
 *   <li>所有操作都在主线程执行</li>
 *   <li>状态变化通过回调通知</li>
 * </ul>
 */
@OptIn(markerClass = UnstableApi.class)
public class PlaybackController {

    // 日志标签
    private static final String TAG = "PlaybackController";
    // 进度更新间隔（毫秒），控制播放进度回调的频率
    private static final long PROGRESS_UPDATE_INTERVAL_MS = 500;
    // 播放位置保存间隔（毫秒），用于节流，避免频繁保存
    private static final long POSITION_SAVE_INTERVAL_MS = 1000;

    // 应用上下文，用于初始化播放器和音频焦点管理
    private final Context context;
    // ExoPlayer 实例，负责实际的媒体播放
    private ExoPlayer exoPlayer;
    // 播放状态回调接口，用于向外部通知状态变化
    private PlaybackCallback callback;
    // 当前播放状态，维护播放状态机
    private PlaybackState currentState;

    // 音频焦点管理器，处理音频焦点的请求与丢失
    private AudioFocusManager audioFocusManager;
    // 标记焦点丢失前是否正在播放，用于焦点恢复时决定是否自动恢复播放
    private boolean wasPlayingBeforeFocusLoss = false;
    // ExoPlayer 播放状态监听器，在 release() 时需移除
    private Player.Listener playerListener;
    // 音频焦点变化监听器，处理焦点获取、短暂丢失和永久丢失三种场景
    private final AudioFocusManager.OnAudioFocusChangeListener focusChangeListener =
            new AudioFocusManager.OnAudioFocusChangeListener() {
                @Override
                public void onFocusGained() {
                    // 内部触发的焦点变化不处理，避免循环调用
                    if (isInternalFocusChange) return;
                    AppLog.d(TAG, "onFocusGained, wasPlaying=" + wasPlayingBeforeFocusLoss);
                    // 焦点恢复时，如果之前在播放则自动恢复播放
                    if (wasPlayingBeforeFocusLoss) {
                        wasPlayingBeforeFocusLoss = false;
                        resumeInternal();
                    }
                }

                @Override
                public void onFocusLostTransient() {
                    // 内部触发的焦点变化不处理，避免循环调用
                    if (isInternalFocusChange) return;
                    AppLog.d(TAG, "onFocusLostTransient, isPlaying=" + isPlaying());
                    // 短暂丢失焦点（如来电），暂停播放并记录状态以便恢复
                    if (isPlaying()) {
                        wasPlayingBeforeFocusLoss = true;
                        pauseInternal();
                    }
                }

                @Override
                public void onFocusLostPermanent() {
                    // 内部触发的焦点变化不处理，避免循环调用
                    if (isInternalFocusChange) return;
                    AppLog.d(TAG, "onFocusLostPermanent");
                    // 永久丢失焦点（如其他应用开始播放），停止播放并清除恢复标记
                    wasPlayingBeforeFocusLoss = false;
                    stopInternal();
                }
            };

    // 主线程 Handler，用于定时更新播放进度
    private final android.os.Handler progressHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    // 上次保存播放位置的时间戳，用于节流控制
    private long lastSavedPositionTime = 0;
    // 标记是否为内部触发的焦点变化，用于避免焦点监听器与内部请求之间的循环调用
    private boolean isInternalFocusChange = false;

    /**
     * 构造函数，初始化播放控制器
     *
     * @param context 上下文对象，内部会自动转换为 ApplicationContext 以避免内存泄漏
     */
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

            playerListener = new Player.Listener() {
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
            };
            exoPlayer.addListener(playerListener);

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

    // ========== 公共 API ==========

    /**
     * 设置播放状态回调接口
     *
     * @param callback 播放回调实例，用于接收播放状态、进度、错误等通知
     */
    public void setCallback(PlaybackCallback callback) {
        this.callback = callback;
    }

    /**
     * 获取当前播放状态
     *
     * @return 当前播放状态对象，包含播放状态、当前歌曲、进度等信息
     */
    public PlaybackState getCurrentState() {
        return currentState;
    }

    /**
     * 判断是否正在播放
     *
     * @return true 表示正在播放，false 表示未播放或播放器未初始化
     */
    public boolean isPlaying() {
        return exoPlayer != null && exoPlayer.isPlaying();
    }

    /**
     * 获取当前播放位置
     *
     * <p>注意：当播放器未就绪或返回无效值时，会回退使用状态中缓存的位置，
     * 确保在各种异常情况下都能返回合理的值。
     *
     * @return 当前播放位置（毫秒），播放器未初始化时返回 0
     */
    public long getCurrentPosition() {
        if (exoPlayer == null) return 0;
        try {
            long pos = exoPlayer.getCurrentPosition();
            // ExoPlayer 可能返回 TIME_UNSET 或负值，此时使用缓存的位置值
            return (pos == C.TIME_UNSET || pos < 0) ? currentState.getCurrentPosition() : pos;
        } catch (Exception e) {
            // 异常情况下回退到缓存的位置
            return currentState.getCurrentPosition();
        }
    }

    /**
     * 获取当前媒体的总时长
     *
     * <p>注意：当播放器未就绪或返回无效值时，会回退使用状态中缓存的时长，
     * 确保在各种异常情况下都能返回合理的值。
     *
     * @return 媒体总时长（毫秒），播放器未初始化时返回 0
     */
    public long getDuration() {
        if (exoPlayer == null) return 0;
        try {
            long dur = exoPlayer.getDuration();
            // 时长未设置或无效时，使用缓存的时长值
            if (dur == C.TIME_UNSET || dur <= 0) {
                return currentState.getDuration();
            }
            return dur;
        } catch (Exception e) {
            // 异常情况下回退到缓存的时长
            return currentState.getDuration();
        }
    }

    /**
     * 播放指定歌曲
     *
     * <p>播放流程：请求音频焦点 → 构建 MediaItem → 设置媒体源 → 准备播放器 → 跳转起始位置 → 开始播放。
     * 播放失败时会通过回调通知错误信息。
     *
     * @param item          要播放的音乐项，包含文件路径、标题、艺术家等信息
     * @param startPosition 起始播放位置（毫秒），为 0 时从头开始播放
     */
    public void play(MusicItem item, long startPosition) {
        // 参数有效性检查，避免空指针异常
        if (exoPlayer == null || item == null) {
            AppLog.e(TAG, "play: exoPlayer=" + exoPlayer + ", item=" + item);
            return;
        }

        AppLog.d(TAG, ">>> play() called: title=" + item.title + ", path=" + item.filePath + ", startPosition=" + startPosition);

        try {
            // 第一步：请求音频焦点，标记为内部触发避免循环回调
            AppLog.d(TAG, "play: step1 - requesting focus");
            isInternalFocusChange = true;
            boolean focusGranted = requestFocus();
            isInternalFocusChange = false;
            AppLog.d(TAG, "play: step1 - focus granted=" + focusGranted);

            // 第二步：构建 MediaItem，将 MusicItem 转换为 ExoPlayer 可识别的格式
            AppLog.d(TAG, "play: step2 - building media item");
            MediaItem mediaItem = buildMediaItem(item);
            AppLog.d(TAG, "play: step2 - mediaItem built, uri=" + mediaItem.localConfiguration.uri);

            // 第三步：设置媒体源，替换当前播放队列
            AppLog.d(TAG, "play: step3 - setMediaItem, currentCount=" + exoPlayer.getMediaItemCount());
            exoPlayer.setMediaItem(mediaItem);

            // 第四步：准备播放器，异步加载媒体资源
            AppLog.d(TAG, "play: step4 - prepare, currentState=" + exoPlayer.getPlaybackState());
            exoPlayer.prepare();

            // 第五步：跳转到指定起始位置（仅当有起始位置时）
            if (startPosition > 0) {
                AppLog.d(TAG, "play: step5 - seekTo " + startPosition);
                exoPlayer.seekTo(startPosition);
            }

            // 第六步：设置自动播放，准备完成后自动开始播放
            AppLog.d(TAG, "play: step6 - setPlayWhenReady(true), state before=" + exoPlayer.getPlaybackState());
            exoPlayer.setPlayWhenReady(true);

            // 更新内部状态为加载中，并通知外部
            setState(currentState.buildUpon()
                    .state(PlaybackState.State.LOADING)
                    .currentItem(item)
                    .currentPosition(startPosition)
                    .build());

            AppLog.d(TAG, ">>> play() completed, final state=" + exoPlayer.getPlaybackState() + ", isPlaying=" + exoPlayer.isPlaying());
        } catch (Exception e) {
            // 播放异常时更新状态为错误，并通过回调通知外部
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
     *
     * <p>仅暂停播放，不释放音频焦点，便于快速恢复播放。
     */
    public void pause() {
        pauseInternal();
    }

    /**
     * 恢复播放（从当前位置继续）
     *
     * <p>如果播放器未准备好或没有媒体项，会自动从当前状态中恢复。
     */
    public void resume() {
        resume(getCurrentPosition());
    }

    /**
     * 从指定位置恢复播放
     *
     * <p>处理多种恢复场景：
     * <ul>
     *   <li>播放器无媒体项时，从当前状态中重建媒体项</li>
     *   <li>播放器处于 IDLE 或 ENDED 状态时，重新 prepare</li>
     *   <li>目标位置与当前位置差异较大时，执行 seek 跳转</li>
     * </ul>
     *
     * @param position 恢复播放的起始位置（毫秒）
     */
    public void resume(long position) {
        if (exoPlayer == null) {
            AppLog.e(TAG, "resume: exoPlayer is null");
            return;
        }

        AppLog.d(TAG, ">>> resume() called: position=" + position + ", currentCount=" + exoPlayer.getMediaItemCount()
                + ", currentState=" + exoPlayer.getPlaybackState() + ", currentItem=" + currentState.getCurrentItem());

        // 请求音频焦点，标记为内部触发避免循环回调
        isInternalFocusChange = true;
        boolean focusGranted = requestFocus();
        isInternalFocusChange = false;
        AppLog.d(TAG, "resume: focus granted=" + focusGranted);

        // 判断是否需要重新 prepare
        boolean needPrepare = false;
        // 场景1：播放器无媒体项但有当前歌曲（如 stop 后 resume），需要重建媒体项
        if (exoPlayer.getMediaItemCount() == 0 && currentState.getCurrentItem() != null) {
            AppLog.d(TAG, "resume: no media items, rebuilding from currentState");
            exoPlayer.setMediaItem(buildMediaItem(currentState.getCurrentItem()));
            needPrepare = true;
        }

        // 场景2：播放器处于 IDLE 或 ENDED 状态，需要重新 prepare
        int playbackState = exoPlayer.getPlaybackState();
        AppLog.d(TAG, "resume: playbackState=" + playbackState + " (IDLE=" + Player.STATE_IDLE + ", ENDED=" + Player.STATE_ENDED + ")");
        if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
            needPrepare = true;
        }

        // 需要 prepare 时调用 prepare，否则跳过（如暂停后恢复无需重新 prepare）
        if (needPrepare) {
            AppLog.d(TAG, "resume: calling prepare(), needPrepare=true");
            exoPlayer.prepare();
        } else {
            AppLog.d(TAG, "resume: skipping prepare, needPrepare=false");
        }

        // 目标位置与当前位置差异超过100ms时才执行 seek，避免不必要的跳转
        if (position >= 0 && Math.abs(exoPlayer.getCurrentPosition() - position) > 100) {
            AppLog.d(TAG, "resume: seeking to " + position + " (current=" + exoPlayer.getCurrentPosition() + ")");
            exoPlayer.seekTo(position);
        }

        // 设置自动播放，准备完成后自动开始
        AppLog.d(TAG, "resume: setPlayWhenReady(true), state before=" + exoPlayer.getPlaybackState());
        exoPlayer.setPlayWhenReady(true);

        AppLog.d(TAG, ">>> resume() completed, final state=" + exoPlayer.getPlaybackState() + ", isPlaying=" + exoPlayer.isPlaying());
    }

    /**
     * 停止播放并释放音频焦点
     *
     * <p>停止播放后会释放音频焦点，允许其他应用获取焦点。
     */
    public void stop() {
        stopInternal();
        abandonFocus();
    }

    /**
     * 跳转到指定播放位置
     *
     * @param position 目标播放位置（毫秒）
     */
    public void seekTo(long position) {
        if (exoPlayer != null) {
            exoPlayer.seekTo(position);
            // 跳转后立即更新进度状态，确保UI及时刷新
            updateProgressState();
        }
    }

    /**
     * 设置播放音量
     *
     * @param volume 音量值，范围 [0.0, 1.0]，超出范围会自动截断
     */
    public void setVolume(float volume) {
        if (exoPlayer != null) {
            // 限制音量在有效范围内，避免传入无效值
            exoPlayer.setVolume(Math.max(0f, Math.min(1f, volume)));
        }
    }

    /**
     * 更新音频输出用法（切换车内/车外音频通道）
     *
     * <p>用于在不同音频输出场景间切换，如车内音响 vs 车外扬声器。
     * 切换时不会中断当前播放。
     *
     * @param audioUsage 音频用法常量，参见 {@link android.media.AudioAttributes#USAGE_MEDIA} 等
     */
    public void updateAudioUsage(int audioUsage) {
        if (exoPlayer == null) return;
        try {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(audioUsage)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build();
            // 第二个参数为 false 表示不请求音频焦点（保持当前焦点状态）
            exoPlayer.setAudioAttributes(audioAttributes, false);
            AppLog.d(TAG, "Audio usage updated: " + audioUsage);
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to update audio usage", e);
        }
    }

    /**
     * 释放播放器资源
     *
     * <p>释放 ExoPlayer、音频焦点管理器等资源，停止进度更新。
     * 调用后应不再使用该实例，或重新创建新实例。
     */
    public void release() {
        // 停止进度更新，避免内存泄漏
        stopProgressUpdates();
        // 移除音频焦点监听器并释放焦点管理器
        if (audioFocusManager != null) {
            audioFocusManager.removeFocusChangeListener(focusChangeListener);
            audioFocusManager = null;
        }
        // 释放 ExoPlayer 资源
        if (exoPlayer != null) {
            if (playerListener != null) {
                exoPlayer.removeListener(playerListener);
                playerListener = null;
            }
            exoPlayer.release();
            exoPlayer = null;
        }
        // 重置状态为空闲
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
                // 开始播放：更新状态为播放中，并启动进度更新
                setState(currentState.buildUpon()
                        .state(PlaybackState.State.PLAYING)
                        .build());
                startProgressUpdates();
                AppLog.d(TAG, "handleIsPlayingChanged: started playing, state updated to PLAYING");
            } else {
                // 暂停/停止播放：只有当当前状态不是 STOPPED 或 IDLE 时才更新为 PAUSED
                // 原因：STOPPED/IDLE 状态下的 isPlaying=false 是正常的，不应被覆盖为 PAUSED
                if (currentState.getState() != PlaybackState.State.STOPPED
                        && currentState.getState() != PlaybackState.State.IDLE) {
                    setState(currentState.buildUpon()
                            .state(PlaybackState.State.PAUSED)
                            .build());
                    AppLog.d(TAG, "handleIsPlayingChanged: paused, state updated to PAUSED");
                }
                // 无论何种原因停止播放，都停止进度更新
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
        // 状态去重：只有当状态、当前歌曲、错误信息都相同时才跳过通知
        // 目的：避免重复回调导致UI不必要的刷新，减少性能消耗
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

        // 更新状态中的进度和时长信息
        setState(currentState.buildUpon()
                .currentPosition(pos)
                .duration(dur)
                .build());

        // 通知外部进度变化
        if (callback != null) {
            callback.onProgressChanged(pos, dur);
        }

        // 定期保存位置（每秒一次），通过时间间隔节流控制保存频率
        // 注意：此处仅更新时间戳，实际保存逻辑由外部实现
        long now = System.currentTimeMillis();
        if (now - lastSavedPositionTime >= POSITION_SAVE_INTERVAL_MS) {
            lastSavedPositionTime = now;
        }
    }

    private MediaItem buildMediaItem(MusicItem item) {
        // 优先使用 contentUri，其次使用 filePath
        // 原因：contentUri 可以访问受 ContentProvider 保护的媒体文件，访问权限更可靠
        String uriString = item.contentUri != null && !item.contentUri.isEmpty()
                ? item.contentUri : item.filePath;
        Uri uri;
        // 已经是 scheme 形式的 URI 直接解析，否则按本地文件路径处理
        if (uriString.startsWith("content://") || uriString.startsWith("file://")) {
            uri = Uri.parse(uriString);
        } else {
            // 文件存在则通过 File 转 URI，确保 file:// 格式正确
            File file = new File(uriString);
            uri = file.exists() ? Uri.fromFile(file) : Uri.parse(uriString);
        }
        // 构建媒体元数据，用于在通知栏等位置显示
        MediaMetadata metadata = new MediaMetadata.Builder()
                .setTitle(item.title)
                .setArtist(item.artist)
                .setAlbumTitle(item.album)
                .build();
        // 以文件路径作为 mediaId，保证唯一性和可追溯性
        return new MediaItem.Builder()
                .setUri(uri)
                .setMediaId(item.filePath)
                .setMediaMetadata(metadata)
                .build();
    }
}
