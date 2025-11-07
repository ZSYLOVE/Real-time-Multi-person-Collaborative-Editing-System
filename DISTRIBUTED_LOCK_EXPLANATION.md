# 分布式锁的作用与重要性

## 📖 什么是分布式锁？

分布式锁是一种在分布式系统中，用于控制多个进程/线程对共享资源访问的机制。在我们的协同编辑系统中，分布式锁用于确保**同一文档的操作是原子性的**。

---

## 🎯 在协同编辑系统中的核心作用

### 1. **防止并发操作冲突**

#### 问题场景（没有分布式锁）：
```
时间线：
T1: 用户A在位置10插入"Hello"
T2: 用户B在位置10插入"World"  (同时发生)
T3: 两个操作同时写入数据库

结果：可能出现数据不一致
- 数据库可能只保存了其中一个操作
- 或者两个操作以错误的顺序保存
- 导致文档内容错乱
```

#### 解决方案（使用分布式锁）：
```
时间线：
T1: 用户A获取锁 → 插入"Hello" → 释放锁
T2: 用户B等待锁 → 获取锁 → 插入"World" → 释放锁

结果：操作按顺序执行，保证一致性
```

### 2. **保证操作序列号的一致性**

在我们的系统中，每个操作都有一个**序列号**（sequence），用于：
- 确定操作的执行顺序
- 解决操作冲突
- 保证最终一致性

**没有分布式锁的问题**：
```java
// 用户A和用户B同时操作
用户A: sequence = getNextSequence()  // 可能得到 100
用户B: sequence = getNextSequence()  // 也可能得到 100 (并发问题！)

结果：两个操作有相同的序列号，无法确定执行顺序
```

**使用分布式锁后**：
```java
// 用户A先获取锁
用户A: 获取锁 → sequence = 100 → 执行操作 → 释放锁
用户B: 等待锁 → 获取锁 → sequence = 101 → 执行操作 → 释放锁

结果：每个操作都有唯一的序列号，顺序明确
```

### 3. **支持多服务器部署**

当系统部署在多台服务器上时（负载均衡），分布式锁确保：
- **跨服务器的操作同步**
- **避免重复处理**
- **保证数据一致性**

#### 场景示例：
```
服务器架构：
  ┌─────────┐    ┌─────────┐    ┌─────────┐
  │ Server1 │    │ Server2 │    │ Server3 │
  └────┬────┘    └────┬────┘    └────┬────┘
       │              │              │
       └──────────┬───┴──────────────┘
                  │
            ┌─────▼─────┐
            │   Redis   │  (分布式锁存储)
            └───────────┘

用户A连接到Server1，用户B连接到Server2
两个用户同时编辑同一文档

没有分布式锁：
- Server1和Server2可能同时处理操作
- 导致数据冲突

有分布式锁：
- 只有一个服务器能获取锁
- 操作按顺序执行
```

---

## 🔧 在我们的系统中如何使用

### 代码示例（`CollaborationServiceImpl`）：

```java
@Override
public void handleOperation(WebSocketMessage message) {
    Long documentId = message.getDocumentId();
    Long userId = message.getUserId();
    
    // 1. 尝试获取分布式锁（最多等待100ms）
    boolean lockAcquired = distributedLockService.tryDocumentLock(
        documentId, userId, 100, TimeUnit.MILLISECONDS
    );
    
    if (!lockAcquired) {
        // 获取锁失败，保存为离线操作（稍后重试）
        offlineSyncService.saveOfflineOperation(documentId, userId, opDTO);
        return;
    }
    
    try {
        // 2. 获取操作序列号（保证唯一性和顺序）
        Long sequence = distributedLockService.getNextSequence(documentId);
        
        // 3. 执行操作（此时是原子性的）
        Document document = documentService.applyOperation(documentId, operation, userId);
        
        // 4. 广播操作结果
        broadcastToDocument(documentId, response, userId);
        
    } finally {
        // 5. 释放锁（确保一定会释放）
        distributedLockService.releaseDocumentLock(documentId, userId);
    }
}
```

---

## 🛡️ 分布式锁的关键特性

### 1. **互斥性（Mutual Exclusion）**
- 同一时刻只有一个用户/服务器能获取锁
- 其他用户必须等待

### 2. **可重入性（Reentrant）**
- 同一用户可以在持有锁的情况下再次获取（如果需要）
- 我们的实现支持通过userId识别

### 3. **超时机制（Timeout）**
- 锁有自动过期时间，防止死锁
- 如果持有锁的进程崩溃，锁会自动释放

### 4. **原子性（Atomic）**
- 使用Redis Lua脚本保证获取锁和设置过期时间的原子性
- 防止竞态条件

---

## 📊 实际效果对比

### 没有分布式锁：
| 问题 | 影响 |
|------|------|
| 并发操作冲突 | 文档内容错乱 |
| 序列号重复 | 无法确定操作顺序 |
| 多服务器冲突 | 数据不一致 |
| 操作丢失 | 用户编辑丢失 |

### 有分布式锁：
| 优势 | 效果 |
|------|------|
| 操作原子性 | 每个操作完整执行 |
| 序列号唯一 | 操作顺序明确 |
| 跨服务器同步 | 多服务器部署安全 |
| 数据一致性 | 文档内容始终正确 |

---

## 🔍 技术实现细节

### 基于Redis的分布式锁

我们使用Redis实现分布式锁，原因：
1. **高性能**：Redis是内存数据库，操作速度快
2. **原子性**：Redis命令是原子性的
3. **持久化**：支持数据持久化（可选）
4. **分布式**：Redis本身支持集群部署

### 实现原理：

```java
// 1. 获取锁（使用Lua脚本保证原子性）
LOCK_ACQUIRE_SCRIPT = 
    "if redis.call('setnx', KEYS[1], ARGV[1]) == 1 then " +
    "    redis.call('expire', KEYS[1], ARGV[2]) " +
    "    return 1 " +
    "else " +
    "    return 0 " +
    "end";

// 2. 释放锁（只有锁的持有者才能释放）
LOCK_SCRIPT = 
    "if redis.call('get', KEYS[1]) == ARGV[1] then " +
    "    return redis.call('del', KEYS[1]) " +
    "else " +
    "    return 0 " +
    "end";
```

### 锁的Key设计：
```java
// 文档锁：document_lock:{documentId}
// 例如：document_lock:123

// 操作序列号：document_sequence:{documentId}
// 例如：document_sequence:123
```

---

## 💡 总结

分布式锁在我们的协同编辑系统中起到**关键作用**：

1. ✅ **保证操作原子性** - 确保每个编辑操作完整执行
2. ✅ **维护操作顺序** - 通过序列号保证操作顺序
3. ✅ **支持分布式部署** - 多服务器环境下保证一致性
4. ✅ **防止数据冲突** - 避免并发操作导致的数据错乱
5. ✅ **提高系统可靠性** - 确保用户编辑不会丢失或错乱

**简单来说**：分布式锁就像给文档编辑操作加了一个"排队机制"，确保同一时刻只有一个操作在执行，从而保证数据的一致性和正确性。

---

## 🚀 性能考虑

- **锁等待时间**：我们设置最多等待100ms，避免长时间阻塞
- **锁超时时间**：自动过期机制防止死锁
- **失败处理**：获取锁失败时，操作保存到离线队列，稍后重试
- **性能影响**：Redis操作非常快，对系统性能影响极小

