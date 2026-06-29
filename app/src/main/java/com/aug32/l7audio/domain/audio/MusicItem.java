package com.aug32.l7audio.domain.audio;

/**
 * 音乐项数据类
 *
 * 职责：纯数据类，描述一首音乐的基本信息，包括文件路径、元数据、歌词、封面等。
 * 作为播放列表、播放器之间传递音乐信息的标准数据结构。
 */
public class MusicItem {
    /** 音乐文件的本地绝对路径 */
    public String filePath;
    /** 音乐内容的Content URI（用于Android系统访问） */
    public String contentUri;
    /** 歌曲标题 */
    public String title;
    /** 艺术家名称 */
    public String artist;
    /** 专辑名称 */
    public String album;
    /** 歌曲时长，单位毫秒 */
    public long duration;
    /** 歌词文本内容（LRC格式或纯文本） */
    public String lyrics;
    /** 歌词最后修改时间戳，用于判断是否需要重新加载 */
    public long lyricsModified;
    /** 音乐文件最后修改时间戳 */
    public long fileModified;
    /** 专辑封面图片字节数组 */
    public byte[] albumArt;

    /**
     * 无参构造函数，创建一个所有字段均为默认值的音乐项
     */
    public MusicItem() {
        this.filePath = "";
        this.contentUri = "";
        this.title = "";
        this.artist = "";
        this.album = "";
        this.duration = 0;
        this.lyrics = "";
        this.lyricsModified = 0;
        this.fileModified = 0;
        this.albumArt = null;
    }

    /**
     * 构造函数，创建包含基本信息的音乐项
     *
     * @param filePath   音乐文件的本地绝对路径
     * @param contentUri 音乐内容的Content URI，若为null则使用filePath代替
     * @param title      歌曲标题
     * @param artist     艺术家名称
     * @param duration   歌曲时长，单位毫秒
     */
    public MusicItem(String filePath, String contentUri, String title, String artist, long duration) {
        this.filePath = filePath;
        // contentUri为空时回退到filePath，保证至少有一个可用的访问路径
        this.contentUri = contentUri == null ? filePath : contentUri;
        this.title = title;
        this.artist = artist;
        this.album = "";
        this.duration = duration;
        this.lyrics = "";
        this.lyricsModified = 0;
        // 文件修改时间默认为当前时间，避免后续比较时出现异常
        this.fileModified = System.currentTimeMillis();
        this.albumArt = null;
    }

    /**
     * 深拷贝当前音乐项，创建一个独立的副本
     *
     * @return 新的MusicItem对象，所有字段值与原对象相同
     */
    public MusicItem copy() {
        MusicItem copy = new MusicItem();
        copy.filePath = this.filePath;
        copy.contentUri = this.contentUri;
        copy.title = this.title;
        copy.artist = this.artist;
        copy.album = this.album;
        copy.duration = this.duration;
        copy.lyrics = this.lyrics;
        copy.lyricsModified = this.lyricsModified;
        copy.fileModified = this.fileModified;
        copy.albumArt = this.albumArt;
        return copy;
    }

    /**
     * 判断两个音乐项是否相等
     *
     * 相等条件：两个MusicItem的filePath相等（均为null或字符串相等）。
     * 以文件路径作为唯一标识，因为同一首歌可能有不同的元数据但路径唯一。
     *
     * @param o 待比较的对象
     * @return 如果对象相同或filePath相等则返回true，否则返回false
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MusicItem musicItem = (MusicItem) o;
        // 使用filePath作为相等判断的唯一依据，确保同一文件不会重复添加
        return filePath != null ? filePath.equals(musicItem.filePath) : musicItem.filePath == null;
    }

    /**
     * 返回音乐项的哈希码
     *
     * 基于filePath计算哈希码，与equals()方法保持一致，
     * 确保在HashMap、HashSet等集合中正确使用。
     *
     * @return 哈希码值，filePath为null时返回0
     */
    @Override
    public int hashCode() {
        return filePath != null ? filePath.hashCode() : 0;
    }
}
