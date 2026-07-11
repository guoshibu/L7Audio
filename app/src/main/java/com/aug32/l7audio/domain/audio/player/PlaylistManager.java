package com.aug32.l7audio.domain.audio.player;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.aug32.l7audio.data.local.AppConfig;
import com.aug32.l7audio.domain.audio.player.MusicItem;
import com.aug32.l7audio.utils.AppExecutors;
import com.aug32.l7audio.utils.AppLog;
import com.aug32.l7audio.utils.AudioMetadataReader;
import com.aug32.l7audio.utils.FileUtils;
import com.aug32.l7audio.utils.WavMetadataReader;

import java.util.HashSet;
import java.util.Set;

/**
 * 播放列表管理器
 *
 * <p>职责：
 * <ul>
 *   <li>管理音乐播放列表（线程安全）</li>
 *   <li>支持音乐的添加、删除、查询等操作</li>
 *   <li>持久化存储播放列表到 SharedPreferences</li>
 *   <li>管理循环模式（列表循环/随机/单曲循环/单曲播放）</li>
 *   <li>提供切歌逻辑（上一首/下一首/自动下一首）</li>
 * </ul>
 *
 * <p>设计意图：
 * <ul>
 *   <li>采用线程安全设计，所有修改操作加锁，读取返回副本，支持多线程访问</li>
 *   <li>通过观察者模式通知播放列表变化，实现与UI层解耦</li>
 *   <li>封装多种循环模式的切歌逻辑，对外提供统一的切歌接口</li>
 *   <li>持久化播放列表和播放位置，应用重启后可恢复</li>
 * </ul>
 *
 * <p>线程安全：所有修改操作都加锁，读取返回副本
 */
public class PlaylistManager {

    // 日志标签
    private static final String TAG = "PlaylistManager";

    /**
     * 添加音乐完成回调接口
     */
    public interface AddCallback {
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

    /**
     * 播放列表变化监听器接口
     */
    public interface PlaylistChangeListener {
        /**
         * 播放列表内容发生变化时回调（添加/删除歌曲）
         */
        void onPlaylistChanged();

        /**
         * 当前播放索引发生变化时回调
         *
         * @param newIndex 新的当前播放索引
         */
        void onCurrentIndexChanged(int newIndex);
    }

    // 应用上下文，用于访问 SharedPreferences 等
    private final Context context;
    // 应用配置管理器，用于持久化存储播放列表和配置
    private final AppConfig appConfig;
    // Gson 实例，用于播放列表的序列化与反序列化
    private final Gson gson = new Gson();
    // 锁对象，用于保证线程安全
    private final Object lock = new Object();
    // 主线程 Handler，用于将回调切换到主线程执行
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    // 防抖 Handler，1秒内多次修改合并为一次持久化
    private final android.os.Handler debounceHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable saveRunnable = () -> {
        // 在 IO 线程中执行序列化，避免 gson.toJson() O(N) 阻塞主线程
        AppExecutors.getInstance().executeOnIOThread(() -> {
            synchronized (lock) {
                saveToStorage();
            }
        });
    };

    // 播放列表数据
    private List<MusicItem> items;
    // 现有路径的规范化集合，用于 O(1) 去重查重，增量维护
    private final Set<String> existingPaths = new HashSet<>();
    // 当前播放索引，-1 表示无当前播放
    private int currentIndex = -1;
    // 循环模式，默认为列表循环
    private int repeatMode = REPEAT_MODE_ALL;
    // 播放列表变化监听器
    private PlaylistChangeListener listener;

    /** 播放列表最大条目数，防止 SP 序列化/反序列化 O(N) 过大 */
    private static final int MAX_PLAYLIST_SIZE = 1000;

    /** 列表循环模式：按顺序循环播放整个列表 */
    public static final int REPEAT_MODE_ALL = 0;
    /** 随机播放模式：随机选择下一首歌曲 */
    public static final int REPEAT_MODE_SHUFFLE = 1;
    /** 单曲循环模式：重复播放当前歌曲 */
    public static final int REPEAT_MODE_ONE = 2;
    /** 单曲播放模式：当前歌曲播放结束后停止 */
    public static final int REPEAT_MODE_OFF = 3;

