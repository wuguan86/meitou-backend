package com.meitou.admin.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Token工具类
 * 用于解析和验证Token (JWT)
 */
@Slf4j
@Component
public class TokenUtil {

    // 随机生成的 SecretKey (HS256 要求至少 32 字节)
    private static final String SECRET_STRING = "MeitouProjectRandomSecretKeyForJwtTokenGeneration2024";
    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(SECRET_STRING.getBytes(StandardCharsets.UTF_8));
    
    // 过期时间：24小时
    private static final long EXPIRATION_TIME = 24 * 60 * 60 * 1000;

    /**
     * 生成 JWT Token
     * 
     * @param userId 用户ID
     * @param type 用户类型 (user/admin)
     * @return Token字符串
     */
    public static String generateToken(Long userId, String type) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("type", type)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(SECRET_KEY)
                .compact();
    }
    
    /**
     * 生成 JWT Token (默认类型为 user)
     * 
     * @param userId 用户ID
     * @return Token字符串
     */
    public static String generateToken(Long userId) {
        return generateToken(userId, "user");
    }
    
    /**
     * 从Token中提取用户ID
     * 
     * @param token Token字符串
     * @return 用户ID，如果解析失败返回null
     */
    public static Long getUserIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        if (claims == null) {
            return null;
        }
        try {
            return Long.parseLong(claims.getSubject());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 从Token中提取用户类型
     * 
     * @param token Token字符串
     * @return 用户类型，如果解析失败返回null
     */
    public static String getUserTypeFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        if (claims == null) {
            return null;
        }
        return claims.get("type", String.class);
    }

    private static Claims getClaimsFromToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        
        // 移除Bearer前缀
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        
        try {
            return Jwts.parser()
                    .verifyWith(SECRET_KEY)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.error("Token parsing failed: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 验证Token格式是否有效
     * 
     * @param token Token字符串
     * @return 是否有效
     */
    public static boolean isValidToken(String token) {
        return getUserIdFromToken(token) != null;
    }
}

