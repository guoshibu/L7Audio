package com.aug32.l7audio.audio;

import android.content.Context;
import android.net.Uri;
import android.os.Build;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer;

import com.aug32.l7audio.AppConfig;
import com.aug32.l7audio.AppLog;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import android.media.MediaMetadataRetriever;

public class MusicPlayerManager {
    private static final String TAG = "MusicPlayerManager";

    private final Context context;// 上下文
    private final AudioOutputManager audioOutputManager;// 音频输出管理器
    private final AppConfig appConfig;// 配置管理器
    private final Gson gson;// JSON解析器

    private ExoPlayer exoPlayer;// ExoPlayer实例
    private List<MusicItem> musicItems;// 音乐项列表
    private int currentIndex = -1;// 当前播放索引
    private boolean isPlaying = false;// 是否正在播放
    private int repeatMode = Player.REPEAT_MODE_OFF;// 循环播放模式
    private boolean shuffleMode = false;// 是否随机播放

    private MusicPlayerCallback callback;// 播放回调回调

    public interface MusicPlayerCallback {
        void onPlaybackStarted(int position);
        void onPlaybackPaused();
        void onPlaybackStopped();
        void onPlaybackCompleted();
        void onPlaybackProgress(long position, long duration);
        void onError(String error);
    }

    public static class MusicItem {// 音乐项
        public String filePath;// 文件路径
        public String title;// 标题
        public String artist;// 艺术家
        public long duration;// 时长

        public MusicItem() {}    // 无参构造函数

        public MusicItem(String filePath, String title, String artist, long duration) {// 有参构造函数
            this.filePath = filePath;
            this.title = title;
            this.artist = artist;
            this.duration = duration;
        }
    }

    private final Player.Listener playerListener = new Player.Listener() {// 播放器监听器
        @Override
        public void onPlaybackStateChanged(int playbackState) {// 播放状态改变事件
            if (playbackState == Player.STATE_ENDED) {
                handleCompletion();// 处理播放完成
            }
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            MusicPlayerManager.this.isPlaying = isPlaying;
            if (isPlaying) {
                if (callback != null) {
                    callback.onPlaybackStarted(currentIndex);
                }
                startProgressUpdate();
            } else {
                if (callback != null) {
                    callback.onPlaybackPaused();
                }
                stopProgressUpdate();
            }
        }

        @Override
        public void onPlayerError(androidx.media3.common.PlaybackException error) {
            handleError("Player error686: " + error.getMessage());
        }
    };

    private Runnable progressRunnable;
    private android.os.Handler handler;

    public MusicPlayerManager(Context context, AudioOutputManager audioOutputManager) {
        this.context = context;
        this.audioOutputManager = audioOutputManager;
        this.appConfig = new AppConfig(context);
        this.gson = new Gson();
        this.musicItems = new ArrayList<>();
        this.handler = new android.os.Handler(android.os.Looper.getMainLooper());
        this.repeatMode = appConfig.getRepeatMode();
        this.shuffleMode = appConfig.isShuffleModeEnabled();

        loadPlaylist();
        // 从AppConfig加载最后播放的索引
        currentIndex = appConfig.getLastPlayedIndex();// 获取最后播放的索引
        // 如果索引无效，则设置为-1
        if (currentIndex < 0 || currentIndex >= musicItems.size()) {
            currentIndex = -1;
        }
        // 如果没有播放索引且有音乐，则自动设置为第一首
        if (currentIndex < 0 && !musicItems.isEmpty()) {
            currentIndex = 0;// 设置为第一首
            appConfig.setLastPlayedIndex(currentIndex);
            AppLog.d(TAG, "Auto set currentIndex to 0 during initialization");
        }
        AppLog.d(TAG, "MusicPlayerManager initialized with currentIndex: " + currentIndex);
    }

    private void loadPlaylist() {
        String playlistJson = appConfig.getMusicPlaylist();
        if (!playlistJson.isEmpty()) {
            try {
                Type listType = new TypeToken<List<MusicItem>>() {}.getType();
                List<MusicItem> loadedList = gson.fromJson(playlistJson, listType);
                if (loadedList != null) {
                    musicItems = loadedList;
                }
            } catch (Exception e) {
                AppLog.e(TAG, "Failed to load playlist", e);
            }
        }
    }

    private void savePlaylist() {
        String playlistJson = gson.toJson(musicItems);
        appConfig.setMusicPlaylist(playlistJson);
    }

    public void setCallback(MusicPlayerCallback callback) {
        this.callback = callback;
    }

