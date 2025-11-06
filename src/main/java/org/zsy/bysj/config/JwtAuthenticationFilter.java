package org.zsy.bysj.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.zsy.bysj.util.JwtUtil;

import java.io.IOException;

/**
 * JWT认证过滤器
 * 在拦截器之前执行，用于WebSocket等场景
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        // 放行OPTIONS请求
        if ("OPTIONS".equals(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // 获取Token
        String token = getTokenFromRequest(request);

        if (token != null && !token.isEmpty()) {
            try {
                // 验证Token
                if (jwtUtil.validateToken(token)) {
                    // 将用户信息存入request
                    Long userId = jwtUtil.getUserIdFromToken(token);
                    request.setAttribute("userId", userId);
                    request.setAttribute("token", token);
                }
            } catch (Exception e) {
                // Token验证失败，但不在这里拦截，交给拦截器处理
                logger.debug("Token验证失败: " + e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 从请求中获取Token
     */
    private String getTokenFromRequest(HttpServletRequest request) {
        // 优先从Header中获取
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }

        // 从参数中获取
        String token = request.getParameter("token");
        if (token != null && !token.isEmpty()) {
            return token;
        }

        return null;
    }
}

