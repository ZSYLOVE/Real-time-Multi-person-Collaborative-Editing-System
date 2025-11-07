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
}

