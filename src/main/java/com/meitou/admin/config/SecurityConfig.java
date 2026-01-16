package com.meitou.admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置类
 * 基础认证配置
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    /**
     * 密码编码器
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * JWT 认证过滤器
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter();
    }
    
    /**
     * 安全过滤器链配置
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults()) // 启用CORS支持，使用CorsConfig中的配置
            .csrf(csrf -> csrf.disable()) // 禁用CSRF（前后端分离项目）
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 无状态会话
            .authorizeHttpRequests(auth -> auth
                // 允许跨域预检请求
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // 公开接口
                .requestMatchers(
                        "/api/app/auth/send-code",
                        "/api/app/auth/login-by-code",
                        "/api/app/auth/login-by-password",
                        "/api/app/user/avatar/**",
                        "/api/app/site/**",
                        "/api/app/recharge/callback/**",
                        "/api/admin/auth/login",
                        "/api/debug/**",
                        "/error"
                ).permitAll()
                // 管理端接口需要 ADMIN 角色
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // 用户端接口需要 USER 角色
                .requestMatchers("/api/app/**").hasRole("USER")
                // 其他接口默认需要认证
                .anyRequest().authenticated()
            )
            // 添加 JWT 过滤器
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}

