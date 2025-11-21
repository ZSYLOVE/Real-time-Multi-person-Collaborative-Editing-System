# 前后端API接口映射对照表

## ✅ 已匹配的接口

### 用户相关
| 功能 | 前端调用 | 后端接口 | 状态 |
|------|---------|---------|------|
| 用户注册 | `POST /api/user/register` | `POST /api/user/register` | ✅ 匹配 |
| 用户登录 | `POST /api/user/login` | `POST /api/user/login` | ✅ 匹配 |
| 获取用户信息 | `GET /api/user/info` | `GET /api/user/{id}` | ⚠️ 需要修复 |

### 文档相关
| 功能 | 前端调用 | 后端接口 | 状态 |
|------|---------|---------|------|
| 创建文档 | `POST /api/documents` | `POST /api/documents` | ✅ 匹配 |
| 获取文档 | `GET /api/documents/{id}` | `GET /api/documents/{id}` | ✅ 匹配 |
| 获取用户文档列表 | `GET /api/documents/user/{userId}` | `GET /api/documents/user/{userId}` | ✅ 匹配 |
| 删除文档 | `DELETE /api/documents/{id}?userId={userId}` | `DELETE /api/documents/{id}?userId={userId}` | ✅ 已修复 |

### 评论相关
| 功能 | 前端调用 | 后端接口 | 状态 |
|------|---------|---------|------|
| 添加评论 | `POST /api/comments` | `POST /api/comments` | ✅ 已修复（需传递userId） |
| 获取文档评论 | `GET /api/comments/document/{documentId}` | `GET /api/comments/document/{documentId}` | ✅ 匹配 |
| 删除评论 | `DELETE /api/comments/{id}` | `DELETE /api/comments/{id}` | ✅ 匹配 |

### 权限相关
| 功能 | 前端调用 | 后端接口 | 状态 |
|------|---------|---------|------|
| 添加权限 | `POST /api/permissions` | `POST /api/permissions` | ✅ 匹配 |
| 更新权限 | `PUT /api/permissions` | `PUT /api/permissions` | ✅ 匹配 |
| 删除权限 | `DELETE /api/permissions?documentId={id}&userId={id}` | `DELETE /api/permissions?documentId={id}&userId={id}` | ✅ 匹配 |
| 获取文档权限 | `GET /api/permissions/document/{documentId}` | `GET /api/permissions/document/{documentId}` | ✅ 匹配 |
| 检查权限 | `GET /api/permissions/check?documentId={id}&userId={id}&permissionType={type}` | `GET /api/permissions/check?documentId={id}&userId={id}&permissionType={type}` | ✅ 已修复 |

### 版本相关
| 功能 | 前端调用 | 后端接口 | 状态 |
|------|---------|---------|------|
| 获取版本列表 | `GET /api/versions/document/{documentId}` | `GET /api/versions/document/{documentId}` | ✅ 匹配 |
| 获取版本快照 | `GET /api/versions/document/{documentId}/version/{version}` | `GET /api/versions/document/{documentId}/version/{version}` | ✅ 匹配 |
| 创建版本快照 | `POST /api/versions/document/{documentId}/snapshot?version={v}` | `POST /api/versions/document/{documentId}/snapshot?version={v}` | ✅ 已修复 |
| 回滚版本 | `POST /api/versions/document/{documentId}/rollback?targetVersion={v}` | `POST /api/versions/document/{documentId}/rollback?targetVersion={v}` | ✅ 已修复 |

### 导出相关
| 功能 | 前端调用 | 后端接口 | 状态 |
|------|---------|---------|------|
| 导出PDF | `GET /api/export/pdf/{documentId}` | `GET /api/export/pdf/{documentId}` | ✅ 匹配 |
| 导出Word | `GET /api/export/word/{documentId}` | `GET /api/export/word/{documentId}` | ✅ 匹配 |
| 导出Markdown | `GET /api/export/markdown/{documentId}` | `GET /api/export/markdown/{documentId}` | ✅ 匹配 |

## ⚠️ 需要修复的接口

### 1. 获取用户信息接口
**问题**：前端调用 `/api/user/info`，但后端只有 `/api/user/{id}`

**解决方案**：
- 方案1：后端添加 `/api/user/info` 接口，从JWT token中获取当前用户信息
- 方案2：前端调用 `/api/user/{id}`，使用当前登录用户的ID

**推荐**：方案1，更符合RESTful规范

## 📝 数据格式说明

### 登录响应
```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzM4NCJ9...",
    "userId": 1,
    "username": "zsy",
    "nickname": "hi"
  }
}
```

### 文档列表响应
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "title": "文档标题",
      "content": "文档内容",
      "creatorId": 1,
      "version": 0,
      "createdAt": "2025-11-19T15:58:18",
      "updatedAt": "2025-11-19T15:58:18"
    }
  ]
}
```

### 评论创建请求
```json
{
  "documentId": 1,
  "userId": 1,  // 必需
  "content": "评论内容",
  "position": 10,  // 可选
  "parentId": null  // 可选，用于回复
}
```

## 🔧 已修复的问题

1. ✅ **删除文档**：添加了userId参数
2. ✅ **添加评论**：添加了userId参数
3. ✅ **版本快照**：修复了version参数传递方式
4. ✅ **版本回滚**：修复了targetVersion参数传递方式
5. ✅ **权限检查**：添加了userId参数，并修复了响应数据提取

## 📋 接口调用注意事项

1. **认证**：除注册、登录外，所有接口都需要在Header中携带JWT Token
   ```
   Authorization: Bearer {token}
   ```

2. **权限验证**：
   - READ权限：查看文档、评论、版本
   - WRITE权限：编辑文档、创建版本快照
   - ADMIN权限：删除文档、管理权限、版本回滚

3. **错误处理**：
   - 401：未认证或Token过期，需要重新登录
   - 403：权限不足
   - 400：请求参数错误
   - 500：服务器内部错误

