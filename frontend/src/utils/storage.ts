/**
 * 本地存储工具函数
 */

export const storage = {
  /**
   * 设置值
   */
  set(key: string, value: any): void {
    try {
      localStorage.setItem(key, JSON.stringify(value));
    } catch (error) {
      console.error('存储失败:', error);
    }
  },

  /**
   * 获取值
   */
  get<T>(key: string, defaultValue?: T): T | null {
    try {
      const item = localStorage.getItem(key);
      if (item === null) {
        return defaultValue || null;
      }
      return JSON.parse(item) as T;
    } catch (error) {
      console.error('读取存储失败:', error);
      return defaultValue || null;
    }
  },

  /**
   * 删除值
   */
  remove(key: string): void {
    try {
      localStorage.removeItem(key);
    } catch (error) {
      console.error('删除存储失败:', error);
    }
  },

  /**
   * 清空所有
   */
  clear(): void {
    try {
      localStorage.clear();
    } catch (error) {
      console.error('清空存储失败:', error);
    }
  },
};

