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
 * <p>三级 fallback 策略：
 * <ol>
 *   <li><b>系统 API 优先</b> - 调用方先尝试 MediaStore / MediaMetadataRetriever</li>
 *   <li><b>自解析 fallback</b> - 系统拿不到时，根据扩展名选择对应格式的二进制解析器</li>
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
 * <p>不做智能解析：绝不从文件名猜测艺术家和标题，避免搞反。
 */
public final class AudioMetadataReader {

    private AudioMetadataReader() {
        // 私有构造函数，防止实例化
    }

    /**
     * 从音频文件读取元数据
     *
     * <p>根据文件扩展名自动选择解析器。解析失败时返回 null，由调用方决定是否使用文件名兜底。
     *
     * @param filePath 音频文件绝对路径
     * @return 元数据结果，解析失败返回 null
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
     * 获取文件名作为标题（兜底方案）
     *
     * <p>注意：仅返回去扩展名的文件名，不做任何"智能"拆分，
     * 避免把艺术家和歌名搞反。艺术家字段始终为空字符串。
     *
     * @param filePath 音频文件路径
     * @return 仅包含 title 的元数据对象，artist 和 album 为空字符串
     */
    public static WavMetadataReader.AudioMetadata getFallbackFromFileName(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return null;
        }
        File file = new File(filePath);
        WavMetadataReader.AudioMetadata meta = new WavMetadataReader.AudioMetadata();
        meta.title = FileUtils.getNameWithoutExtension(file);
        meta.artist = "";
        meta.album = "";
        meta.durationMs = 0;
        return meta;
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