    /**
     * 构造函数，初始化播放列表管理器
     *
     * @param context 上下文对象，内部会自动转换为 ApplicationContext 以避免内存泄漏
     */
    public PlaylistManager(Context context) {
        this.context = context.getApplicationContext();
        this.appConfig = new AppConfig(this.context);
        this.items = new ArrayList<>();
        loadFromStorage();
    }

    /**
     * 设置播放列表变化监听器
     *
     * @param listener 播放列表变化监听器实例
     */
    public void setPlaylistChangeListener(PlaylistChangeListener listener) {
        this.listener = listener;
    }

    // ========== 查询方法（线程安全） ==========

    /**
     * 获取所有音乐项
     *
     * <p>返回不可修改的列表包装，内部 item 可能被 MusicPlayerManager 写入 lyrics/albumArt 缓存。
     * 调用方应只读访问。
     *
     * @return 所有音乐项的只读列表
     */
    public List<MusicItem> getAllItems() {
        synchronized (lock) {
            return Collections.unmodifiableList(new ArrayList<>(items));
        }
    }

    /**
     * 获取音乐项数量
     *
     * @return 播放列表中音乐的数量
     */
    public int getItemCount() {
        synchronized (lock) {
            return items.size();
        }
    }

    /**
     * 获取指定位置的音乐项
     *
     * @param index 索引位置
     * @return 该位置的音乐项，索引越界时返回 null
     */
    public MusicItem getItemAt(int index) {
        synchronized (lock) {
            if (index < 0 || index >= items.size()) return null;
            return items.get(index);
        }
    }

    /**
     * 获取当前播放索引
     *
     * @return 当前播放索引，无当前播放时返回 -1
     */
    public int getCurrentIndex() {
        synchronized (lock) {
            return currentIndex;
        }
    }

    /**
     * 获取当前播放的音乐项
     *
     * @return 当前播放的音乐项，无当前播放时返回 null
     */
    public MusicItem getCurrentItem() {
        synchronized (lock) {
            if (currentIndex < 0 || currentIndex >= items.size()) return null;
            return items.get(currentIndex);
        }
    }

    /**
     * 根据文件路径查找音乐索引
     *
     * @param filePath 音乐文件路径
     * @return 找到的索引位置，未找到时返回 -1
     */
    public int getIndexOf(String filePath) {
        if (filePath == null) return -1;
        synchronized (lock) {
            // 按文件路径精确匹配，filePath 作为唯一标识
            for (int i = 0; i < items.size(); i++) {
                if (filePath.equals(items.get(i).filePath)) {
                    return i;
                }
            }
            return -1;
        }
    }

    /**
     * 判断播放列表是否包含指定文件路径的音乐
     *
     * @param filePath 音乐文件路径
     * @return true 表示包含，false 表示不包含
     */
    public boolean contains(String filePath) {
        return getIndexOf(filePath) >= 0;
    }

    /**
     * 获取当前循环模式
     *
     * @return 循环模式常量，参见 REPEAT_MODE_* 常量
     */
    public int getRepeatMode() {
        synchronized (lock) {
            return repeatMode;
        }
    }

    /**
     * 判断是否为随机播放模式
     *
     * @return true 表示随机播放模式，false 表示其他模式
     */
    public boolean isShuffleMode() {
        synchronized (lock) {
            return repeatMode == REPEAT_MODE_SHUFFLE;
        }
    }

    // ========== 修改方法 ==========

    /**
     * 设置当前播放索引
     *
     * <p>索引变化时会保存到持久化存储，并通过监听器通知外部。
     *
     * @param index 新的当前播放索引，范围 [-1, items.size())，-1 表示无当前播放
     */
    public void setCurrentIndex(int index) {
        boolean changed = false;
        synchronized (lock) {
            // 只有索引有效且与当前不同时才更新，避免无意义的回调
            if (index >= -1 && index < items.size() && index != currentIndex) {
                currentIndex = index;
                // 持久化保存当前播放索引
                appConfig.setLastPlayedIndex(index);
                changed = true;
            }
        }
        // 在锁外回调，避免死锁
        if (changed && listener != null) {
            listener.onCurrentIndexChanged(currentIndex);
        }
    }

