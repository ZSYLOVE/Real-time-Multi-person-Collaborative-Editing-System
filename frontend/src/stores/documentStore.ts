/**
 * 文档状态管理（Zustand）
 */

import { create } from 'zustand';
import type { Document, OnlineUser, Comment, DocumentVersion } from '@/types';

interface DocumentState {
  // 当前文档
  currentDocument: Document | null;
  
  // 在线用户列表
  onlineUsers: OnlineUser[];
  
  // 评论列表
  comments: Comment[];
  
  // 版本列表
  versions: DocumentVersion[];
  
  // 加载状态
  loading: boolean;
  
  // 错误信息
  error: string | null;

  // Actions
  setCurrentDocument: (document: Document | null) => void;
  updateDocumentContent: (content: string) => void;
  updateDocument: (document: Document) => void;
  setOnlineUsers: (users: OnlineUser[]) => void;
  addOnlineUser: (user: OnlineUser) => void;
  removeOnlineUser: (userId: number) => void;
  updateUserCursor: (userId: number, position: number) => void;
  setComments: (comments: Comment[]) => void;
  addComment: (comment: Comment) => void;
  removeComment: (commentId: number) => void;
  setVersions: (versions: DocumentVersion[]) => void;
  setLoading: (loading: boolean) => void;
  setError: (error: string | null) => void;
  reset: () => void;
}

const useDocumentStore = create<DocumentState>((set) => ({
  currentDocument: null,
  onlineUsers: [],
  comments: [],
  versions: [],
  loading: false,
  error: null,

  setCurrentDocument: (document) => set({ currentDocument: document }),
  
  updateDocumentContent: (content) =>
    set((state) => ({
      currentDocument: state.currentDocument
        ? { ...state.currentDocument, content }
        : null,
    })),
  
  updateDocument: (document) => set({ currentDocument: document }),

  setOnlineUsers: (users) => set({ onlineUsers: users }),
  
  addOnlineUser: (user) =>
    set((state) => {
      const exists = state.onlineUsers.find((u) => u.userId === user.userId);
      if (exists) {
        return state;
      }
      return { onlineUsers: [...state.onlineUsers, user] };
    }),
  
  removeOnlineUser: (userId) =>
    set((state) => ({
      onlineUsers: state.onlineUsers.filter((u) => u.userId !== userId),
    })),
  
  updateUserCursor: (userId, position) =>
    set((state) => ({
      onlineUsers: state.onlineUsers.map((u) =>
        u.userId === userId ? { ...u, cursorPosition: position } : u
      ),
    })),

  setComments: (comments) => set({ comments }),
  
  addComment: (comment) =>
    set((state) => {
      // 检查评论是否已存在，避免重复添加
      const exists = state.comments.find(c => c.id === comment.id);
      if (exists) {
        return state;
      }
      return {
        comments: [...state.comments, comment],
      };
    }),
  
  removeComment: (commentId) =>
    set((state) => ({
      comments: state.comments.filter((c) => c.id !== commentId),
    })),

  setVersions: (versions) => set({ versions }),
  
  setLoading: (loading) => set({ loading }),
  
  setError: (error) => set({ error }),
  
  reset: () =>
    set({
      currentDocument: null,
      onlineUsers: [],
      comments: [],
      versions: [],
      loading: false,
      error: null,
    }),
}));

export default useDocumentStore;

