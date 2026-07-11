package com.aug32.l7audio.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/**
 * 专辑封面统一缓存管理器
 *
 * <p>职责：
 * <ul>
 *   <li>LRU 内存缓存 Bitmap，避免重复解码</li>
 *   <li>put() 预热 LruCache，确保后续 get() 命中避免主线程 decode</li>
 *   <li>采样解码（inSampleSize），匹配目标尺寸减少内存</li>
 *   <li>统一解码入口，避免三处各自 decodeByteArray</li>
 * </ul>
 *
 * <p>设计决定：不下磁盘，仅靠 LruCache + MusicItem.albumArt byte[]（transient）。
 * 避免磁盘竞态、字节上限管理、写磨损；跨 Session 由 ensureAlbumArt() 异步重提取。
 *
 * <p>线程模型：
 * <ul>
 *   <li>put()：仅在计算线程（ensureAlbumArt）调用，做 decode+存入，已同步</li>
 *   <li>get()：播放/通知/MediaSession/Fragment 主线程调用，纯读无锁</li>
 * </ul>
 * 保证同一 key 只有一个线程 decode+put，消除 sizeOf 不一致 Crash。
 */
public class AlbumArtCache {

    private static final String TAG = "AlbumArtCache";
    private static final int MAX_MEM_CACHE_SIZE = 10 * 1024 * 1024; // 10MB
    private static volatile AlbumArtCache instance;

    /**
     * 缓存条目：包装 Bitmap 并固化 size，确保 sizeOf() 恒定。
     * 仅缓存持有 Bitmap 引用，外部不得 recycle。
     */
    private static final class Entry {
        final Bitmap bitmap;
        final int size; // 存入时一次性计算，永不变

        Entry(Bitmap b) {
            this.bitmap = b;
            this.size = b.getByteCount();
        }
    }

    private final android.util.LruCache<String, Entry> memCache;

    private AlbumArtCache() {
        memCache = new android.util.LruCache<String, Entry>(MAX_MEM_CACHE_SIZE) {
            @Override
            protected int sizeOf(String key, Entry entry) {
                return entry.size; // 固定值，绝不动态计算
            }
            @Override
            protected void entryRemoved(boolean evicted, String key, Entry oldEntry, Entry newEntry) {
                // 仅缓存真正淘汰时回收，外部绝不持有 Entry.bitmap 引用
                if (evicted && oldEntry != null && !oldEntry.bitmap.isRecycled()) {
                    oldEntry.bitmap.recycle();
                }
            }
        };
    }

    public static AlbumArtCache getInstance() {
        if (instance == null) {
            synchronized (AlbumArtCache.class) {
                if (instance == null) {
                    instance = new AlbumArtCache();
                }
            }
        }
        return instance;
    }

    /**
     * 同步锁：仅保护 decode+put 临界区，避免并发重复 put 同一 key
     */
    private final Object decodeLock = new Object();

    /**
     * 获取预热缓存的 512px Bitmap（纯读，无锁）
     *
     * @param key 唯一键（通常为 filePath）
     * @return 缓存的 Bitmap，未命中返回 null
     */
    public Bitmap get(String key) {
        if (key == null) return null;
        String cacheKey = cacheKey(key);
        Entry entry = memCache.get(cacheKey);
        return (entry != null && !entry.bitmap.isRecycled()) ? entry.bitmap : null;
    }

    /**
     * 一次性解码指定尺寸（不入缓存，供 Fragment 等非标准尺寸使用）
     */
    public Bitmap decodeForSize(byte[] albumArt, int reqWidth, int reqHeight) {
        if (albumArt == null || albumArt.length == 0) return null;
        return decodeSampled(albumArt, reqWidth, reqHeight);
    }

    /**
     * 预热内存缓存（由 ensureAlbumArt() 提取封面后调用）
     *
     * <p>将 byte[] decode 为 512px Bitmap 后存入 LruCache，确保后续
     * updateNotification()/MediaSession 等处 get() 能直接命中，避免主线程 decode。
     *
     * @param key      唯一键（文件路径）
     * @param albumArt 专辑封面字节数组
     */
    public void put(String key, byte[] albumArt) {
        if (key == null || albumArt == null || albumArt.length == 0) return;
        String cacheKey = cacheKey(key);

        synchronized (decodeLock) {
            // 双重检查：另一线程可能刚 put 完
            Entry existing = memCache.get(cacheKey);
            if (existing != null && !existing.bitmap.isRecycled()) {
                return;
            }
            Bitmap bitmap = decodeSampled(albumArt, 512, 512);
            if (bitmap != null) {
                memCache.put(cacheKey, new Entry(bitmap));
            }
        }
    }

    /**
     * 从内存缓存中移除
     */
    public void remove(String key) {
        if (key == null) return;
        String cacheKey = cacheKey(key);
        memCache.remove(cacheKey);
    }

    /**
     * 清除所有内存缓存
     */
    public void clear() {
        memCache.evictAll();
    }

    // ========== 内部方法 ==========

    private Bitmap decodeSampled(byte[] data, int reqWidth, int reqHeight) {
        if (reqWidth <= 0 || reqHeight <= 0) {
            return BitmapFactory.decodeByteArray(data, 0, data.length);
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, opts);
        opts.inSampleSize = calculateSampleSize(opts.outWidth, opts.outHeight, reqWidth, reqHeight);
        opts.inJustDecodeBounds = false;
        return BitmapFactory.decodeByteArray(data, 0, data.length, opts);
    }

    private int calculateSampleSize(int outWidth, int outHeight, int reqWidth, int reqHeight) {
        int sampleSize = 1;
        if (outWidth > reqWidth || outHeight > reqHeight) {
            int halfW = outWidth / 2;
            int halfH = outHeight / 2;
            while (halfW / sampleSize >= reqWidth && halfH / sampleSize >= reqHeight) {
                sampleSize *= 2;
            }
        }
        return sampleSize;
    }

    private String cacheKey(String filePath) {
        return filePath;
    }
}