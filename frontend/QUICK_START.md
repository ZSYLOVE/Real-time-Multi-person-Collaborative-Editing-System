# 前端项目快速启动指南

## 📋 前置要求

1. **Node.js**: 18.0 或更高版本
2. **npm/yarn/pnpm**: 包管理器
3. **后端服务**: 确保后端Spring Boot应用已启动（端口8080）

## 🚀 快速开始

### 1. 安装依赖

```bash
cd frontend
npm install
```

### 2. 启动开发服务器

```bash
npm run dev
```

前端将在 http://localhost:3000 启动

### 3. 访问应用

打开浏览器访问：http://localhost:3000

## 📝 使用流程

1. **注册账号**
   - 访问注册页面，创建新用户账号

2. **登录系统**
   - 使用注册的账号登录

3. **创建文档**
   - 在文档列表页面点击"新建文档"
   - 输入文档标题

4. **开始编辑**
   - 点击文档进入编辑器
   - 开始编辑，操作会实时同步

5. **多用户测试**
   - 打开多个浏览器窗口
   - 使用不同账号登录
   - 同时编辑同一文档，观察实时同步效果

## 🔧 配置说明

### 修改后端API地址

编辑 `vite.config.ts`：

```typescript
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:8080', // 修改为你的后端地址
    },
  },
}
```

### 修改WebSocket地址

编辑 `src/services/websocket.ts`：

```typescript
const socket = new SockJS('http://localhost:8080/ws'); // 修改为你的WebSocket地址
```

## 🐛 常见问题

### 1. 无法连接后端

- 检查后端服务是否启动
- 检查端口8080是否被占用
- 检查CORS配置

### 2. WebSocket连接失败

- 检查后端WebSocket配置
- 检查JWT Token是否有效
- 查看浏览器控制台错误信息

### 3. 依赖安装失败

```bash
# 清除缓存重新安装
rm -rf node_modules package-lock.json
npm install
```

## 📦 构建生产版本

```bash
npm run build
```

构建产物在 `dist` 目录

## 🎯 下一步开发

### 待完善功能

- [ ] 评论系统UI组件
- [ ] 版本历史查看
- [ ] 权限管理界面
- [ ] 文档分享功能
- [ ] 离线编辑支持
- [ ] 文档导出功能

### 代码结构说明

- `src/components/` - 可复用组件
- `src/pages/` - 页面组件
- `src/services/` - API和WebSocket服务
- `src/stores/` - 状态管理（Zustand）
- `src/types/` - TypeScript类型定义

## 📚 相关文档

- [React官方文档](https://react.dev)
- [Quill编辑器文档](https://quilljs.com)
- [Ant Design文档](https://ant.design)
- [Zustand文档](https://github.com/pmndrs/zustand)

