import { apiService } from '@/services/api';
import useAuthStore from '@/stores/authStore';

/**
 * 统一退出登录：
 * 1) 调后端 /api/user/logout 释放“登录占用锁”
 * 2) 清理前端会话（sessionStorage + zustand）
 * 3) 跳转登录页
 */
export async function signOut(options?: { redirectTo?: string }) {
  const redirectTo = options?.redirectTo ?? '/login';

  try {
    // 只有在本地仍有 token 时才尝试通知后端
    const token = sessionStorage.getItem('token');
    if (token) {
      await apiService.logout();
    }
  } catch (e) {
    // 后端失败不阻塞前端退出；但可能导致账号锁未释放，只能等TTL过期
    console.warn('signOut: 调用后端 logout 失败（可能导致账号仍处于在线状态）:', e);
  } finally {
    const { logout } = useAuthStore.getState();
    logout();

    // 不在这里用 react-router 的 navigate（此处可能在组件外调用）
    if (typeof window !== 'undefined') {
      window.location.href = redirectTo;
    }
  }
}

