package com.aug32.l7audio.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * 专辑封面统一缓存管理器
 *
 * <p>职责：
 * <ul>
 *   <li>LRU 内存缓存 Bitmap，避免重复解码</li>
 *   <li>文件缓存 byte[]，避免 SP 序列化大字段</li>
 *   <li>采样解码（inSampleSize），匹配目标尺寸减少内存</li>
 *   <li>统一解码入口，避免三处各自 decodeByteArray</li>
 * </ul>
 */
public class AlbumArtCache {

    private static final String TAG = "AlbumArtCache";
    private static final int MAX_MEM_CACHE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final String DISK_CACHE_DIR = "album_art_cache";
    private static final int MAX_DISK_FILES = 200;
    private static volatile AlbumArtCache instance;

    private final android.util.LruCache<String, Bitmap> memCache;
    private final File diskCacheDir;

    private AlbumArtCache(Context context) {
        memCache = new android.util.LruCache<String, Bitmap>(MAX_MEM_CACHE_SIZE) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount();
            }
            @Override
            protected void entryRemoved(boolean evicted, String key, Bitmap oldBitmap, Bitmap newBitmap) {
                if (evicted && oldBitmap != null && !oldBitmap.isRecycled()) {
                    oldBitmap.recycle();
                }
            }
        };
        diskCacheDir = new File(context.getCacheDir(), DISK_CACHE_DIR);
        if (!diskCacheDir.exists()) {
            diskCacheDir.mkdirs();
        }
    }

    public static AlbumArtCache getInstance(Context context) {
        if (instance == null) {
            synchronized (AlbumArtCache.class) {
                if (instance == null) {
                    instance = new AlbumArtCache(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    /**
     * 获取封面 Bitmap（同步，指定目标尺寸，会采样压缩）
     *
     * @param key       唯一键（通常为 filePath）
     * @param albumArt  MusicItem 中的 albumArt byte[]（可为 null，从磁盘回退）
     * @param reqWidth  目标宽度（px），<=0 时不采样
     * @param reqHeight 目标高度（px），<=0 时不采样
     */
    public Bitmap get(String key, byte[] albumArt, int reqWidth, int reqHeight) {
        if (key == null) return null;
        String cacheKey = cacheKey(key);

        Bitmap cached = memCache.get(cacheKey);
        if (cached != null && !cached.isRecycled()) {
            return cached;
        }

        // 硬盘缓存 byte[] 更通用（可被不同采样需求复用）
        byte[] data = (albumArt != null && albumArt.length > 0) ? albumArt : loadFromDisk(cacheKey);
        if (data == null || data.length == 0) return null;

        Bitmap bitmap = decodeSampled(data, reqWidth, reqHeight);
        if (bitmap != null) {
            memCache.put(cacheKey, bitmap);
        }
        return bitmap;
    }

    /**
     * 获取封面 Bitmap（同步，默认最大 512px 采样解码）
     */
    public Bitmap get(String key, byte[] albumArt) {
        return get(key, albumArt, 512, 512);
    }

    /**
     * 保存封面 byte[] 到文件缓存（由 PlaylistManager 添加歌曲时调用）
     */
    public void put(String key, byte[] albumArt) {
        if (key == null || albumArt == null || albumArt.length == 0) return;
        String cacheKey = cacheKey(key);
        saveToDisk(cacheKey, albumArt);
    }

    /**
     * 从缓存中移除
     */
    public void remove(String key) {
        if (key == null) return;
        String cacheKey = cacheKey(key);
        memCache.remove(cacheKey);
        File f = new File(diskCacheDir, cacheKey);
        if (f.exists()) f.delete();
    }

    /**
     * 清除所有缓存
     */
    public void clear() {
        memCache.evictAll();
        File[] files = diskCacheDir.listFiles();
        if (files != null) {
            for (File f : files) f.delete();
        }
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
        return String.valueOf(filePath.hashCode());
    }

    private void saveToDisk(String cacheKey, byte[] data) {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(new File(diskCacheDir, cacheKey));
            fos.write(data);
            // 超过文件数上限时，删除最旧的文件
            File[] files = diskCacheDir.listFiles();
            if (files != null && files.length > MAX_DISK_FILES) {
                File oldest = null;
                for (File f : files) {
                    if (oldest == null || f.lastModified() < oldest.lastModified()) {
                        oldest = f;
                    }
                }
                if (oldest != null) oldest.delete();
            }
        } catch (Exception e) {
            AppLog.d(TAG, "Failed to save album art to disk: " + cacheKey);
        } finally {
            if (fos != null) {
                try { fos.close(); } catch (Exception ignored) {}
            }
        }
    }

    private byte[] loadFromDisk(String cacheKey) {
        FileInputStream fis = null;
        try {
            File f = new File(diskCacheDir, cacheKey);
            if (!f.exists()) return null;
            byte[] data = new byte[(int) f.length()];
            fis = new FileInputStream(f);
            int offset = 0, remaining = data.length;
            while (remaining > 0) {
                int read = fis.read(data, offset, remaining);
                if (read == -1) break;
                offset += read;
                remaining -= read;
            }
            return offset == data.length ? data : null;
        } catch (Exception e) {
            AppLog.d(TAG, "Failed to load album art from disk: " + cacheKey);
            return null;
        } finally {
            if (fis != null) {
                try { fis.close(); } catch (Exception ignored) {}
            }
        }
    }
}
