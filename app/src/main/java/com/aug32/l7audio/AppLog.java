package com.aug32.l7audio;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AppLog {
    private static final String TAG = "AppLog";
    private static final String LOG_FILE_NAME = "l7audio_log.txt";
    private static final int MAX_LOG_FILE_SIZE = 1024 * 1024; // 1MB
    private static boolean isInitialized = false;
    private static File logFile;

    public static void init(Context context) {
        if (isInitialized) {
            return;
        }

        try {
            // 使用应用的外部存储目录存储日志
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
        Log.d(tag, message);
        writeToFile("D", tag, message);
    }

    public static void i(String tag, String message) {
        Log.i(tag, message);
        writeToFile("I", tag, message);
    }

    public static void w(String tag, String message) {
        Log.w(tag, message);
        writeToFile("W", tag, message);
    }

    public static void e(String tag, String message) {
        Log.e(tag, message);
        writeToFile("E", tag, message);
    }

    public static void e(String tag, String message, Throwable throwable) {
        Log.e(tag, message, throwable);
        writeToFile("E", tag, message + "\n" + Log.getStackTraceString(throwable));
    }

    private static void writeToFile(String level, String tag, String message) {
        if (!isInitialized || logFile == null) {
            return;
        }

        try {
            // 检查文件大小，如果超过限制则清空
            if (logFile.length() > MAX_LOG_FILE_SIZE) {
                FileWriter writer = new FileWriter(logFile, false);
                writer.write("[LOG RESET] " + getCurrentTime() + "\n");
                writer.close();
            }

            // 追加日志
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
        return logFile;
    }
}