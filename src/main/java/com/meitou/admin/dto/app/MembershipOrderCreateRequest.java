package com.meitou.admin.dto.app;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MembershipOrderCreateRequest {

    @NotNull
    private Integer packageId;

    @NotBlank
    private String billingCycle;

    private Integer quantity;

    @NotBlank
    private String paymentType;
}

