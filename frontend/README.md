# 实时多人协同编辑系统 - 前端

基于 React + TypeScript + Quill 的实时协同编辑前端应用。

## 技术栈

- **React 18** - UI框架
- **TypeScript** - 类型安全
- **Vite** - 构建工具
- **Quill.js** - 富文本编辑器
- **SockJS + STOMP.js** - WebSocket实时通信
- **Zustand** - 状态管理
- **Ant Design** - UI组件库
- **React Router** - 路由管理
- **Axios** - HTTP客户端

## 快速开始

### 安装依赖

```bash
npm install
# 或
yarn install
# 或
pnpm install
```

### 启动开发服务器

```bash
npm run dev
```

访问 http://localhost:3000

### 构建生产版本

```bash
npm run build
```

## 项目结构

```
src/
├── components/          # 组件
│   └── Editor/         # 编辑器组件
├── pages/              # 页面
│   ├── Login.tsx       # 登录页
│   ├── Register.tsx   # 注册页
│   ├── DocumentList.tsx # 文档列表
│   └── DocumentEditor.tsx # 文档编辑器
├── services/           # 服务层
│   ├── api.ts         # API服务
│   └── websocket.ts   # WebSocket服务
├── stores/            # 状态管理
│   ├── authStore.ts   # 认证状态
│   └── documentStore.ts # 文档状态
├── types/             # TypeScript类型
│   └── index.ts
├── App.tsx           # 根组件
└── main.tsx          # 入口文件
```

## 功能特性

- ✅ 用户注册/登录
- ✅ 文档列表管理
- ✅ 实时协同编辑
- ✅ WebSocket实时同步
- ✅ 富文本编辑（Quill）
- ✅ 在线用户显示
- ✅ 光标位置同步

## 开发说明

### 环境要求

- Node.js 18+
- npm/yarn/pnpm

### 配置

后端API地址在 `vite.config.ts` 中配置：

```typescript
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
    },
  },
}
```

### WebSocket连接

WebSocket服务会自动在登录后连接，连接地址：`ws://localhost:8080/ws`

## 待完善功能

- [ ] 评论系统UI
- [ ] 版本历史UI
- [ ] 权限管理UI
- [ ] 离线编辑支持
- [ ] 文档导出功能
- [ ] 用户头像显示
- [ ] 更多富文本格式支持

## 注意事项

1. 确保后端服务已启动（端口8080）
2. 确保Redis和MySQL服务正常运行
3. WebSocket连接需要有效的JWT Token