    private void initPlayer() {
        AppLog.d(TAG, "=== 开始初始化播放器 ===");
        if (exoPlayer == null) {
            AppLog.d(TAG, "ExoPlayer未初始化，开始初始化过程");
            
            // 步骤1：配置渲染器工厂
            AppLog.d(TAG, "步骤1：配置渲染器工厂");
            DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(context);
            AppLog.d(TAG, "步骤1完成：渲染器工厂配置完成");
            
            // 步骤2：构建ExoPlayer实例
            AppLog.d(TAG, "步骤2：构建ExoPlayer实例");
            exoPlayer = new ExoPlayer.Builder(context)
                    .setRenderersFactory(renderersFactory)// 设置渲染器工厂
                    .build();
            AppLog.d(TAG, "步骤2完成：ExoPlayer实例创建成功");
            
            // 步骤3：添加播放器监听器
            AppLog.d(TAG, "步骤3：添加播放器监听器");
            exoPlayer.addListener(playerListener);// 添加播放器监听器
            AppLog.d(TAG, "步骤3完成：播放器监听器添加成功");
            
            // 步骤4：核心：配置音频属性（根据车内外模式）
            AppLog.d(TAG, "=== 步骤4：配置音频属性（核心步骤）===");
            updateAudioAttributes();// 更新音频属性，确保播放器使用正确的音频用途
            AppLog.d(TAG, "步骤4完成：音频属性配置成功");
            
            // 步骤5：配置后台播放
            AppLog.d(TAG, "步骤5：配置后台播放");
            // 使用NETWORK模式以获得更可靠的后台播放支持
            exoPlayer.setWakeMode(android.os.PowerManager.PARTIAL_WAKE_LOCK); // 允许后台播放
            // 设置播放器在后台时继续运行
            exoPlayer.setHandleAudioBecomingNoisy(true);
            AppLog.d(TAG, "步骤5完成：后台播放配置成功");
        
            AppLog.d(TAG, "=== ExoPlayer初始化完成 ===");           
        } else {
            AppLog.d(TAG, "ExoPlayer已初始化，跳过初始化");
        }
    }

    private AudioAttributes createAudioAttributes() {
        int audioUsage;
        int contentType;

        AppLog.d(TAG, "=== 开始创建音频属性 ===");
        
        // 获取 audioUsage 的逻辑保持不变...
        if (audioOutputManager != null) {
            audioUsage = audioOutputManager.getAudioUsage();
            int outputMode = audioOutputManager.getOutputMode();
            if (outputMode == 0) {
                contentType = C.AUDIO_CONTENT_TYPE_MUSIC;
            } else {
                contentType = C.AUDIO_CONTENT_TYPE_SPEECH;
            }
            AppLog.d(TAG, "音频输出管理器状态：outputMode=" + outputMode + " (" + (outputMode == 0 ? "车内" : "车外") + "), audioUsage=" + audioUsage);
        } else {
            audioUsage = appConfig.getAudioOutputUsageExternal();// 车外模式
            contentType = C.AUDIO_CONTENT_TYPE_MUSIC; // 修复：默认也用音乐类型
            AppLog.d(TAG, "AudioOutputManager为空，使用AppConfig默认配置: audioUsage=" + audioUsage + ", contentType=CONTENT_TYPE_MUSIC");
        }
        
        AppLog.d(TAG, "准备创建AudioAttributes: audioUsage=" + audioUsage + ", contentType=" + contentType);
        
        AudioAttributes media3Attributes = null;
        try {
            media3Attributes = new AudioAttributes.Builder()
                    .setUsage(audioUsage)  // Media3 1.9.2 中这是公开的
                    .setContentType(contentType)
                    .build();
            AppLog.d(TAG, "直接调用方式创建AudioAttributes成功");
        } catch (Exception e) {
            AppLog.e(TAG, "报错了？！直接调用方式创建AudioAttributes失败", e);
            // 方案3：如果直接调用也失败，使用最终降级方案（USAGE_MEDIA） 
            AppLog.e(TAG, "直接调用方式也失败，使用最终降级方案（USAGE_MEDIA）", e);
            media3Attributes = new AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)  // 降级为USAGE_MEDIA，确保兼容性
                    .setContentType(contentType)
                    .build();
            AppLog.d(TAG, "最终降级方案创建AudioAttributes成功（USAGE_MEDIA）");
        }
        
