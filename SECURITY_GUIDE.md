# 安全功能说明文档

## 已实现的安全功能

### 1. JWT认证机制

#### JWT拦截器 (`JwtInterceptor`)
- **功能**：验证请求中的JWT Token
- **验证位置**：在控制器方法执行前
- **Token获取方式**：
  - 优先从 `Authorization: Bearer <token>` Header获取
  - 从请求参数 `token` 获取（用于WebSocket等场景）
- **验证失败处理**：返回401未授权错误

#### JWT认证过滤器 (`JwtAuthenticationFilter`)
- **功能**：在拦截器之前执行，提前验证Token
- **用途**：适用于WebSocket等需要提前验证的场景
- **优先级**：最高优先级执行

#### 公开端点注解 (`@PublicEndpoint`)
- **功能**：标记不需要JWT验证的接口
- **使用场景**：登录、注册等公开接口
- **示例**：
```java
@PublicEndpoint
@PostMapping("/login")
public ResponseEntity<Result<Map<String, Object>>> login(...) {
    // 登录逻辑
}
```

### 2. 权限验证机制

#### 权限拦截器 (`PermissionInterceptor`)
- **功能**：验证用户对文档的访问权限
- **权限级别**：
  - `READ`: 读取权限（最低）
  - `WRITE`: 写入权限
  - `ADMIN`: 管理员权限（最高）
- **权限继承**：
  - ADMIN权限包含WRITE和READ
  - WRITE权限包含READ
  - READ权限最低

#### 权限验证注解 (`@RequirePermission`)
- **功能**：标记需要权限验证的方法
- **使用示例**：
```java
@RequirePermission("READ")
@GetMapping("/{id}")
public ResponseEntity<Result<Document>> getDocument(@PathVariable Long id) {
    // 需要READ权限
}

@RequirePermission("ADMIN")
@DeleteMapping("/{id}")
public ResponseEntity<Result<Void>> deleteDocument(@PathVariable Long id) {
    // 需要ADMIN权限
}
```

### 3. 配置说明

#### WebMvcConfig配置
```java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    // 注册JWT拦截器（优先级1）
    // 注册权限拦截器（优先级2）
}
```

**拦截路径**：
- 拦截所有 `/api/**` 路径
- 排除公开接口：
  - `/api/user/register` - 用户注册
  - `/api/user/login` - 用户登录
  - `/api/export/**` - 导出接口（可根据需要调整）

### 4. 工具类

#### RequestUtil
- **功能**：从请求中获取用户信息
- **方法**：
  - `getUserId(HttpServletRequest request)` - 获取用户ID
  - `getToken(HttpServletRequest request)` - 获取Token

**使用示例**：
```java
@PostMapping
public ResponseEntity<Result<Document>> createDocument(
        @RequestBody Map<String, Object> request,
        HttpServletRequest httpRequest) {
    Long userId = RequestUtil.getUserId(httpRequest);
    // 使用userId
}
```

### 5. 安全流程

#### 请求处理流程
1. **JWT认证过滤器**（优先级最高）
   - 验证Token（如果存在）
   - 将用户信息存入request attribute

2. **JWT拦截器**（优先级1）
   - 检查是否有`@PublicEndpoint`注解
   - 验证Token（公开接口跳过）
   - 将用户ID存入request attribute

3. **权限拦截器**（优先级2）
   - 检查是否有`@RequirePermission`注解
   - 从请求中提取文档ID
   - 验证用户权限

4. **控制器方法执行**
   - 使用`RequestUtil.getUserId()`获取用户ID

### 6. 使用示例

#### 公开接口（不需要认证）
```java
@PublicEndpoint
@PostMapping("/register")
public ResponseEntity<Result<...>> register(...) {
    // 注册逻辑
}
```

#### 需要认证的接口
```java
@PostMapping
public ResponseEntity<Result<Document>> createDocument(
        @RequestBody Map<String, Object> request,
        HttpServletRequest httpRequest) {
    Long userId = RequestUtil.getUserId(httpRequest);
    // 创建文档
}
```

#### 需要权限验证的接口
```java
@RequirePermission("READ")
@GetMapping("/{id}")
public ResponseEntity<Result<Document>> getDocument(@PathVariable Long id) {
    // 获取文档（需要READ权限）
}

@RequirePermission("WRITE")
@PutMapping("/{id}")
public ResponseEntity<Result<Document>> updateDocument(...) {
    // 更新文档（需要WRITE权限）
}

@RequirePermission("ADMIN")
@DeleteMapping("/{id}")
public ResponseEntity<Result<Void>> deleteDocument(@PathVariable Long id) {
    // 删除文档（需要ADMIN权限）
}
```

### 7. 错误响应

#### 401未授权
```json
{
  "code": 401,
  "message": "未提供认证Token",
  "timestamp": 1234567890
}
```

#### 403无权限
```json
{
  "code": 403,
  "message": "无权限访问该资源",
  "timestamp": 1234567890
}
```

### 8. 前端调用示例

#### 带Token的请求
```javascript
// 方式1：使用Authorization Header
fetch('/api/documents/1', {
    headers: {
        'Authorization': 'Bearer ' + token
    }
})

// 方式2：使用token参数
fetch('/api/documents/1?token=' + token)
```

### 9. 安全建议

1. **Token安全**
   - Token应存储在安全的地方（如HttpOnly Cookie或安全存储）
   - 定期刷新Token
   - Token过期时间设置合理（当前24小时）

2. **权限管理**
   - 文档创建者自动拥有ADMIN权限
   - 合理分配权限级别
   - 定期检查权限配置

3. **HTTPS**
   - 生产环境必须使用HTTPS
   - 防止Token被窃取

4. **日志记录**
   - 记录认证失败事件
   - 记录权限验证失败事件
   - 监控异常访问

### 10. 已保护的接口

- ✅ 文档创建 - 需要JWT认证
- ✅ 文档查询 - 需要JWT认证 + READ权限
- ✅ 文档删除 - 需要JWT认证 + ADMIN权限
- ✅ 评论创建 - 需要JWT认证
- ✅ 权限管理 - 需要JWT认证

### 11. 公开接口

- ✅ 用户注册 - `@PublicEndpoint`
- ✅ 用户登录 - `@PublicEndpoint`

## 总结

安全功能已完整实现，包括：
- ✅ JWT Token认证
- ✅ 基于注解的权限验证
- ✅ 公开端点支持
- ✅ 统一的错误响应
- ✅ 灵活的权限级别

系统现已具备完整的安全防护机制！

