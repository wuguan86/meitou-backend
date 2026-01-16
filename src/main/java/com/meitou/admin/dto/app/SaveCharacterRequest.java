package com.meitou.admin.dto.app;

import lombok.Data;

/**
 * 保存角色视频请求
 */
@Data
public class SaveCharacterRequest {
    /**
     * 生成记录的PID (来自 generation_records.pid)
     */
    private String pid;

    /**
     * 时间戳 (例如 "0,3")
     */
    private String timestamps;

    /**
     * 角色名称
     */
    private String name;
}
