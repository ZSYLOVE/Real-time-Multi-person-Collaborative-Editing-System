/**
 * 主题状态管理
 */

import { create } from 'zustand';

export type ThemeMode = 'light' | 'dark';

interface ThemeState {
  theme: ThemeMode;
  toggleTheme: () => void;
  setTheme: (theme: ThemeMode) => void;
  init: () => void;
}

const useThemeStore = create<ThemeState>((set) => ({
  theme: 'light',
  
  init: () => {
    const savedTheme = localStorage.getItem('theme-mode') as ThemeMode;
    if (savedTheme === 'light' || savedTheme === 'dark') {
      set({ theme: savedTheme });
      // 应用主题类名
      document.documentElement.classList.toggle('dark-theme', savedTheme === 'dark');
    }
  },
  
  toggleTheme: () => {
    set((state) => {
      const newTheme = state.theme === 'light' ? 'dark' : 'light';
      localStorage.setItem('theme-mode', newTheme);
      // 应用主题类名到根元素
      document.documentElement.classList.toggle('dark-theme', newTheme === 'dark');
      return { theme: newTheme };
    });
  },
  
  setTheme: (theme) => {
    localStorage.setItem('theme-mode', theme);
    document.documentElement.classList.toggle('dark-theme', theme === 'dark');
    set({ theme });
  },
}));

export default useThemeStore;
