package org.zsy.bysj.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.zsy.bysj.constant.RedisKeyConstant;
import org.zsy.bysj.service.DistributedLockService;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁服务实现类（基于Redis）
 */
@Service
public class DistributedLockServiceImpl implements DistributedLockService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String LOCK_SCRIPT = 
        "if redis.call('get', KEYS[1]) == ARGV[1] then " +
        "    return redis.call('del', KEYS[1]) " +
        "else " +
        "    return 0 " +
        "end";

    private static final String LOCK_ACQUIRE_SCRIPT =
        "if redis.call('setnx', KEYS[1], ARGV[1]) == 1 then " +
        "    redis.call('expire', KEYS[1], ARGV[2]) " +
        "    return 1 " +
        "else " +
        "    return 0 " +
        "end";

    @Override
    public boolean tryLock(String key, String value, long timeout, TimeUnit unit) {
        try {
            // 使用Spring Data Redis提供的setIfAbsent方法，更可靠
            Boolean result = redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit);
            System.out.println("SETNX结果: key=" + key + ", value=" + value + ", result=" + result);
            return result != null && result;
        } catch (Exception e) {
            System.out.println("获取锁异常: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean releaseLock(String key, String value) {
        try {
            // 获取当前锁的值
            String currentValue = (String) redisTemplate.opsForValue().get(key);
            if (currentValue != null && currentValue.equals(value)) {
                redisTemplate.delete(key);
                System.out.println("锁释放成功: key=" + key);
                return true;
            } else {
                System.out.println("锁释放失败: 值不匹配或锁不存在, current=" + currentValue + ", expected=" + value);
                return false;
            }
        } catch (Exception e) {
            System.out.println("释放锁异常: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean tryDocumentLock(Long documentId, Long userId, long timeout, TimeUnit unit) {
        String key = RedisKeyConstant.buildDocumentLockKey(documentId);
        String value = userId.toString() + ":" + System.currentTimeMillis();

        // 检查锁状态
        Object currentLock = redisTemplate.opsForValue().get(key);
        if (currentLock != null) {
            // 检查锁是否已过期（超过5秒）
            String lockValue = currentLock.toString();
            if (lockValue.contains(":")) {
                try {
                    long lockTime = Long.parseLong(lockValue.split(":")[1]);
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lockTime > 5000) { // 5秒超时
                        System.out.println("检测到过期锁，强制删除: " + lockValue);
                        redisTemplate.delete(key);
                        currentLock = null;
                    }
                } catch (Exception e) {
                    System.out.println("解析锁时间戳失败: " + lockValue);
                }
            }
        }

        System.out.println("尝试获取锁: key=" + key + ", currentLock=" + currentLock + ", userId=" + userId);

        boolean result = tryLock(key, value, timeout, unit);
        System.out.println("锁获取结果: " + result + ", value=" + value);
        return result;
    }

    @Override
    public boolean releaseDocumentLock(Long documentId, Long userId) {
        String key = RedisKeyConstant.buildDocumentLockKey(documentId);
        String value = userId.toString();

        // 获取当前锁的值
        Object currentValue = redisTemplate.opsForValue().get(key);
        System.out.println("释放锁: key=" + key + ", currentValue=" + currentValue + ", userId=" + userId);

        if (currentValue != null) {
            String currentValueStr = currentValue.toString();
            boolean isOwner = currentValueStr.startsWith(value + ":");
            System.out.println("锁拥有者检查: 期望前缀='" + value + ":', 当前值='" + currentValueStr + "', 是否匹配=" + isOwner);

            if (isOwner) {
                boolean result = releaseLock(key, currentValueStr);
                System.out.println("锁释放结果: " + result);
                return result;
            } else {
                System.out.println("锁释放失败：当前用户不是锁的拥有者");
            }
        } else {
            System.out.println("锁释放失败：锁不存在");
        }
        return false;
    }

    @Override
    public Long getNextSequence(Long documentId) {
        String key = RedisKeyConstant.buildDocumentSequenceKey(documentId);
        return redisTemplate.opsForValue().increment(key);
    }

    @Override
    public Long getCurrentSequence(Long documentId) {
        String key = RedisKeyConstant.buildDocumentSequenceKey(documentId);
        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? Long.valueOf(value.toString()) : 0L;
    }

    @Override
    public void queueOperation(Long documentId, Long userId, String operation) {
        String queueKey = RedisKeyConstant.buildDocumentLockQueueKey(documentId);
        String queueItem = userId + ":" + System.currentTimeMillis() + ":" + operation;
        redisTemplate.opsForList().rightPush(queueKey, queueItem);
        // 设置队列过期时间为1小时，避免积压
        redisTemplate.expire(queueKey, 1, TimeUnit.HOURS);
        System.out.println("操作已加入队列: documentId=" + documentId + ", userId=" + userId + ", queueLength=" + getQueueLength(documentId));
    }

    @Override
    public String dequeueOperation(Long documentId) {
        String queueKey = RedisKeyConstant.buildDocumentLockQueueKey(documentId);
        Object item = redisTemplate.opsForList().leftPop(queueKey);
        if (item != null) {
            System.out.println("从队列中取出操作: documentId=" + documentId + ", item=" + item);
        }
        return item != null ? item.toString() : null;
    }

    @Override
    public Long getQueueLength(Long documentId) {
        String queueKey = RedisKeyConstant.buildDocumentLockQueueKey(documentId);
        Long length = redisTemplate.opsForList().size(queueKey);
        return length != null ? length : 0L;
    }

    @Override
    public boolean tryDocumentLockWithQueue(Long documentId, Long userId, long maxWaitTime) {
        String lockKey = RedisKeyConstant.buildDocumentLockKey(documentId);
        String lockValue = userId.toString() + ":" + System.currentTimeMillis();

        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < maxWaitTime) {
            // 尝试获取锁
            Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, 30, TimeUnit.SECONDS);
            if (lockAcquired != null && lockAcquired) {
                System.out.println("获取文档锁成功: documentId=" + documentId + ", userId=" + userId + ", 等待时间=" + (System.currentTimeMillis() - startTime) + "ms");
                return true;
            }

            // 检查锁是否过期，如果过期则强制清理
            Object currentLock = redisTemplate.opsForValue().get(lockKey);
            if (currentLock != null) {
                String lockValueStr = currentLock.toString();
                if (lockValueStr.contains(":")) {
                    try {
                        long lockTime = Long.parseLong(lockValueStr.split(":")[1]);
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lockTime > 30000) { // 30秒超时
                            System.out.println("检测到过期锁，强制删除: " + lockValueStr);
                            redisTemplate.delete(lockKey);
                            // 再次尝试获取锁
                            continue;
                        }
                    } catch (Exception e) {
                        System.out.println("解析锁时间戳失败: " + lockValueStr);
                    }
                }
            }

            // 短暂等待后重试
            try {
                Thread.sleep(50); // 50ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        System.out.println("获取文档锁超时: documentId=" + documentId + ", userId=" + userId + ", 等待时间=" + maxWaitTime + "ms");
        return false;
    }

    @Override
    public boolean releaseDocumentLockAndProcessQueue(Long documentId, Long userId, OperationHandler operationHandler) {
        String lockKey = RedisKeyConstant.buildDocumentLockKey(documentId);
        String expectedValue = userId.toString();

        // 获取当前锁的值
        Object currentValue = redisTemplate.opsForValue().get(lockKey);
        if (currentValue != null) {
            String currentValueStr = currentValue.toString();
            boolean isOwner = currentValueStr.startsWith(expectedValue + ":");

            if (isOwner) {
                // 释放锁
                boolean released = releaseLock(lockKey, currentValueStr);
                if (released) {
                    System.out.println("文档锁释放成功: documentId=" + documentId + ", userId=" + userId);

                    // 检查队列中是否有等待的操作
                    String nextOperation = dequeueOperation(documentId);
                    if (nextOperation != null && operationHandler != null) {
                        try {
                            System.out.println("处理队列中的下一个操作: " + nextOperation);
                            operationHandler.handleOperation(nextOperation);
                        } catch (Exception e) {
                            System.err.println("处理队列操作失败: " + e.getMessage());
                        }
                    }
                }
                return released;
            } else {
                System.out.println("锁释放失败：当前用户不是锁的拥有者");
                return false;
            }
        } else {
            System.out.println("锁释放失败：锁不存在");
            return false;
        }
    }
}

