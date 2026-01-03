package org.zsy.bysj.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.zsy.bysj.annotation.PublicEndpoint;
import org.zsy.bysj.constant.RedisKeyConstant;
import org.zsy.bysj.util.JwtUtil;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * JWT拦截器 - 验证Token
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 主流做法：滑动过期（sliding expiration）
     * - 只要用户持续有请求，就延长“在线锁”的TTL
     * - 一段时间无请求（如60分钟）自动释放锁，允许重新登录
     */
    private static final long ONLINE_LOCK_TTL_MINUTES = 60;

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

            // ===== 登录占用锁续期（滑动过期）=====
            // 只要用户持续访问接口，就刷新 user_login_lock:{userId} 的 TTL。
            // 主流网站常见策略：60 分钟无操作自动下线。
            try {
                String lockKey = RedisKeyConstant.buildUserLoginLockKey(userId);
                // 如果锁存在则续期；不存在则不创建（避免“凭token复活锁”带来混乱）
                Boolean hasKey = stringRedisTemplate.hasKey(lockKey);
                if (Boolean.TRUE.equals(hasKey)) {
                    stringRedisTemplate.expire(lockKey, ONLINE_LOCK_TTL_MINUTES, TimeUnit.MINUTES);
                }
            } catch (Exception ex) {
                // Redis异常不影响正常请求（否则会导致大量误401）
                System.out.println("[LOGIN_LOCK] 续期失败: " + ex.getMessage());
            }

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

