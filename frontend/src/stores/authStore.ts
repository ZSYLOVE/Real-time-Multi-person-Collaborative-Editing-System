/**
 * 认证状态管理
 */

import { create } from 'zustand';
import type { User } from '@/types';

interface AuthState {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  loginTime: number | null; // 登录时间戳

  // Actions
  setUser: (user: User | null) => void;
  setToken: (token: string | null) => void;
  login: (user: User, token: string) => void;
  logout: () => void;
  init: () => void;
  getSessionDuration: () => number; // 获取使用时长（毫秒）
}

// 模块级别的标志，确保storage监听器只注册一次
// 方案2(tab 独立) 不再需要 storage 监听器
// 标记当前标签页是否正在登录，用于忽略登录过程中的一些边界情况
let isLoggingIn = false;

const useAuthStore = create<AuthState>((set, get) => {
  // 方案2：各页面互不影响（tab 独立）
  // 我们把认证信息存到 sessionStorage，并且不再监听 storage 事件做多标签同步
  // 因为 sessionStorage 本身就是“每个标签页独立”的

  return {
    user: null,
    token: null,
    isAuthenticated: false,
    loginTime: null,

    setUser: (user) => {
      // 如果正在登录，设置标志以避免 storage 事件误判
      const wasLoggingIn = isLoggingIn;
      if (!wasLoggingIn && user) {
        isLoggingIn = true;
        console.log('authStore.setUser: 设置 isLoggingIn 标志');
      }
      
      // 保存当前 token，确保不会被清除
      const currentToken = sessionStorage.getItem('token');
      
      set({
        user,
        isAuthenticated: !!user,
      });
      
      // 如果设置了新用户，更新 sessionStorage（tab 独立）
      if (user) {
        sessionStorage.setItem('user', JSON.stringify(user));
        // 确保 token 没有被清除
        if (currentToken && !sessionStorage.getItem('token')) {
          console.warn('authStore.setUser: Token被清除，恢复token');
          sessionStorage.setItem('token', currentToken);
        }
        // 延迟重置标志
        if (!wasLoggingIn) {
          setTimeout(() => {
            isLoggingIn = false;
            console.log('authStore.setUser: isLoggingIn 标志已重置');
          }, 150);
        }
      } else {
        // 如果 user 为 null，清除 sessionStorage
        sessionStorage.removeItem('user');
        if (!wasLoggingIn) {
          setTimeout(() => {
            isLoggingIn = false;
          }, 150);
        }
      }
    },

    setToken: (token) => {
      set({ token });
      if (token) {
        sessionStorage.setItem('token', token);
      } else {
        sessionStorage.removeItem('token');
      }
    },

    login: (user, token) => {
      // 设置登录标志，避免 storage 事件监听器误判
      isLoggingIn = true;
      
      try {
        // 登录前先清除旧的认证信息，确保不会混淆
        console.log('authStore.login: 清除旧认证信息，设置新用户', user.id);
        sessionStorage.removeItem('token');
        sessionStorage.removeItem('user');
        sessionStorage.removeItem('loginTime');
        
        // 验证用户和token的有效性
        if (!user || !user.id || !token) {
          console.error('authStore.login: 用户或token无效', { user, hasToken: !!token });
          throw new Error('用户或token无效');
        }
        
        const loginTime = Date.now();
        set({
          user,
          token,
          isAuthenticated: true,
          loginTime,
        });
        
        // 使用同步方式设置，确保立即生效
        sessionStorage.setItem('token', token);
        sessionStorage.setItem('user', JSON.stringify(user));
        sessionStorage.setItem('loginTime', loginTime.toString());
        
        console.log('authStore.login: 登录成功，用户ID:', user.id);
        
        // 验证 token 是否已保存
        const savedToken = sessionStorage.getItem('token');
        if (!savedToken) {
          console.error('authStore.login: Token未保存，重新设置');
          sessionStorage.setItem('token', token);
        }
        console.log('authStore.login: Token验证完成，已保存:', !!sessionStorage.getItem('token'));
        
        // 再次验证，确保 token 没有被清除
        setTimeout(() => {
          const verifyToken = sessionStorage.getItem('token');
          if (!verifyToken) {
            console.error('authStore.login: Token在保存后被清除，重新设置');
            sessionStorage.setItem('token', token);
          }
        }, 50);
      } finally {
        // 延迟重置标志，确保所有 storage 事件都已处理
        // 延长到 500ms，确保整个登录流程（包括 setUser 和导航）完成
        setTimeout(() => {
          isLoggingIn = false;
          console.log('authStore.login: isLoggingIn 标志已重置');
        }, 500);
      }
    },

    // 初始化：从sessionStorage恢复状态
    // 注意：这里只恢复状态，不验证token有效性
    // token有效性验证应该在App.tsx中通过API调用进行
    // 但这里会验证 user 和 token 的基本一致性（user.id 必须存在）
    init: () => {
      const token = sessionStorage.getItem('token');
      const userStr = sessionStorage.getItem('user');
      const loginTimeStr = sessionStorage.getItem('loginTime');
      
      if (token && userStr) {
        try {
          const user = JSON.parse(userStr);
          const loginTime = loginTimeStr ? parseInt(loginTimeStr, 10) : Date.now();
          
          // 验证用户数据完整性
          if (!user || !user.id) {
            console.warn('authStore.init: 用户数据不完整，清除认证信息');
            sessionStorage.removeItem('token');
            sessionStorage.removeItem('user');
            sessionStorage.removeItem('loginTime');
            set({
              user: null,
              token: null,
              isAuthenticated: false,
              loginTime: null,
            });
            return;
          }
          
          // 验证 token 和 user 的基本一致性
          // 如果 token 或 user 为空，清除所有认证信息
          if (!token || !user.id) {
            console.warn('authStore.init: token 或 user.id 为空，清除认证信息');
            sessionStorage.removeItem('token');
            sessionStorage.removeItem('user');
            sessionStorage.removeItem('loginTime');
            set({
              user: null,
              token: null,
              isAuthenticated: false,
              loginTime: null,
            });
            return;
          }
          
          set({
            user,
            token,
            isAuthenticated: true,
            loginTime,
          });
          // 如果sessionStorage中没有登录时间，设置当前时间
          if (!loginTimeStr) {
            sessionStorage.setItem('loginTime', loginTime.toString());
          }
        } catch (error) {
          console.error('authStore.init: 恢复用户状态失败:', error);
          sessionStorage.removeItem('token');
          sessionStorage.removeItem('user');
          sessionStorage.removeItem('loginTime');
          set({
            user: null,
            token: null,
            isAuthenticated: false,
            loginTime: null,
          });
        }
      } else {
        // 如果 token 或 user 不存在，确保状态被清除
        if (token || userStr) {
          console.warn('authStore.init: token 或 user 不完整，清除认证信息');
          sessionStorage.removeItem('token');
          sessionStorage.removeItem('user');
          sessionStorage.removeItem('loginTime');
        }
        set({
          user: null,
          token: null,
          isAuthenticated: false,
          loginTime: null,
        });
      }
    },

    logout: () => {
      set({
        user: null,
        token: null,
        isAuthenticated: false,
        loginTime: null,
      });
      sessionStorage.removeItem('token');
      sessionStorage.removeItem('user');
      sessionStorage.removeItem('loginTime');
    },

    // 获取使用时长（毫秒）
    getSessionDuration: () => {
      const { loginTime } = get();
      if (!loginTime) return 0;
      return Date.now() - loginTime;
    },
  };
});

export default useAuthStore;

