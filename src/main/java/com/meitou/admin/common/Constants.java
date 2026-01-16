package com.meitou.admin.common;

/**
 * 常量定义类
 */
public class Constants {
    
    /**
     * 用户状态
     */
    public static final String USER_STATUS_ACTIVE = "active";
    public static final String USER_STATUS_SUSPENDED = "suspended";
    
    /**
     * 站点分类
     */
    public static final String CATEGORY_MEDICAL = "medical";
    public static final String CATEGORY_ECOMMERCE = "ecommerce";
    public static final String CATEGORY_LIFE = "life";
    
    /**
     * 资产类型
     */
    public static final String ASSET_TYPE_IMAGE = "image";
    public static final String ASSET_TYPE_VIDEO = "video";
    public static final String ASSET_TYPE_AUDIO = "audio";
    
    /**
     * 资产状态
     */
    public static final String ASSET_STATUS_PUBLISHED = "published";
    public static final String ASSET_STATUS_HIDDEN = "hidden";
    
    /**
     * 生成记录状态
     */
    public static final String GEN_STATUS_SUCCESS = "success";
    public static final String GEN_STATUS_FAILED = "failed";
    public static final String GEN_STATUS_PROCESSING = "processing";
    
    /**
     * 后台账号角色
     */
    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_OPERATOR = "operator";
    public static final String ROLE_VIEWER = "viewer";
    
    /**
     * 后台账号状态
     */
    public static final String ACCOUNT_STATUS_ACTIVE = "active";
    public static final String ACCOUNT_STATUS_LOCKED = "locked";
    
    /**
     * JWT Token 相关
     */
    public static final String TOKEN_HEADER = "Authorization";
    public static final String TOKEN_PREFIX = "Bearer ";
    public static final long TOKEN_EXPIRE_TIME = 24 * 60 * 60 * 1000; // 24小时
    
    /**
     * 订单状态
     */
    public static final String ORDER_STATUS_PENDING = "pending"; // 待支付
    public static final String ORDER_STATUS_PAYING = "paying"; // 支付中
    public static final String ORDER_STATUS_PAID = "paid"; // 已支付
    public static final String ORDER_STATUS_CANCELLED = "cancelled"; // 已取消
    public static final String ORDER_STATUS_REFUNDED = "refunded"; // 已退款
    public static final String ORDER_STATUS_FAILED = "failed"; // 支付失败
}

