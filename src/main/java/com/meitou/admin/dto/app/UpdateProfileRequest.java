package com.meitou.admin.dto.app;

import lombok.Data;

@Data
public class UpdateProfileRequest {
    private String username;

    private String email;

    private String company;
}
