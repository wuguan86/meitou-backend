package com.meitou.admin.config;

import com.meitou.admin.util.TokenUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.GenericFilterBean;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.DispatcherType;

/**
 * JWT 认证过滤器
 * 拦截请求并验证 JWT Token
 */
@Slf4j
public class JwtAuthenticationFilter extends GenericFilterBean {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // 获取 Authorization 头
        String authHeader = httpRequest.getHeader("Authorization");
        String requestURI = httpRequest.getRequestURI();

        // 放行 OPTIONS 请求
        if ("OPTIONS".equals(httpRequest.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        String token = authHeader.substring(7);
        Long userId = TokenUtil.getUserIdFromToken(token);
        String type = TokenUtil.getUserTypeFromToken(token);
        
        // 如果提供了 Token 但无效（过期或格式错误），直接返回 401
        if (userId == null) {
            log.warn("Invalid token for request: {}", requestURI);
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.setContentType("application/json;charset=UTF-8");
            httpResponse.getWriter().write("{\"code\": 401, \"msg\": \"Invalid Token\", \"data\": null}");
            return;
        }

        // 无论 SecurityContext 是否已有认证，我们都重新设置，确保 Async 场景下有上下文
        // 注意：SecurityContextHolder 默认是 ThreadLocal 的
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            // Token 有效，设置 SecurityContext
            
            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            if (type != null) {
                // 将 type 转换为角色，例如 user -> ROLE_USER, admin -> ROLE_ADMIN
                authorities.add(new SimpleGrantedAuthority("ROLE_" + type.toUpperCase()));
            } else {
                // 默认为 USER 角色
                authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
            }
            
            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    userId, null, authorities);
            
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }
        
        filterChain.doFilter(request, response);
    }
}
