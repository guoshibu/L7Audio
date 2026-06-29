package com.aug32.l7audio.data.model;

/**
 * TTS 列表项数据模型
 *
 * 用于封装 TTS 预设文本的相关信息，包括文本内容、自定义名称和创建时间
 */
public class TTSItem {

    /** TTS 文本内容 */
    public String text;
    /** 用户自定义的显示名称 */
    public String customName;
    /** 创建时间戳（毫秒） */
    public long createdAt;

    /**
     * 无参构造函数
     */
    public TTSItem() {
    }

    /**
     * 构造函数，仅传入文本内容
     * 自定义名称默认为空字符串，创建时间为当前系统时间
     *
     * @param text TTS 文本内容
     */
    public TTSItem(String text) {
        this.text = text;
        this.customName = "";
        this.createdAt = System.currentTimeMillis();
    }

    /**
     * 构造函数，传入文本内容和自定义名称
     * 创建时间为当前系统时间
     *
     * @param text TTS 文本内容
     * @param customName 自定义显示名称，为 null 时默认为空字符串
     */
    public TTSItem(String text, String customName) {
        this.text = text;
        this.customName = customName != null ? customName : "";
        this.createdAt = System.currentTimeMillis();
    }

    /**
     * 获取显示名称
     * 优先返回自定义名称，若无则返回文本内容（超过15字符时截断并添加省略号）
     *
     * @return 用于显示的名称字符串
     */
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
