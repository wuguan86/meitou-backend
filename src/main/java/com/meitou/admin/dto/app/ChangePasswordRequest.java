package com.meitou.admin.dto.app;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChangePasswordRequest {
    private String oldPassword;

    private String code;

    @NotBlank(message = "新密码不能为空")
    private String newPassword;
}

