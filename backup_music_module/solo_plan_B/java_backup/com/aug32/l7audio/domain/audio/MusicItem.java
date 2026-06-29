package com.aug32.l7audio.domain.audio;

/**
 * 音乐项数据类
 *
 * 职责：纯数据类，描述一首音乐的基本信息
 */
public class MusicItem {
    public String filePath;
    public String contentUri;
    public String title;
    public String artist;
    public String album;
    public long duration;
    public String lyrics;
    public long lyricsModified;
    public long fileModified;

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
    }

    public MusicItem(String filePath, String contentUri, String title, String artist, long duration) {
        this.filePath = filePath;
        this.contentUri = contentUri == null ? filePath : contentUri;
        this.title = title;
        this.artist = artist;
        this.album = "";
        this.duration = duration;
        this.lyrics = "";
        this.lyricsModified = 0;
        this.fileModified = System.currentTimeMillis();
    }

    /**
     * 深拷贝
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
        return copy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MusicItem musicItem = (MusicItem) o;
        return filePath != null ? filePath.equals(musicItem.filePath) : musicItem.filePath == null;
    }

    @Override
    public int hashCode() {
        return filePath != null ? filePath.hashCode() : 0;
    }
}
