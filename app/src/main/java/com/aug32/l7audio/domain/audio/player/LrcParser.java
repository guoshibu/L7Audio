package com.aug32.l7audio.domain.audio.player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.aug32.l7audio.utils.AppLog;

/**
 * 轻量级 LRC 歌词解析器
 *
 * 支持：
 * - [mm:ss.xx] / [mm:ss] 标准时间戳格式
 * - 一行多时间戳（同一行歌词对应多个时间点）
 * - ID 标签忽略（ti、ar、al等元数据标签）
 * - 纯文本 fallback（无时间戳时按行分配默认时间）
 *
 * 目标 SDK：Android 11 (API 30)
 *
 * 设计特点：
 * - 工具类，不可实例化
 * - 解析结果按时间升序排列
 * - 容错性强，格式错误时优雅降级
 */
public final class LrcParser {

    /** 日志标签 */
    private static final String TAG = "Lyrics";
    /**
     * 时间戳行正则表达式
     *
     * 匹配格式：[mm:ss] 或 [mm:ss.xx] 或 [mm:ss.xxx]
     * 分组1：分钟（1-3位）
     * 分组2：秒（1-2位）
     * 分组3：毫秒部分（可选，1-3位）
     */
    private static final Pattern TIMESTAMP_LINE =
            Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]");
    /**
     * ID标签正则表达式
     *
     * 匹配LRC文件中的元数据标签，如[ti:标题]、[ar:艺术家]等，
     * 这些标签不包含歌词内容，解析时直接跳过。
     */
    private static final Pattern ID_TAG = Pattern.compile("^\\s*\\[(ti|ar|al|by|offset|length|re|ve|tool)(:.*)?\\]\\s*$");

    /**
     * LRC 解析后单行歌词
     *
     * 不可变数据类，包含歌词的时间戳和文本内容。
     */
    public static final class LrcLine {
        /** 歌词显示的时间点，单位毫秒 */
        public final long timeMs;
        /** 歌词文本内容 */
        public final String text;

        /**
         * 构造函数，创建单行歌词
         *
         * @param timeMs 歌词显示的时间点，单位毫秒
         * @param text   歌词文本内容，为null时自动转为空字符串
         */
        public LrcLine(long timeMs, String text) {
            this.timeMs = timeMs;
            // text为null时转为空字符串，避免后续调用出现空指针异常
            this.text = text == null ? "" : text;
        }
    }

    /**
     * 私有构造函数，防止工具类被实例化
     */
    private LrcParser() {}

    /**
     * 解析 LRC 文本为歌词行列表
     *
     * 解析流程：
     * 1. 按行分割文本
     * 2. 跳过空行和ID标签行
     * 3. 提取每行的所有时间戳和歌词文本
     * 4. 同一行的多个时间戳分别生成独立的LrcLine
     * 5. 无时间戳时使用纯文本回退模式
     * 6. 最终结果按时间升序排序
     *
     * @param lrcText LRC格式的歌词文本
     * @return 按时间升序排列的歌词行列表，输入为空时返回空列表
     */
    public static List<LrcLine> parse(String lrcText) {
        if (lrcText == null || lrcText.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<LrcLine> result = new ArrayList<>();
        String[] lines = lrcText.split("\\r?\\n");

        // 标记是否找到有效时间戳，用于判断是否需要使用纯文本回退
        boolean hasAnyTimestamp = false;
        // 纯文本回退列表：无时间戳时按行号分配默认时间
        List<LrcLine> fallbackPlainText = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String rawLine = lines[i];
            if (rawLine == null) continue;
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            // 检查是否为ID标签行，是则跳过（如[ti:标题]、[ar:艺术家]等）
            Matcher idMatcher = ID_TAG.matcher(line);
            if (idMatcher.matches()) {
                continue;
            }

            Matcher m = TIMESTAMP_LINE.matcher(line);
            List<Long> timestamps = new ArrayList<>();
            String remaining = line;
            // 循环提取一行中的所有时间戳（支持一行多时间戳格式）
            while (m.find()) {
                try {
                    long minutes = Long.parseLong(m.group(1));
                    long seconds = Long.parseLong(m.group(2));
                    String fracStr = m.group(3);
                    long frac = 0;
                    if (fracStr != null && !fracStr.isEmpty()) {
                        // 根据毫秒部分的位数统一转换为毫秒（3位直接用，2位乘10，1位乘100）
                        if (fracStr.length() >= 3) {
                            frac = Long.parseLong(fracStr.substring(0, 3));
                        } else if (fracStr.length() == 2) {
                            frac = Long.parseLong(fracStr) * 10;
                        } else {
                            frac = Long.parseLong(fracStr) * 100;
                        }
                    }
                    long ms = minutes * 60_000L + seconds * 1000L + frac;
                    timestamps.add(ms);
                    hasAnyTimestamp = true;
                } catch (NumberFormatException ignored) {
                    // 数字解析失败时忽略该时间戳，继续处理下一个
                }
            }

            // 移除所有时间戳后，剩余部分即为歌词文本
            String textPart = m.replaceAll("").trim();

            if (!timestamps.isEmpty()) {
                // 每个时间戳对应一行歌词（支持一行多时间戳的情况）
                for (Long ts : timestamps) {
                    result.add(new LrcLine(ts, textPart));
                }
            } else {
                // 无时间戳时，按行号乘以5秒作为默认时间，存入回退列表
                fallbackPlainText.add(new LrcLine((long) i * 5000L, line));
            }
        }

        // 全文未找到任何时间戳时，使用纯文本回退模式
        if (!hasAnyTimestamp && !fallbackPlainText.isEmpty()) {
            AppLog.d(TAG, "No timestamps found; using plain-text fallback");
            result = fallbackPlainText;
        }

        // 按时间升序排序，确保歌词按正确顺序显示
        Collections.sort(result, (a, b) -> Long.compare(a.timeMs, b.timeMs));

        return result;
    }

    /**
     * 查找指定播放时间对应的歌词行索引
     *
     * 使用二分查找算法，找到最后一个时间戳小于等于positionMs的歌词行。
     * 时间复杂度：O(log n)
     *
     * @param lines      歌词行列表，必须按时间升序排列
     * @param positionMs 当前播放位置，单位毫秒
     * @return 对应的歌词行索引，未找到时返回-1
     */
    public static int findIndexAt(List<LrcLine> lines, long positionMs) {
        if (lines == null || lines.isEmpty()) return -1;
        int lo = 0, hi = lines.size() - 1, ans = -1;
        // 二分查找：找到最大的timeMs <= positionMs的行索引
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            LrcLine midLine = lines.get(mid);
            if (midLine.timeMs <= positionMs) {
                // 当前行可能是目标，但可能还有更晚的行也满足条件，继续向右查找
                ans = mid;
                lo = mid + 1;
            } else {
                // 当前行时间太晚，向左查找
                hi = mid - 1;
            }
        }
        return ans;
    }

    /**
     * 查找指定播放时间对应的歌词文本
     *
     * @param lines      歌词行列表，必须按时间升序排列
     * @param positionMs 当前播放位置，单位毫秒
     * @return 对应的歌词文本，未找到时返回空字符串
     */
    public static String findLineAt(List<LrcLine> lines, long positionMs) {
        int idx = findIndexAt(lines, positionMs);
        if (idx < 0) return "";
        return lines.get(idx).text;
    }

}