    /**
     * 设置循环模式
     *
     * @param mode 循环模式常量，参见 REPEAT_MODE_* 常量
     */
    public void setRepeatMode(int mode) {
        synchronized (lock) {
            this.repeatMode = mode;
            // 持久化保存循环模式设置
            appConfig.setRepeatMode(mode);
            // 同步保存随机模式设置（兼容旧配置）
            appConfig.setShuffleModeEnabled(mode == REPEAT_MODE_SHUFFLE);
        }
    }

    /**
     * 从文件路径添加音乐（异步，会读取元数据）
     *
     * <p>从文件路径创建 MusicItem，会读取文件的媒体元数据（标题、艺术家、时长等）。
     * 在计算线程中处理，避免阻塞主线程。
     *
     * @param filePaths 音乐文件路径列表
     * @param callback  添加完成回调，可为 null
     */
    public void addFromFilePaths(final List<String> filePaths, final AddCallback callback) {
        AppExecutors.getInstance().executeOnComputeThread(() -> {
            List<MusicItem> newItems = new ArrayList<>();
            for (String path : filePaths) {
                MusicItem item = createItemFromFile(path, "file://" + path);
                if (item != null) {
                    newItems.add(item);
                }
            }
            addItemsInternal(newItems, callback);
        });
    }

    /**
     * 移除指定位置的音乐
     *
     * <p>支持批量删除，会自动调整当前播放索引：
     * <ul>
     *   <li>如果当前播放的歌曲被删除，currentIndex 设为 -1</li>
     *   <li>如果删除的位置在当前播放索引之前，currentIndex 减 1</li>
     * </ul>
     *
     * @param positions 要删除的位置列表
     */
    public void removeItems(List<Integer> positions) {
        if (positions == null || positions.isEmpty()) return;

        boolean currentRemoved = false;
        boolean playlistChanged = false;

        synchronized (lock) {
            // 倒序排列后删除，避免正序删除导致索引偏移问题
            List<Integer> sorted = new ArrayList<>(positions);
            Collections.sort(sorted, (a, b) -> b - a);

            for (int pos : sorted) {
                if (pos >= 0 && pos < items.size()) {
                    // 同时从 existingPaths 移除对应路径
                    String removedPath = normalizeFilePath(items.get(pos).filePath);
                    items.remove(pos);
                    existingPaths.remove(removedPath);
                    playlistChanged = true;
                    // 当前播放的歌曲被删除
                    if (currentIndex == pos) {
                        currentIndex = -1;
                        currentRemoved = true;
                    } else if (currentIndex > pos) {
                        // 删除位置在当前索引之前，当前索引前移
                        currentIndex--;
                    }
                }
            }

            // 防抖持久化保存（1s 窗口）
            if (playlistChanged) {
                if (currentRemoved) {
                    appConfig.setLastPlayedIndex(-1);
                }
            }
        }

        // 防抖持久化保存
        if (playlistChanged) {
            debounceSave();
        }

        // 在锁外回调，避免死锁
        if (listener != null) {
            if (currentRemoved) {
                listener.onCurrentIndexChanged(currentIndex);
            }
            if (playlistChanged) {
                listener.onPlaylistChanged();
            }
        }
    }

