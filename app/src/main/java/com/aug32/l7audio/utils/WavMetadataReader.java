package com.aug32.l7audio.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;

/**
 * WAV 音频文件元数据解析器
 *
 * <p>职责：直接读取 WAV 文件的二进制 RIFF INFO 块，提取标题、艺术家、专辑等元数据。
 *
 * <p>设计背景：Android MediaStore 和 MediaMetadataRetriever 对 WAV 格式的
 * 元数据支持不完善，经常返回 &lt;unknown&gt; 或空值，因此需要自行解析二进制文件头。
 *
 * <p>WAV RIFF 文件结构：
 * <pre>
 * RIFF....WAVE
 *   fmt ....
 *   LIST....INFO
 *     INAM标题数据
 *     IART艺术家数据
 *     IPRD专辑数据
 *     ...
 *   data....
 * </pre>
 *
 * <p>支持的 INFO 标签：
 * <ul>
 *   <li>INAM - 标题 (Title)</li>
 *   <li>IART - 艺术家 (Artist)</li>
 *   <li>IPRD - 专辑 (Album)</li>
 *   <li>ICMT - 备注 (Comment)</li>
 *   <li>IGNR - 流派 (Genre)</li>
 * </ul>
 *
 * <p>字节序说明：RIFF/WAV 使用小端序 (little-endian) 存储多字节整数。
 */
public class WavMetadataReader {

    /**
     * 音频元数据结果
     */
    public static class AudioMetadata {
        /** 歌曲标题 */
        public String title;
        /** 艺术家名称 */
        public String artist;
        /** 专辑名称 */
        public String album;
        /** 歌曲时长（毫秒），解析失败为 0 */
        public long durationMs;
    }

    /**
     * 从 WAV 文件中读取元数据
     *
     * @param filePath WAV 文件绝对路径
     * @return 解析结果，解析失败返回 null
     */
    public static AudioMetadata readMetadata(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return null;
        }
        File file = new File(filePath);
        if (!file.exists() || file.length() < 12) {
            return null;
        }

