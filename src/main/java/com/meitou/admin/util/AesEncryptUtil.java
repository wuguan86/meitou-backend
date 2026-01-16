package com.meitou.admin.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * AES加密工具类
 * 用于加密和解密敏感数据，如API密钥
 */
public class AesEncryptUtil {
    
    /**
     * 加密密钥（16字节，对应AES-128）
     * 注意：生产环境中应该从配置文件或环境变量读取，不要硬编码
     * 这里使用一个固定的密钥，实际项目中应该使用更安全的方式管理密钥
     */
    private static final String SECRET_KEY = "MeitouAdminKey!X"; // 16字节密钥
    
    /**
     * 加密算法
     */
    private static final String ALGORITHM = "AES";
    
    /**
     * 加密模式
     */
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";
    
    /**
     * 加密字符串
     * 
     * @param plainText 明文
     * @return Base64编码的密文
     */
    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        
        try {
            // 创建密钥规范
            SecretKeySpec secretKey = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            
            // 创建加密器
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            
            // 加密
            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            
            // 返回Base64编码的密文
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException("加密失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 解密字符串
     * 
     * @param encryptedText Base64编码的密文
     * @return 明文，如果解密失败则返回null（表示需要重新设置）
     */
    public static String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }
        
        try {
            // 创建密钥规范
            SecretKeySpec secretKey = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            
            // 创建解密器
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            
            // Base64解码
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedText);
            
            // 解密
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            
            // 返回明文
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 如果解密失败，可能是：
            // 1. 使用了旧密钥加密的数据（密钥已更改）
            // 2. 数据本身就不是加密的
            // 3. Base64解码失败
            // 返回null表示解密失败，需要重新设置
            return null;
        }
    }
    
    /**
     * 判断字符串是否为加密的密文
     * 通过尝试解密来判断，如果解密成功且结果与原值不同，则为密文
     * 
     * @param text 待判断的文本
     * @return 是否为密文
     */
    public static boolean isEncrypted(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        
        try {
            // 首先检查是否为Base64格式（加密后的字符串应该是Base64编码）
            // Base64字符串只包含A-Z, a-z, 0-9, +, /, =字符
            if (!text.matches("^[A-Za-z0-9+/=]+$")) {
                return false;
            }
            
            // 尝试Base64解码
            byte[] decodedBytes = Base64.getDecoder().decode(text);
            
            // 如果能成功解码，尝试解密
            SecretKeySpec secretKey = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decryptedBytes = cipher.doFinal(decodedBytes);
            String decrypted = new String(decryptedBytes, StandardCharsets.UTF_8);
            
            // 如果解密后的值与原值不同，说明是密文
            return !decrypted.equals(text);
        } catch (Exception e) {
            // 如果解密失败，说明不是我们加密的密文
            return false;
        }
    }
}

