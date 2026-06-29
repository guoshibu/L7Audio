package com.aug32.l7audio.domain.audio;

import com.aug32.l7audio.utils.AppLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 轻量级 LRC 歌词解析器
 *
 * 支持：
 * - [mm:ss.xx] / [mm:ss] 标准时间戳
 * - 一行多时间戳
 * - ID 标签忽略
 * - 纯文本 fallback
 *
 * 目标 SDK：Android 11 (API 30)
 */
public final class LrcParser {

    private static final String TAG = "Lyrics";
    private static final Pattern TIMESTAMP_LINE =
            Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]");
    private static final Pattern ID_TAG = Pattern.compile("^\\s*\\[(ti|ar|al|by|offset|length|re|ve|tool)(:.*)?\\]\\s*$");

    /** LRC 解析后单行歌词 */
    public static final class LrcLine {
        public final long timeMs;
        public final String text;

        public LrcLine(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text == null ? "" : text;
        }
    }

    private LrcParser() {}

    /** 解析 LRC 文本 */
    public static List<LrcLine> parse(String lrcText) {
        if (lrcText == null || lrcText.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<LrcLine> result = new ArrayList<>();
        String[] lines = lrcText.split("\\r?\\n");

        boolean hasAnyTimestamp = false;
        List<LrcLine> fallbackPlainText = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String rawLine = lines[i];
            if (rawLine == null) continue;
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            Matcher idMatcher = ID_TAG.matcher(line);
            if (idMatcher.matches()) {
                continue;
            }

            Matcher m = TIMESTAMP_LINE.matcher(line);
            List<Long> timestamps = new ArrayList<>();
            String remaining = line;
            while (m.find()) {
                try {
                    long minutes = Long.parseLong(m.group(1));
                    long seconds = Long.parseLong(m.group(2));
                    String fracStr = m.group(3);
                    long frac = 0;
                    if (fracStr != null && !fracStr.isEmpty()) {
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
                } catch (NumberFormatException ignored) {}
            }

            String textPart = m.replaceAll("").trim();

            if (!timestamps.isEmpty()) {
                for (Long ts : timestamps) {
                    result.add(new LrcLine(ts, textPart));
                }
            } else {
                fallbackPlainText.add(new LrcLine((long) i * 5000L, line));
            }
        }

        if (!hasAnyTimestamp && !fallbackPlainText.isEmpty()) {
            AppLog.d(TAG, "No timestamps found; using plain-text fallback");
            result = fallbackPlainText;
        }

        Collections.sort(result, (a, b) -> Long.compare(a.timeMs, b.timeMs));

        return result;
    }

    /** 查找指定时间对应的歌词行索引 */
    public static int findIndexAt(List<LrcLine> lines, long positionMs) {
        if (lines == null || lines.isEmpty()) return -1;
        int lo = 0, hi = lines.size() - 1, ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            LrcLine midLine = lines.get(mid);
            if (midLine.timeMs <= positionMs) {
                ans = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return ans;
    }

    /** 查找指定时间对应的歌词文本 */
    public static String findLineAt(List<LrcLine> lines, long positionMs) {
        int idx = findIndexAt(lines, positionMs);
        if (idx < 0) return "";
        return lines.get(idx).text;
    }

    /** 解析纯文本为歌词行 */
    public static List<LrcLine> parsePlainTextAsLines(String text) {
        if (text == null || text.trim().isEmpty()) return Collections.emptyList();
        String[] lines = text.split("\\r?\\n");
        List<LrcLine> result = new ArrayList<>(lines.length);
        for (int i = 0; i < lines.length; i++) {
            String s = lines[i];
            if (s != null && !s.trim().isEmpty()) {
                result.add(new LrcLine((long) i * 5000L, s.trim()));
            }
        }
        return result;
    }
}