        InputStream is = null;
        try {
            is = new FileInputStream(file);

            // 1. 读取 RIFF 文件头（12 字节）
            byte[] header = new byte[12];
            if (readFully(is, header) != 12) {
                return null;
            }

            // 检查 RIFF 标识
            if (!"RIFF".equals(bytesToString(header, 0, 4))) {
                return null;
            }
            // 检查 WAVE 标识
            if (!"WAVE".equals(bytesToString(header, 8, 4))) {
                return null;
            }

            AudioMetadata result = new AudioMetadata();
            long fileSize = file.length();
            long offset = 12;

            // 2. 遍历所有块，寻找 LIST INFO 块
            while (offset + 8 < fileSize) {
                byte[] chunkHeader = new byte[8];
                // 从文件指定位置读取块头
                if (is instanceof FileInputStream) {
                    ((FileInputStream) is).getChannel().position(offset);
                }
                if (readFully(is, chunkHeader) != 8) {
                    break;
                }

                String chunkId = bytesToString(chunkHeader, 0, 4);
                int chunkSize = readIntLE(chunkHeader, 4);
                if (chunkSize <= 0 || chunkSize > fileSize) {
                    break;
                }

                long dataOffset = offset + 8;

                if ("LIST".equals(chunkId)) {
                    // 读取 LIST 块类型
                    byte[] listType = new byte[4];
                    if (is instanceof FileInputStream) {
                        ((FileInputStream) is).getChannel().position(dataOffset);
                    }
                    if (readFully(is, listType) == 4
                            && "INFO".equals(bytesToString(listType, 0, 4))) {
                        // 解析 INFO 子块
                        parseInfoChunk(is, dataOffset + 4, dataOffset + chunkSize, result);
                        break;
                    }
                }

                // 移动到下一个块（块大小按 2 字节对齐）
                offset = dataOffset + chunkSize;
                if (chunkSize % 2 != 0) {
                    offset++;
                }
            }

            // 3. 如果 RIFF INFO 没读到有效信息，尝试从文件末尾读取 ID3v2 标签
            // 原因：部分 WAV 文件使用 ID3v2 标签存储元数据（类似 MP3），而非 RIFF INFO
            boolean hasValidInfo = (result.title != null && !result.title.isEmpty())
                    || (result.artist != null && !result.artist.isEmpty())
                    || (result.album != null && !result.album.isEmpty());
            if (!hasValidInfo) {
                parseId3v2FromTail(is, fileSize, result);
            }

            // 4. 如果标题或艺术家有一个解析到了，就认为成功
            if ((result.title != null && !result.title.isEmpty())
                    || (result.artist != null && !result.artist.isEmpty())
                    || (result.album != null && !result.album.isEmpty())) {
                return result;
            }

            return null;

        } catch (Exception e) {
            AppLog.d("WavMetadataReader", "Failed to read WAV metadata: " + filePath);
            return null;
        } finally {
            if (is != null) {
                try { is.close(); } catch (IOException ignored) {}
            }
        }
    }

    /**
     * 解析 LIST INFO 块中的各个子标签
     *
     * @param is         文件输入流
     * @param startPos   INFO 子块起始位置
     * @param endPos     LIST 块结束位置
     * @param result     结果写入对象
     */
    private static void parseInfoChunk(InputStream is, long startPos, long endPos,
                                       AudioMetadata result) throws IOException {
        long pos = startPos;
        byte[] subHeader = new byte[8];

        while (pos + 8 <= endPos) {
            if (is instanceof FileInputStream) {
                ((FileInputStream) is).getChannel().position(pos);
            }
            if (readFully(is, subHeader) != 8) {
                break;
            }

            String subId = bytesToString(subHeader, 0, 4);
            int subSize = readIntLE(subHeader, 4);
            if (subSize <= 0 || pos + 8 + subSize > endPos) {
                break;
            }

            byte[] data = new byte[subSize];
            if (is instanceof FileInputStream) {
                ((FileInputStream) is).getChannel().position(pos + 8);
            }
            if (readFully(is, data) == subSize) {
                String value = bytesToUtf8String(data);
                switch (subId) {
                    case "INAM":
                        result.title = value;
                        break;
                    case "IART":
                        result.artist = value;
                        break;
                    case "IPRD":
                        result.album = value;
                        break;
                    default:
                        // 其他标签暂不处理
                        break;
                }
            }

            // 子块也是按 2 字节对齐
            pos += 8 + subSize;
            if (subSize % 2 != 0) {
                pos++;
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
     * 将字节数组转换为字符串（智能编码检测）
     *
     * <p>WAV RIFF INFO 块的编码没有统一标准：
     * <ul>
     *   <li>Windows 写入的中文 WAV 通常是 GBK 编码</li>
     *   <li>部分现代工具使用 UTF-8 编码</li>
     *   <li>英文 ASCII 两者都兼容</li>
     * </ul>
     *
     * <p>检测策略：
     * <ol>
     *   <li>纯 ASCII 直接返回</li>
     *   <li>非 ASCII 时，优先尝试 GBK（中文 WAV 最常见）</li>
     *   <li>GBK 解码后有乱码字符（如 锟斤拷、?等），则 fallback 到 UTF-8</li>
     * </ol>
     *
     * @param bytes 字节数组
     * @return 解码后的字符串
     */
    private static String bytesToUtf8String(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        // 找到第一个空字符位置
        int len = bytes.length;
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] == 0) {
                len = i;
                break;
            }
        }
        if (len <= 0) return "";

        // 纯 ASCII 直接返回（两种编码都兼容）
        boolean isPureAscii = true;
        for (int i = 0; i < len; i++) {
            if ((bytes[i] & 0xFF) > 127) {
                isPureAscii = false;
                break;
            }
        }
        if (isPureAscii) {
            return new String(bytes, 0, len, java.nio.charset.StandardCharsets.US_ASCII);
        }

        // 中文 WAV 优先尝试 GBK（Windows 资源管理器写入的 WAV 默认 GBK）
        try {
            String gbkStr = new String(bytes, 0, len, "GBK");
            // 检查 GBK 解码结果是否有明显乱码特征
            if (isReasonableChineseText(gbkStr)) {
                return gbkStr;
            }
        } catch (Exception ignored) {}

        // GBK 不合理，尝试 UTF-8
        try {
            return new String(bytes, 0, len, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 都失败了退回 GBK
            try {
                return new String(bytes, 0, len, "GBK");
            } catch (Exception e2) {
                return "";
            }
        }
    }

    /**
     * 判断字符串是否为合理的中文文本（简单启发式）
     *
     * <p>用于检测 GBK 解码结果是否合理，避免把 UTF-8 编码的文本用 GBK 解出乱码。
     *
     * @param text 待检测文本
     * @return true=看起来是合理文本
     */
    private static boolean isReasonableChineseText(String text) {
        if (text == null || text.isEmpty()) return false;
        int chineseCount = 0;
        int weirdCount = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // 常用汉字范围
            if (c >= '\u4E00' && c <= '\u9FA5') {
                chineseCount++;
            }
            // 替换字符 / 控制字符 / 非常见符号算乱码
            else if (c == '\uFFFD' || c < 0x20 || (c > 0x7E && c < 0xA0)) {
                weirdCount++;
            }
        }
        // 有中文字符且乱码字符比例低 → 认为合理
        int total = text.length();
        if (chineseCount > 0 && weirdCount * 10 < total) {
            return true;
        }
        // 全英文/数字/标点也算合理
        if (chineseCount == 0 && weirdCount == 0) {
            return true;
        }
        return false;
    }

    /**
     * 从文件末尾解析 ID3v2 标签
     *
     * <p>部分 WAV 文件在末尾附加 ID3v2 标签（类似 MP3），而非使用 RIFF INFO。
     * ID3v2 标签通常以 "ID3" 开头，位于文件最后 128 字节到几 KB 范围内。
     *
     * @param is       文件输入流
     * @param fileSize 文件大小
     * @param result   结果写入对象
     */
    private static void parseId3v2FromTail(InputStream is, long fileSize, AudioMetadata result) {
        if (!(is instanceof FileInputStream)) return;
        if (fileSize < 128) return;

        try {
            FileInputStream fis = (FileInputStream) is;
            // 从末尾往前搜索 ID3 标签头（最多搜索 32KB）
            long searchSize = Math.min(32 * 1024, fileSize - 128);
            long searchStart = fileSize - searchSize;

            byte[] tailBuffer = new byte[(int) searchSize];
            fis.getChannel().position(searchStart);
            int read = readFully(fis, tailBuffer);
            if (read < 10) return;

            // 在尾部缓冲区中查找 "ID3" 标识
            for (int i = 0; i < read - 10; i++) {
                if (tailBuffer[i] == 'I' && tailBuffer[i + 1] == 'D' && tailBuffer[i + 2] == '3') {
                    // 找到 ID3v2 头，解析标签
                    int majorVersion = tailBuffer[i + 3] & 0xFF;
                    int tagSize = syncSafeInt(tailBuffer, i + 6);
                    if (tagSize <= 0 || tagSize > read - i - 10) continue;

                    byte[] tagData = new byte[tagSize];
                    System.arraycopy(tailBuffer, i + 10, tagData, 0, tagSize);
                    parseId3v2Frames(tagData, majorVersion, result);
                    return;
                }
            }
        } catch (Exception e) {
            AppLog.d("WavMetadataReader", "Failed to parse ID3v2 from tail");
        }
    }

    /**
     * 解析 ID3v2 帧数据
     *
     * @param data         帧数据（不包含 ID3 头）
     * @param majorVersion ID3v2 主版本号（3 或 4）
     * @param result       结果写入对象
     */
    private static void parseId3v2Frames(byte[] data, int majorVersion, AudioMetadata result) {
        int pos = 0;
        int frameIdLen = (majorVersion == 2) ? 3 : 4;
        int frameHeaderLen = (majorVersion == 2) ? 6 : 10;

        while (pos + frameHeaderLen <= data.length) {
            String frameId;
            int frameSize;

            if (majorVersion == 2) {
                frameId = bytesToString(data, pos, 3);
                frameSize = ((data[pos + 3] & 0xFF) << 16)
                        | ((data[pos + 4] & 0xFF) << 8)
                        | (data[pos + 5] & 0xFF);
            } else {
                frameId = bytesToString(data, pos, 4);
                frameSize = syncSafeInt(data, pos + 4);
            }

            if (frameSize <= 0 || pos + frameHeaderLen + frameSize > data.length) {
                break;
            }
            if (frameId.isEmpty() || frameId.equals("\0\0\0\0")) {
                break;
            }

            // 帧内容从 frameHeaderLen 后开始
            int contentOffset = pos + frameHeaderLen;
            // 跳过编码字节（第 0 字节是文本编码）
            if (frameSize > 1) {
                byte encoding = data[contentOffset];
                int textOffset = contentOffset + 1;
                int textLen = frameSize - 1;
                if (textLen > 0) {
                    String text = decodeId3v2Text(data, textOffset, textLen, encoding);
                    // 去掉 BOM 和空字符
                    text = stripId3v2Text(text);

                    switch (frameId) {
                        case "TIT2":
                        case "TT2":
                            result.title = text;
                            break;
                        case "TPE1":
                        case "TP1":
                            result.artist = text;
                            break;
                        case "TALB":
                        case "TAL":
                            result.album = text;
                            break;
                        default:
                            break;
                    }
                }
            }

            pos += frameHeaderLen + frameSize;
        }
    }

    /**
     * 解码 ID3v2 文本帧
     *
     * @param data     数据
     * @param offset   偏移
     * @param length   长度
     * @param encoding 编码类型（0=ISO-8859-1, 1=UTF-16 BOM, 2=UTF-16 BE, 3=UTF-8）
     * @return 解码后的字符串
     */
    private static String decodeId3v2Text(byte[] data, int offset, int length, byte encoding) {
        if (length <= 0) return "";
        try {
            switch (encoding) {
                case 0:
                    return new String(data, offset, length, "ISO-8859-1");
                case 1:
                    return new String(data, offset, length, "UTF-16");
                case 2:
                    return new String(data, offset, length, "UTF-16BE");
                case 3:
                    return new String(data, offset, length, "UTF-8");
                default:
                    return new String(data, offset, length, "ISO-8859-1");
            }
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 去除 ID3v2 文本中的 BOM 和末尾空字符
     *
     * @param text 原始文本
     * @return 清理后的文本
     */
    private static String stripId3v2Text(String text) {
        if (text == null || text.isEmpty()) return "";
        // 去掉开头 BOM
        if (text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        // 去掉末尾空字符
        int end = text.length();
        while (end > 0 && (text.charAt(end - 1) == '\0' || text.charAt(end - 1) == ' ')) {
            end--;
        }
        return text.substring(0, end);
    }

    /**
     * 读取 ID3v2 同步安全整数（每字节只用 7 位）
     *
     * @param data   数据
     * @param offset 偏移
     * @return 整数值
     */
    private static int syncSafeInt(byte[] data, int offset) {
        return ((data[offset] & 0x7F) << 21)
                | ((data[offset + 1] & 0x7F) << 14)
                | ((data[offset + 2] & 0x7F) << 7)
                | (data[offset + 3] & 0x7F);
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
