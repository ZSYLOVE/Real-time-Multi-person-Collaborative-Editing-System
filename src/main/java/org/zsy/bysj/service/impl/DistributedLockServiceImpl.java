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
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptText(LOCK_ACQUIRE_SCRIPT);
            script.setResultType(Long.class);
            
            Long result = redisTemplate.execute(script, 
                Collections.singletonList(key), 
                value, 
                String.valueOf(unit.toSeconds(timeout)));
            
            return result != null && result == 1;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean releaseLock(String key, String value) {
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptText(LOCK_SCRIPT);
            script.setResultType(Long.class);
            
            Long result = redisTemplate.execute(script, 
                Collections.singletonList(key), 
                value);
            
            return result != null && result == 1;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean tryDocumentLock(Long documentId, Long userId, long timeout, TimeUnit unit) {
        String key = RedisKeyConstant.buildDocumentLockKey(documentId);
        String value = userId.toString() + ":" + System.currentTimeMillis();
        return tryLock(key, value, timeout, unit);
    }

    @Override
    public boolean releaseDocumentLock(Long documentId, Long userId) {
        String key = RedisKeyConstant.buildDocumentLockKey(documentId);
        String value = userId.toString();
        
        // 获取当前锁的值
        Object currentValue = redisTemplate.opsForValue().get(key);
        if (currentValue != null && currentValue.toString().startsWith(value + ":")) {
            return releaseLock(key, currentValue.toString());
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

