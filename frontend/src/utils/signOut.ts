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

  // 退出登录时仍然尽量保留 userId，用于兜底释放后端登录锁
  const { user } = useAuthStore.getState();
  const userId = user?.id ?? null;

  try {
    // token 可能已过期导致 /logout 走不了（或后端拦截），所以同时把 userId 传给后端作为兜底
    await apiService.logout(userId);
  } catch (e) {
    // 后端失败不阻塞前端退出；但如果仍能释放锁，用户不会再被“已在线”拦截
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

