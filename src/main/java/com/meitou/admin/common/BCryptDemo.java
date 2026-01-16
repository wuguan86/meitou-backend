package com.meitou.admin.common;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * @ClassName BCryptDemo
 * @Description
 * @Author 你的名字
 * @Date 2025/12/20 9:30
 * @Version 1.0
 */
public class BCryptDemo {

    public static void main(String[] args) {
        // 1. 创建 BCryptPasswordEncoder 实例（默认工作因子10）
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

        // 2. 待加密的字符串
        String rawPassword = "123456";

        // 3. 第一次加密：得到第一个结果
        String encodedPassword1 = passwordEncoder.encode(rawPassword);
        System.out.println("第一次加密结果：" + encodedPassword1);

        // 4. 第二次加密：得到第二个不同的结果
        String encodedPassword2 = passwordEncoder.encode(rawPassword);
        System.out.println("第二次加密结果：" + encodedPassword2);

        // 5. 验证：虽然加密结果不同，但都能匹配原始密码
        boolean isMatch1 = passwordEncoder.matches(rawPassword, encodedPassword1);
        boolean isMatch2 = passwordEncoder.matches(rawPassword, encodedPassword2);
        System.out.println("原始密码与第一次加密结果匹配：" + isMatch1); // true
        System.out.println("原始密码与第二次加密结果匹配：" + isMatch2);
    }
}
