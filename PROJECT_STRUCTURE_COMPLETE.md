# 完整项目结构说明

## 📁 项目目录结构

```
Real-time-Multi-person-Collaborative-Editing-System-master/
│
├── 📂 frontend/                    # React前端项目
│   ├── src/                        # 前端源代码
│   │   ├── components/            # React组件
│   │   │   ├── Editor/            # 协同编辑器
│   │   │   ├── OnlineUsers/       # 在线用户列表
│   │   │   └── Comment/           # 评论面板
│   │   ├── pages/                 # 页面组件
│   │   │   ├── Login.tsx          # 登录页
│   │   │   ├── Register.tsx       # 注册页
│   │   │   ├── DocumentList.tsx   # 文档列表
│   │   │   └── DocumentEditor.tsx # 文档编辑器
│   │   ├── services/              # 服务层
│   │   │   ├── api.ts             # API服务
│   │   │   └── websocket.ts       # WebSocket服务
│   │   ├── stores/                # 状态管理
│   │   │   ├── authStore.ts       # 认证状态
│   │   │   └── documentStore.ts   # 文档状态
│   │   ├── types/                 # TypeScript类型
│   │   ├── utils/                 # 工具函数
│   │   ├── App.tsx                # 根组件
│   │   └── main.tsx               # 入口文件
│   ├── package.json               # 前端依赖配置
│   ├── vite.config.ts             # Vite配置
│   ├── tsconfig.json              # TypeScript配置
│   └── README.md                  # 前端说明文档
│
├── 📂 src/                         # 后端源代码
│   ├── main/
│   │   ├── java/                  # Java源代码
│   │   │   └── org/zsy/bysj/
│   │   │       ├── algorithm/     # OT算法
│   │   │       ├── controller/    # REST控制器
│   │   │       ├── service/       # 业务逻辑
│   │   │       ├── mapper/       # 数据访问
│   │   │       ├── model/        # 实体模型
│   │   │       ├── config/       # 配置类
│   │   │       └── websocket/     # WebSocket处理
│   │   └── resources/
│   │       ├── application.properties # 应用配置
│   │       ├── db/schema.sql      # 数据库脚本
│   │       └── static/            # 静态资源（测试页面）
│   └── test/                      # 测试代码
│
├── 📂 target/                      # Maven编译输出（忽略）
│
├── 📄 pom.xml                      # Maven项目配置
├── 📄 README.md                    # 项目主文档
├── 📄 .gitignore                   # Git忽略配置
│
└── 📄 各种文档.md                  # 项目文档
```

## 🎯 项目特点

### ✅ 前后端分离架构
- **后端**: Spring Boot RESTful API + WebSocket
- **前端**: React SPA应用
- **通信**: HTTP API + WebSocket实时通信

### ✅ 统一仓库管理
- 前后端代码在同一仓库
- 便于版本控制和协同开发
- 统一的文档和配置管理

### ✅ 独立部署
- 前端构建后是静态文件，可独立部署
- 后端是Spring Boot应用，可独立运行
- 支持前后端分离部署

## 🚀 开发流程

### 1. 后端开发
```bash
# 启动后端
./mvnw spring-boot:run
# 或
mvn spring-boot:run
```

后端运行在：http://localhost:8080

### 2. 前端开发
```bash
# 进入前端目录
cd frontend

# 安装依赖（首次）
npm install

# 启动开发服务器
npm run dev
```

前端运行在：http://localhost:3000

### 3. 生产构建

**后端构建：**
```bash
./mvnw clean package
java -jar target/bysj-0.0.1-SNAPSHOT.jar
```

**前端构建：**
```bash
cd frontend
npm run build
# 构建产物在 frontend/dist/
```

## 📦 部署方案

### 方案一：前后端分离部署（推荐）

```
服务器架构：
┌─────────────────────────────────┐
│         Nginx (80端口)          │
│  ┌───────────────────────────┐ │
│  │  静态文件服务 (前端dist/)   │ │
│  └───────────────────────────┘ │
│  ┌───────────────────────────┐ │
│  │  反向代理 (后端API)        │ │
│  │  /api/* → localhost:8080  │ │
│  │  /ws/* → localhost:8080   │ │
│  └───────────────────────────┘ │
└─────────────────────────────────┘
         │
    ┌────┴────┐
    │         │
┌───▼───┐ ┌──▼────┐
│Spring │ │ MySQL │
│Boot   │ │ Redis │
│(8080) │ │       │
└───────┘ └───────┘
```

### 方案二：Spring Boot内置静态资源

可以将前端构建产物复制到 `src/main/resources/static/`，Spring Boot会自动提供静态文件服务。

## 🔧 配置说明

### 后端配置
- `src/main/resources/application.properties`
- 数据库连接、Redis配置、JWT密钥等

### 前端配置
- `frontend/.env.development` - 开发环境
- `frontend/.env.production` - 生产环境
- `frontend/vite.config.ts` - Vite配置

## 📝 Git管理

### .gitignore 已配置
- 后端：`target/`, `.idea/`, `*.iml` 等
- 前端：`node_modules/`, `dist/`, `.env.local` 等

### 提交建议
```bash
# 后端更改
git add src/ pom.xml
git commit -m "feat: 后端功能更新"

# 前端更改
git add frontend/
git commit -m "feat: 前端功能更新"

# 文档更新
git add *.md
git commit -m "docs: 更新文档"
```

## 🎓 适合毕业设计

### 优势
1. ✅ **完整项目**：前后端齐全，功能完整
2. ✅ **技术栈现代**：React + Spring Boot
3. ✅ **架构清晰**：前后端分离，易于理解
4. ✅ **文档完善**：各种文档齐全
5. ✅ **易于演示**：功能直观，演示效果好

### 项目亮点
- 实时协同编辑（OT算法）
- WebSocket实时通信
- 完整的权限管理
- 版本历史管理
- 评论系统
- 文档导出功能

## 📚 相关文档

- `README.md` - 项目主文档
- `frontend/README.md` - 前端详细文档
- `frontend/QUICK_START.md` - 前端快速开始
- `frontend/DEPLOY.md` - 前端部署指南
- `BACKEND_FEATURES.md` - 后端功能清单
- `PROJECT_STRUCTURE.md` - 后端项目结构

## 💡 开发建议

1. **先启动后端**，确保API正常
2. **再启动前端**，进行联调
3. **使用Postman**测试后端API
4. **浏览器控制台**查看前端日志
5. **多窗口测试**协同编辑功能

