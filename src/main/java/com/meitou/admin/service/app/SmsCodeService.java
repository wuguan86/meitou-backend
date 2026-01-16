package com.meitou.admin.service.app;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Random;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 短信验证码服务
 * 用于发送、存储和验证手机验证码
 * 支持阿里云短信服务和测试模式
 */
@Slf4j
@Service
public class SmsCodeService {
    
    /**
     * 短信服务类型：aliyun（阿里云短信）或 mock（测试模式，仅打印不发送）
     */
    @Value("${sms.type:mock}")
    private String smsType;
    
    /**
     * 阿里云短信AccessKeyId
     */
    @Value("${sms.aliyun.access-key-id:}")
    private String aliyunAccessKeyId;
    
    /**
     * 阿里云短信AccessKeySecret
     */
    @Value("${sms.aliyun.access-key-secret:}")
    private String aliyunAccessKeySecret;
    
    /**
     * 阿里云短信服务区域
     */
    @Value("${sms.aliyun.region:cn-hangzhou}")
    private String aliyunRegion;
    
    /**
     * 短信签名
     */
    @Value("${sms.aliyun.sign-name:美迹AI}")
    private String signName;
    
    /**
     * 短信模板代码
     */
    @Value("${sms.aliyun.template-code:}")
    private String templateCode;
    
    /**
     * 阿里云短信客户端
     */
    private Client aliyunSmsClient;
    
    /**
     * 验证码存储（手机号 -> 验证码信息）
     * 实际项目中应使用Redis等缓存中间件
     */
    private final Map<String, CodeInfo> codeStorage = new ConcurrentHashMap<>();
    
    /**
     * 验证码有效期（分钟）
     */
    private static final int CODE_EXPIRE_MINUTES = 5;
    
    /**
     * 验证码信息内部类
     */
    private static class CodeInfo {
        String code; // 验证码
        LocalDateTime createTime; // 创建时间
        int tryCount; // 尝试次数
        
        CodeInfo(String code) {
            this.code = code;
            this.createTime = LocalDateTime.now();
            this.tryCount = 0;
        }
    }
    
    /**
     * 初始化阿里云短信客户端
     */
    private void initAliyunSmsClient() {
        if (aliyunSmsClient != null) {
            return;
        }
        
        try {
            Config config = new Config()
                .setAccessKeyId(aliyunAccessKeyId)
                .setAccessKeySecret(aliyunAccessKeySecret)
                .setEndpoint("dysmsapi.aliyuncs.com");
            
            aliyunSmsClient = new Client(config);
            log.info("阿里云短信客户端初始化成功");
        } catch (Exception e) {
            log.error("阿里云短信客户端初始化失败", e);
        }
    }
    
    /**
     * 发送验证码
     * 
     * @param phone 手机号
     * @return 验证码（实际项目中不应该返回，这里仅用于演示）
     */
    public String sendCode(String phone) {
        // 生成6位数字验证码
        String code = generateCode();
        
        // 存储验证码
        codeStorage.put(phone, new CodeInfo(code));
        
        // 根据配置发送短信
        if ("aliyun".equals(smsType)) {
            // 使用阿里云短信服务发送
            sendSmsByAliyun(phone, code);
        } else {
            // 测试模式：仅打印验证码到控制台（不实际发送短信）
            log.info("【测试模式-验证码】手机号：{}，验证码：{}，有效期5分钟", phone, code);
            System.out.println("【测试模式-验证码】手机号：" + phone + "，验证码：" + code + "，有效期5分钟");
        }
        
        return code; // 实际项目中不应该返回验证码
    }
    
