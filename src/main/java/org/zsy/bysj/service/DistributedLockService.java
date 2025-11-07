package org.zsy.bysj.service;

import java.util.concurrent.TimeUnit;

/**
 * 分布式锁服务接口
 */
public interface DistributedLockService {
    
    /**
     * 尝试获取锁
     * @param key 锁的key
     * @param value 锁的值（用于标识持有者）
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return 是否获取成功
     */
    boolean tryLock(String key, String value, long timeout, TimeUnit unit);
    
    /**
     * 释放锁
     * @param key 锁的key
     * @param value 锁的值（必须匹配才能释放）
     * @return 是否释放成功
     */
    boolean releaseLock(String key, String value);
    
    /**
     * 获取文档操作锁
     */
    boolean tryDocumentLock(Long documentId, Long userId, long timeout, TimeUnit unit);
    
    /**
     * 释放文档操作锁
     */
    boolean releaseDocumentLock(Long documentId, Long userId);
    
    /**
     * 获取下一个操作序列号
     */
    Long getNextSequence(Long documentId);
    
    /**
     * 获取当前操作序列号
     */
    Long getCurrentSequence(Long documentId);

    /**
     * 将操作加入锁队列等待
     * @param documentId 文档ID
     * @param userId 用户ID
     * @param operation 操作数据
     */
    void queueOperation(Long documentId, Long userId, String operation);

    /**
     * 从锁队列中获取下一个等待的操作
     * @param documentId 文档ID
     * @return 操作数据，如果队列为空则返回null
     */
    String dequeueOperation(Long documentId);

    /**
     * 获取锁队列长度
     * @param documentId 文档ID
     * @return 队列长度
     */
    Long getQueueLength(Long documentId);

    /**
     * 尝试获取文档锁，支持队列等待
     * @param documentId 文档ID
     * @param userId 用户ID
     * @param maxWaitTime 最大等待时间（毫秒）
     * @return 是否获取成功
     */
    boolean tryDocumentLockWithQueue(Long documentId, Long userId, long maxWaitTime);

    /**
     * 释放文档锁并处理队列中的下一个操作
     * @param documentId 文档ID
     * @param userId 用户ID
     * @param operationHandler 操作处理器回调
     * @return 是否释放成功
     */
    boolean releaseDocumentLockAndProcessQueue(Long documentId, Long userId, OperationHandler operationHandler);

    /**
     * 操作处理器接口
     */
    interface OperationHandler {
        void handleOperation(String operationData);
    }
}

