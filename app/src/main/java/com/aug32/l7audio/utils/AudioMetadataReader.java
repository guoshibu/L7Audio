package com.aug32.l7audio.utils;

import java.io.File;
import java.util.Locale;

/**
 * 音频文件元数据统一读取入口
 *
 * <p>职责：根据音频文件格式，自动选择对应的元数据解析器读取标题、艺术家、专辑等信息。
 *
 * <p>设计背景：Android MediaStore 和 MediaMetadataRetriever 对部分音频格式
 * （尤其是 WAV、FLAC、M4A）的元数据支持不完善，经常返回 &lt;unknown&gt; 或空值。
 * 本类提供三级 fallback 机制，确保最大程度读取到歌曲信息。
 *
 * <p>在扫码流程中，各格式按顺序尝试：
 * <ol>
 *   <li><b>自解析优先</b> - WAV/FLAC/M4A 直接走二进制解析器读取元数据</li>
 *   <li><b>系统 API</b> - MP3/其他格式走 MediaMetadataRetriever</li>
 *   <li><b>文件名兜底</b> - 以上都失败时，文件名去扩展名作为标题，艺术家留空</li>
 * </ol>
 *
 * <p>支持的自解析格式：
 * <ul>
 *   <li>WAV - RIFF INFO 块</li>
 *   <li>FLAC - Vorbis Comment</li>
 *   <li>M4A / AAC - MP4 ilst (iTunes 元数据)</li>
 * </ul>
 *
 * <p>文件名解析规则：只去扩展名，不做智能猜测（不从"艺术家 - 标题"格式中拆解）。
 */
public final class AudioMetadataReader {

    private AudioMetadataReader() {
        // 私有构造函数，防止实例化
    }

    /**
     * 从音频文件读取元数据
     *
     * <p>根据文件扩展名自动选择解析器。对于 WAV/FLAC/M4A 始终返回结果（可能全空），
     * 其他格式的自解析暂不支持时返回 null。
     *
     * @param filePath 音频文件绝对路径
     * @return 元数据结果，无法解析或无对应器时返回 null
     */
    public static WavMetadataReader.AudioMetadata readMetadata(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return null;
        }

        File file = new File(filePath);
        if (!file.exists() || !file.isFile() || file.length() == 0) {
            return null;
        }

        String ext = FileUtils.getExtension(filePath).toLowerCase(Locale.ROOT);

        switch (ext) {
            case ".wav":
                return WavMetadataReader.readMetadata(filePath);
            case ".flac":
                return FlacMetadataReader.readMetadata(filePath);
            case ".m4a":
            case ".aac":
            case ".mp4":
                return M4aMetadataReader.readMetadata(filePath);
            default:
                // 其他格式（MP3、OGG、WMA、AMR 等）暂不自解析
                // MP3 由系统 API 通常能正常读取，OGG/WMA 用的少
                return null;
        }
    }

    /**
     * 判断给定艺术家字符串是否为"未知"或空值
     *
     * <p>用于判断系统 API 返回的 artist 是否需要走自解析 fallback。
     * 常见的未知标识包括：空字符串、null、"&lt;unknown&gt;"、"未知艺术家"等。
     *
     * @param artist 待检测的艺术家字符串
     * @return true=未知或空，需要 fallback
     */
    public static boolean isUnknownArtist(String artist) {
        if (artist == null || artist.isEmpty()) {
            return true;
        }
        String lower = artist.toLowerCase(Locale.ROOT).trim();
        return lower.equals("<unknown>")
                || lower.equals("unknown")
                || lower.equals("未知艺术家")
                || lower.equals("未知");
    }

    /**
     * 判断给定标题字符串是否为"未知"或空值
     *
     * @param title 待检测的标题字符串
     * @return true=未知或空，需要 fallback
     */
    public static boolean isUnknownTitle(String title) {
        if (title == null || title.isEmpty()) {
            return true;
        }
        String lower = title.toLowerCase(Locale.ROOT).trim();
        return lower.equals("<unknown>")
                || lower.equals("unknown");
    }
}
