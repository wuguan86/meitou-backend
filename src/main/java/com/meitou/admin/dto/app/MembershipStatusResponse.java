package com.meitou.admin.dto.app;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MembershipStatusResponse {

    private boolean oldUser;

    private Integer activePackageId;

    private String activeLevelCode;

    private String activeBillingCycle;

    private LocalDateTime activeEndAt;

    private boolean canSwitchType;
}

