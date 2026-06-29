package com.aug32.l7audio.utils;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 统一日志工具类
 *
 * DEBUG 构建：全部日志输出到 logcat 和文件
 * RELEASE 构建：所有日志输出被禁用
 *
 * 目标 SDK：Android 11 (API 30)
 */
public class AppLog {
    private static final String TAG = "AppLog";
    private static final String LOG_FILE_NAME = "l7audio_log.txt";
    private static final int MAX_LOG_FILE_SIZE = 1024 * 1024;
    private static boolean isInitialized = false;
    private static File logFile;

    // 通过包内静态方法判断是否为调试模式（默认开启）
    private static volatile boolean debugEnabled = true;

    /** 设置调试模式开关 */
    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
    }

    /** 初始化日志工具 */
    public static void init(Context context) {
        if (!debugEnabled) {
            return;
        }
        if (isInitialized) {
            return;
        }

        try {
            File externalFilesDir = context.getExternalFilesDir(null);
            if (externalFilesDir != null) {
                logFile = new File(externalFilesDir, LOG_FILE_NAME);
                if (!logFile.exists()) {
                    logFile.createNewFile();
                }
            }
            isInitialized = true;
            d(TAG, "AppLog initialized");
        } catch (IOException e) {
            Log.e(TAG, "Failed to initialize AppLog", e);
        }
    }

    public static void d(String tag, String message) {
        if (!debugEnabled) return;
        Log.d(tag, message);
        writeToFile("D", tag, message);
    }

    public static void i(String tag, String message) {
        if (!debugEnabled) return;
        Log.i(tag, message);
        writeToFile("I", tag, message);
    }

    public static void w(String tag, String message) {
        if (!debugEnabled) return;
        Log.w(tag, message);
        writeToFile("W", tag, message);
    }

    public static void e(String tag, String message) {
        if (!debugEnabled) return;
        Log.e(tag, message);
        writeToFile("E", tag, message);
    }

    public static void e(String tag, String message, Throwable throwable) {
        if (!debugEnabled) return;
        Log.e(tag, message, throwable);
        writeToFile("E", tag, message + "\n" + Log.getStackTraceString(throwable));
    }

    /** 将日志写入文件 */
    private static void writeToFile(String level, String tag, String message) {
        if (!isInitialized || logFile == null) {
            return;
        }

        try {
            if (logFile.length() > MAX_LOG_FILE_SIZE) {
                FileWriter writer = new FileWriter(logFile, false);
                writer.write("[LOG RESET] " + getCurrentTime() + "\n");
                writer.close();
            }

            FileWriter writer = new FileWriter(logFile, true);
            String logMessage = "[" + level + "] " + getCurrentTime() + " " + tag + ": " + message + "\n";
            writer.write(logMessage);
            writer.close();
        } catch (IOException e) {
            Log.e(TAG, "Failed to write log to file", e);
        }
    }

    private static String getCurrentTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
        return sdf.format(new Date());
    }

    public static File getLogFile() {
        return debugEnabled ? logFile : null;
    }
}
