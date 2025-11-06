package org.zsy.bysj.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.zsy.bysj.annotation.RequirePermission;
import org.zsy.bysj.service.PermissionService;

import java.lang.reflect.Method;

/**
 * 权限验证拦截器
 */
@Component
public class PermissionInterceptor implements HandlerInterceptor {

    @Autowired
    private PermissionService permissionService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 只处理方法处理器
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        Method method = handlerMethod.getMethod();

        // 检查方法是否有权限注解
        RequirePermission requirePermission = method.getAnnotation(RequirePermission.class);
        if (requirePermission == null) {
            // 检查类上是否有权限注解
            requirePermission = handlerMethod.getBeanType().getAnnotation(RequirePermission.class);
        }

        // 如果没有权限注解，直接放行
        if (requirePermission == null) {
            return true;
        }

        // 获取用户ID
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未认证\",\"timestamp\":" + System.currentTimeMillis() + "}");
            return false;
        }

        // 获取文档ID（从路径参数或请求参数中）
        Long documentId = getDocumentIdFromRequest(request, requirePermission);

        if (documentId == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":400,\"message\":\"缺少文档ID参数\",\"timestamp\":" + System.currentTimeMillis() + "}");
            return false;
        }

        // 验证权限
        String permissionType = requirePermission.value();
        boolean hasPermission = permissionService.hasPermission(documentId, userId, permissionType);

        if (!hasPermission) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"无权限访问该资源\",\"timestamp\":" + System.currentTimeMillis() + "}");
            return false;
        }

        return true;
    }

    /**
     * 从请求中获取文档ID
     */
    private Long getDocumentIdFromRequest(HttpServletRequest request, RequirePermission annotation) {
        // 从路径变量获取（如 /api/documents/{id}）
        String requestURI = request.getRequestURI();
        String[] pathParts = requestURI.split("/");
        for (String part : pathParts) {
            if (part.matches("\\d+")) {
                try {
                    return Long.parseLong(part);
                } catch (NumberFormatException e) {
                    // 继续查找
                }
            }
        }

        // 从请求参数获取
        String documentIdParam = request.getParameter("documentId");
        if (documentIdParam != null && documentIdParam.matches("\\d+")) {
            return Long.parseLong(documentIdParam);
        }

        // 从请求参数获取id
        String idParam = request.getParameter("id");
        if (idParam != null && idParam.matches("\\d+")) {
            return Long.parseLong(idParam);
        }

        return null;
    }
}

