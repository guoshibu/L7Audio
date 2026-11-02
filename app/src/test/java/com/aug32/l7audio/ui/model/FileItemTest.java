package com.aug32.l7audio.ui.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 音频文件后缀判断测试。
 *
 * <p>该类是纯字符串逻辑（无磁盘 IO），用于锁定：
 * <ul>
 *   <li>支持的扩展名集合判断正确</li>
 *   <li>大写扩展名也能识别 —— 回归“用默认 Locale 做 toLowerCase，
 *       在土耳其语等区域把 I 转成 ı 导致判断失败”的问题</li>
 * </ul>
 */
public class FileItemTest {

    @Test
    public void recognizesSupportedExtensions() {
        assertTrue(FileItem.isAudioFileName("song.mp3"));
        assertTrue(FileItem.isAudioFileName("track.wav"));
        assertTrue(FileItem.isAudioFileName("album.flac"));
        assertTrue(FileItem.isAudioFileName("voice.ogg"));
        assertTrue(FileItem.isAudioFileName("music.m4a"));
        assertTrue(FileItem.isAudioFileName("music.aac"));
        assertTrue(FileItem.isAudioFileName("music.wma"));
        assertTrue(FileItem.isAudioFileName("record.amr"));
    }

    @Test
    public void recognizesUpperCaseExtensions() {
        // Locale.ROOT 回归用例：这些输入在土耳其语默认 Locale 下曾判断失败
        assertTrue(FileItem.isAudioFileName("SONG.MP3"));
        assertTrue(FileItem.isAudioFileName("TRACK.FLAC"));
        assertTrue(FileItem.isAudioFileName("MUSIC.M4A"));
    }

    @Test
    public void rejectsNonAudioAndInvalidNames() {
        assertFalse(FileItem.isAudioFileName(null));
        assertFalse(FileItem.isAudioFileName(""));
        assertFalse(FileItem.isAudioFileName("cover.jpg"));
        assertFalse(FileItem.isAudioFileName("readme.txt"));
        // 没有扩展名，只是名字里出现 mp3
        assertFalse(FileItem.isAudioFileName("my-mp3-list"));
    }
}
