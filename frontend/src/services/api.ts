/**
 * API服务层
 * 封装所有HTTP请求
 */

import axios, { AxiosInstance } from 'axios';
import type { 
  ApiResult, 
  User, 
  Document, 
  LoginRequest, 
  RegisterRequest,
  DocumentPermission,
  Comment,
  DocumentVersion
} from '@/types';

class ApiService {
  private api: AxiosInstance;

  constructor() {
    const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || '';
    this.api = axios.create({
      baseURL: apiBaseUrl ? `${apiBaseUrl}/api` : '/api',
      timeout: 10000,
      headers: {
        'Content-Type': 'application/json',
      },
    });

    // 请求拦截器：添加JWT Token
    this.api.interceptors.request.use(
      (config) => {
        const token = localStorage.getItem('token');
        if (token) {
          config.headers.Authorization = `Bearer ${token}`;
        }
        // 如果是FormData，删除Content-Type，让浏览器自动设置（包括boundary）
        if (config.data instanceof FormData) {
          delete config.headers['Content-Type'];
        }
        return config;
      },
      (error) => {
        return Promise.reject(error);
      }
    );

    // 响应拦截器：处理错误
    this.api.interceptors.response.use(
      (response) => {
        return response.data;
      },
      (error) => {
        // 记录错误详情，便于调试
        if (error.config) {
          console.error('API请求失败:', {
            url: error.config.url,
            method: error.config.method,
            status: error.response?.status,
            message: error.message,
            response: error.response?.data
          });
        }
        
        if (error.response?.status === 401) {
          // Token过期，清除本地存储并跳转登录
          localStorage.removeItem('token');
          localStorage.removeItem('user');
          window.location.href = '/login';
        }
        return Promise.reject(error);
      }
    );
  }

  // ========== 用户相关 ==========
  
  /**
   * 获取验证码图片
   */
  async getCaptcha(): Promise<{ captchaId: string; imageUrl: string }> {
    // 使用axios直接请求，绕过响应拦截器（因为验证码返回的是blob，不是JSON）
    const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || '';
    const baseURL = apiBaseUrl ? `${apiBaseUrl}/api` : '/api';
    
    const response = await axios.get(
      `${baseURL}/captcha/generate`,
      {
        responseType: 'blob',
        headers: {
          // 不添加Authorization，验证码接口是公开的
        },
      }
    );
    
    // 获取验证码ID（从响应头）
    const captchaId = response.headers['x-captcha-id'] || response.headers['X-Captcha-Id'];
    if (!captchaId) {
      throw new Error('无法获取验证码ID');
    }
    
    const imageUrl = URL.createObjectURL(response.data);
    return { captchaId, imageUrl };
  }

  /**
   * 用户注册
   */
  async register(data: RegisterRequest): Promise<ApiResult<User>> {
    return this.api.post('/user/register', data);
  }

  /**
   * 用户登录
   */
  async login(data: LoginRequest): Promise<ApiResult<{ token: string; userId: number; username: string; nickname?: string; email?: string }>> {
    return this.api.post('/user/login', data);
  }

  /**
   * 获取用户信息
   */
  async getUserInfo(): Promise<ApiResult<User>> {
    return this.api.get('/user/info');
  }

  /**
   * 搜索用户
   */
  async searchUsers(keyword: string): Promise<ApiResult<User[]>> {
    return this.api.get('/user/search', { params: { keyword } });
  }

  /**
   * 根据ID获取用户信息
   */
  async getUser(userId: number): Promise<ApiResult<User>> {
    return this.api.get(`/user/${userId}`);
  }

  /**
   * 更新用户信息
   */
  async updateUserInfo(updates: { nickname?: string; email?: string; avatar?: string }): Promise<ApiResult<User>> {
    return this.api.put('/user/info', updates);
  }

  /**
   * 上传头像文件
   */
  async uploadAvatar(file: File): Promise<ApiResult<{ url: string; path: string }>> {
    const formData = new FormData();
    formData.append('file', file);
    // 不设置Content-Type，让浏览器自动设置（包括boundary）
    return this.api.post('/file/avatar', formData);
  }

  // ========== 文档相关 ==========

  /**
   * 创建文档
   */
  async createDocument(title: string): Promise<ApiResult<Document>> {
    return this.api.post('/documents', { title });
  }

  /**
   * 获取文档详情
   */
  async getDocument(id: number): Promise<ApiResult<Document>> {
    return this.api.get(`/documents/${id}`);
  }

  /**
   * 获取用户文档列表（创建的）
   */
  async getUserDocuments(userId: number): Promise<ApiResult<Document[]>> {
    return this.api.get(`/documents/user/${userId}`);
  }

  /**
   * 获取用户被共享的文档列表
   */
  async getSharedDocuments(userId: number): Promise<ApiResult<Document[]>> {
    return this.api.get(`/documents/shared/${userId}`);
  }

  /**
   * 更新文档内容（保存文档）
   */
  async updateDocumentContent(id: number, content: string): Promise<ApiResult<Document>> {
    return this.api.put(`/documents/${id}/content`, { content });
  }

  /**
   * 删除文档
   */
  async deleteDocument(id: number, userId: number): Promise<ApiResult<void>> {
    return this.api.delete(`/documents/${id}`, { params: { userId } });
  }

  // ========== 权限相关 ==========

  /**
   * 添加文档权限
   */
  async addPermission(documentId: number, userId: number, permissionType: string): Promise<ApiResult<DocumentPermission>> {
    return this.api.post('/permissions', { documentId, userId, permissionType });
  }

