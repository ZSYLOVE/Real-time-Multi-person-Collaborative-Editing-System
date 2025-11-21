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

const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  token: null,
  isAuthenticated: false,
  loginTime: null,

  setUser: (user) =>
    set({
      user,
      isAuthenticated: !!user,
    }),

  setToken: (token) => {
    set({ token });
    if (token) {
      localStorage.setItem('token', token);
    } else {
      localStorage.removeItem('token');
    }
  },

  login: (user, token) => {
    const loginTime = Date.now();
    set({
      user,
      token,
      isAuthenticated: true,
      loginTime,
    });
    localStorage.setItem('token', token);
    localStorage.setItem('user', JSON.stringify(user));
    localStorage.setItem('loginTime', loginTime.toString());
  },

  // 初始化：从localStorage恢复状态
  init: () => {
    const token = localStorage.getItem('token');
    const userStr = localStorage.getItem('user');
    const loginTimeStr = localStorage.getItem('loginTime');
    
    if (token && userStr) {
      try {
        const user = JSON.parse(userStr);
        const loginTime = loginTimeStr ? parseInt(loginTimeStr, 10) : Date.now();
        set({
          user,
          token,
          isAuthenticated: true,
          loginTime,
        });
        // 如果localStorage中没有登录时间，设置当前时间
        if (!loginTimeStr) {
          localStorage.setItem('loginTime', loginTime.toString());
        }
      } catch (error) {
        console.error('恢复用户状态失败:', error);
        localStorage.removeItem('token');
        localStorage.removeItem('user');
        localStorage.removeItem('loginTime');
      }
    }
  },

  logout: () => {
    set({
      user: null,
      token: null,
      isAuthenticated: false,
      loginTime: null,
    });
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    localStorage.removeItem('loginTime');
  },

  // 获取使用时长（毫秒）
  getSessionDuration: () => {
    const { loginTime } = get();
    if (!loginTime) return 0;
    return Date.now() - loginTime;
  },
}));

export default useAuthStore;

