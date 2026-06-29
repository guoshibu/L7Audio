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

    /** 最后修改时间 */
    public long lastModified;

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
        this.lastModified = file.lastModified();
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
        this.lastModified = 0;
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