        AppLog.d(TAG, "=== AudioAttributes 创建完成 ===");
        return media3Attributes;
    }

    public void updateAudioAttributes() {
        AppLog.d(TAG, "=== 开始更新音频属性 ===");
        if (exoPlayer != null) {
            AppLog.d(TAG, "ExoPlayer实例存在，准备创建新的音频属性");
            AudioAttributes audioAttributes = createAudioAttributes();
            AppLog.d(TAG, "音频属性创建完成，准备设置到ExoPlayer");
            exoPlayer.setAudioAttributes(audioAttributes, true);
            AppLog.d(TAG, "=== ExoPlayer AudioAttributes更新完成 ===");
        } else {
            AppLog.d(TAG, "ExoPlayer实例不存在，跳过音频属性更新");
        }
    }

    public void addMusicFile(String filePath) {
        File file = new File(filePath);
        if (!file.exists() || !isMusicFile(file)) {
            return;
        }

        // 获取文件的绝对路径，避免路径格式不同导致的重复添加
        String absolutePath;
        try {
            absolutePath = file.getCanonicalPath();
        } catch (java.io.IOException e) {
            absolutePath = file.getAbsolutePath();
        }

        // 检查是否已经存在相同的文件（使用绝对路径比较）
        for (MusicItem item : musicItems) {
            File existingFile = new File(item.filePath);
            String existingAbsolutePath;
            try {
                existingAbsolutePath = existingFile.getCanonicalPath();
            } catch (java.io.IOException e) {
                existingAbsolutePath = existingFile.getAbsolutePath();
            }
            if (existingAbsolutePath.equals(absolutePath)) {
                AppLog.d(TAG, "Music file already exists: " + filePath);
                return;
            }
        }

        // 提取音频文件元数据
        MusicItem item = extractMetadata(filePath);
        musicItems.add(item);
        savePlaylist();

        // 如果当前没有播放索引且有音乐，则自动设置为第一首
        if (currentIndex < 0 && !musicItems.isEmpty()) {
            currentIndex = 0;
            appConfig.setLastPlayedIndex(currentIndex);
            appConfig.setLastPlayedPosition(0);
            AppLog.d(TAG, "Auto set currentIndex to 0 after adding music and saved to AppConfig");
        }

        AppLog.d(TAG, "Added music file: " + filePath + " with title: " + item.title);
    }

    /**
     * 添加多个音乐文件
     * @param filePaths 文件路径列表
     * @param callback 回调，用于返回实际添加的文件数量
     */
    public void addMusicFiles(List<String> filePaths, AddMusicCallback callback) {
        // 在后台线程中处理批量添加，避免阻塞UI线程
        new Thread(() -> {
            int addedCount = 0;
            List<MusicItem> newItems = new ArrayList<>();

            // 先收集所有新的音乐项
            for (String filePath : filePaths) {
                File file = new File(filePath);
                if (!file.exists() || !isMusicFile(file)) {
                    continue;
                }

                // 获取文件的绝对路径，避免路径格式不同导致的重复添加
                String absolutePath;
                try {
                    absolutePath = file.getCanonicalPath();
                } catch (java.io.IOException e) {
                    absolutePath = file.getAbsolutePath();
                }

                boolean exists = false;
                for (MusicItem item : musicItems) {
                    File existingFile = new File(item.filePath);
                    String existingAbsolutePath;
                    try {
                        existingAbsolutePath = existingFile.getCanonicalPath();
                    } catch (java.io.IOException e) {
                        existingAbsolutePath = existingFile.getAbsolutePath();
                    }
                    if (existingAbsolutePath.equals(absolutePath)) {
                        AppLog.d(TAG, "Music file already exists: " + filePath);
                        exists = true;
                        break;
                    }
                }

                if (!exists) {
                    // 提取音频文件元数据
                    MusicItem item = extractMetadata(filePath);
                    newItems.add(item);
                    addedCount++;
                }
            }

            // 添加新的音乐项并保存播放列表
            if (!newItems.isEmpty()) {
                musicItems.addAll(newItems);
                savePlaylist();

                // 如果当前没有播放索引且有音乐，则自动设置为第一首
                if (currentIndex < 0 && !musicItems.isEmpty()) {
                    currentIndex = 0;
                    appConfig.setLastPlayedIndex(currentIndex);
                    appConfig.setLastPlayedPosition(0);
                    AppLog.d(TAG, "Auto set currentIndex to 0 after adding multiple music files and saved to AppConfig");
                }

                AppLog.d(TAG, "Added " + addedCount + " music files");
            }

            // 回调返回实际添加的文件数量
            if (callback != null) {
                callback.onAddComplete(addedCount);
            }
        }).start();
    }

    // 用于回调返回实际添加的文件数量
    public interface AddMusicCallback {
        void onAddComplete(int addedCount);
    }

    // 重载方法，保持向后兼容
    public void addMusicFiles(List<String> filePaths) {
        addMusicFiles(filePaths, null);
    }

    public void removeMusicItem(int index) {
        if (index >= 0 && index < musicItems.size()) {
            musicItems.remove(index);
            savePlaylist();
            if (index == currentIndex) {
                stop();
                currentIndex = -1;
            } else if (index < currentIndex) {
                currentIndex--;
            }
        }
    }

    public void removeMusicItems(List<Integer> indices) {
        java.util.Collections.sort(indices, java.util.Collections.reverseOrder());
        for (int index : indices) {
            removeMusicItem(index);
        }
    }

    public List<MusicItem> getMusicItems() {
        return new ArrayList<>(musicItems);
    }

    public int getMusicItemCount() {
        return musicItems.size();
    }

    public MusicItem getCurrentMusicItem() {
        if (currentIndex >= 0 && currentIndex < musicItems.size()) {
            return musicItems.get(currentIndex);
        }
        return null;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    /**
     * 开始播放指定索引的音乐
     * 
     * 方法链路：
     * MusicPlayerFragment列表点击 → musicPlayerManager.start()
     * MusicPlayerFragment下一曲/上一曲 → musicPlayerManager.playNext/Previous() → musicPlayerManager.start()
     * MusicPlayerFragment恢复播放（无当前索引）→ musicPlayerManager.resumeLastPlayed() → musicPlayerManager.start()
     * 
     * 核心策略：直接使用正确的AudioAttributes初始化，避免MediaCodec错误！
     * 1. 每次都完全重新初始化播放器
     * 2. 初始化时直接使用正确的audioUsage（从AudioOutputManager获取最新的）
     * 3. 直接开始播放，不在播放后更新属性
     * 
     * @param index 要播放的音乐在播放列表中的索引
     * @return 是否成功开始播放
     */
    public boolean start(int index) {
        return start(index, -1);
    }
    
    public boolean start(int index, long userPosition) {
        AppLog.d(TAG, "=== 开始播放方法调用 ===");
        AppLog.d(TAG, "播放索引: " + index + ", 总音乐数: " + musicItems.size() + ", 用户位置: " + userPosition);
        
        if (index < 0 || index >= musicItems.size()) {// 验证索引是否有效
            AppLog.e(TAG, "无效的音乐索引: " + index + ", 总项数: " + musicItems.size());
            return false;
        }

        // 步骤1：停止进度更新，避免与播放冲突
        stopProgressUpdate();
        AppLog.d(TAG, "步骤1完成：进度更新已停止");
        
        // 步骤2：保存当前播放位置（如果有）- 只有在播放同一首歌曲时才需要
        long seekPosition = 0;// 当前播放位置
        // 保存当前播放位置（如果有）
        if (userPosition >= 0) {
            // 使用用户选择的位置
            seekPosition = userPosition;
            AppLog.d(TAG, "步骤2完成：使用用户选择的播放位置: " + seekPosition);
        } else if (exoPlayer != null && currentIndex >= 0 && currentIndex == index) {
            seekPosition = exoPlayer.getCurrentPosition();// 获取当前播放位置
            AppLog.d(TAG, "步骤2完成：保存播放位置: " + seekPosition);
        } else if (exoPlayer == null && currentIndex >= 0 && currentIndex == index) {
            // 应用重新启动时，从AppConfig加载保存的播放位置
            seekPosition = appConfig.getLastPlayedPosition();
            AppLog.d(TAG, "步骤2完成：从AppConfig加载播放位置: " + seekPosition);
        } else {
            AppLog.d(TAG, "步骤2：无需保存播放位置（不同歌曲或首次播放）");
        }
        
        // 步骤3：完全释放旧的播放器实例，确保新实例是干净的
        // 这是参考MicrophoneManager的成功实现
        AppLog.d(TAG, "步骤3：释放旧的播放器实例");
        releasePlayer();// 释放旧的播放器实例，确保新实例是干净的
        AppLog.d(TAG, "步骤3完成：旧播放器已释放");
        
        // 步骤4：初始化新的播放器实例
        AppLog.d(TAG, "步骤4：初始化新的播放器实例");
        initPlayer();// 初始化新的播放器实例
        AppLog.d(TAG, "步骤4完成：新播放器初始化完成");

        try {
            // 步骤5：获取要播放的音乐项
            MusicItem item = musicItems.get(index);
            AppLog.d(TAG, "步骤5完成：选中音乐项: " + item.title + " 路径: " + item.filePath);
            
            // 步骤5.1：尝试提取并更新元数据
            AppLog.d(TAG, "步骤5.1：尝试提取并更新元数据");
            MusicItem updatedItem = extractMetadata(item.filePath);
            if (!updatedItem.title.equals(item.title) || !updatedItem.artist.equals(item.artist)) {
                // 更新音乐项的元数据
                item.title = updatedItem.title;
                item.artist = updatedItem.artist;
                item.duration = updatedItem.duration;
                // 保存更新后的播放列表
                savePlaylist();
                AppLog.d(TAG, "步骤5.1完成：元数据已更新并保存");
            } else {
                AppLog.d(TAG, "步骤5.1完成：元数据无需更新");
            }
            
            // 步骤6：创建MediaItem并设置到播放器
            MediaItem mediaItem = MediaItem.fromUri(Uri.fromFile(new File(item.filePath)));
            AppLog.d(TAG, "步骤6完成：创建MediaItem成功");
            
            AppLog.d(TAG, "步骤7：设置MediaItem到播放器");
            exoPlayer.setMediaItem(mediaItem);// 设置播放器的媒体项
            AppLog.d(TAG, "步骤7完成：MediaItem设置成功");
            
            // 步骤8：准备播放器，加载音频文件
            AppLog.d(TAG, "步骤8：准备播放器，加载音频文件");
            exoPlayer.prepare();// 准备播放器，加载音频文件
            AppLog.d(TAG, "步骤8完成：播放器准备完成");
            
            // 步骤9：跳转到之前保存的位置（如果有）- 只有在播放同一首歌曲时才需要
            if (seekPosition > 0 && currentIndex == index) {// 如果有保存的位置播放位置且是同一首歌曲
                AppLog.d(TAG, "步骤9：跳转到保存的位置: " + seekPosition);
                exoPlayer.seekTo(seekPosition);// 跳转到之前保存的位置播放位置
                AppLog.d(TAG, "步骤9完成：跳转位置成功");
            } else {
                AppLog.d(TAG, "步骤9：无需跳转位置（无保存位置或不同歌曲）");
            }
            
            // 步骤10：新策略 - 直接使用正确的AudioAttributes播放！
            // 已经在initPlayer()中设置了正确的属性，直接开始播放
            AppLog.d(TAG, "=== 步骤10：开始播放 ===");
            AppLog.d(TAG, "使用正确的音频属性开始播放");
            AppLog.d(TAG, "当前输出模式: " + (audioOutputManager != null ? (audioOutputManager.getOutputMode() == 0 ? "车内" : "车外") : "未知"));
            AppLog.d(TAG, "音频用途: " + (audioOutputManager != null ? audioOutputManager.getAudioUsage() : "未知"));
            
            // 启动前台服务，确保后台播放权限
            com.aug32.l7audio.service.AudioForegroundService.start(context);
            
            exoPlayer.play();// 开始播放
            isPlaying = true;
            AppLog.d(TAG, "步骤10完成：播放开始成功");
            AppLog.d(TAG, "isPlaying状态已更新为: true");
            
            // 步骤11：更新当前索引并保存到AppConfig
            currentIndex = index;// 更新当前索引
            appConfig.setLastPlayedIndex(currentIndex);// 保存当前索引到AppConfig
            AppLog.d(TAG, "步骤11完成：更新最后播放索引到: " + currentIndex);

            AppLog.d(TAG, "=== 播放启动成功 ===");
            AppLog.d(TAG, "成功开始播放: " + item.title + " - " + item.artist);
            return true;
        } catch (Exception e) {
            AppLog.e(TAG, "播放启动失败", e);
            AppLog.e(TAG, "错误详情: " + e.getMessage());
            AppLog.e(TAG, "当前状态 - 模式: " + (audioOutputManager != null ? audioOutputManager.getOutputMode() : "null") + ", 用途: " + (audioOutputManager != null ? audioOutputManager.getAudioUsage() : "null") + ", 播放器: " + (exoPlayer != null ? "已初始化" : "null"));
            handleError("播放启动失败: " + e.getMessage());
            return false;
        }
    }
    
    private void releasePlayer() {
        AppLog.d(TAG, "=== 开始释放播放器 ===");
        if (exoPlayer != null) {
            AppLog.d(TAG, "ExoPlayer实例存在，开始释放过程");
            
            // 步骤1：停止进度更新，避免与播放冲突
            stopProgressUpdate();
            AppLog.d(TAG, "步骤1完成：进度更新已停止");
            
            // 步骤2：停止播放器
            try {
                exoPlayer.stop();
                AppLog.d(TAG, "步骤2完成：播放器已停止");
            } catch (Exception e) {
                AppLog.e(TAG, "步骤2失败：停止播放器时出错", e);
            }
            
            // 步骤3：清除媒体项
            try {
                exoPlayer.clearMediaItems();// 清除播放器的媒体项
                AppLog.d(TAG, "步骤3完成：媒体项已清除");
            } catch (Exception e) {
                AppLog.e(TAG, "步骤3失败：清除媒体项时出错", e);
            }
            
            // 步骤4：移除监听器
            try {
                exoPlayer.removeListener(playerListener);// 移除播放器监听器
                AppLog.d(TAG, "步骤4完成：播放器监听器已移除");
            } catch (Exception e) {
                AppLog.e(TAG, "步骤4失败：移除监听器时出错", e);
            }
            
            // 步骤5：释放播放器资源
            try {
                exoPlayer.release();// 释放播放器资源
                AppLog.d(TAG, "步骤5完成：播放器资源已释放");
            } catch (Exception e) {
                AppLog.e(TAG, "步骤5失败：释放播放器时出错", e);
            }
            
            // 步骤6：重置状态
            exoPlayer = null;
            isPlaying = false;
            AppLog.d(TAG, "步骤6完成：状态已重置");
            
            AppLog.d(TAG, "=== 播放器释放完成 ===");
        } else {
            AppLog.d(TAG, "无ExoPlayer实例需要释放");
        }
    }

    /**
     * 恢复播放最后一首
     * 
     * 方法链路：
     * MusicPlayerFragment.togglePlayPause()（无当前索引）
     * → MusicPlayerManager.resumeLastPlayed()
     * → MusicPlayerManager.start(lastIndex)
     * → [核心策略：先播放，后更新音频属性]
     */
    public void resumeLastPlayed() {
        AppLog.d(TAG, "=== METHOD CHAIN: resumeLastPlayed() ===");
        int lastIndex = appConfig.getLastPlayedIndex();
        AppLog.d(TAG, "Last played index from AppConfig: " + lastIndex);
        if (lastIndex >= 0 && lastIndex < musicItems.size()) {
            AppLog.d(TAG, "Calling start(" + lastIndex + ") to resume last played");
            if (start(lastIndex)) {
                long lastPosition = appConfig.getLastPlayedPosition();
                AppLog.d(TAG, "Last played position: " + lastPosition);
                if (lastPosition > 0 && exoPlayer != null) {
                    exoPlayer.seekTo(lastPosition);
                }
            }
        } else if (!musicItems.isEmpty()) {
            // 如果没有有效的lastPlayedIndex但有音乐，则自动播放第一首
            AppLog.d(TAG, "No valid last played index, playing first song");
            start(0);
        }
    }

    /**
     * 暂停播放
     * 
     * 方法链路：
     * MusicPlayerFragment.togglePlayPause()（正在播放）
     * → MusicPlayerManager.pause()
     */
    public void pause() {
        AppLog.d(TAG, "=== METHOD CHAIN: pause() ===");
        if (exoPlayer != null && isPlaying) {
            exoPlayer.pause();
            if (exoPlayer != null) {
                appConfig.setLastPlayedPosition(exoPlayer.getCurrentPosition());
               // audioOutputManager.pauseAudio();// 暂停音频输出
               // exoPlayer.stop();// 停止播放器
            }
            isPlaying = false;
            AppLog.d(TAG, "Playback paused");
        }
    }

    /**
     * 恢复播放
     * 
     * 方法链路：
     * MusicPlayerFragment.togglePlayPause()（有当前索引且暂停）
     * → MusicPlayerManager.resume()
     */
    private long userSelectedPosition = -1;
    
    public void resume() {
        AppLog.d(TAG, "=== METHOD CHAIN: resume() ===");
        if (exoPlayer != null && !isPlaying) {
            // 新策略：直接播放，因为属性已经在初始化时设置正确了
            AppLog.d(TAG, "Resuming playback - attributes already correct");
            exoPlayer.play();
            isPlaying = true;
            AppLog.d(TAG, "Playback resumed successfully");
        } else if (exoPlayer == null && currentIndex >= 0) {
            // 如果播放器未初始化但有当前播放索引，重新开始播放
            AppLog.d(TAG, "ExoPlayer not initialized but has current index, calling start(" + currentIndex + ")");
            start(currentIndex, userSelectedPosition);
            // 重置用户选择的位置
            userSelectedPosition = -1;
        }
    }
    
    /**
     * 恢复播放（带用户选择的位置）
     */
    public void resume(long position) {
        userSelectedPosition = position;
        resume();
    }

    public void stop() {
        if (exoPlayer != null) {
            stopProgressUpdate();
            exoPlayer.stop();
            exoPlayer.clearMediaItems();
        }

        isPlaying = false;
        if (currentIndex >= 0 && exoPlayer != null) {
            appConfig.setLastPlayedPosition(exoPlayer.getCurrentPosition());
        }

        if (callback != null) {
            callback.onPlaybackStopped();
        }

        AppLog.d(TAG, "Playback stopped");
    }

    public void release() {
        stop();
        if (exoPlayer != null) {
            exoPlayer.removeListener(playerListener);
            exoPlayer.release();
            exoPlayer = null;
        }
        stopProgressUpdate();
    }

    /**
     * 播放下一首 
     * 
     * 方法链路：
     * MusicPlayerFragment.playNext()
     * → MusicPlayerManager.playNext()
     * → MusicPlayerManager.start(nextIndex)
     * → [核心策略：先播放，后更新音频属性]
     * 
     * @return 是否成功开始播放
     */
    public boolean playNext() {
        AppLog.d(TAG, "=== METHOD CHAIN: playNext() ===");
        if (musicItems.isEmpty()) {
            AppLog.d(TAG, "Music list is empty, cannot play next");
            return false;
        }

        int nextIndex;
        if (shuffleMode) {
            // 生成一个与当前索引不同的随机索引
            do {
                nextIndex = (int) (Math.random() * musicItems.size());
            } while (nextIndex == currentIndex && musicItems.size() > 1);
            AppLog.d(TAG, "Shuffle mode enabled, random index: " + nextIndex);
        } else {
            nextIndex = (currentIndex + 1) % musicItems.size();
            AppLog.d(TAG, "Normal mode, next index: " + nextIndex);
        }
        AppLog.d(TAG, "Calling start(" + nextIndex + ")");
        return start(nextIndex);
    }

    /**
     * 播放上一首
     * 
     * 方法链路：
     * MusicPlayerFragment.playPrevious()
     * → MusicPlayerManager.playPrevious()
     * → MusicPlayerManager.start(prevIndex)
     * → [核心策略：先播放，后更新音频属性]
     * 
     * @return 是否成功开始播放
     */
    public boolean playPrevious() {
        AppLog.d(TAG, "=== METHOD CHAIN: playPrevious() ===");
        if (musicItems.isEmpty()) {
            AppLog.d(TAG, "Music list is empty, cannot play previous");
            return false;
        }

        int prevIndex = currentIndex - 1;// 上一首索引
        if (prevIndex < 0) {// 上一首索引小于0，循环播放最后一首
            prevIndex = musicItems.size() - 1;
        }
        AppLog.d(TAG, "Previous index: " + prevIndex);
        AppLog.d(TAG, "Calling start(" + prevIndex + ")");
        return start(prevIndex);
    }

    public void seekTo(long positionMs) {
        if (exoPlayer != null) {
            exoPlayer.seekTo(positionMs);
            AppLog.d(TAG, "Seeked to: " + positionMs + "ms");
        }
    }

    public long getCurrentPosition() {
        if (exoPlayer != null) {
            return exoPlayer.getCurrentPosition();
        }
        return 0;
    }

    public long getDuration() {
        if (exoPlayer != null) {
            return exoPlayer.getDuration();
        }
        return 0;
    }

    public boolean isPlaying() {
        // 确保isPlaying状态与实际播放器状态一致
        if (exoPlayer != null) {
            isPlaying = exoPlayer.isPlaying();
        }
        return isPlaying;
    }

    public boolean isExoPlayerInitialized() {
        return exoPlayer != null;
    }

    public void resetPlayingState() {
        isPlaying = false;
        AppLog.d(TAG, "Playing state reset to false");
    }

    public long getLastPlayedPosition() {
        return appConfig.getLastPlayedPosition();
    }

    public int getRepeatMode() {
        return repeatMode;
    }

    public void setRepeatMode(int mode) {
        this.repeatMode = mode;
        if (exoPlayer != null) {
            exoPlayer.setRepeatMode(mode);
        }
        appConfig.setRepeatMode(mode);
    }

    public boolean isShuffleModeEnabled() {
        return shuffleMode;
    }

    public void setShuffleModeEnabled(boolean enabled) {
        this.shuffleMode = enabled;
        if (exoPlayer != null) {
            exoPlayer.setShuffleModeEnabled(enabled);
        }
        appConfig.setShuffleModeEnabled(enabled);
    }

    private void handleCompletion() {
        if (callback != null) {
            callback.onPlaybackCompleted();
        }

        if (repeatMode == Player.REPEAT_MODE_ONE) {
            if (exoPlayer != null) {
                exoPlayer.seekTo(0);
                exoPlayer.play();
            }
        } else if (repeatMode == Player.REPEAT_MODE_ALL || (currentIndex + 1 < musicItems.size())) {
            playNext();
        }
    }

    private void handleError(String error) {
        if (callback != null) {
            callback.onError(error);
        }
        AppLog.e(TAG, "Error occurred: " + error);
        AppLog.e(TAG, "Current state - 689mode: " + (audioOutputManager != null ? audioOutputManager.getAudioOutputMode() : "null") + ", usage: " + (audioOutputManager != null ? audioOutputManager.getAudioUsage() : "null") + ", player: " + (exoPlayer != null ? "initialized" : "null"));
    }

    private void startProgressUpdate() {
        if (progressRunnable == null) {
            progressRunnable = new Runnable() {
                @Override
                public void run() {
                    if (exoPlayer != null && isPlaying) {
                        long position = exoPlayer.getCurrentPosition();
                        long duration = exoPlayer.getDuration();
                        if (callback != null) {
                            callback.onPlaybackProgress(position, duration);
                        }
                        appConfig.setLastPlayedPosition(position);
                    }
                    handler.postDelayed(this, 1000);
                }
            };
        }
        handler.post(progressRunnable);
    }

    private void stopProgressUpdate() {
        if (progressRunnable != null) {
            handler.removeCallbacks(progressRunnable);
        }
    }

    private boolean isMusicFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".flac") ||
                name.endsWith(".ogg") || name.endsWith(".m4a") || name.endsWith(".aac");
    }

    /**
     * 提取音频文件的元数据
     */
    private MusicItem extractMetadata(String filePath) {
        MediaMetadataRetriever retriever = null;
        try {
            retriever = new MediaMetadataRetriever();
            
            // 尝试不同的方式设置数据源
            try {
                retriever.setDataSource(filePath);
                AppLog.d(TAG, "Successfully set data source using file path: " + filePath);
            } catch (Exception e) {
                AppLog.e(TAG, "Failed to set data source using file path", e);
                // 尝试使用URI方式
                try {
                    android.net.Uri uri = android.net.Uri.fromFile(new File(filePath));
                    retriever.setDataSource(uri.toString());
                    AppLog.d(TAG, "Successfully set data source using URI: " + uri.toString());
                } catch (Exception e2) {
                    AppLog.e(TAG, "Failed to set data source using URI", e2);
                    throw e2;
                }
            }

            String title = null;
            String artist = null;
            String durationStr = null;

            // 详细日志：尝试所有可能的元数据键
            AppLog.d(TAG, "=== 开始提取元数据 ===");

            // 尝试提取标题
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            AppLog.d(TAG, "METADATA_KEY_TITLE: " + title);
            
            // 尝试提取艺术家
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            AppLog.d(TAG, "METADATA_KEY_ARTIST: " + artist);
            
            // 尝试提取专辑
            String album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
            AppLog.d(TAG, "METADATA_KEY_ALBUM: " + album);
            
            // 尝试提取时长
            durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            AppLog.d(TAG, "METADATA_KEY_DURATION: " + durationStr);

            // 尝试其他可能的键
            String albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST);
            AppLog.d(TAG, "METADATA_KEY_ALBUMARTIST: " + albumArtist);
            
            String composer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER);
            AppLog.d(TAG, "METADATA_KEY_COMPOSER: " + composer);

            // 如果没有提取到艺术家，尝试使用专辑艺术家
            if (artist == null || artist.isEmpty()) {
                artist = albumArtist;
                AppLog.d(TAG, "Using album artist as artist: " + artist);
            }

            // 如果没有提取到标题，使用文件名
            if (title == null || title.isEmpty()) {
                File file = new File(filePath);
                title = file.getName();
                // 移除文件扩展名
                int dotIndex = title.lastIndexOf('.');
                if (dotIndex > 0) {
                    title = title.substring(0, dotIndex);
                }
                AppLog.d(TAG, "Using filename as title: " + title);
            }

            // 如果没有提取到艺术家，使用默认值
            if (artist == null || artist.isEmpty()) {
                artist = "未知艺术家";
                AppLog.d(TAG, "Using default artist: " + artist);
            }

            // 解析时长
            long duration = 0;
            if (durationStr != null && !durationStr.isEmpty()) {
                try {
                    duration = Long.parseLong(durationStr);
                    AppLog.d(TAG, "Parsed duration: " + duration);
                } catch (NumberFormatException e) {
                    AppLog.e(TAG, "Failed to parse duration", e);
                }
            }

            AppLog.d(TAG, "=== 元数据提取完成 ===");
            AppLog.d(TAG, "Final metadata - Title: " + title + ", Artist: " + artist + ", Duration: " + duration);
            return new MusicItem(filePath, title, artist, duration);
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to extract metadata from " + filePath, e);
            // 提取失败时，使用默认值
            File file = new File(filePath);
            String title = file.getName();
            // 移除文件扩展名
            int dotIndex = title.lastIndexOf('.');
            if (dotIndex > 0) {
                title = title.substring(0, dotIndex);
            }
            AppLog.d(TAG, "Fallback metadata - Title: " + title + ", Artist: 未知艺术家, Duration: 0");
            return new MusicItem(filePath, title, "未知艺术家", 0);
        } finally {
            if (retriever != null) {
                try {
                    retriever.release();
                    AppLog.d(TAG, "Successfully released MediaMetadataRetriever");
                } catch (Exception e) {
                    AppLog.e(TAG, "Failed to release MediaMetadataRetriever", e);
                }
            }
        }
    }

    /**
     * 提取音频文件的专辑封面
     */
    public android.graphics.Bitmap extractAlbumArt(String filePath) {
        MediaMetadataRetriever retriever = null;
        try {
            retriever = new MediaMetadataRetriever();
            retriever.setDataSource(filePath);

            byte[] artBytes = retriever.getEmbeddedPicture();
            if (artBytes != null && artBytes.length > 0) {
                return android.graphics.BitmapFactory.decodeByteArray(artBytes, 0, artBytes.length);
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to extract album art from " + filePath, e);
        } finally {
            if (retriever != null) {
                try {
                    retriever.release();
                } catch (Exception e) {
                    AppLog.e(TAG, "Failed to release MediaMetadataRetriever", e);
                }
            }
        }
        return null;
    }

    public void setVolume(int volume) {
        if (exoPlayer != null) {
            float volumeLevel = volume / 100.0f;
            exoPlayer.setVolume(volumeLevel);
        }
    }

    public int getVolume() {
        if (exoPlayer != null) {
            return (int) (exoPlayer.getVolume() * 100);
        }
        return 100;
    }

    public int getCurrentOutputMode() {
        if (audioOutputManager != null) {
            return audioOutputManager.getOutputMode();
        }
        return -1;
    }
}