    /**
     * 获取下一首的索引（手动点击下一首时调用）
     *
     * <p>注意：单曲循环模式下，手动点击下一首也会切换到下一首，
     * 与歌曲自动结束时的行为不同（单曲循环模式下自动结束会重播当前歌曲）。
     *
     * @return 下一首的索引，列表为空时返回 -1
     */
    public int getNextIndex() {
        synchronized (lock) {
            if (items.isEmpty()) return -1;
            // 无当前播放时，从第一首开始
            if (currentIndex < 0) return 0;

            switch (repeatMode) {
                case REPEAT_MODE_SHUFFLE:
                    // 随机模式：随机选择一首
                    return getRandomIndex();
                case REPEAT_MODE_ONE:
                case REPEAT_MODE_ALL:
                case REPEAT_MODE_OFF:
                default:
                    // 列表循环/单曲循环/单曲播放模式：手动下一首都按顺序播放
                    // 使用取模运算实现列表末尾回到开头的循环效果
                    return (currentIndex + 1) % items.size();
            }
        }
    }

    /**
     * 获取上一首的索引（手动点击上一首时调用）
     *
     * <p>注意：单曲循环模式下，手动点击上一首也会切换到上一首，
     * 与歌曲自动结束时的行为不同。
     *
     * @return 上一首的索引，列表为空时返回 -1
     */
    public int getPreviousIndex() {
        synchronized (lock) {
            if (items.isEmpty()) return -1;
            // 无当前播放时，从第一首开始
            if (currentIndex < 0) return 0;

            if (repeatMode == REPEAT_MODE_SHUFFLE) {
                // 随机模式：随机选择一首（上一首也随机）
                return getRandomIndex();
            }
            // 顺序模式：计算上一首索引
            // 加 items.size() 是为了避免 currentIndex=0 时出现负数
            return (currentIndex - 1 + items.size()) % items.size();
        }
    }

    /**
     * 歌曲结束时，决定下一首（根据循环模式）
     *
     * <p>与手动切歌不同，自动切歌时：
     * <ul>
     *   <li>单曲循环模式：重播当前歌曲</li>
     *   <li>单曲播放模式：返回 -1，表示停止播放</li>
     * </ul>
     *
     * @return 下一首的索引，返回 -1 表示应该停止播放
     */
    public int getNextIndexOnSongEnd() {
        synchronized (lock) {
            if (items.isEmpty()) return -1;

            switch (repeatMode) {
                case REPEAT_MODE_ONE:
                    // 单曲循环：重播当前歌曲
                    return currentIndex;
                case REPEAT_MODE_ALL:
                case REPEAT_MODE_SHUFFLE:
                    // 列表循环/随机：播放下一首
                    return getNextIndex();
                case REPEAT_MODE_OFF:
                default:
                    // 单曲播放：当前歌曲结束后停止
                    return -1;
            }
        }
    }

    private int getRandomIndex() {
        int n = items.size();
        // 只有一首时直接返回 0
        if (n <= 1) return 0;
        // 重新随机直到与当前不同，保证无偏（拒绝采样）
        int idx;
        do {
            idx = (int) (Math.random() * n);
        } while (idx == currentIndex);
        return idx;
    }

    // ========== 内部方法 ==========

    /**
     * 规范化文件路径，用于去重比对
     *
     * <p>规范化规则：
     * <ul>
     *   <li>优先使用 File.getCanonicalPath() 解析软链接和相对路径</li>
     *   <li>统一转换为小写，避免大小写敏感导致的重复</li>
     *   <li>统一使用正斜杠分隔符</li>
     * </ul>
     *
     * @param path 原始路径
     * @return 规范化后的路径
     */
    private String normalizeFilePath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        // content:// URI 不做规范化
        if (path.startsWith("content://")) {
            return path.toLowerCase(java.util.Locale.ROOT);
        }
        try {
            java.io.File file = new java.io.File(path);
            return file.getCanonicalPath().toLowerCase(java.util.Locale.ROOT);
        } catch (Exception e) {
            return path.toLowerCase(java.util.Locale.ROOT).replace('\\', '/');
        }
    }

