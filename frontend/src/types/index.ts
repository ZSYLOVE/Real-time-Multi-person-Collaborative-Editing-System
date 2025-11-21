/**
 * TypeScript类型定义
 * 与后端DTO保持一致
 */

// 操作类型
export type OperationType = 'INSERT' | 'DELETE' | 'RETAIN' | 'FORMAT';

// 权限类型
export type PermissionType = 'READ' | 'WRITE' | 'ADMIN';

// 操作DTO
export interface OperationDTO {
  type: OperationType;
  data?: string;
  position: number;
  length: number;
  timestamp: number;
  version: number;
  attributes?: Record<string, any>;
  formatType?: string;
  formatValue?: any;
}

// WebSocket消息类型
export type WebSocketMessageType = 'OPERATION' | 'CURSOR' | 'COMMENT' | 'PERMISSION' | 'JOIN' | 'LEAVE' | 'DOCUMENT_UPDATED';

// WebSocket消息
export interface WebSocketMessage {
  type: WebSocketMessageType;
  documentId: number;
  userId: number;
  data?: any;
  timestamp?: number;
}

// 用户信息
export interface User {
  id: number;
  username: string;
  email: string;
  nickname?: string;
  avatar?: string;
  createdAt?: string;
}

// 文档信息
export interface Document {
  id: number;
  title: string;
  content: string;
  creatorId: number;
  version: number;
  createdAt?: string;
  updatedAt?: string;
  isShared?: boolean; // 是否是共享给我的文档
  permissionType?: PermissionType; // 我的权限类型（如果是共享文档）
}

// 文档权限
export interface DocumentPermission {
  id: number;
  documentId: number;
  userId: number;
  permissionType: PermissionType;
  createdAt?: string;
  user?: User;
}

// 评论
export interface Comment {
  id: number;
  documentId: number;
  userId: number;
  content: string;
  position?: number;
  parentId?: number;
  isResolved?: boolean;
  createdAt?: string;
  updatedAt?: string;
  user?: User;
  replies?: Comment[];
}

// 文档版本
export interface DocumentVersion {
  id: number;
  documentId: number;
  version: number;
  content: string;
  snapshot?: string;
  createdBy: number;
  createdAt: string;
}

// 在线用户信息
export interface OnlineUser {
  userId: number;
  username: string;
  nickname?: string;
  avatar?: string;
  cursorPosition?: number;
  color?: string;
}

// API响应格式
export interface ApiResult<T = any> {
  code: number;
  message: string;
  data?: T;
}

// 登录请求
export interface LoginRequest {
  username: string;
  password: string;
  captchaId?: string;
  captchaCode?: string;
}

// 注册请求
export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
  nickname?: string;
  captchaId?: string;
  captchaCode?: string;
}

// 光标位置数据
export interface CursorData {
  position: number;
  userId: number;
}

