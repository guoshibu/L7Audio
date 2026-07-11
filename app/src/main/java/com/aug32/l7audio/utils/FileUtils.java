package com.aug32.l7audio.utils;

import java.util.Locale;

/**
 * 文件操作工具类
 *
 * 目标 SDK：Android 11 (API 30)
 * 最低 SDK：Android 11 (API 30)
 */
public final class FileUtils {

    /** 支持的音频格式扩展名 */
    public static final String[] AUDIO_EXTENSIONS = {
            ".mp3", ".wav", ".flac", ".ogg", ".m4a", ".aac", ".wma", ".amr"
    };

    private FileUtils() {
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
}
