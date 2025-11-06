# 实时多人协同编辑系统

## 项目简介

这是一个基于 Spring Boot + Flutter 的实时多人协同编辑系统，类似于 Google Docs，支持多人在线实时编辑文档，具有操作冲突检测与自动解决、离线编辑、权限管理等功能。

## 技术栈

### 后端
- Spring Boot 3.5.7
- MyBatis 3.0.5
- MySQL 8.0+
- Redis（实时同步、缓存）
- WebSocket（实时通信）
- OT算法（操作转换，解决冲突）

### 前端
- Flutter（跨平台：iOS/Android/Web）
- WebSocket（实时通信）
- 本地存储（离线支持）

## 核心功能

1. **多人实时协同编辑**
   - 支持多用户同时编辑同一文档
   - 实时显示其他用户的编辑位置和光标
   - 操作实时同步

2. **操作冲突解决**
   - 基于OT（操作转换）算法
   - 自动检测和解决编辑冲突
   - 保证最终一致性

3. **文档版本管理**
   - 文档版本历史记录
   - 版本对比与回滚
   - 变更记录追踪

4. **评论与批注**
   - 文档内评论功能
   - 批注系统
   - 评论通知

5. **离线支持**
   - 本地存储编辑内容
   - 离线编辑
   - 自动同步机制

6. **权限管理**
   - 文档分享功能
   - 读写权限控制
   - 协作权限管理

7. **文档导出**
   - PDF导出
   - Word导出
   - Markdown导出

## 系统架构

```
┌─────────────────┐
│  Flutter Client │ (iOS/Android/Web)
└────────┬────────┘
         │ WebSocket/HTTP
         │
┌────────▼─────────────────┐
│   Spring Boot Backend    │
│  ┌─────────────────────┐ │
│  │  WebSocket Handler │ │
│  │  OT Algorithm      │ │
│  │  Document Service  │ │
│  └─────────────────────┘ │
└────────┬──────────────────┘
         │
    ┌────┴────┐
    │         │
┌───▼───┐ ┌──▼────┐
│ MySQL │ │ Redis │
└───────┘ └───────┘
```

## 数据库设计

### 用户表 (user)
- id, username, email, password, avatar, created_at

### 文档表 (document)
- id, title, content, creator_id, created_at, updated_at, version

### 文档版本表 (document_version)
- id, document_id, version, content, created_at, created_by

### 文档权限表 (document_permission)
- id, document_id, user_id, permission_type (read/write/admin), created_at

### 文档操作日志表 (document_operation)
- id, document_id, user_id, operation_type, operation_data, timestamp, version

### 评论表 (comment)
- id, document_id, user_id, content, position, created_at, parent_id

## 项目结构

```
bysj/
├── src/main/java/org/zsy/bysj/
│   ├── BysjApplication.java
│   ├── config/          # 配置类
│   ├── controller/      # 控制器
│   ├── service/         # 业务逻辑
│   ├── mapper/          # MyBatis映射
│   ├── model/           # 实体类
│   ├── dto/             # 数据传输对象
│   ├── websocket/       # WebSocket相关
│   ├── algorithm/       # OT算法实现
│   └── util/            # 工具类
├── src/main/resources/
│   ├── mapper/          # MyBatis XML
│   └── application.properties
└── flutter_app/         # Flutter前端项目
```

## 开发计划

1. ✅ 项目初始化与依赖配置
2. ⏳ 数据库设计与表结构创建
3. ⏳ 后端基础框架搭建
4. ⏳ OT算法核心实现
5. ⏳ WebSocket实时通信
6. ⏳ 文档管理模块
7. ⏳ 权限管理模块
8. ⏳ Flutter前端开发
9. ⏳ 测试与优化

## 运行说明

### 后端启动
```bash
mvn spring-boot:run
```

### 前端启动
```bash
cd flutter_app
flutter run
```

## 许可证

MIT License

