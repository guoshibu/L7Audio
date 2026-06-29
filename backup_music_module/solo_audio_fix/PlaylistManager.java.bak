package com.aug32.l7audio.domain.audio.playlist;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import com.aug32.l7audio.data.local.AppConfig;
import com.aug32.l7audio.domain.audio.MusicItem;
import com.aug32.l7audio.utils.AppExecutors;
import com.aug32.l7audio.utils.AppLog;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 播放列表管理器
 *
 * 职责：
 * - 管理音乐播放列表（线程安全）
 * - 支持添加、删除、查询
 * - 持久化存储（SharedPreferences）
 * - 支持多种音乐来源（通过 MusicSource 接口扩展）
 *
 * 线程安全：所有修改操作都加锁，读取返回副本
 */
public class PlaylistManager {

    private static final String TAG = "PlaylistManager";

    public interface AddCallback {
        void onAddComplete(List<MusicItem> addedItems, int startPosition,
                          int skippedExistCount, int skippedFailedCount);
    }

    public interface PlaylistChangeListener {
        void onPlaylistChanged();
        void onCurrentIndexChanged(int newIndex);
    }

    private final Context context;
    private final AppConfig appConfig;
    private final Gson gson = new Gson();
    private final Object lock = new Object();
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    private List<MusicItem> items;
    private int currentIndex = -1;
    private int repeatMode = REPEAT_MODE_ALL;
    private PlaylistChangeListener listener;

    public static final int REPEAT_MODE_ALL = 0;
    public static final int REPEAT_MODE_SHUFFLE = 1;
    public static final int REPEAT_MODE_ONE = 2;
    public static final int REPEAT_MODE_OFF = 3;

    public PlaylistManager(Context context) {
        this.context = context.getApplicationContext();
        this.appConfig = new AppConfig(this.context);
        this.items = new ArrayList<>();
        loadFromStorage();
    }

    public void setPlaylistChangeListener(PlaylistChangeListener listener) {
        this.listener = listener;
    }

    // ========== 查询方法（返回副本，线程安全） ==========

    public List<MusicItem> getAllItems() {
        synchronized (lock) {
            List<MusicItem> copy = new ArrayList<>(items.size());
            for (MusicItem item : items) {
                copy.add(item.copy());
            }
            return copy;
        }
    }

    public int getItemCount() {
        synchronized (lock) {
            return items.size();
        }
    }

    public MusicItem getItemAt(int index) {
        synchronized (lock) {
            if (index < 0 || index >= items.size()) return null;
            return items.get(index).copy();
        }
    }

    public int getCurrentIndex() {
        synchronized (lock) {
            return currentIndex;
        }
    }

    public MusicItem getCurrentItem() {
        synchronized (lock) {
            if (currentIndex < 0 || currentIndex >= items.size()) return null;
            return items.get(currentIndex).copy();
        }
    }

    public int getIndexOf(String filePath) {
        if (filePath == null) return -1;
        synchronized (lock) {
            for (int i = 0; i < items.size(); i++) {
                if (filePath.equals(items.get(i).filePath)) {
                    return i;
                }
            }
            return -1;
        }
    }

    public boolean contains(String filePath) {
        return getIndexOf(filePath) >= 0;
    }

    public int getRepeatMode() {
        synchronized (lock) {
            return repeatMode;
        }
    }

    public boolean isShuffleMode() {
        synchronized (lock) {
            return repeatMode == REPEAT_MODE_SHUFFLE;
        }
    }

    // ========== 修改方法 ==========

    public void setCurrentIndex(int index) {
        boolean changed = false;
        synchronized (lock) {
            if (index >= -1 && index < items.size() && index != currentIndex) {
                currentIndex = index;
                appConfig.setLastPlayedIndex(index);
                changed = true;
            }
        }
        if (changed && listener != null) {
            listener.onCurrentIndexChanged(currentIndex);
        }
    }

    public void setRepeatMode(int mode) {
        synchronized (lock) {
            this.repeatMode = mode;
            appConfig.setRepeatMode(mode);
            appConfig.setShuffleModeEnabled(mode == REPEAT_MODE_SHUFFLE);
        }
    }

    /**
     * 从 MusicSource 添加音乐（异步）
     */
    public void addFromSource(MusicSource source, AddCallback callback) {
        AppExecutors.getInstance().executeOnComputeThread(() -> {
            source.loadMusic(new MusicSource.LoadCallback() {
                @Override
                public void onSuccess(List<MusicItem> newItems) {
                    if (newItems != null && !newItems.isEmpty()) {
                        addItemsInternal(newItems, callback);
                    } else if (callback != null) {
                        callback.onAddComplete(Collections.emptyList(), 0, 0, 0);
                    }
                }

                @Override
                public void onError(String error) {
                    AppLog.e(TAG, "Failed to load from source: " + source.getName() + ", error: " + error);
                    if (callback != null) {
                        callback.onAddComplete(Collections.emptyList(), 0, 0, 0);
                    }
                }
            });
        });
    }