  /**
   * 更新文档权限
   */
  async updatePermission(documentId: number, userId: number, permissionType: string): Promise<ApiResult<DocumentPermission>> {
    return this.api.put('/permissions', { documentId, userId, permissionType });
  }

  /**
   * 删除文档权限
   */
  async deletePermission(documentId: number, userId: number): Promise<ApiResult<void>> {
    return this.api.delete('/permissions', { params: { documentId, userId } });
  }

  /**
   * 获取文档权限列表
   */
  async getDocumentPermissions(documentId: number): Promise<ApiResult<DocumentPermission[]>> {
    return this.api.get(`/permissions/document/${documentId}`);
  }

  /**
   * 获取用户的所有权限
   */
  async getUserPermissions(): Promise<ApiResult<DocumentPermission[]>> {
    return this.api.get('/permissions/user');
  }

  /**
   * 检查权限
   */
  async checkPermission(documentId: number, userId: number, permissionType: string): Promise<ApiResult<boolean>> {
    const result: ApiResult<{ hasPermission: boolean }> = await this.api.get('/permissions/check', { params: { documentId, userId, permissionType } });
    // 后端返回 { hasPermission: boolean }，需要提取
    if (result.code === 200 && result.data) {
      return {
        code: result.code,
        message: result.message,
        data: result.data.hasPermission || false,
      };
    }
    return {
      code: result.code || 500,
      message: result.message || '检查权限失败',
      data: false,
    };
  }

  // ========== 评论相关 ==========

  /**
   * 添加评论
   */
  async addComment(documentId: number, userId: number, content: string, position?: number, parentId?: number): Promise<ApiResult<Comment>> {
    return this.api.post('/comments', { documentId, userId, content, position, parentId });
  }

  /**
   * 获取文档评论列表
   */
  async getDocumentComments(documentId: number): Promise<ApiResult<Comment[]>> {
    return this.api.get(`/comments/document/${documentId}`);
  }

  /**
   * 更新评论
   */
  async updateComment(id: number, updates: { content?: string; isResolved?: boolean }): Promise<ApiResult<Comment>> {
    return this.api.put(`/comments/${id}`, updates);
  }

  /**
   * 删除评论
   */
  async deleteComment(id: number): Promise<ApiResult<void>> {
    return this.api.delete(`/comments/${id}`);
  }

  // ========== 版本相关 ==========

  /**
   * 获取文档版本列表
   */
  async getDocumentVersions(documentId: number): Promise<ApiResult<DocumentVersion[]>> {
    return this.api.get(`/versions/document/${documentId}`);
  }

  /**
   * 获取版本快照
   */
  async getVersionSnapshot(documentId: number, version: number): Promise<ApiResult<DocumentVersion>> {
    return this.api.get(`/versions/document/${documentId}/version/${version}`);
  }

  /**
   * 创建版本快照
   */
  async createVersionSnapshot(documentId: number, version: number): Promise<ApiResult<DocumentVersion>> {
    return this.api.post(`/versions/document/${documentId}/snapshot`, null, { params: { version } });
  }

  /**
   * 回滚到指定版本
   */
  async rollbackToVersion(documentId: number, targetVersion: number): Promise<ApiResult<Document>> {
    return this.api.post(`/versions/document/${documentId}/rollback`, null, { params: { targetVersion } });
  }

  // ========== 协作相关 ==========

  /**
   * 获取文档在线用户列表
   */
  async getOnlineUsers(documentId: number): Promise<ApiResult<any[]>> {
    return this.api.get(`/collaboration/online/${documentId}`);
  }

  // ========== 导出相关 ==========

  /**
   * 导出PDF
   */
  async exportPDF(documentId: number): Promise<Blob> {
    // 直接使用axios，绕过响应拦截器（因为导出返回的是blob，不是JSON）
    const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || '';
    const baseURL = apiBaseUrl ? `${apiBaseUrl}/api` : '/api';
    
    const response = await axios.get(
      `${baseURL}/export/pdf/${documentId}`,
      {
        responseType: 'blob',
        headers: {
          Authorization: `Bearer ${localStorage.getItem('token')}`,
        },
      }
    );
    return response.data;
  }

  /**
   * 导出Word
   */
  async exportWord(documentId: number): Promise<Blob> {
    // 直接使用axios，绕过响应拦截器（因为导出返回的是blob，不是JSON）
    const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || '';
    const baseURL = apiBaseUrl ? `${apiBaseUrl}/api` : '/api';
    
    const response = await axios.get(
      `${baseURL}/export/word/${documentId}`,
      {
        responseType: 'blob',
        headers: {
          Authorization: `Bearer ${localStorage.getItem('token')}`,
        },
      }
    );
    return response.data;
  }

  /**
   * 导出Markdown
   */
  async exportMarkdown(documentId: number): Promise<Blob> {
    // 直接使用axios，绕过响应拦截器（因为导出返回的是文本，不是JSON）
    const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || '';
    const baseURL = apiBaseUrl ? `${apiBaseUrl}/api` : '/api';
    
    const response = await axios.get(
      `${baseURL}/export/markdown/${documentId}`,
      {
        responseType: 'text',
        headers: {
          Authorization: `Bearer ${localStorage.getItem('token')}`,
        },
      }
    );
    // 将文本转换为Blob
    return new Blob([response.data], { type: 'text/markdown;charset=utf-8' });
  }
}

export const apiService = new ApiService();

