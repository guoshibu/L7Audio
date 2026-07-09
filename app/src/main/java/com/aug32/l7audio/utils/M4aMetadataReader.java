package com.aug32.l7audio.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;

/**
 * M4A / AAC 音频文件元数据解析器
 *
 * <p>职责：直接读取 M4A / MP4 / AAC 文件的 ilst 原子块，提取标题、艺术家、专辑等元数据。
 *
 * <p>设计背景：Android 系统对 M4A / AAC 格式的元数据支持不稳定，部分设备上
 * MediaMetadataRetriever 可能无法正确读取，因此需要自行解析 MP4 原子结构。
 *
 * <p>M4A (MP4) 文件结构：
 * <pre>
 * moov
 *   udta
 *     meta
 *       ilst
 *         ©nam  (标题)
 *         ©ART  (艺术家)
 *         ©alb  (专辑)
 *         ©gen  (流派)
 *         ...
 * </pre>
 *
 * <p>支持的 iTunes 元数据标签：
 * <ul>
 *   <li>©nam - 标题 (Title)</li>
 *   <li>©ART - 艺术家 (Artist)</li>
 *   <li>©alb - 专辑 (Album)</li>
 *   <li>©gen - 流派 (Genre)</li>
 *   <li>©day - 日期 (Date)</li>
 * </ul>
 *
 * <p>字节序说明：MP4/M4A 所有多字节整数均使用大端序 (big-endian)。
 */
public class M4aMetadataReader {

    /**
     * 从 M4A / AAC 文件中读取元数据
     *
     * @param filePath M4A 文件绝对路径
     * @return 解析结果，解析失败返回 null
     */
    public static WavMetadataReader.AudioMetadata readMetadata(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return null;
        }
        File file = new File(filePath);
        if (!file.exists() || file.length() < 100) {
            return null;
        }

