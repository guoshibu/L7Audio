package com.aug32.l7audio.domain.audio.playlist;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.aug32.l7audio.data.local.AppConfig;
import com.aug32.l7audio.domain.audio.MusicItem;
import com.aug32.l7audio.utils.AppExecutors;
import com.aug32.l7audio.utils.AppLog;
import com.aug32.l7audio.utils.AudioMetadataReader;
import com.aug32.l7audio.utils.WavMetadataReader;

/**
 * 播放列表管理器
 *
 * <p>职责：
 * <ul>
 *   <li>管理音乐播放列表（线程安全）</li>
 *   <li>支持音乐的添加、删除、查询等操作</li>
 *   <li>持久化存储播放列表到 SharedPreferences</li>
 *   <li>支持多种音乐来源（通过 MusicSource 接口扩展）</li>
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

    // 播放列表数据
    private List<MusicItem> items;
    // 当前播放索引，-1 表示无当前播放
    private int currentIndex = -1;
    // 循环模式，默认为列表循环
    private int repeatMode = REPEAT_MODE_ALL;
    // 播放列表变化监听器
    private PlaylistChangeListener listener;

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

    // ========== 查询方法（返回副本，线程安全） ==========

    /**
     * 获取所有音乐项
     *
     * <p>返回列表的深拷贝，避免外部修改内部数据，保证线程安全。
     *
     * @return 所有音乐项的副本列表
     */
    public List<MusicItem> getAllItems() {
        synchronized (lock) {
            // 深拷贝每个 MusicItem，保证外部修改不影响内部数据
            List<MusicItem> copy = new ArrayList<>(items.size());
            for (MusicItem item : items) {
                copy.add(item.copy());
            }
            return copy;
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
     * @return 该位置的音乐项副本，索引越界时返回 null
     */
    public MusicItem getItemAt(int index) {
        synchronized (lock) {
            if (index < 0 || index >= items.size()) return null;
            return items.get(index).copy();
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
     * @return 当前播放的音乐项副本，无当前播放时返回 null
     */
    public MusicItem getCurrentItem() {
        synchronized (lock) {
            if (currentIndex < 0 || currentIndex >= items.size()) return null;
            return items.get(currentIndex).copy();
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
     * 从 MusicSource 添加音乐（异步）
     *
     * <p>在计算线程中加载音乐数据，加载完成后通过回调通知结果。
     *
     * @param source   音乐来源，提供音乐数据加载能力
     * @param callback 添加完成回调，可为 null
     */
    public void addFromSource(MusicSource source, AddCallback callback) {
        AppExecutors.getInstance().executeOnComputeThread(() -> {
            source.loadMusic(new MusicSource.LoadCallback() {
                @Override
                public void onSuccess(List<MusicItem> newItems) {
                    // 加载成功且有数据时添加到列表
                    if (newItems != null && !newItems.isEmpty()) {
                        addItemsInternal(newItems, callback);
                    } else if (callback != null) {
                        // 无数据时也回调通知
                        callback.onAddComplete(Collections.emptyList(), 0, 0, 0);
                    }
                }

                @Override
                public void onError(String error) {
                    AppLog.e(TAG, "Failed to load from source: " + source.getName() + ", error: " + error);
                    // 加载失败时回调空结果
                    if (callback != null) {
                        callback.onAddComplete(Collections.emptyList(), 0, 0, 0);
                    }
                }
            });
        });
    }

    /**
     * 从扫描结果添加音乐（异步）
     *
     * <p>将 MediaStore 扫描结果转换为 MusicItem 后添加到播放列表。
     * 在计算线程中处理，避免阻塞主线程。
     *
     * @param scannedList 扫描结果列表
     * @param callback    添加完成回调，可为 null
     */
    public void addFromScannedInfo(final List<ScannedMusicInfo> scannedList, final AddCallback callback) {
        AppExecutors.getInstance().executeOnComputeThread(() -> {
            final List<MusicItem> newItems = new ArrayList<>();
            for (ScannedMusicInfo info : scannedList) {
                // 跳过无效数据
                if (info == null || info.filePath == null) continue;
                File file = new File(info.filePath);
                // 文件不存在或为空时跳过
                if (!file.exists() || file.length() == 0) continue;

                // 优先使用扫描到的元数据，缺失时使用默认值
                String title = (info.title != null && !info.title.isEmpty())
                        ? info.title : file.getName();
                String artist = (info.artist != null && !info.artist.isEmpty())
                        ? info.artist : "未知艺术家";
                String album = (info.album != null && !info.album.isEmpty())
                        ? info.album : "";

                MusicItem item = new MusicItem(info.filePath, info.contentUri, title, artist, info.duration);
                item.album = album;
                item.fileModified = file.lastModified();
                newItems.add(item);
            }
            addItemsInternal(newItems, callback);
        });
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
                    items.remove(pos);
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

            // 播放列表变化时持久化保存
            if (playlistChanged) {
                saveToStorage();
                if (currentRemoved) {
                    appConfig.setLastPlayedIndex(-1);
                }
            }
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
        // 只有一首时直接返回 0，避免死循环
        if (items.size() == 1) return 0;
        int idx = (int) (Math.random() * items.size());
        // 如果随机到当前播放的歌曲，则取下一首，保证切歌时一定切换
        if (idx == currentIndex) {
            idx = (idx + 1) % items.size();
        }
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
            // 预构建现有路径的规范化集合，加速去重比对
            java.util.Set<String> existingPaths = new java.util.HashSet<>();
            for (MusicItem existing : items) {
                existingPaths.add(normalizeFilePath(existing.filePath));
            }
            for (MusicItem item : newItems) {
                // 跳过无效数据
                if (item == null || item.filePath == null || item.filePath.isEmpty()) {
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
            // 持久化保存播放列表
            saveToStorage();
        }

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

            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
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
            String artist = "未知艺术家";
            long duration = 0;
            byte[] albumArt = null;

            try {
                // content:// URI 使用 Context 设置数据源，否则使用文件路径
                // 原因：content:// URI 需要 ContentResolver 访问权限
                if (contentUri != null && contentUri.startsWith("content://")) {
                    retriever.setDataSource(context, Uri.parse(contentUri));
                } else {
                    retriever.setDataSource(filePath);
                }

                // 提取标题，提取失败时使用默认值（文件名）
                String extractedTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                if (extractedTitle != null && !extractedTitle.isEmpty()) {
                    title = extractedTitle;
                }

                // 提取艺术家，提取失败时使用默认值（未知艺术家）
                String extractedArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                if (extractedArtist != null && !extractedArtist.isEmpty()) {
                    artist = extractedArtist;
                }

                // 提取时长，提取失败时使用默认值（0）
                String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (durationStr != null) {
                    duration = Long.parseLong(durationStr);
                }

                // 提取专辑封面图片
                try {
                    albumArt = retriever.getEmbeddedPicture();
                } catch (Exception e) {
                    // 封面提取失败不影响整体流程
                    AppLog.d(TAG, "Failed to extract album art from " + actualPath);
                }
            } catch (Exception e) {
                // 元数据提取失败不影响整体流程，使用默认值即可
                AppLog.d(TAG, "Failed to extract metadata from " + actualPath);
            } finally {
                // 确保释放 MediaMetadataRetriever 资源
                try { retriever.release(); } catch (Exception ignore) {}
            }

            // 二级 fallback：系统 API 拿不到元数据时，尝试自解析二进制文件头
            // 原因：WAV/FLAC/M4A 等格式在部分 Android 版本上 MediaMetadataRetriever 支持不完善
            // 注意：content:// URI 无法直接读取文件，跳过自解析 fallback
            if (!isContentUri && file != null) {
                boolean titleUnknown = AudioMetadataReader.isUnknownTitle(title) || title.equals(file.getName());
                boolean artistUnknown = AudioMetadataReader.isUnknownArtist(artist);
                if (titleUnknown || artistUnknown) {
                    WavMetadataReader.AudioMetadata selfParsed = AudioMetadataReader.readMetadata(filePath);
                    if (selfParsed != null) {
                        if (titleUnknown && selfParsed.title != null && !selfParsed.title.isEmpty()) {
                            title = selfParsed.title;
                        }
                        if (artistUnknown && selfParsed.artist != null && !selfParsed.artist.isEmpty()) {
                            artist = selfParsed.artist;
                        }
                        if (selfParsed.album != null && !selfParsed.album.isEmpty()) {
                            // album 在 MusicItem 中已有字段，此处暂不扩展
                        }
                    }
                }
            }

            MusicItem item = new MusicItem(actualPath, contentUri, title, artist, duration);
            item.albumArt = albumArt;
            if (file != null) {
                item.fileModified = file.lastModified();
            }
            return item;
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to create MusicItem", e);
            return null;
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
