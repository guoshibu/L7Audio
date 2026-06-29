package com.aug32.l7audio.utils;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.webkit.MimeTypeMap;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

import com.aug32.l7audio.utils.AppLog;

/**
 * 文件操作工具类
 *
 * 提供：
 * - 文件类型判断
 * - 文件路径处理
 * - 文件复制
 * - 存储权限检查
 *
 * 目标 SDK：Android 11 (API 30)
 * 最低 SDK：Android 11 (API 30)
 */
public final class FileUtils {

    private static final String TAG = "FileUtils";

    // 音频文件扩展名
    private static final String[] AUDIO_EXTENSIONS = {
            ".mp3", ".wav", ".flac", ".ogg", ".m4a", ".aac", ".wma", ".amr"
    };

    // 歌词文件扩展名
    private static final String LRC_EXTENSION = ".lrc";

    private FileUtils() {
        // 私有构造函数，防止实例化
    }

    /**
     * 判断是否为音频文件
     *
     * @param file 文件
     * @return true=是音频文件
     */
    public static boolean isAudioFile(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        String name = file.getName().toLowerCase(Locale.ROOT);
        for (String ext : AUDIO_EXTENSIONS) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否为音频文件（通过路径）
     *
     * @param path 文件路径
     * @return true=是音频文件
     */
    public static boolean isAudioFile(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        return isAudioFile(new File(path));
    }

    /**
     * 判断是否为歌词文件
     *
     * @param file 文件
     * @return true=是歌词文件
     */
    public static boolean isLrcFile(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(LRC_EXTENSION);
    }

    /**
     * 获取不带扩展名的文件名
     *
     * @param file 文件
     * @return 不带扩展名的文件名
     */
    public static String getNameWithoutExtension(File file) {
        if (file == null) {
            return "";
        }
        return getNameWithoutExtension(file.getName());
    }

    /**
     * 获取不带扩展名的文件名
     *
     * @param fileName 文件名
     * @return 不带扩展名的文件名
     */
    public static String getNameWithoutExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            return fileName.substring(0, dotIndex);
        }
        return fileName;
    }

    /**
     * 获取文件扩展名（包含点号）
     *
     * @param file 文件
     * @return 扩展名，如 ".mp3"，或 ""
     */
    public static String getExtension(File file) {
        if (file == null) {
            return "";
        }
        return getExtension(file.getName());
    }

    /**
     * 获取文件扩展名（包含点号）
     *
     * @param fileName 文件名
     * @return 扩展名，如 ".mp3"，或 ""
     */
    public static String getExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex).toLowerCase(Locale.ROOT);
        }
        return "";
    }

    /**
     * 获取文件的 MIME 类型
     *
     * @param file 文件
     * @return MIME 类型，如 "audio/mpeg"，或 null
     */
    public static String getMimeType(File file) {
        if (file == null) {
            return null;
        }
        String extension = getExtension(file);
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.substring(1));
    }

    /**
     * 规范文件路径（解析符号链接等）
     *
     * @param file 文件
     * @return 规范化的绝对路径
     */
    public static String getCanonicalPath(File file) {
        if (file == null) {
            return "";
        }
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            return file.getAbsolutePath();
        }
    }

    /**
     * 规范文件路径（解析符号链接等）
     *
     * @param path 文件路径
     * @return 规范化的绝对路径
     */
    public static String getCanonicalPath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        return getCanonicalPath(new File(path));
    }

    /**
     * 获取与音频文件同名的歌词文件路径
     *
     * @param audioFilePath 音频文件路径
     * @return 歌词文件路径，或 null（如果不存在）
     */
    public static String getLrcFilePath(String audioFilePath) {
        if (audioFilePath == null || audioFilePath.isEmpty()) {
            return null;
        }
        File audioFile = new File(audioFilePath);
        File parentDir = audioFile.getParentFile();
        if (parentDir == null) {
            return null;
        }
        String lrcFileName = getNameWithoutExtension(audioFile) + LRC_EXTENSION;
        File lrcFile = new File(parentDir, lrcFileName);
        if (lrcFile.exists() && lrcFile.isFile()) {
            return lrcFile.getAbsolutePath();
        }
        return null;
    }

    /**
     * 读取文本文件内容
     *
     * @param filePath 文件路径
     * @return 文件内容字符串，失败返回空字符串
     */
    public static String readTextFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return "";
        }
        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(line);
            }
        } catch (IOException e) {
            AppLog.e(TAG, "Failed to read text file: " + filePath, e);
            return "";
        }
        return sb.toString();
    }

    /**
     * 复制文件
     *
     * @param source 源文件
     * @param dest   目标文件
     * @return true=复制成功
     */
    public static boolean copyFile(File source, File dest) {
        if (source == null || dest == null || !source.isFile()) {
            return false;
        }

        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
            return true;
        } catch (IOException e) {
            AppLog.e(TAG, "Failed to copy file: " + source + " -> " + dest, e);
            return false;
        }
    }

    /**
     * 获取应用外部存储目录
     *
     * @param context Context
     * @return 外部存储目录，或 null
     */
    public static File getExternalFilesDir(Context context) {
        if (context == null) {
            return null;
        }
        return context.getExternalFilesDir(null);
    }

    /**
     * 判断外部存储是否可用
     *
     * @return true=可用
     */
    public static boolean isExternalStorageAvailable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    /**
     * 判断是否为外部存储路径
     *
     * @param path 文件路径
     * @return true=是外部存储路径
     */
    public static boolean isExternalStoragePath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        return path.startsWith("/storage/") ||
                path.startsWith("/sdcard/") ||
                path.startsWith("/mnt/");
    }

    /**
     * 安全删除文件
     *
     * @param file 文件
     * @return true=删除成功或文件不存在
     */
    public static boolean deleteFileSafely(File file) {
        if (file == null) {
            return true;
        }
        if (!file.exists()) {
            return true;
        }
        try {
            return file.delete();
        } catch (SecurityException e) {
            AppLog.e(TAG, "Failed to delete file: " + file, e);
            return false;
        }
    }

    /**
     * 格式化文件大小
     *
     * @param bytes 字节数
     * @return 格式化后的大小字符串，如 "1.5 MB"
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format(Locale.getDefault(), "%.1f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    /**
     * 从 Uri 获取文件名
     *
     * @param context Context
     * @param uri     Uri
     * @return 文件名
     */
    public static String getFileNameFromUri(Context context, Uri uri) {
        if (context == null || uri == null) {
            return null;
        }

        String fileName = null;
        if ("content".equals(uri.getScheme())) {
            android.database.Cursor cursor = null;
            try {
                cursor = context.getContentResolver().query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex);
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        if (fileName == null) {
            fileName = uri.getLastPathSegment();
        }
        return fileName;
    }
}
