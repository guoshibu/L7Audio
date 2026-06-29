package com.aug32.l7audio.domain.audio.playlist;

/**
 * 扫描得到的音乐信息
 *
 * 用于从 MediaStore 扫描后直接传入，避免重复提取元数据。
 * 这是 MusicSource 接口的一个标准实现的数据传输对象。
 */
public class ScannedMusicInfo {
    public String filePath;
    public String contentUri;
    public String title;
    public String artist;
    public String album;
    public long duration;

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
