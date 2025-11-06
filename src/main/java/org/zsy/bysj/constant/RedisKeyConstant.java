package org.zsy.bysj.constant;

/**
 * Redis Key常量类
 * 统一管理所有Redis key前缀，避免重复定义
 */
public class RedisKeyConstant {

    /**
     * 文档缓存key前缀
     */
    public static final String DOCUMENT_CACHE_KEY = "document:";

    /**
     * 在线用户列表key前缀
     */
    public static final String ONLINE_USERS_KEY = "online_users:";

    /**
     * 用户光标位置key前缀
     */
    public static final String USER_CURSOR_KEY = "user_cursor:";

    /**
     * 构建文档缓存key
     */
    public static String buildDocumentCacheKey(Long documentId) {
        return DOCUMENT_CACHE_KEY + documentId;
    }

    /**
     * 构建在线用户列表key
     */
    public static String buildOnlineUsersKey(Long documentId) {
        return ONLINE_USERS_KEY + documentId;
    }

    /**
     * 构建用户光标位置key
     */
    public static String buildUserCursorKey(Long documentId, Long userId) {
        return USER_CURSOR_KEY + documentId + ":" + userId;
    }
}

