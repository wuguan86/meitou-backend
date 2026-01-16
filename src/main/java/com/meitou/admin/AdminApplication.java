package com.meitou.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Meitou 平台管理系统启动类
 * 
 * @author Meitou Team
 * @version 1.0.0
 * 
 * 注意：移除了 @MapperScan 注解，改为在每个 Mapper 接口上使用 @Mapper 注解
 * 这样可以避免 Spring Boot 3.x 与 MyBatis Plus 的兼容性问题
 */
@EnableScheduling
@SpringBootApplication
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}

