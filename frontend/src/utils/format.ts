/**
 * 格式化工具函数
 */

import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import 'dayjs/locale/zh-cn';

dayjs.extend(relativeTime);
dayjs.locale('zh-cn');

/**
 * 格式化日期时间
 */
export const formatDateTime = (date: string | Date | number): string => {
  return dayjs(date).format('YYYY-MM-DD HH:mm:ss');
};

/**
 * 相对时间（如：3分钟前）
 */
export const formatRelativeTime = (date: string | Date | number): string => {
  return dayjs(date).fromNow();
};

/**
 * 格式化文件大小
 */
export const formatFileSize = (bytes: number): string => {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
};

