package com.meitou.admin.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 错误码枚举
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {
    
    SUCCESS(200, "操作成功"),
    SYSTEM_ERROR(500, "系统繁忙，请稍后再试"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或Token已过期"),
    FORBIDDEN(403, "无权限访问"),
    
    // 认证相关 1000-1999
    PASSWORD_ERROR(1001, "密码错误"),
    ACCOUNT_NOT_FOUND(1002, "账号不存在"),
    ACCOUNT_DISABLED(1003, "你的账号已被封禁，请联系客服"),
    VERIFY_CODE_ERROR(1004, "验证码错误或已过期"),
    INVITATION_CODE_INVALID(1005, "邀请码无效"),
    NOT_SET_PASSWORD(1006, "尚未设置密码"),
    USER_NOT_FOUND(1007, "用户不存在"),
    SITE_NOT_FOUND(1008, "无法识别站点信息"),
    ACCOUNT_LOCKED(1009, "账号已锁定，请15分钟后再试"),
    PASSWORD_TOO_WEAK(1010, "密码强度不足，需8位以上且包含字母数字特殊符号"),

    // 生成相关 2000-2999
    GENERATION_PLATFORM_NOT_CONFIGURED(2001, "生成平台未配置"),
    GENERATION_INTERFACE_NOT_CONFIGURED(2002, "生成接口未配置"),
    API_KEY_ERROR(2003, "API密钥配置错误"),
    GENERATION_FAILED(2004, "生成失败"),
    PARSE_RESPONSE_FAILED(2005, "解析响应失败"),
    REFERENCE_IMAGE_REQUIRED(2006, "参考图片不能为空"),
    API_CALL_FAILED(2007, "API调用失败"),
    API_RESPONSE_ERROR(2008, "API返回错误"),

    // 资源/记录相关 3000-3999
    RECORD_NOT_FOUND(3001, "记录不存在"),
    ASSET_NOT_FOUND(3002, "资产不存在"),
    PERMISSION_DENIED(3003, "无权操作"),
    CONTENT_NOT_FOUND(3004, "内容不存在"),

    // 支付/充值相关 4000-4999
    PAYMENT_FAILED(4001, "支付失败"),
    PAYMENT_CONFIG_ERROR(4002, "支付配置错误"),
    PAYMENT_ORDER_CREATE_FAILED(4003, "创建支付订单失败"),
    PAYMENT_CALLBACK_VERIFY_FAILED(4004, "支付回调验证失败"),
    RECHARGE_AMOUNT_INVALID(4005, "充值金额无效"),
    PAYMENT_METHOD_DISABLED(4006, "支付方式未启用"),
    PAYMENT_METHOD_NOT_SUPPORTED(4007, "不支持的支付方式"),
    ORDER_STATUS_INVALID(4008, "订单状态无效"),
    INSUFFICIENT_BALANCE(4009, "算力不足，请充值！");
    
    private final Integer code;
    private final String message;

    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
