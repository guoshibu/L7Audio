package com.aug32.l7audio.domain.audio.playlist;

/**
 * 扫描得到的音乐信息
 *
 * 数据传输对象（DTO），用于从 MediaStore 扫描后直接传入音乐来源，
 * 避免后续重复提取元数据，提高加载效率。
 *
 * 这是 MusicSource 接口的标准实现中使用的数据结构，
 * 专门用于存储扫描阶段已获取的音乐元数据。
 */
public class ScannedMusicInfo {
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

    /**
     * 构造函数，创建扫描音乐信息对象
     *
     * @param filePath   音乐文件的本地绝对路径
     * @param contentUri 音乐内容的Content URI
     * @param title      歌曲标题
     * @param artist     艺术家名称
     * @param album      专辑名称
     * @param duration   歌曲时长，单位毫秒
     */
    public ScannedMusicInfo(String filePath, String contentUri, String title,
                           String artist, String album, long duration) {
        this.filePath = filePath;
        this.contentUri = contentUri;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.duration = duration;
    }
}
