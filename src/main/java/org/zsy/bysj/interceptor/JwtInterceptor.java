package org.zsy.bysj.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.zsy.bysj.annotation.PublicEndpoint;
import org.zsy.bysj.util.JwtUtil;

/**
 * JWT拦截器 - 验证Token
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行OPTIONS请求
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }

        // 检查是否是方法处理器
        if (handler instanceof HandlerMethod) {
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            
            // 检查方法或类上是否有@PublicEndpoint注解
            if (handlerMethod.getMethod().isAnnotationPresent(PublicEndpoint.class) ||
                handlerMethod.getBeanType().isAnnotationPresent(PublicEndpoint.class)) {
                return true;  // 公开接口，直接放行
            }
        }

        // 获取Token
        String token = getTokenFromRequest(request);

        if (token == null || token.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未提供认证Token\",\"timestamp\":" + System.currentTimeMillis() + "}");
            return false;
        }

        try {
            // 验证Token
            if (!jwtUtil.validateToken(token)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"message\":\"Token无效或已过期\",\"timestamp\":" + System.currentTimeMillis() + "}");
                return false;
            }

            // 将用户信息存入request，供后续使用
            Long userId = jwtUtil.getUserIdFromToken(token);
            request.setAttribute("userId", userId);
            request.setAttribute("token", token);

            return true;
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"Token验证失败: " + e.getMessage() + "\",\"timestamp\":" + System.currentTimeMillis() + "}");
            return false;
        }
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

        // 从参数中获取（用于WebSocket等场景）
        String token = request.getParameter("token");
        if (token != null && !token.isEmpty()) {
            return token;
        }

        return null;
    }
}

