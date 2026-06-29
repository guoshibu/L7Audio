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
 * 统一日志工具类。
 *
 * <p>主要职责：
 * <ul>
 *   <li>日志分级输出：支持 DEBUG、INFO、WARN、ERROR 四个日志级别</li>
 *   <li>双渠道输出：同时输出到 Logcat 和日志文件</li>
 *   <li>调试开关：支持开启/关闭全部日志输出</li>
 *   <li>文件管理：自动限制日志文件大小，超出时重置文件</li>
 * </ul>
 *
 * <p>设计意图：
 * 统一封装 Android 原生 Log 类，提供全局开关控制日志输出，
 * 在 Release 构建中可彻底关闭日志，避免敏感信息泄露和性能损耗。
 * 日志同时写入文件，便于离线排查问题。
 *
 * <p>目标 SDK：Android 11 (API 30)
 */
public class AppLog {
    /** 日志标签 */
    private static final String TAG = "AppLog";
    /** 日志文件名 */
    private static final String LOG_FILE_NAME = "l7audio_log.txt";
    /** 日志文件最大大小（1MB），超出后重置 */
    private static final int MAX_LOG_FILE_SIZE = 1024 * 1024;
    /** 是否已初始化标志 */
    private static boolean isInitialized = false;
    /** 日志文件对象 */
    private static File logFile;

    /** 调试模式开关，默认为 true，Release 版本应设为 false */
    private static volatile boolean debugEnabled = true;

    /**
     * 设置调试模式开关。
     *
     * <p>关闭后所有日志输出将被禁用，包括 Logcat 输出和文件写入。
     * Release 版本应调用此方法关闭日志，避免性能损耗和信息泄露。
     *
     * @param enabled true=开启日志，false=关闭日志
     */
    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
    }

    /**
     * 初始化日志工具。
     *
     * <p>在应用启动时调用，创建日志文件。
     * 仅在调试模式开启时执行初始化，重复调用会被忽略。
     * 日志文件存储在应用外部私有目录中，无需存储权限。
     *
     * @param context 应用上下文
     */
    public static void init(Context context) {
        // 非调试模式下不初始化，减少资源消耗
        if (!debugEnabled) {
            return;
        }
        // 已初始化则跳过，避免重复创建文件
        if (isInitialized) {
            return;
        }

        try {
            // 使用外部私有目录，无需存储权限，且卸载应用时会自动清除
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

    /**
     * 输出 DEBUG 级别日志。
     *
     * @param tag     日志标签
     * @param message 日志内容
     */
    public static void d(String tag, String message) {
        if (!debugEnabled) return;
        Log.d(tag, message);
        writeToFile("D", tag, message);
    }

    /**
     * 输出 INFO 级别日志。
     *
     * @param tag     日志标签
     * @param message 日志内容
     */
    public static void i(String tag, String message) {
        if (!debugEnabled) return;
        Log.i(tag, message);
        writeToFile("I", tag, message);
    }

    /**
     * 输出 WARN 级别日志。
     *
     * @param tag     日志标签
     * @param message 日志内容
     */
    public static void w(String tag, String message) {
        if (!debugEnabled) return;
        Log.w(tag, message);
        writeToFile("W", tag, message);
    }

    /**
     * 输出 ERROR 级别日志。
     *
     * @param tag     日志标签
     * @param message 日志内容
     */
    public static void e(String tag, String message) {
        if (!debugEnabled) return;
        Log.e(tag, message);
        writeToFile("E", tag, message);
    }

    /**
     * 输出 ERROR 级别日志（带异常堆栈）。
     *
     * @param tag       日志标签
     * @param message   日志内容
     * @param throwable 异常对象，将输出其堆栈信息
     */
    public static void e(String tag, String message, Throwable throwable) {
        if (!debugEnabled) return;
        Log.e(tag, message, throwable);
        writeToFile("E", tag, message + "\n" + Log.getStackTraceString(throwable));
    }

    /**
     * 将日志写入文件。
     *
     * <p>当日志文件大小超过 MAX_LOG_FILE_SIZE 时，清空文件重新写入，
     * 防止日志文件无限增长占用过多存储空间。
     *
     * @param level   日志级别
     * @param tag     日志标签
     * @param message 日志内容
     */
    private static void writeToFile(String level, String tag, String message) {
        if (!isInitialized || logFile == null) {
            return;
        }

        try {
            // 文件大小超过限制时重置，避免占用过多存储空间
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

    /**
     * 获取当前时间字符串。
     *
     * <p>格式为 "yyyy-MM-dd HH:mm:ss.SSS"，精确到毫秒，便于定位问题发生时间。
     *
     * @return 格式化的时间字符串
     */
    private static String getCurrentTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
        return sdf.format(new Date());
    }

    /**
     * 获取日志文件对象。
     *
     * <p>仅在调试模式开启时返回有效文件对象，非调试模式返回 null。
     *
     * @return 日志文件对象，调试模式关闭时返回 null
     */
    public static File getLogFile() {
        return debugEnabled ? logFile : null;
    }
}
