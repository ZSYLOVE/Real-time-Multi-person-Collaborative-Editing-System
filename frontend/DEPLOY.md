# 前端项目部署指南

## 📦 构建项目

### 开发环境构建
```bash
cd frontend
npm install
npm run dev
```

### 生产环境构建
```bash
cd frontend
npm install
npm run build
```

构建产物在 `dist/` 目录

## 🚀 部署到服务器

### 方式一：Nginx静态部署（推荐）

#### 1. 复制构建产物到服务器
```bash
scp -r dist/* user@your-server:/var/www/collaborative-editor/frontend/
```

#### 2. Nginx配置
```nginx
server {
    listen 80;
    server_name your-domain.com;
    
    # 前端静态文件
    root /var/www/collaborative-editor/frontend;
    index index.html;
    
    # SPA路由支持
    location / {
        try_files $uri $uri/ /index.html;
    }
    
    # API代理
    location /api {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
    
    # WebSocket代理
    location /ws {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
    
    # 静态资源缓存
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
}
```

#### 3. 配置环境变量
编辑 `.env.production`：
```env
VITE_API_BASE_URL=http://your-server-ip:8080
VITE_WS_URL=ws://your-server-ip:8080/ws
```

重新构建：
```bash
npm run build
```

### 方式二：Docker部署

#### Dockerfile
```dockerfile
FROM node:18-alpine as builder

WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

#### docker-compose.yml
```yaml
version: '3.8'
services:
  frontend:
    build: ./frontend
    ports:
      - "80:80"
    depends_on:
      - backend
```

## 🔧 配置说明

### 环境变量

- `VITE_API_BASE_URL`: 后端API地址
- `VITE_WS_URL`: WebSocket地址

### 构建优化

生产环境构建会自动：
- 代码压缩
- Tree Shaking
- 代码分割
- 资源优化

## 📝 注意事项

1. **CORS配置**: 确保后端允许前端域名访问
2. **WebSocket**: 确保Nginx支持WebSocket代理
3. **HTTPS**: 生产环境建议使用HTTPS
4. **缓存**: 静态资源设置长期缓存，index.html不缓存

## 🐛 常见问题

### 1. 路由404错误
确保Nginx配置了 `try_files $uri $uri/ /index.html;`

### 2. WebSocket连接失败
检查Nginx WebSocket代理配置是否正确

### 3. API请求失败
检查后端服务是否启动，CORS配置是否正确

