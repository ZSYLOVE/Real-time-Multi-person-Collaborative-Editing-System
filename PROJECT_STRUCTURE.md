# 项目结构说明

## 后端项目结构（Spring Boot）

```
src/main/java/org/zsy/bysj/
├── BysjApplication.java          # 启动类
├── config/                        # 配置类
│   ├── WebSocketConfig.java      # WebSocket配置
│   ├── RedisConfig.java          # Redis配置
│   └── CorsConfig.java           # 跨域配置
├── controller/                    # 控制器
│   └── DocumentController.java   # 文档REST API
├── service/                       # 服务层
│   ├── DocumentService.java      # 文档服务接口
│   ├── CollaborationService.java # 协同编辑服务接口
│   └── impl/                     # 服务实现
│       ├── DocumentServiceImpl.java
│       └── CollaborationServiceImpl.java
├── mapper/                        # MyBatis映射
│   ├── DocumentMapper.java
│   └── DocumentOperationMapper.java
├── model/                         # 实体类
│   ├── User.java
│   ├── Document.java
│   ├── DocumentOperation.java
│   ├── DocumentPermission.java
│   └── Comment.java
├── dto/                           # 数据传输对象
│   ├── OperationDTO.java
│   └── WebSocketMessage.java
├── algorithm/                     # OT算法
│   ├── Operation.java
│   └── OTAlgorithm.java
├── websocket/                     # WebSocket相关
│   ├── WebSocketController.java
│   └── WebSocketEventListener.java
└── util/                          # 工具类
    └── JwtUtil.java
```

## 前端项目结构（Flutter）

```
flutter_app/
├── lib/
│   ├── main.dart                 # 入口文件
│   ├── config/                    # 配置
│   │   └── api_config.dart
│   ├── models/                    # 数据模型
│   │   ├── document.dart
│   │   └── operation.dart
│   ├── services/                  # 服务层
│   │   ├── websocket_service.dart
│   │   └── document_service.dart
│   ├── providers/                 # 状态管理
│   │   ├── document_provider.dart
│   │   └── collaboration_provider.dart
│   ├── screens/                   # 页面
│   │   ├── home_screen.dart
│   │   ├── document_list_screen.dart
│   │   └── editor_screen.dart
│   ├── widgets/                   # 组件
│   │   ├── editor/                # 编辑器组件
│   │   │   ├── collaborative_editor.dart
│   │   │   └── cursor_indicator.dart
│   │   └── common/                # 通用组件
│   └── utils/                     # 工具类
│       └── storage_util.dart
└── pubspec.yaml                   # 依赖配置
```

## 数据库表结构

### user - 用户表
- id, username, email, password, avatar, nickname, created_at, updated_at

### document - 文档表
- id, title, content, creator_id, version, is_deleted, created_at, updated_at

### document_version - 文档版本表
- id, document_id, version, content, snapshot, created_by, created_at

### document_permission - 文档权限表
- id, document_id, user_id, permission_type, created_at

### document_operation - 文档操作日志表
- id, document_id, user_id, operation_type, operation_data, position, length, timestamp, version, created_at

### comment - 评论表
- id, document_id, user_id, content, position, parent_id, is_resolved, created_at, updated_at

### user_session - 用户会话表
- id, user_id, document_id, session_id, cursor_position, last_active_at

## 核心功能模块

### 1. 文档管理模块
- 创建、读取、更新、删除文档
- 文档版本管理
- 文档列表查询

### 2. 协同编辑模块
- WebSocket实时通信
- OT算法冲突解决
- 操作记录与同步
- 光标位置同步

### 3. 权限管理模块
- 文档分享
- 权限控制（读/写/管理员）
- 用户管理

### 4. 评论批注模块
- 文档内评论
- 批注系统
- 评论通知

### 5. 离线支持模块
- 本地存储
- 离线编辑
- 自动同步

## 技术要点

### OT算法（操作转换）
- 实现INSERT、DELETE、RETAIN三种操作
- 操作转换算法解决冲突
- 保证最终一致性

### WebSocket通信
- 使用STOMP协议
- 实时消息推送
- 连接管理

### Redis缓存
- 文档内容缓存
- 在线用户列表
- 用户光标位置

### 数据同步策略
- 操作版本控制
- 增量同步
- 冲突检测与解决

