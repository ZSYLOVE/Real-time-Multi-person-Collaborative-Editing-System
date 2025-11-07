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
}

