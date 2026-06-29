package com.aug32.l7audio.data.model;

/**
 * TTS 列表项数据模型
 */
public class TTSItem {

    public String text;
    public String customName;
    public long createdAt;

    public TTSItem() {
    }

    public TTSItem(String text) {
        this.text = text;
        this.customName = "";
        this.createdAt = System.currentTimeMillis();
    }

    public TTSItem(String text, String customName) {
        this.text = text;
        this.customName = customName != null ? customName : "";
        this.createdAt = System.currentTimeMillis();
    }

    public String getDisplayName() {
        if (customName != null && !customName.isEmpty()) {
            return customName;
        }
        if (text != null && text.length() > 15) {
            return text.substring(0, 12) + "...";
        }
        return text != null ? text : "";
    }
}
