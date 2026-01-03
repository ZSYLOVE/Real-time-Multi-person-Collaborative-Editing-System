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
     * 用户登录占用锁 key 前缀（用于限制同账号同时登录）
     */
    public static final String USER_LOGIN_LOCK_KEY = "user_login_lock:";


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

    /**
     * 构建用户登录占用锁 key
     */
    public static String buildUserLoginLockKey(Long userId) {
        return USER_LOGIN_LOCK_KEY + userId;
    }

    /**
     * 离线操作队列key前缀
     */
    public static final String OFFLINE_OPERATIONS_KEY = "offline_operations:";

    /**
     * 文档操作锁key前缀
     */
    public static final String DOCUMENT_LOCK_KEY = "document_lock:";

    /**
     * 文档操作序列号key前缀
     */
    public static final String DOCUMENT_SEQUENCE_KEY = "document_sequence:";

    /**
     * 用户离线状态key前缀
     */
    public static final String USER_OFFLINE_KEY = "user_offline:";

    /**
     * 文档锁队列key前缀
     */
    public static final String DOCUMENT_LOCK_QUEUE_KEY = "document_lock_queue:";

    /**
     * 构建离线操作队列key
     */
    public static String buildOfflineOperationsKey(Long documentId, Long userId) {
        return OFFLINE_OPERATIONS_KEY + documentId + ":" + userId;
    }

    /**
     * 构建文档锁key
     */
    public static String buildDocumentLockKey(Long documentId) {
        return DOCUMENT_LOCK_KEY + documentId;
    }

    /**
     * 构建文档操作序列号key
     */
    public static String buildDocumentSequenceKey(Long documentId) {
        return DOCUMENT_SEQUENCE_KEY + documentId;
    }

    /**
     * 构建用户离线状态key
     */
    public static String buildUserOfflineKey(Long documentId, Long userId) {
        return USER_OFFLINE_KEY + documentId + ":" + userId;
    }

    /**
     * 构建文档锁队列key
     */
    public static String buildDocumentLockQueueKey(Long documentId) {
        return DOCUMENT_LOCK_QUEUE_KEY + documentId;
    }
}

