# JWT认证过滤器 vs JWT拦截器 - 区别与作用

## 执行顺序对比

```
请求 → Filter（过滤器） → Interceptor（拦截器） → Controller（控制器）
       ↓                   ↓
   JwtAuthenticationFilter  JwtInterceptor
   (优先级最高)              (优先级1)
```

## 1. JWT认证过滤器 (JwtAuthenticationFilter)

### 特点
- **执行时机**：在Servlet容器层面，**最早执行**
- **作用范围**：可以处理所有类型的请求（包括静态资源、WebSocket握手等）
- **拦截能力**：**不拦截**，只是提取和验证Token，然后放行
- **优先级**：`Ordered.HIGHEST_PRECEDENCE`（最高优先级）

### 主要作用

#### 1. **提前提取Token**
```java
// 在拦截器之前就提取Token
String token = getTokenFromRequest(request);
if (token != null && jwtUtil.validateToken(token)) {
    // 将用户信息存入request，供后续使用
    request.setAttribute("userId", userId);
    request.setAttribute("token", token);
}
```

#### 2. **支持WebSocket场景**
- WebSocket握手请求需要提前验证Token
- 拦截器可能无法完全覆盖WebSocket的场景
- 过滤器可以在握手阶段就验证Token

#### 3. **统一预处理**
- 所有请求都会经过过滤器
- 提前准备好用户信息，减少重复提取

## 2. JWT拦截器 (JwtInterceptor)

### 特点
- **执行时机**：在Spring MVC框架层面，**在过滤器之后**
- **作用范围**：只能拦截Controller的请求
- **拦截能力**：**可以拦截**，验证失败直接返回401错误
- **优先级**：order(1)

### 主要作用

#### 1. **强制验证Token**
```java
// 如果没有Token或Token无效，直接拦截
if (token == null || !jwtUtil.validateToken(token)) {
    response.setStatus(401);
    return false; // 拦截请求
}
```

#### 2. **支持公开端点注解**
```java
// 检查@PublicEndpoint注解，公开接口跳过验证
if (handlerMethod.getMethod().isAnnotationPresent(PublicEndpoint.class)) {
    return true; // 放行
}
```

#### 3. **提供详细的错误响应**
```java
// 返回标准的JSON错误响应
response.getWriter().write("{\"code\":401,\"message\":\"Token无效\"}");
```

## 为什么需要两个？

### 场景1：WebSocket握手
```
WebSocket握手请求 → Filter提取Token → 验证 → 存入request
                  ↓
                WebSocket握手成功
                  ↓
                拦截器可能无法拦截WebSocket请求
```

### 场景2：静态资源（可选）
```
静态资源请求 → Filter可以处理 → 如果不需要认证，可以放行
```

### 场景3：双重保障
```
请求 → Filter（提前验证，不拦截） → Interceptor（强制验证，拦截）
      ↓                              ↓
   提取Token                       如果没有Token，拦截
   存入request                     返回401错误
```

## 实际使用建议

### 方案1：简化版（推荐）
**如果不需要WebSocket提前验证，可以只使用拦截器：**

```java
// 只保留JwtInterceptor，移除JwtAuthenticationFilter
// 拦截器已经足够处理大部分场景
```

### 方案2：完整版（当前实现）
**如果需要WebSocket或特殊场景支持，保留过滤器：**

```java
// Filter：提前提取Token，不拦截
// Interceptor：强制验证，拦截无效请求
// 两者配合，提供完整的认证机制
```

## 当前项目的实际效果

### 请求流程
```
1. 请求到达
   ↓
2. JwtAuthenticationFilter（过滤器）
   - 提取Token（如果有）
   - 验证Token（如果有效，存入request）
   - 放行请求（不拦截）
   ↓
3. JwtInterceptor（拦截器）
   - 检查@PublicEndpoint注解
   - 如果没有注解，验证Token
   - 如果Token无效，拦截并返回401
   ↓
4. PermissionInterceptor（权限拦截器）
   - 检查@RequirePermission注解
   - 验证权限
   ↓
5. Controller方法执行
```

## 总结

### JWT认证过滤器的作用：
1. ✅ **提前提取Token** - 在拦截器之前就准备好用户信息
2. ✅ **支持WebSocket** - 可以在握手阶段验证Token
3. ✅ **统一预处理** - 所有请求统一处理Token提取
4. ✅ **不拦截请求** - 只是准备数据，交给拦截器决定是否拦截

### 如果不需要WebSocket支持：
可以**移除过滤器**，只使用拦截器即可，代码更简洁。

### 如果保留过滤器：
可以**优化过滤器**，让它只做提取不做验证，验证交给拦截器统一处理。