        InputStream is = null;
        try {
            is = new FileInputStream(file);
            long fileSize = file.length();

            WavMetadataReader.AudioMetadata result = new WavMetadataReader.AudioMetadata();

            // 查找 moov atom
            long moovPos = findAtom(is, 0, fileSize, "moov");
            if (moovPos < 0) return null;

            long moovSize = getAtomSize(is, moovPos);

            // 从 moov → mvhd 中解析时长
            long mvhdPos = findAtom(is, moovPos + 8, moovPos + moovSize, "mvhd");
            if (mvhdPos > 0) {
                long mvhdSize = getAtomSize(is, mvhdPos);
                long mvhdDataStart = mvhdPos + 8;
                // mvhd: version(1) + flags(3) + creationTime(4) / version=0 则 4 字节
                // version=0: 后续 times(4) + duration(4)
                // version=1: 后续 times(8) + duration(8)
                byte[] mvhdBuf = new byte[4];
                if (is instanceof FileInputStream) {
                    ((FileInputStream) is).getChannel().position(mvhdDataStart);
                }
                if (readFully(is, mvhdBuf) == 4) {
                    int version = mvhdBuf[0] & 0xFF;
                    if (version == 0 && mvhdSize >= 24) {
                        byte[] mvhdFields = new byte[8];
                        if (is instanceof FileInputStream) {
                            ((FileInputStream) is).getChannel().position(mvhdDataStart + 4 + 4);
                        }
                        if (readFully(is, mvhdFields) == 8) {
                            long timeScale = readInt32BE(mvhdFields, 0);
                            long duration = readInt32BE(mvhdFields, 4);
                            if (timeScale > 0) {
                                result.durationMs = (duration * 1000) / timeScale;
                            }
                        }
                    } else if (version == 1 && mvhdSize >= 32) {
                        byte[] mvhdFields = new byte[16];
                        if (is instanceof FileInputStream) {
                            ((FileInputStream) is).getChannel().position(mvhdDataStart + 4 + 4);
                        }
                        if (readFully(is, mvhdFields) == 16) {
                            long timeScale = readInt32BE(mvhdFields, 0);
                            long duration = readInt64BE(mvhdFields, 8);
                            if (timeScale > 0) {
                                result.durationMs = (duration * 1000) / timeScale;
                            }
                        }
                    }
                }
            }

            // 查找 moov → udta → meta → ilst 获取标题/艺术家
            long udtaPos = findAtom(is, moovPos + 8, moovPos + moovSize, "udta");
            if (udtaPos < 0) return result;

            long udtaSize = getAtomSize(is, udtaPos);
            long metaPos = findAtom(is, udtaPos + 8, udtaPos + udtaSize, "meta");
            if (metaPos < 0) return result;

            long metaSize = getAtomSize(is, metaPos);
            long ilstPos = findAtom(is, metaPos + 12, metaPos + metaSize, "ilst");
            if (ilstPos < 0) return result;

            long ilstSize = getAtomSize(is, ilstPos);

            // 遍历 ilst 的子 atom
            parseIlstAtom(is, ilstPos + 8, ilstPos + ilstSize, result);

            // 返回结果（可能只有时长，没有标题/艺术家）
            return result;

        } catch (Exception e) {
            AppLog.d("M4aMetadataReader", "Failed to read M4A metadata: " + filePath);
            return null;
        } finally {
            if (is != null) {
                try { is.close(); } catch (IOException ignored) {}
            }
        }
    }

    /**
     * 在指定范围内查找指定类型的原子
     *
     * @param is       文件输入流
     * @param startPos 起始位置
     * @param endPos   结束位置
     * @param atomName 原子名称（4字符）
     * @return 原子起始位置，未找到返回 -1
     */
    private static long findAtom(InputStream is, long startPos, long endPos, String atomName)
            throws IOException {
        long pos = startPos;
        byte[] header = new byte[8];

        while (pos + 8 <= endPos) {
            if (is instanceof FileInputStream) {
                ((FileInputStream) is).getChannel().position(pos);
            }
            if (readFully(is, header) != 8) {
                break;
            }

            long size = readInt32BE(header, 0);
            String name = bytesToString(header, 4, 4);

            if (size < 8) break;

            if (atomName.equals(name)) {
                return pos;
            }

            pos += size;
        }
        return -1;
    }

    /**
     * 获取指定位置原子的大小
     *
     * @param is  文件输入流
     * @param pos 原子起始位置
     * @return 原子大小
     */
    private static long getAtomSize(InputStream is, long pos) throws IOException {
        byte[] header = new byte[8];
        if (is instanceof FileInputStream) {
            ((FileInputStream) is).getChannel().position(pos);
        }
        if (readFully(is, header) != 8) {
            return 0;
        }
        long size = readInt32BE(header, 0);
        // 处理 64 位大小（size=1 时，后面 8 字节是真实大小）
        if (size == 1) {
            byte[] extSize = new byte[8];
            if (readFully(is, extSize) == 8) {
                size = readInt64BE(extSize, 0);
            }
        }
        return size;
    }

    /**
     * 解析 ilst 原子中的元数据标签
     *
     * @param is       文件输入流
     * @param startPos 起始位置
     * @param endPos   结束位置
     * @param result   结果写入对象
     */
    private static void parseIlstAtom(InputStream is, long startPos, long endPos,
                                      WavMetadataReader.AudioMetadata result) throws IOException {
        long pos = startPos;
        byte[] header = new byte[8];

        while (pos + 8 <= endPos) {
            if (is instanceof FileInputStream) {
                ((FileInputStream) is).getChannel().position(pos);
            }
            if (readFully(is, header) != 8) {
                break;
            }

            long size = readInt32BE(header, 0);
            String name = bytesToString(header, 4, 4);
            if (size < 8 || size > endPos - pos) break;

            // 找到子 atom 中的 data atom
            // 结构：名称 atom → data atom → (4字节版本标志) + 数据
            long dataPos = findAtom(is, pos + 8, pos + size, "data");
            if (dataPos > 0) {
                long dataSize = getAtomSize(is, dataPos);
                // data atom 的数据从 16 字节开始：8字节头 + 4字节版本标志 + 4字节保留
                int contentSize = (int) (dataSize - 16);
                if (contentSize > 0 && contentSize < 1024 * 1024) {
                    byte[] content = new byte[contentSize];
                    if (is instanceof FileInputStream) {
                        ((FileInputStream) is).getChannel().position(dataPos + 16);
                    }
                    if (readFully(is, content) == contentSize) {
                        String value = new String(content, java.nio.charset.StandardCharsets.UTF_8);
                        switch (name) {
                            case "\u00A9nam": // ©nam
                                result.title = value;
                                break;
                            case "\u00A9ART": // ©ART
                                result.artist = value;
                                break;
                            case "\u00A9alb": // ©alb
                                result.album = value;
                                break;
                            default:
                                // 其他标签暂不处理
                                break;
                        }
                    }
                }
            }

            pos += size;
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
     * 以大端序读取 4 字节整数
     *
     * @param bytes  字节数组
     * @param offset 起始偏移
     * @return 整数值
     */
    private static long readInt32BE(byte[] bytes, int offset) {
        return ((long) (bytes[offset] & 0xFF) << 24)
                | ((long) (bytes[offset + 1] & 0xFF) << 16)
                | ((long) (bytes[offset + 2] & 0xFF) << 8)
                | (long) (bytes[offset + 3] & 0xFF);
    }

    /**
     * 以大端序读取 8 字节整数
     *
     * @param bytes  字节数组
     * @param offset 起始偏移
     * @return 长整数值
     */
    private static long readInt64BE(byte[] bytes, int offset) {
        long result = 0;
        for (int i = 0; i < 8; i++) {
            result = (result << 8) | (bytes[offset + i] & 0xFF);
        }
        return result;
    }
}
