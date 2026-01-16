package com.meitou.admin.dto.app;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 设置密码请求
 */
@Data
public class SetPasswordRequest {
    
    /**
     * 新密码
     */
    @NotBlank(message = "密码不能为空")
    private String password;
}