    /**
     * 使用阿里云短信服务发送验证码
     * 
     * @param phone 手机号
     * @param code 验证码
     */
    private void sendSmsByAliyun(String phone, String code) {
        try {
            // 初始化客户端
            if (aliyunSmsClient == null) {
                initAliyunSmsClient();
            }
            
            // 检查配置是否完整
            if (!StringUtils.hasText(aliyunAccessKeyId) || !StringUtils.hasText(aliyunAccessKeySecret)) {
                log.warn("阿里云短信配置不完整，使用测试模式");
                log.info("【测试模式-验证码】手机号：{}，验证码：{}，有效期5分钟", phone, code);
                return;
            }
            
            if (!StringUtils.hasText(templateCode)) {
                log.warn("短信模板代码未配置，使用测试模式");
                log.info("【测试模式-验证码】手机号：{}，验证码：{}，有效期5分钟", phone, code);
                return;
            }
            
            // 创建发送请求
            SendSmsRequest request = new SendSmsRequest()
                .setPhoneNumbers(phone)
                .setSignName(signName)
                .setTemplateCode(templateCode)
                .setTemplateParam("{\"code\":\"" + code + "\"}"); // 模板参数，code为验证码变量名
            
            // 发送短信
            SendSmsResponse response = aliyunSmsClient.sendSms(request);
            
            
            // 检查发送结果
            String responseCode = response.getBody().getCode();
            String responseMessage = response.getBody().getMessage();
            
            if ("OK".equals(responseCode)) {
                log.info("验证码发送成功，手机号：{}", phone);
                // 即使发送成功，也在控制台打印验证码（方便开发和测试）
                System.out.println("【验证码】手机号：" + phone + "，验证码：" + code + "，有效期5分钟");
                log.info("【验证码】手机号：{}，验证码：{}，有效期5分钟", phone, code);
            } else {
                log.error("验证码发送失败，手机号：{}，错误码：{}，错误信息：{}", 
                    phone, responseCode, responseMessage);
                // 发送失败时，打印验证码到控制台，方便调试和测试
                System.out.println("【验证码发送失败】手机号：" + phone + "，验证码：" + code + "，有效期5分钟");
                System.out.println("错误码：" + responseCode + "，错误信息：" + responseMessage);
                log.info("【验证码】手机号：{}，验证码：{}，有效期5分钟（短信发送失败：{}）", phone, code, responseMessage);
                // 抛出异常，让调用方知道发送失败
                throw new RuntimeException("短信发送失败：" + responseMessage);
            }
        } catch (Exception e) {
            log.error("发送短信异常，手机号：{}", phone, e);
            // 异常时，打印验证码到控制台，方便调试和测试
            System.out.println("【验证码发送异常】手机号：" + phone + "，验证码：" + code + "，有效期5分钟");
            System.out.println("异常信息：" + e.getMessage());
            log.info("【验证码】手机号：{}，验证码：{}，有效期5分钟（短信发送异常：{}）", phone, code, e.getMessage());
            // 不抛出异常，允许在测试模式下继续使用验证码
        }
    }
    
    /**
     * 验证验证码
     * 
     * @param phone 手机号
     * @param code 验证码
     * @return 是否验证成功
     */
    public boolean verifyCode(String phone, String code) {
        CodeInfo codeInfo = codeStorage.get(phone);
        
        // 验证码不存在
        if (codeInfo == null) {
            return false;
        }
        
        // 验证码已过期
        long minutes = ChronoUnit.MINUTES.between(codeInfo.createTime, LocalDateTime.now());
        if (minutes > CODE_EXPIRE_MINUTES) {
            codeStorage.remove(phone); // 清除过期验证码
            return false;
        }
        
        // 验证码错误
        if (!codeInfo.code.equals(code)) {
            codeInfo.tryCount++;
            // 超过5次尝试失败，清除验证码
            if (codeInfo.tryCount >= 5) {
                codeStorage.remove(phone);
            }
            return false;
        }
        
        // 验证成功，清除验证码（一次性使用）
        codeStorage.remove(phone);
        return true;
    }
    
    /**
     * 生成6位数字验证码
     * 
     * @return 验证码
     */
    private String generateCode() {
        Random random = new Random();
        int code = 100000 + random.nextInt(900000); // 生成100000-999999之间的随机数
        return String.valueOf(code);
    }
    
    /**
     * 检查验证码是否存在且未过期（用于防重复发送）
     * 
     * @param phone 手机号
     * @return 是否已存在有效验证码
     */
    public boolean hasValidCode(String phone) {
        CodeInfo codeInfo = codeStorage.get(phone);
        if (codeInfo == null) {
            return false;
        }
        
        long minutes = ChronoUnit.MINUTES.between(codeInfo.createTime, LocalDateTime.now());
        return minutes <= CODE_EXPIRE_MINUTES;
    }
}

