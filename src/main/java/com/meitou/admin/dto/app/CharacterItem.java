package com.meitou.admin.dto.app;

/**
 * @ClassName CharacterItem
 * @Description
 * @Author 你的名字
 * @Date 2026/1/8 8:59
 * @Version 1.0
 */
public class CharacterItem {
    private String url;
    private String timestamps;

    // 构造函数
    public CharacterItem(String url, String timestamps) {
        this.url = url;
        this.timestamps = timestamps;
    }

    // Getters and Setters...
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getTimestamps() { return timestamps; }
    public void setTimestamps(String timestamps) { this.timestamps = timestamps; }
}