    private void addItemsInternal(List<MusicItem> newItems, AddCallback callback) {
        final List<MusicItem> added = new ArrayList<>();
        // 使用数组包装基本类型，以便在匿名内部类中修改（Java 闭包限制）
        final int skippedExist[] = {0};
        final int skippedFailed[] = {0};
        final int startPos[] = {0};

        synchronized (lock) {
            // 记录添加前的列表大小，作为新添加项的起始位置
            startPos[0] = items.size();
            for (MusicItem item : newItems) {
                // 跳过无效数据
                if (item == null || item.filePath == null || item.filePath.isEmpty()) {
                    skippedFailed[0]++;
                    continue;
                }
                // 检查是否达到列表上限
                if (items.size() >= MAX_PLAYLIST_SIZE) {
                    skippedFailed[0]++;
                    continue;
                }
                // 检查是否已存在（按规范化文件路径去重）
                String normalizedPath = normalizeFilePath(item.filePath);
                if (existingPaths.contains(normalizedPath)) {
                    skippedExist[0]++;
                    continue;
                }
                items.add(item);
                added.add(item.copy());
                existingPaths.add(normalizedPath);
            }
        }
        // 防抖持久化保存（1s 窗口），避免频繁增删时反复序列化全列表
        debounceSave();

        // 在主线程回调播放列表变化
        if (listener != null && !added.isEmpty()) {
            mainHandler.post(() -> listener.onPlaylistChanged());
        }

        // 在主线程回调添加完成结果
        if (callback != null) {
            mainHandler.post(() ->
                    callback.onAddComplete(added, startPos[0], skippedExist[0], skippedFailed[0]));
        }
    }

    private MusicItem createItemFromFile(String filePath, String contentUri) {
        try {
            // 判断是否为 content:// URI（无法通过 File API 直接访问）
            boolean isContentUri = (filePath != null && filePath.startsWith("content://"))
                    || (contentUri != null && contentUri.startsWith("content://"));
            // 实际用于播放的路径
            String actualPath = filePath;
            if (isContentUri && (filePath == null || filePath.startsWith("content://"))) {
                // 没有真实文件路径时，content URI 也作为路径存入（供播放使用）
                actualPath = contentUri;
            }

            File file = null;
            if (!isContentUri) {
                file = new File(filePath);
                // 文件不存在或为空时返回 null
                if (!file.exists() || file.length() == 0) {
                    return null;
                }
            }

            // 默认值：文件名作为标题，未知艺术家，时长为 0
            String title = "";
            if (file != null) {
                title = file.getName();
            } else if (contentUri != null) {
                // 从 content URI 中提取文件名作为默认标题
                title = extractFileNameFromUri(contentUri);
            }
            if (title == null || title.isEmpty()) {
                title = "未知歌曲";
            }
            String artist = "";
            long duration = 0;

            // 格式感知的元数据提取：WAV 快路径 / FLAC 自解析 / 其余走 MediaMetadataRetriever
            if (!isContentUri && file != null) {
                String ext = FileUtils.getExtension(filePath).toLowerCase(Locale.ROOT);
                switch (ext) {
                    case ".wav":
                        WavMetadataReader.AudioMetadata wavMeta = AudioMetadataReader.readMetadata(filePath);
                        if (wavMeta != null) {
                            if (wavMeta.title != null && !wavMeta.title.isEmpty()) title = wavMeta.title;
                            if (wavMeta.artist != null && !wavMeta.artist.isEmpty()) artist = wavMeta.artist;
                            if (wavMeta.durationMs > 0) duration = wavMeta.durationMs;
                        }
                        if (AudioMetadataReader.isUnknownTitle(title) || title.equals(file.getName())) {
                            title = parseTitleFromFileName(filePath);
                        }
                        if (duration == 0) {
                            duration = parseWavDuration(file);
                        }
                        break;
                    case ".flac":
                    case ".m4a":
                    case ".aac":
                    case ".mp4":
                        WavMetadataReader.AudioMetadata selfMeta = AudioMetadataReader.readMetadata(filePath);
                        if (selfMeta != null) {
                            if (selfMeta.title != null && !selfMeta.title.isEmpty()) title = selfMeta.title;
                            if (selfMeta.artist != null && !selfMeta.artist.isEmpty()) artist = selfMeta.artist;
                            if (selfMeta.durationMs > 0) duration = selfMeta.durationMs;
                        }
                        if (AudioMetadataReader.isUnknownTitle(title) || title.equals(file.getName())) {
                            title = parseTitleFromFileName(filePath);
                        }
                        break;
                    default:
                        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                        try {
                            retriever.setDataSource(filePath);
                            String extractedTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                            if (extractedTitle != null && !extractedTitle.isEmpty()) title = extractedTitle;
                            String extractedArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                            if (extractedArtist != null && !extractedArtist.isEmpty()) artist = extractedArtist;
                            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                            if (durationStr != null) duration = Long.parseLong(durationStr);
                        } catch (Exception e) {
                            AppLog.d(TAG, "Failed to extract metadata from " + actualPath);
                        } finally {
                            try { retriever.release(); } catch (Exception ignore) {}
                        }
                        if (AudioMetadataReader.isUnknownTitle(title) || title.equals(file.getName())) {
                            title = parseTitleFromFileName(filePath);
                        }
                        break;
                }
            } else {
                // content:// URI：使用 MediaMetadataRetriever（原逻辑）
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                try {
                    if (contentUri != null && contentUri.startsWith("content://")) {
                        retriever.setDataSource(context, Uri.parse(contentUri));
                    } else {
                        retriever.setDataSource(filePath);
                    }
                    String extractedTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                    if (extractedTitle != null && !extractedTitle.isEmpty()) title = extractedTitle;
                    String extractedArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                    if (extractedArtist != null && !extractedArtist.isEmpty()) artist = extractedArtist;
                    String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    if (durationStr != null) duration = Long.parseLong(durationStr);
                } catch (Exception e) {
                    AppLog.d(TAG, "Failed to extract metadata from " + actualPath);
                } finally {
                    try { retriever.release(); } catch (Exception ignore) {}
                }
            }

            MusicItem item = new MusicItem(actualPath, contentUri, title, artist, duration);
            if (file != null) {
                item.fileModified = file.lastModified();
            }
            return item;
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to create MusicItem", e);
            return null;
        }
    }