    /**
     * 从扫描结果添加音乐（异步）
     */
    public void addFromScannedInfo(final List<ScannedMusicInfo> scannedList, final AddCallback callback) {
        AppExecutors.getInstance().executeOnComputeThread(() -> {
            final List<MusicItem> newItems = new ArrayList<>();
            for (ScannedMusicInfo info : scannedList) {
                if (info == null || info.filePath == null) continue;
                File file = new File(info.filePath);
                if (!file.exists() || file.length() == 0) continue;

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
     */
    public void removeItems(List<Integer> positions) {
        if (positions == null || positions.isEmpty()) return;

        boolean currentRemoved = false;
        boolean playlistChanged = false;

        synchronized (lock) {
            List<Integer> sorted = new ArrayList<>(positions);
            Collections.sort(sorted, (a, b) -> b - a);

            for (int pos : sorted) {
                if (pos >= 0 && pos < items.size()) {
                    items.remove(pos);
                    playlistChanged = true;
                    if (currentIndex == pos) {
                        currentIndex = -1;
                        currentRemoved = true;
                    } else if (currentIndex > pos) {
                        currentIndex--;
                    }
                }
            }

            if (playlistChanged) {
                saveToStorage();
                if (currentRemoved) {
                    appConfig.setLastPlayedIndex(-1);
                }
            }
        }

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
     * 注意：单曲循环模式下，手动点击下一首也会切换到下一首
     */
    public int getNextIndex() {
        synchronized (lock) {
            if (items.isEmpty()) return -1;
            if (currentIndex < 0) return 0;

            switch (repeatMode) {
                case REPEAT_MODE_SHUFFLE:
                    return getRandomIndex();
                case REPEAT_MODE_ONE:
                case REPEAT_MODE_ALL:
                case REPEAT_MODE_OFF:
                default:
                    return (currentIndex + 1) % items.size();
            }
        }
    }

    /**
     * 获取上一首的索引（手动点击上一首时调用）
     * 注意：单曲循环模式下，手动点击上一首也会切换到上一首
     */
    public int getPreviousIndex() {
        synchronized (lock) {
            if (items.isEmpty()) return -1;
            if (currentIndex < 0) return 0;

            if (repeatMode == REPEAT_MODE_SHUFFLE) {
                return getRandomIndex();
            }
            return (currentIndex - 1 + items.size()) % items.size();
        }
    }

    /**
     * 歌曲结束时，决定下一首（根据循环模式）
     * 返回 -1 表示应该停止
     */
    public int getNextIndexOnSongEnd() {
        synchronized (lock) {
            if (items.isEmpty()) return -1;

            switch (repeatMode) {
                case REPEAT_MODE_ONE:
                    return currentIndex;
                case REPEAT_MODE_ALL:
                case REPEAT_MODE_SHUFFLE:
                    return getNextIndex();
                case REPEAT_MODE_OFF:
                default:
                    // 顺序播放，最后一首结束后停止
                    int next = (currentIndex + 1) % items.size();
                    if (next == 0 && currentIndex == items.size() - 1) {
                        return -1;
                    }
                    return next;
            }
        }
    }

    private int getRandomIndex() {
        if (items.size() == 1) return 0;
        int idx = (int) (Math.random() * items.size());
        if (idx == currentIndex) {
            idx = (idx + 1) % items.size();
        }
        return idx;
    }

    // ========== 内部方法 ==========

    private void addItemsInternal(List<MusicItem> newItems, AddCallback callback) {
        final List<MusicItem> added = new ArrayList<>();
        final int skippedExist[] = {0};
        final int skippedFailed[] = {0};
        final int startPos[] = {0};

        synchronized (lock) {
            startPos[0] = items.size();
            for (MusicItem item : newItems) {
                if (item == null || item.filePath == null || item.filePath.isEmpty()) {
                    skippedFailed[0]++;
                    continue;
                }
                boolean exists = false;
                for (MusicItem existing : items) {
                    if (item.filePath.equals(existing.filePath)) {
                        exists = true;
                        break;
                    }
                }
                if (exists) {
                    skippedExist[0]++;
                    continue;
                }
                items.add(item);
                added.add(item.copy());
            }
            saveToStorage();
        }

        if (listener != null && !added.isEmpty()) {
            mainHandler.post(() -> listener.onPlaylistChanged());
        }

        if (callback != null) {
            mainHandler.post(() ->
                    callback.onAddComplete(added, startPos[0], skippedExist[0], skippedFailed[0]));
        }
    }

    private MusicItem createItemFromFile(String filePath, String contentUri) {
        try {
            File file = new File(filePath);
            if (!file.exists() || file.length() == 0) {
                return null;
            }

            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            String title = file.getName();
            String artist = "未知艺术家";
            long duration = 0;

            try {
                if (contentUri != null && contentUri.startsWith("content://")) {
                    retriever.setDataSource(context, Uri.parse(contentUri));
                } else {
                    retriever.setDataSource(filePath);
                }

                String extractedTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                if (extractedTitle != null && !extractedTitle.isEmpty()) {
                    title = extractedTitle;
                }

                String extractedArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                if (extractedArtist != null && !extractedArtist.isEmpty()) {
                    artist = extractedArtist;
                }

                String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (durationStr != null) {
                    duration = Long.parseLong(durationStr);
                }
            } catch (Exception e) {
                AppLog.d(TAG, "Failed to extract metadata from " + filePath);
            } finally {
                try { retriever.release(); } catch (Exception ignore) {}
            }

            MusicItem item = new MusicItem(filePath, contentUri, title, artist, duration);
            item.fileModified = file.lastModified();
            return item;
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to create MusicItem", e);
            return null;
        }
    }

    private void loadFromStorage() {
        String playlistJson = appConfig.getMusicPlaylist();
        if (playlistJson != null && !playlistJson.isEmpty()) {
            try {
                Type listType = new TypeToken<List<MusicItem>>(){}.getType();
                List<MusicItem> savedItems = gson.fromJson(playlistJson, listType);
                if (savedItems != null) {
                    items.addAll(savedItems);
                }
            } catch (Exception e) {
                AppLog.e(TAG, "Failed to load playlist", e);
            }
        }

        int lastIndex = appConfig.getLastPlayedIndex();
        if (lastIndex >= 0 && lastIndex < items.size()) {
            currentIndex = lastIndex;
        }

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
            AppLog.e(TAG, "Failed to save playlist", e);
        }
    }
}
