package com.aug32.l7audio.ui.model;

import java.io.File;

/**
 * 文件浏览器项数据模型
 *
 * <p>用于 FileBrowserFragment 展示文件和目录列表，
 * 支持目录选择模式和文件选择模式。
 */
public class FileItem {

    /** 文件/目录名称 */
    public String name;

    /** 完整路径 */
    public String path;

    /** 是否为目录 */
    public boolean isDirectory;

    /** 是否选中（多选模式） */
    public boolean isSelected;

    /** 文件大小（字节，仅文件有效） */
    public long size;

    /** 目录子项数（仅目录有效，后台线程预计算，UI 线程只读） */
    public int childCount;

    /**
     * 是否为受支持的音频文件（目录恒为 false）。
     * 性能修复：扫描构造时用纯字符串后缀判断预计算，
     * UI 交互（全选统计/单击选中）直接读此标志，
     * 避免每次交互对列表每项做 new File().isFile() 磁盘 stat
     */
    public boolean isAudio;

    /**
     * 构造文件项
     *
     * @param file 原始文件对象
     */
    public FileItem(File file) {
        this.name = file.getName();
        this.path = file.getAbsolutePath();
        this.isDirectory = file.isDirectory();
        this.isSelected = false;
        this.size = file.length();
        this.isAudio = !this.isDirectory && isAudioFileName(this.name);
    }

    /**
     * 构造文件项（手动指定）
     *
     * @param name        名称
     * @param path        路径
     * @param isDirectory 是否为目录
     */
    public FileItem(String name, String path, boolean isDirectory) {
        this.name = name;
        this.path = path;
        this.isDirectory = isDirectory;
        this.isSelected = false;
        this.size = 0;
        this.isAudio = !isDirectory && isAudioFileName(name);
    }

    /**
     * 纯字符串后缀判断是否为音频文件名（无任何磁盘 IO）
     *
     * <p>公开给文件浏览器/播放器复用，避免多处各写一份后缀判断导致行为不一致。</p>
     *
     * @param fileName 文件名
     * @return true 表示后缀属于受支持的音频格式
     */
    public static boolean isAudioFileName(String fileName) {
        if (fileName == null) return false;
        // 必须用 Locale.ROOT：土耳其语等默认区域会把 'I' 小写成 'ı'，
        // 用默认 Locale 做 toLowerCase 会让含大写字母的扩展名判断失败
        String name = fileName.toLowerCase(java.util.Locale.ROOT);
        for (String ext : com.aug32.l7audio.utils.FileUtils.AUDIO_EXTENSIONS) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取格式化的文件大小
     *
     * @return 格式化后的大小字符串，如 "3.5 MB"
     */
    public String getFormattedSize() {
        if (isDirectory) {
            return "";
        }
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }
}