    private static String parseTitleFromFileName(String filePath) {
        String name = new File(filePath).getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        return name;
    }

    private static long parseWavDuration(File file) {
        try {
            RandomAccessFile raf = new RandomAccessFile(file, "r");
            try {
                byte[] buf = new byte[4];
                raf.readFully(buf);
                if (!"RIFF".equals(new String(buf, "US-ASCII"))) return 0;
                raf.skipBytes(4);
                raf.readFully(buf);
                if (!"WAVE".equals(new String(buf, "US-ASCII"))) return 0;

                int channels = 0, sampleRate = 0, bitsPerSample = 0;
                long dataSize = 0;

                while (raf.getFilePointer() + 8 < raf.length()) {
                    raf.readFully(buf);
                    byte[] sizeBytes = new byte[4];
                    raf.readFully(sizeBytes);
                    int chunkSize = (sizeBytes[0] & 0xFF) | ((sizeBytes[1] & 0xFF) << 8)
                            | ((sizeBytes[2] & 0xFF) << 16) | ((sizeBytes[3] & 0xFF) << 24);
                    long nextChunk = raf.getFilePointer() + chunkSize;

                    String chunkId = new String(buf, "US-ASCII");
                    if ("fmt ".equals(chunkId)) {
                        byte[] fmtData = new byte[Math.min(chunkSize, 16)];
                        raf.readFully(fmtData);
                        channels = (fmtData[2] & 0xFF) | ((fmtData[3] & 0xFF) << 8);
                        sampleRate = (fmtData[4] & 0xFF) | ((fmtData[5] & 0xFF) << 8)
                                | ((fmtData[6] & 0xFF) << 16) | ((fmtData[7] & 0xFF) << 24);
                        bitsPerSample = (fmtData[14] & 0xFF) | ((fmtData[15] & 0xFF) << 8);
                    } else if ("data".equals(chunkId)) {
                        dataSize = (sizeBytes[0] & 0xFF) | ((sizeBytes[1] & 0xFF) << 8)
                                | ((sizeBytes[2] & 0xFF) << 16) | ((sizeBytes[3] & 0xFF) << 24);
                        break;
                    }
                    raf.seek(nextChunk);
                }

                if (sampleRate > 0 && channels > 0 && bitsPerSample > 0) {
                    long bytesPerSec = (long) sampleRate * channels * (bitsPerSample / 8);
                    if (bytesPerSec > 0) {
                        return (dataSize * 1000) / bytesPerSec;
                    }
                }
                return 0;
            } finally {
                raf.close();
            }
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 从 content URI 中提取文件名
     *
     * <p>用于当无法获取真实文件路径时，从 URI 中获取一个可读的名称作为默认标题。
     * 优先使用 ContentResolver 查询 DISPLAY_NAME，失败则从 URI 路径中提取。
     *
     * @param uriString content:// URI
     * @return 文件名，获取失败返回 null
     */
    private String extractFileNameFromUri(String uriString) {
        if (uriString == null || uriString.isEmpty()) return null;
        try {
            Uri uri = Uri.parse(uriString);
            // 尝试从 ContentResolver 查询 DISPLAY_NAME
            String[] projection = {android.provider.MediaStore.MediaColumns.DISPLAY_NAME};
            android.database.Cursor cursor = context.getContentResolver()
                    .query(uri, projection, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        int col = cursor.getColumnIndex(
                                android.provider.MediaStore.MediaColumns.DISPLAY_NAME);
                        if (col >= 0) {
                            String name = cursor.getString(col);
                            if (name != null && !name.isEmpty()) {
                                return name;
                            }
                        }
                    }
                } finally {
                    cursor.close();
                }
            }
            // fallback：从 URI 最后一段路径提取
            String lastPath = uri.getLastPathSegment();
            if (lastPath != null && !lastPath.isEmpty()) {
                return lastPath;
            }
        } catch (Exception e) {
            AppLog.d(TAG, "Failed to extract file name from URI: " + uriString);
        }
        return null;
    }

