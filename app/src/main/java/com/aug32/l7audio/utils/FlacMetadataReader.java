package com.aug32.l7audio.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;

/**
 * FLAC 音频文件元数据解析器
 *
 * <p>职责：直接读取 FLAC 文件的二进制 Vorbis Comment 块，提取标题、艺术家、专辑等元数据。
 *
 * <p>设计背景：Android 系统对 FLAC 格式的元数据支持不稳定，部分版本无法正常读取，
 * 因此需要自行解析文件头。
 *
 * <p>FLAC 文件结构：
 * <pre>
 * fLaC
 *   STREAMINFO 块 (必须第一个)
 *   VORBIS_COMMENT 块
 *     vendor string
 *     user comments: TITLE=xxx, ARTIST=xxx, ALBUM=xxx
 *   ...其他块...
 *   音频帧
 * </pre>
 *
 * <p>支持的 Vorbis Comment 标签（不区分大小写）：
 * <ul>
 *   <li>TITLE - 标题</li>
 *   <li>ARTIST - 艺术家</li>
 *   <li>ALBUM - 专辑</li>
 *   <li>GENRE - 流派</li>
 *   <li>DATE - 日期</li>
 * </ul>
 *
 * <p>字节序说明：FLAC 块头使用大端序，Vorbis Comment 内部长度使用小端序。
 */
public class FlacMetadataReader {

    /**
     * 从 FLAC 文件中读取元数据
     *
     * @param filePath FLAC 文件绝对路径
     * @return 解析结果，解析失败返回 null
     */
    public static WavMetadataReader.AudioMetadata readMetadata(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return null;
        }
        File file = new File(filePath);
        if (!file.exists() || file.length() < 42) {
            return null;
        }

        InputStream is = null;
        try {
            is = new FileInputStream(file);

            // 1. 读取 fLaC 标识（4 字节）
            byte[] magic = new byte[4];
            if (readFully(is, magic) != 4) {
                return null;
            }
            if (!"fLaC".equals(bytesToString(magic, 0, 4))) {
                return null;
            }

            WavMetadataReader.AudioMetadata result = new WavMetadataReader.AudioMetadata();
            long fileSize = file.length();
            long offset = 4;
            boolean isLastBlock = false;

            // 2. 遍历元数据块，寻找 VORBIS_COMMENT (类型 4)
            while (!isLastBlock && offset + 4 < fileSize) {
                byte[] blockHeader = new byte[4];
                if (is instanceof FileInputStream) {
                    ((FileInputStream) is).getChannel().position(offset);
                }
                if (readFully(is, blockHeader) != 4) {
                    break;
                }

                // 第一个字节：bit 7 = isLast, bit 0-6 = block type
                isLastBlock = (blockHeader[0] & 0x80) != 0;
                int blockType = blockHeader[0] & 0x7F;
                // 后 3 字节：块大小（大端序）
                int blockSize = readInt24BE(blockHeader, 1);

                if (blockSize <= 0 || offset + 4 + blockSize > fileSize) {
                    break;
                }

                if (blockType == 4) {
                    // VORBIS_COMMENT 块，解析它
                    parseVorbisComment(is, offset + 4, blockSize, result);
                    break;
                }

                // 移动到下一个块
                offset += 4 + blockSize;
            }

            // 3. 判断是否解析到了有用信息
            if ((result.title != null && !result.title.isEmpty())
                    || (result.artist != null && !result.artist.isEmpty())
                    || (result.album != null && !result.album.isEmpty())) {
                return result;
            }

            return null;

        } catch (Exception e) {
            AppLog.d("FlacMetadataReader", "Failed to read FLAC metadata: " + filePath);
            return null;
        } finally {
            if (is != null) {
                try { is.close(); } catch (IOException ignored) {}
            }
        }
    }

    /**
     * 解析 Vorbis Comment 块
     *
     * @param is        文件输入流
     * @param dataStart 数据起始位置
     * @param dataSize  数据大小
     * @param result    结果写入对象
     */
    private static void parseVorbisComment(InputStream is, long dataStart, int dataSize,
                                           WavMetadataReader.AudioMetadata result) throws IOException {
        byte[] data = new byte[dataSize];
        if (is instanceof FileInputStream) {
            ((FileInputStream) is).getChannel().position(dataStart);
        }
        if (readFully(is, data) != dataSize) {
            return;
        }

        int pos = 0;

        // 读取 vendor string length (4 bytes, little-endian)
        if (pos + 4 > data.length) return;
        int vendorLen = readIntLE(data, pos);
        pos += 4;
        if (pos + vendorLen > data.length) return;
        pos += vendorLen; // 跳过 vendor string

        // 读取 user comment list length (4 bytes, little-endian)
        if (pos + 4 > data.length) return;
        int commentCount = readIntLE(data, pos);
        pos += 4;

        // 逐条解析 comment
        for (int i = 0; i < commentCount; i++) {
            if (pos + 4 > data.length) break;
            int commentLen = readIntLE(data, pos);
            pos += 4;
            if (commentLen <= 0 || pos + commentLen > data.length) break;

            String comment = new String(data, pos, commentLen, java.nio.charset.StandardCharsets.UTF_8);
            pos += commentLen;

            int eqIdx = comment.indexOf('=');
            if (eqIdx <= 0) continue;

            String key = comment.substring(0, eqIdx).toUpperCase();
            String value = comment.substring(eqIdx + 1);

            switch (key) {
                case "TITLE":
                    result.title = value;
                    break;
                case "ARTIST":
                    result.artist = value;
                    break;
                case "ALBUM":
                    result.album = value;
                    break;
                default:
                    // 其他标签暂不处理
                    break;
            }
        }
    }

    /**
     * 从输入流中读取指定长度的字节
     *
     * @param is  输入流
     * @param buf 缓冲区
     * @return 实际读取的字节数
     */
    private static int readFully(InputStream is, byte[] buf) throws IOException {
        int total = 0;
        while (total < buf.length) {
            int n = is.read(buf, total, buf.length - total);
            if (n < 0) break;
            total += n;
        }
        return total;
    }

    /**
     * 将字节数组转换为 ASCII 字符串
     *
     * @param bytes  字节数组
     * @param offset 起始偏移
     * @param length 长度
     * @return 字符串
     */
    private static String bytesToString(byte[] bytes, int offset, int length) {
        try {
            return new String(bytes, offset, length, "ISO-8859-1");
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 以大端序读取 3 字节整数
     *
     * @param bytes  字节数组
     * @param offset 起始偏移
     * @return 整数值
     */
    private static int readInt24BE(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 16)
                | ((bytes[offset + 1] & 0xFF) << 8)
                | (bytes[offset + 2] & 0xFF);
    }

    /**
     * 以小端序读取 4 字节整数
     *
     * @param bytes  字节数组
     * @param offset 起始偏移
     * @return 整数值
     */
    private static int readIntLE(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF)
                | ((bytes[offset + 1] & 0xFF) << 8)
                | ((bytes[offset + 2] & 0xFF) << 16)
                | ((bytes[offset + 3] & 0xFF) << 24);
    }
}
