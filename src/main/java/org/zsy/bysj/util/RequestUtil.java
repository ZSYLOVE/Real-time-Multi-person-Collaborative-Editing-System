package org.zsy.bysj.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 请求工具类
 */
public class RequestUtil {

    /**
     * 从请求中获取用户ID
     */
    public static Long getUserId(HttpServletRequest request) {
        Object userIdObj = request.getAttribute("userId");
        if (userIdObj instanceof Long) {
            return (Long) userIdObj;
        }
        return null;
    }

    /**
     * 从请求中获取Token
     */
    public static String getToken(HttpServletRequest request) {
        Object tokenObj = request.getAttribute("token");
        if (tokenObj instanceof String) {
            return (String) tokenObj;
        }
        return null;
    }
}