    private void loadFromStorage() {
        // 加载播放列表
        String playlistJson = appConfig.getMusicPlaylist();
        if (playlistJson != null && !playlistJson.isEmpty()) {
            try {
                Type listType = new TypeToken<List<MusicItem>>(){}.getType();
                List<MusicItem> savedItems = gson.fromJson(playlistJson, listType);
                if (savedItems != null) {
                    items.addAll(savedItems);
                    // 构建现有路径集合
                    rebuildExistingPaths();
                }
            } catch (Exception e) {
                // 加载失败不影响应用运行，使用空列表即可
                AppLog.e(TAG, "Failed to load playlist", e);
            }
        }

        // 加载上次播放索引，仅在索引有效时恢复
        int lastIndex = appConfig.getLastPlayedIndex();
        if (lastIndex >= 0 && lastIndex < items.size()) {
            currentIndex = lastIndex;
        }

        // 加载循环模式设置，仅在模式有效时恢复
        int savedRepeatMode = appConfig.getRepeatMode();
        if (savedRepeatMode >= REPEAT_MODE_ALL && savedRepeatMode <= REPEAT_MODE_OFF) {
            repeatMode = savedRepeatMode;
        }
    }

    /**
     * 重建 existingPaths 集合（全量重建）
     * 在 loadFromStorage() 和外部批量替换时调用
     */
    private void rebuildExistingPaths() {
        existingPaths.clear();
        for (MusicItem item : items) {
            if (item.filePath != null) {
                existingPaths.add(item.filePath);
            }
        }
    }

    private void debounceSave() {
        debounceHandler.removeCallbacks(saveRunnable);
        debounceHandler.postDelayed(saveRunnable, 1000);
    }

    private void saveToStorage() {
        try {
            String json = gson.toJson(items);
            appConfig.setMusicPlaylist(json);
        } catch (Exception e) {
            // 保存失败不影响当前使用，仅记录日志
            AppLog.e(TAG, "Failed to save playlist", e);
        }
    }
}
