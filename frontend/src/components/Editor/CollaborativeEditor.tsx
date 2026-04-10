/**
 * 协同编辑器组件
 * 基于Quill实现富文本编辑，支持实时协同
 */

import React, { useEffect, useRef, useState, useCallback } from 'react';
import ReactQuill from 'react-quill';
import Quill from 'quill';
import { Button, message, Space } from 'antd';
import { SaveOutlined } from '@ant-design/icons';
import 'react-quill/dist/quill.snow.css';
import { websocketService } from '@/services/websocket';
import { apiService } from '@/services/api';
import useDocumentStore from '@/stores/documentStore';
import type { OperationDTO, WebSocketMessage } from '@/types';
import './CollaborativeEditor.css';

const Delta = Quill.import('delta');

interface CollaborativeEditorProps {
  documentId: number;
  userId: number;
  readOnly?: boolean;
}

const CollaborativeEditor: React.FC<CollaborativeEditorProps> = ({
  documentId,
  userId,
  readOnly = false,
}) => {
  const quillRef = useRef<ReactQuill>(null);
  const [isLocalChange, setIsLocalChange] = useState(true);
  const [isInitialized, setIsInitialized] = useState(false);
  const [saving, setSaving] = useState(false);
  const initializedContentRef = useRef<string | null>(null);
  const isLocalUpdateRef = useRef<boolean>(false); // 标记是否是本地更新
  const [cursorPositions, setCursorPositions] = useState<Map<number, { top: number; left: number }>>(new Map());
  const [commentMarkers, setCommentMarkers] = useState<Map<number, { top: number; left: number }>>(new Map());
  const cursorDebounceTimerRef = useRef<NodeJS.Timeout | null>(null); // 光标位置发送防抖定时器
  /** 上次已成功同步到服务端的文档 Delta（用于中文 IME 组合结束后一次性 diff 补发） */
  const lastBroadcastedDeltaRef = useRef<InstanceType<typeof Delta> | null>(null);
  /** 是否处于输入法组合输入（composition）阶段 */
  const isImeComposingRef = useRef(false);
  /** compositionend 中已用 diff 补发，跳过后续一次 handleChange 中的增量发送，避免重复 */
  const skipNextTextChangeSendRef = useRef(false);
  const imeFlushTimerRef = useRef<number | null>(null);
  const isLocalChangeRef = useRef(isLocalChange);
  const { currentDocument, updateDocumentContent, updateDocument, onlineUsers, updateUserCursor, comments } = useDocumentStore();

  useEffect(() => {
    isLocalChangeRef.current = isLocalChange;
  }, [isLocalChange]);

  // Quill工具栏配置 - 扩展格式选项
  const modules = {
    toolbar: readOnly
      ? false
      : [
          [{ header: [1, 2, 3, 4, 5, 6, false] }], // 扩展标题级别
          [{ size: ['small', false, 'large', 'huge'] }], // 字体大小
          [{ font: [] }], // 字体选择
          ['bold', 'italic', 'underline', 'strike'], // 基本格式
          [{ script: 'sub'}, { script: 'super' }], // 上标/下标
          [{ color: [] }, { background: [] }], // 文字颜色和背景色
          [{ align: [] }], // 对齐方式
          [{ indent: '-1' }, { indent: '+1' }], // 缩进
          [{ list: 'ordered' }, { list: 'bullet' }], // 列表
          [{ direction: 'rtl' }], // 文字方向
          ['blockquote', 'code-block'], // 引用和代码块
          ['link', 'image', 'video'], // 媒体
          ['clean'], // 清除格式
        ],
  };

  // 注册评论标记Blot和配置Quill格式
  useEffect(() => {
    if (typeof window !== 'undefined' && Quill) {
      // 定义评论标记的Blot
      const CommentBlot = Quill.import('blots/embed');
      class CommentMarker extends CommentBlot {
        static create(commentId: number) {
          const node = super.create();
          node.setAttribute('data-comment-id', commentId.toString());
          node.setAttribute('contenteditable', 'false');
          node.classList.add('comment-marker');
          return node;
        }
        static value(node: HTMLElement) {
          return parseInt(node.getAttribute('data-comment-id') || '0');
        }
      }
      CommentMarker.blotName = 'commentMarker';
      CommentMarker.tagName = 'span';
      CommentMarker.className = 'comment-marker';
      Quill.register(CommentMarker, true);

      // 配置字体列表（支持常用字体）
      try {
        const Font = Quill.import('formats/font');
        Font.whitelist = [
          'arial',
          'comic-sans',
          'courier-new',
          'georgia',
          'helvetica',
          'lucida',
          'times-new-roman',
          'verdana',
          'simsun', // 宋体
          'simhei', // 黑体
          'kaiti', // 楷体
          'fangsong', // 仿宋
          'microsoft-yahei', // 微软雅黑
        ];
        Quill.register(Font, true);
      } catch (e) {
        console.warn('字体格式注册失败:', e);
      }

      // 配置字体大小（确保大小选项生效）
      try {
        const Size = Quill.import('formats/size');
        Size.whitelist = ['small', false, 'large', 'huge'];
        Quill.register(Size, true);
      } catch (e) {
        console.warn('字体大小格式注册失败:', e);
      }
    }
  }, []);

  // 初始化编辑器内容
  useEffect(() => {
    if (currentDocument?.content && quillRef.current) {
      const quill = quillRef.current.getEditor();
      const contentString = typeof currentDocument.content === 'string' 
        ? currentDocument.content 
        : JSON.stringify(currentDocument.content);
      
      // 如果内容已经初始化过且没有变化，跳过
      if (isInitialized && initializedContentRef.current === contentString) {
        return;
      }
      
      // 如果编辑器已经初始化，检查是否是本地更新
      // 如果是本地更新，不需要重新设置内容（避免光标位置丢失）
      if (isInitialized && isLocalUpdateRef.current) {
        // 这是本地更新，不需要重新设置内容
        initializedContentRef.current = contentString;
        isLocalUpdateRef.current = false; // 重置标记
        return;
      }
      
      // 如果编辑器已经初始化，比较当前编辑器内容和新的内容
      // 如果内容相同，说明是本地更新导致的，不需要重新设置内容（避免光标位置丢失）
      if (isInitialized) {
        const currentEditorContent = quill.root.innerHTML;
        // 标准化HTML内容进行比较（去除空白差异）
        const normalizeHTML = (html: string) => html.replace(/\s+/g, ' ').trim();
        const normalizedCurrent = normalizeHTML(currentEditorContent);
        const normalizedNew = normalizeHTML(contentString);
        if (normalizedCurrent === normalizedNew) {
          // 内容相同，只是状态更新，不需要重新设置
          initializedContentRef.current = contentString;
          return;
        }
      }
      
      try {
        // 如果content是HTML字符串，使用clipboard来正确解析HTML（保留格式）
        if (typeof currentDocument.content === 'string' && currentDocument.content.trim()) {
          // 先清空编辑器
          quill.setContents(new Delta(), 'silent');
          
          // 方法1：使用clipboard.convert转换为Delta，然后setContents
          // 这是最可靠的方法，可以正确保留所有HTML格式（字体、颜色、样式等）
          try {
            const delta = quill.clipboard.convert(currentDocument.content);
            quill.setContents(delta, 'silent');
            
            // 验证内容是否正确设置（检查是否有实际内容）
            const currentContent = quill.root.innerHTML;
            if (!currentContent || currentContent === '<p><br></p>') {
              // 如果设置失败，尝试使用dangerouslyPasteHTML方法
              console.warn('clipboard.convert可能未正确解析，尝试使用dangerouslyPasteHTML');
              quill.clipboard.dangerouslyPasteHTML(currentDocument.content, 'silent');
            }
          } catch (e) {
            console.error('clipboard.convert失败，尝试使用dangerouslyPasteHTML:', e);
            // 备用方案：使用dangerouslyPasteHTML
            try {
              quill.clipboard.dangerouslyPasteHTML(currentDocument.content, 'silent');
            } catch (e2) {
              console.error('dangerouslyPasteHTML也失败，使用innerHTML作为最后备用方案:', e2);
              // 最后的备用方案：直接设置innerHTML
              quill.root.innerHTML = currentDocument.content;
            }
          }
          
          // 记录已初始化的内容
          initializedContentRef.current = contentString;
          lastBroadcastedDeltaRef.current = quill.getContents();
        } else if (currentDocument.content) {
          // 如果是Delta格式，使用setContents（需要是Delta对象）
          try {
            const deltaContent = typeof currentDocument.content === 'string' 
              ? JSON.parse(currentDocument.content) 
              : currentDocument.content;
            quill.setContents(deltaContent, 'silent');
            initializedContentRef.current = contentString;
            lastBroadcastedDeltaRef.current = quill.getContents();
          } catch (e) {
            console.error('解析Delta内容失败:', e);
          }
        }
        
        if (!isInitialized) {
          setIsInitialized(true);
        }
      } catch (error) {
        console.error('初始化编辑器内容失败:', error);
        // 如果dangerouslyPasteHTML失败，尝试使用clipboard.convert作为备用方案
        try {
          if (typeof currentDocument.content === 'string') {
            const delta = quill.clipboard.convert(currentDocument.content);
            quill.setContents(delta, 'silent');
            initializedContentRef.current = contentString;
            lastBroadcastedDeltaRef.current = quill.getContents();
          }
        } catch (e) {
          console.error('备用初始化方法也失败:', e);
          // 最后的备用方案：直接设置innerHTML
          if (typeof currentDocument.content === 'string') {
            quill.root.innerHTML = currentDocument.content;
            initializedContentRef.current = contentString;
            lastBroadcastedDeltaRef.current = quill.getContents();
          }
        }
        if (!isInitialized) {
          setIsInitialized(true);
        }
      }
    } else if (!currentDocument?.content && quillRef.current && !isInitialized) {
      // 如果没有内容，也标记为已初始化
      setIsInitialized(true);
      initializedContentRef.current = null;
      lastBroadcastedDeltaRef.current = quillRef.current.getEditor().getContents();
    }
  }, [currentDocument?.content, isInitialized]);

  // 应用远程操作
  // 转换光标位置：根据操作调整光标位置
  const transformCursorPosition = useCallback((cursorPos: number, operation: OperationDTO): number => {
    if (operation.type === 'INSERT') {
      // 如果插入在光标之前，光标位置后移
      if (operation.position <= cursorPos) {
        return cursorPos + (operation.data?.length || 0);
      }
      return cursorPos;
    } else if (operation.type === 'DELETE') {
      // 如果删除在光标之前，光标位置前移
      if (operation.position + operation.length <= cursorPos) {
        return cursorPos - operation.length;
      } else if (operation.position < cursorPos) {
        // 删除范围包含光标位置，光标移动到删除起点
        return operation.position;
      }
      return cursorPos;
    }
    return cursorPos;
  }, []);

  const applyRemoteOperation = useCallback((operation: OperationDTO) => {
    const quill = quillRef.current?.getEditor();
    if (!quill) {
      return;
    }

    // 保存当前光标位置（在应用操作前）
    const currentSelection = quill.getSelection();
    const savedCursorIndex = currentSelection ? currentSelection.index : null;

    // 防止本地变更触发循环 - 立即设置，不使用延迟
    const wasLocalChange = isLocalChange;
    setIsLocalChange(false);

    try {
      // 获取当前文档长度，用于验证位置
      const currentLength = quill.getLength() - 1; // Quill 末尾有一个换行符
      
      // 根据操作类型应用操作
      switch (operation.type) {
        case 'INSERT': {
          // 确保位置有效
          const position = Math.min(operation.position, currentLength);
          const delta = new Delta()
            .retain(position)
            .insert(operation.data || '', operation.attributes || {});
          // 使用 'api' source 避免触发 onChange 事件
          quill.updateContents(delta, 'api');
          break;
        }
        case 'DELETE': {
          // 确保位置和长度有效
          const position = Math.min(operation.position, currentLength);
          const length = Math.min(operation.length, currentLength - position);
          if (length > 0) {
            const delta = new Delta()
              .retain(position)
              .delete(length);
            quill.updateContents(delta, 'api');
          }
          break;
        }
        case 'FORMAT': {
          // 格式化操作
          if (operation.formatType && operation.formatValue !== undefined) {
            const position = Math.min(operation.position, currentLength);
            const length = Math.min(operation.length || 1, currentLength - position);
            quill.formatText(position, length, operation.formatType, operation.formatValue, 'api');
          }
          break;
        }
        default:
          console.warn('未知的操作类型:', operation.type);
      }
      
      // 恢复自己的光标位置（根据操作转换）
      if (savedCursorIndex !== null) {
        const transformedCursor = transformCursorPosition(savedCursorIndex, operation);
        // 延迟设置光标，确保DOM已更新
        setTimeout(() => {
          quill.setSelection(transformedCursor, 0, 'api');
        }, 0);
      }
      
      // 转换所有其他用户的光标位置
      const updatedUsers = onlineUsers.map(user => {
        if (user.userId !== userId && user.cursorPosition !== undefined && user.cursorPosition !== null) {
          const transformedPos = transformCursorPosition(user.cursorPosition, operation);
          return { ...user, cursorPosition: transformedPos };
        }
        return user;
      });
      
      // 更新store中的用户光标位置
      updatedUsers.forEach(user => {
        if (user.userId !== userId && user.cursorPosition !== undefined) {
          updateUserCursor(user.userId, user.cursorPosition);
        }
      });
      
      // 操作应用后，更新文档内容状态（不触发 onChange）
      const content = quill.root.innerHTML;
      updateDocumentContent(content);
      lastBroadcastedDeltaRef.current = quill.getContents();
      
      // 更新所有用户的光标位置（延迟执行，确保DOM已更新）
      setTimeout(() => {
        const editorElement = quill.root;
        const collaborativeEditor = editorElement.closest('.collaborative-editor');
        if (!collaborativeEditor) return;
        
        const editorRect = editorElement.getBoundingClientRect();
        const containerRect = (collaborativeEditor as HTMLElement).getBoundingClientRect();
        
        updatedUsers.forEach((user) => {
          if (user.userId !== userId && user.cursorPosition !== undefined && user.cursorPosition !== null) {
            try {
              const bounds = quill.getBounds(user.cursorPosition, 0);
              if (bounds) {
                setCursorPositions((prev) => {
                  const newMap = new Map(prev);
                  // 计算相对于 .collaborative-editor 的位置
                  newMap.set(user.userId, { 
                    top: bounds.top + editorRect.top - containerRect.top,
                    left: bounds.left + editorRect.left - containerRect.left
                  });
                  return newMap;
                });
              }
            } catch (error) {
              // 忽略错误，光标位置可能无效
            }
          }
        });
      }, 10);
    } catch (error) {
      console.error('应用远程操作失败:', error, operation);
    } finally {
      // 立即恢复本地变更标志（不使用延迟，避免影响后续操作）
      setIsLocalChange(wasLocalChange);
    }
  }, [onlineUsers, userId, updateDocumentContent, isLocalChange, transformCursorPosition, updateUserCursor]);

  // 更新用户光标像素位置的辅助函数（带重试机制）
  const updateCursorPixelPosition = useCallback((targetUserId: number, position: number, retryCount = 0) => {
    const quill = quillRef.current?.getEditor();
    if (!quill || !isInitialized) {
      // 如果 Quill 未初始化，延迟重试（最多重试3次）
      if (retryCount < 3) {
        setTimeout(() => {
          updateCursorPixelPosition(targetUserId, position, retryCount + 1);
        }, 200);
      }
      return;
    }

    // 使用 requestAnimationFrame 确保在 DOM 完全渲染后计算位置
    requestAnimationFrame(() => {
      try {
        // 确保位置不超过文档长度
        const docLength = quill.getLength() - 1;
        if (docLength <= 0) {
          // 文档为空，光标应该在顶部
          const editorElement = quill.root;
          const collaborativeEditor = editorElement.closest('.collaborative-editor');
          if (collaborativeEditor) {
            const editorRect = editorElement.getBoundingClientRect();
            const containerRect = (collaborativeEditor as HTMLElement).getBoundingClientRect();
            
            setCursorPositions((prev) => {
              const newMap = new Map(prev);
              newMap.set(targetUserId, { 
                top: editorRect.top - containerRect.top,
                left: editorRect.left - containerRect.left
              });
              return newMap;
            });
          }
          return;
        }
        
        const validPosition = Math.min(Math.max(0, position), docLength);
        
        // 使用 getBounds 获取光标位置，length 为 0 表示光标位置
        // 注意：getBounds 返回的位置是相对于 .ql-editor 的，不是相对于 .ql-container
        const bounds = quill.getBounds(validPosition, 0);
        if (bounds && bounds.top !== null && bounds.left !== null && !isNaN(bounds.top) && !isNaN(bounds.left)) {
          const editorElement = quill.root; // .ql-editor
          const collaborativeEditor = editorElement.closest('.collaborative-editor');
          if (collaborativeEditor) {
            // 获取 .ql-editor 相对于 .collaborative-editor 的位置
            const editorRect = editorElement.getBoundingClientRect();
            const containerRect = (collaborativeEditor as HTMLElement).getBoundingClientRect();
            
            // 计算相对于 .collaborative-editor 容器的位置
            // bounds.top 是相对于 .ql-editor 的位置
            // editorRect.top - containerRect.top 是 .ql-editor 相对于容器的偏移
            let absoluteTop = bounds.top + editorRect.top - containerRect.top;
            let absoluteLeft = bounds.left + editorRect.left - containerRect.left;
            
            // 验证计算出的位置是否合理
            // 允许一定的负值（因为可能有滚动），但不要太大
            const containerHeight = containerRect.height;
            const containerWidth = containerRect.width;
            
            // 检查位置是否在合理范围内（允许一些误差）
            // 如果位置不合理，可能是 Quill 还未完全渲染，重试
            if (absoluteTop < -100 || absoluteTop > containerHeight + 100 || 
                absoluteLeft < -100 || absoluteLeft > containerWidth + 100) {
              if (retryCount < 3) {
                setTimeout(() => {
                  updateCursorPixelPosition(targetUserId, position, retryCount + 1);
                }, 200);
                return;
              } else {
                console.warn('计算出的光标位置无效，已重试3次:', { 
                  absoluteTop, 
                  absoluteLeft, 
                  bounds, 
                  position,
                  containerHeight,
                  containerWidth,
                  editorRect,
                  containerRect
                });
                return;
              }
            }
            
            // 位置合理，更新光标位置
            setCursorPositions((prev) => {
              const newMap = new Map(prev);
              newMap.set(targetUserId, { 
                top: absoluteTop,
                left: absoluteLeft
              });
              return newMap;
            });
          }
        } else {
          // 如果获取bounds失败，可能是 Quill 还未完全渲染，重试
          if (retryCount < 3) {
            setTimeout(() => {
              updateCursorPixelPosition(targetUserId, position, retryCount + 1);
            }, 200);
          } else {
            // 重试3次后仍失败，从map中移除
            setCursorPositions((prev) => {
              const newMap = new Map(prev);
              newMap.delete(targetUserId);
              return newMap;
            });
          }
        }
      } catch (error) {
        console.error('更新光标像素位置失败:', error, '用户:', targetUserId, '位置:', position);
        // 出错时也重试
        if (retryCount < 3) {
          setTimeout(() => {
            updateCursorPixelPosition(targetUserId, position, retryCount + 1);
          }, 200);
        } else {
          setCursorPositions((prev) => {
            const newMap = new Map(prev);
            newMap.delete(targetUserId);
            return newMap;
          });
        }
      }
    });
  }, [isInitialized]);

  // 初始化WebSocket监听
  useEffect(() => {
    if (!documentId || !userId) {
      return;
    }

    // 定义消息处理器（在useEffect作用域内）
    const handleRemoteOperation = (message: WebSocketMessage) => {
      // 只处理其他用户的操作（后端已经排除了发送者）
      if (message.type === 'OPERATION') {
        // 确保 data 是 OperationDTO 对象
        let operation: OperationDTO;
        if (typeof message.data === 'object' && message.data !== null) {
          operation = message.data as OperationDTO;
        } else {
          console.error('无效的操作数据:', message.data);
          return;
        }
        
        // 如果是自己的操作，跳过（虽然后端应该已经排除了）
        if (message.userId === userId) {
          console.log('跳过自己的操作:', operation);
          return;
        }
        
        console.log('收到远程操作:', operation, '来自用户:', message.userId);
        try {
          applyRemoteOperation(operation);
        } catch (error) {
          console.error('应用远程操作失败:', error);
        }
      }
    };

    const handleCursorMove = (message: WebSocketMessage) => {
      if (message.type === 'CURSOR' && message.userId !== userId) {
        const cursorData = message.data as { position: number };
        const position = cursorData.position;
        
        // 验证位置有效性
        if (position === undefined || position === null || position < 0) {
          console.warn('收到无效的光标位置:', position, '来自用户:', message.userId);
          return;
        }
        
        // 更新store中的光标位置
        updateUserCursor(message.userId, position);
        
        // 使用统一的更新函数更新光标像素位置
        // 使用 requestAnimationFrame 确保在 DOM 更新后计算位置
        requestAnimationFrame(() => {
          setTimeout(() => {
            updateCursorPixelPosition(message.userId, position);
          }, 50);
        });
      }
    };

    const handleUserJoin = async (message: WebSocketMessage) => {
      if (message.type === 'JOIN') {
        console.log('收到用户加入消息:', message.userId, '当前用户:', userId);
        // 获取最新的在线用户列表
        try {
          const result = await apiService.getOnlineUsers(documentId);
          console.log('用户加入后获取在线用户列表:', result);
          if (result.code === 200 && result.data) {
            // 将后端返回的用户数据转换为 OnlineUser 格式
            const users = result.data.map((user: any) => ({
              userId: user.userId,
              username: user.username || `用户${user.userId}`,
              nickname: user.nickname,
              avatar: user.avatar,
              color: user.color || `#${Math.floor(Math.random()*16777215).toString(16)}`,
              cursorPosition: user.position || undefined,
            }));
            console.log('更新在线用户列表:', users);
            useDocumentStore.getState().setOnlineUsers(users);
            
            // 更新光标位置（延迟执行，确保Quill完全初始化）
            // 使用多次延迟，确保DOM完全渲染
            setTimeout(() => {
              users.forEach((user: any) => {
                if (user.userId !== userId && user.cursorPosition !== undefined && user.cursorPosition !== null) {
                  // 更新store中的光标位置
                  updateUserCursor(user.userId, user.cursorPosition);
                  // 延迟更新光标像素位置，确保Quill完全渲染
                  requestAnimationFrame(() => {
                    setTimeout(() => {
                      updateCursorPixelPosition(user.userId, user.cursorPosition);
                    }, 100);
                  });
                }
              });
            }, 300); // 增加延迟，确保Quill完全初始化
          }
        } catch (error) {
          console.error('获取在线用户列表失败:', error);
        }
      }
    };

    const handleUserLeave = async (message: WebSocketMessage) => {
      if (message.type === 'LEAVE' && message.userId !== userId) {
        // 用户离开处理
        console.log('用户离开:', message.userId);
        // 从在线用户列表中移除
        useDocumentStore.getState().removeOnlineUser(message.userId);
        
        // 重新获取在线用户列表以确保同步
        try {
          const result = await apiService.getOnlineUsers(documentId);
          if (result.code === 200 && result.data) {
            const users = result.data.map((user: any) => ({
              userId: user.userId,
              username: user.username || `用户${user.userId}`,
              nickname: user.nickname,
              avatar: user.avatar,
              color: user.color || `#${Math.floor(Math.random()*16777215).toString(16)}`,
              cursorPosition: user.position || undefined,
            }));
            useDocumentStore.getState().setOnlineUsers(users);
          }
        } catch (error) {
          console.error('获取在线用户列表失败:', error);
        }
      }
    };

    const handleDocumentUpdate = (message: WebSocketMessage) => {
      if (message.type === 'DOCUMENT_UPDATED') {
        const updateData = message.data as { content: string; version: number };
        if (updateData && updateData.content) {
          const quill = quillRef.current?.getEditor();
          if (quill) {
            setIsLocalChange(false);
            try {
              // 使用clipboard.convert来正确解析HTML内容（包括HTML实体）
              const delta = quill.clipboard.convert(updateData.content);
              quill.setContents(delta, 'silent');
            } catch (error) {
              console.error('更新文档内容失败:', error);
              // 如果clipboard.convert失败，尝试直接设置innerHTML作为备用方案
              try {
                quill.root.innerHTML = updateData.content;
              } catch (e) {
                console.error('备用更新方法也失败:', e);
              }
            }
            // 更新文档状态
            updateDocument({
              ...currentDocument!,
              content: updateData.content,
              version: updateData.version,
            });
            setTimeout(() => {
              setIsLocalChange(true);
            }, 100);
          }
        }
      }
    };

    // 检查WebSocket连接状态，如果未连接则等待
    const setupWebSocket = async () => {
      let retries = 0;
      const maxRetries = 10;
      
      while (!websocketService.getConnected() && retries < maxRetries) {
        await new Promise(resolve => setTimeout(resolve, 1000));
        retries++;
      }
      
      if (!websocketService.getConnected()) {
        return;
      }

      // 注册消息处理器
      websocketService.onMessage('OPERATION', handleRemoteOperation);
      websocketService.onMessage('CURSOR', handleCursorMove);
      websocketService.onMessage('JOIN', handleUserJoin);
      websocketService.onMessage('LEAVE', handleUserLeave);
      websocketService.onMessage('DOCUMENT_UPDATED', handleDocumentUpdate);

      // 加入文档编辑（等待连接完全建立）
      try {
        await websocketService.joinDocument(documentId);
        console.log('成功加入文档:', documentId);
      } catch (error) {
        console.error('加入文档失败:', error);
      }
      
      // 获取初始在线用户列表（延迟一点时间，确保后端已处理JOIN消息）
      const fetchOnlineUsers = async () => {
        try {
          console.log('获取在线用户列表，文档ID:', documentId);
          const result = await apiService.getOnlineUsers(documentId);
          console.log('在线用户列表API响应:', result);
          if (result.code === 200 && result.data) {
            // 将后端返回的用户数据转换为 OnlineUser 格式
            const users = result.data.map((user: any) => ({
              userId: user.userId,
              username: user.username || `用户${user.userId}`,
              nickname: user.nickname,
              avatar: user.avatar,
              color: user.color || `#${Math.floor(Math.random()*16777215).toString(16)}`,
              cursorPosition: user.position || undefined,
            }));
            console.log('设置在线用户列表:', users);
            useDocumentStore.getState().setOnlineUsers(users);
            
            // 更新光标位置（延迟执行，确保Quill完全初始化）
            // 使用多次延迟，确保DOM完全渲染
            setTimeout(() => {
              users.forEach((user: any) => {
                if (user.userId !== userId && user.cursorPosition !== undefined && user.cursorPosition !== null) {
                  // 更新store中的光标位置
                  updateUserCursor(user.userId, user.cursorPosition);
                  // 延迟更新光标像素位置，确保Quill完全渲染
                  requestAnimationFrame(() => {
                    setTimeout(() => {
                      updateCursorPixelPosition(user.userId, user.cursorPosition);
                    }, 100);
                  });
                }
              });
            }, 300); // 增加延迟，确保Quill完全初始化
          } else {
            console.warn('获取在线用户列表失败，响应码:', result.code, '数据:', result.data);
          }
        } catch (error) {
          console.error('获取在线用户列表失败:', error);
        }
      };
      
      // 立即获取一次，然后延迟再获取一次（确保后端已处理）
      fetchOnlineUsers();
      setTimeout(fetchOnlineUsers, 1000);
    };

    // 调用setupWebSocket
    setupWebSocket();

    // 清理函数
    return () => {
      // 清理防抖定时器
      if (cursorDebounceTimerRef.current) {
        clearTimeout(cursorDebounceTimerRef.current);
        cursorDebounceTimerRef.current = null;
      }
      
      websocketService.offMessage('OPERATION', handleRemoteOperation);
      websocketService.offMessage('CURSOR', handleCursorMove);
      websocketService.offMessage('JOIN', handleUserJoin);
      websocketService.offMessage('LEAVE', handleUserLeave);
      websocketService.offMessage('DOCUMENT_UPDATED', handleDocumentUpdate);
      websocketService.leaveDocument(documentId);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [documentId, userId]); // 只依赖 documentId 和 userId，避免重复执行

  // 定期更新光标位置和评论标记位置（当文档内容变化时）
  useEffect(() => {
    if (!quillRef.current || !isInitialized) return;

    const quill = quillRef.current.getEditor();
    const updatePositions = () => {
      // 获取 .ql-editor 元素（实际的内容区域）
      const editorElement = quill.root;
      if (!editorElement) return;
      
      // 获取 .collaborative-editor 容器（光标指示器的父容器）
      const collaborativeEditor = editorElement.closest('.collaborative-editor');
      if (!collaborativeEditor) return;
      
      const editorRect = editorElement.getBoundingClientRect();
      const containerRect = (collaborativeEditor as HTMLElement).getBoundingClientRect();
      
      // 更新用户光标位置
      onlineUsers.forEach((user) => {
        if (user.userId !== userId && user.cursorPosition !== undefined && user.cursorPosition !== null) {
          try {
            const bounds = quill.getBounds(user.cursorPosition, 0);
            if (bounds) {
              setCursorPositions((prev) => {
                const newMap = new Map(prev);
                // 计算相对于 .collaborative-editor 的位置
                newMap.set(user.userId, { 
                  top: bounds.top + editorRect.top - containerRect.top,
                  left: bounds.left + editorRect.left - containerRect.left
                });
                return newMap;
              });
            }
          } catch (error) {
            // 忽略错误，光标位置可能无效
          }
        }
      });

      // 更新评论标记位置
      comments.forEach((comment) => {
        if (comment.position !== undefined && comment.position > 0) {
          try {
            const bounds = quill.getBounds(comment.position, 0);
            if (bounds) {
              setCommentMarkers((prev) => {
                const newMap = new Map(prev);
                newMap.set(comment.id, { 
                  top: bounds.top + editorRect.top - containerRect.top,
                  left: bounds.left + editorRect.left - containerRect.left
                });
                return newMap;
              });
            }
          } catch (error) {
            // 忽略错误，评论位置可能无效
          }
        }
      });
    };

    // 初始更新
    updatePositions();

    // 监听内容变化和滚动
    const interval = setInterval(updatePositions, 100);
    
    // 监听滚动事件
    const scrollHandler = () => updatePositions();
    const scrollContainer = quill.root.parentElement?.parentElement;
    if (scrollContainer) {
      scrollContainer.addEventListener('scroll', scrollHandler);
    }

    return () => {
      clearInterval(interval);
      if (scrollContainer) {
        scrollContainer.removeEventListener('scroll', scrollHandler);
      }
    };
  }, [currentDocument?.content, onlineUsers, comments, userId, isInitialized]);

  // 将Quill Delta转换为操作DTO
  const convertDeltaToOperations = useCallback((delta: any): OperationDTO[] => {
    const operations: OperationDTO[] = [];
    let position = 0;

    delta.ops?.forEach((op: any) => {
      if (op.insert) {
        // 插入操作
        operations.push({
          type: 'INSERT',
          data: typeof op.insert === 'string' ? op.insert : '',
          position,
          length: typeof op.insert === 'string' ? op.insert.length : 0,
          timestamp: Date.now(),
          version: currentDocument?.version || 0,
          attributes: op.attributes,
        });
        position += typeof op.insert === 'string' ? op.insert.length : 0;
      } else if (op.delete) {
        // 删除操作
        operations.push({
          type: 'DELETE',
          position,
          length: op.delete,
          timestamp: Date.now(),
          version: currentDocument?.version || 0,
        });
        // 删除操作不改变位置
      } else if (op.retain) {
        // 保留操作（用于格式化）
        if (op.attributes && Object.keys(op.attributes).length > 0) {
          // 将attributes对象转换为formatType和formatValue
          // Quill的attributes可能是多个格式，我们需要为每个格式创建一个操作
          Object.keys(op.attributes).forEach((formatType) => {
            const formatValue = op.attributes[formatType];
            operations.push({
              type: 'FORMAT',
              position,
              length: op.retain,
              timestamp: Date.now(),
              version: currentDocument?.version || 0,
              formatType,
              formatValue,
              attributes: { [formatType]: formatValue },
            });
          });
        }
        position += op.retain;
      }
    });

    return operations;
  }, [currentDocument?.version]);

  // 中文等 IME：组合输入过程中不向服务器发增量 op（避免拼音首字母单独成条、与远端不一致）；
  // composition 结束后用「上次已同步 Delta」与当前文档做 diff，一次性补发并跳过后续一次重复增量。
  useEffect(() => {
    if (!isInitialized || readOnly) return;
    const quill = quillRef.current?.getEditor();
    if (!quill) return;
    const root = quill.root;

    const onCompositionStart = () => {
      isImeComposingRef.current = true;
    };

    const onCompositionEnd = () => {
      isImeComposingRef.current = false;
      if (imeFlushTimerRef.current != null) {
        window.clearTimeout(imeFlushTimerRef.current);
      }
      imeFlushTimerRef.current = window.setTimeout(() => {
        imeFlushTimerRef.current = null;
        const q = quillRef.current?.getEditor();
        if (!q || !isLocalChangeRef.current) return;
        const prev = lastBroadcastedDeltaRef.current;
        const cur = q.getContents();
        if (!prev) {
          lastBroadcastedDeltaRef.current = cur;
          return;
        }
        const diff = prev.diff(cur);
        if (diff.length() > 0) {
          const operations = convertDeltaToOperations(diff);
          if (operations.length > 0) {
            operations.forEach((op) => websocketService.sendOperation(op));
            // 若本轮已在定时器里补发，则忽略紧随其后的同一次 text-change 增量，避免重复
            skipNextTextChangeSendRef.current = true;
          }
        }
        lastBroadcastedDeltaRef.current = cur;
      }, 0);
    };

    root.addEventListener('compositionstart', onCompositionStart);
    root.addEventListener('compositionend', onCompositionEnd);
    return () => {
      root.removeEventListener('compositionstart', onCompositionStart);
      root.removeEventListener('compositionend', onCompositionEnd);
      if (imeFlushTimerRef.current != null) {
        window.clearTimeout(imeFlushTimerRef.current);
        imeFlushTimerRef.current = null;
      }
    };
  }, [isInitialized, readOnly, convertDeltaToOperations]);

  // 处理文本变化
  const handleChange = useCallback(
    (content: string, delta: any, source: string) => {
      // 只处理用户输入的变化，忽略程序触发的变化
      // source 可能是 'user', 'api', 'silent' 等
      if (source !== 'user' || !isLocalChange) {
        // 调试日志：记录被忽略的变化
        if (source !== 'user') {
          console.log('忽略非用户操作，source:', source);
        }
        if (!isLocalChange) {
          console.log('忽略非本地变更');
        }
        return;
      }

      const quill = quillRef.current?.getEditor();
      if (!quill) {
        return;
      }

      // 获取当前光标位置
      const selection = quill.getSelection();
      if (selection) {
        websocketService.sendCursorPosition(selection.index);
      }

      // 更新本地状态（标记为本地更新，避免触发内容重置）
      isLocalUpdateRef.current = true;
      updateDocumentContent(content);

      // Quill 内部与原生 composition 状态：组合输入期间不向服务器发 op，改由 compositionend 后 diff 补发
      const composing =
        isImeComposingRef.current === true ||
        (quill as unknown as { selection?: { composing?: boolean } }).selection?.composing === true;
      if (composing) {
        return;
      }

      // compositionend 的定时补发已发送本轮变更，跳过一次 Quill 紧随其后的 text-change 增量，避免重复
      if (skipNextTextChangeSendRef.current) {
        skipNextTextChangeSendRef.current = false;
        lastBroadcastedDeltaRef.current = quill.getContents();
        return;
      }

      const operations = convertDeltaToOperations(delta);
      if (operations.length > 0) {
        console.log('发送操作到服务器:', operations);
        operations.forEach((op) => {
          websocketService.sendOperation(op);
        });
      }
      lastBroadcastedDeltaRef.current = quill.getContents();
    },
    [isLocalChange, convertDeltaToOperations, updateDocumentContent]
  );

  // 处理光标选择变化（带防抖）
  const handleSelectionChange = useCallback((range: any) => {
    if (range && !readOnly && isInitialized) {
      // 清除之前的定时器
      if (cursorDebounceTimerRef.current) {
        clearTimeout(cursorDebounceTimerRef.current);
      }
      
      // 只有在初始化完成且 WebSocket 已连接时才发送光标位置
      if (websocketService.getConnected()) {
        // 使用防抖，避免频繁发送光标位置（100ms内只发送一次）
        cursorDebounceTimerRef.current = setTimeout(() => {
          websocketService.sendCursorPosition(range.index);
        }, 100);
      }
    }
  }, [readOnly, isInitialized]);

  // 保存文档
  const handleSave = useCallback(async () => {
    if (!currentDocument || !quillRef.current || saving) return;

    const quill = quillRef.current.getEditor();
    const content = quill.root.innerHTML; // 获取HTML内容

    setSaving(true);
    try {
      const result = await apiService.updateDocumentContent(documentId, content);
      if (result.code === 200 && result.data) {
        // 更新文档状态（包括版本号）
        updateDocument(result.data);
        message.success(`保存成功！版本号已更新为 ${result.data.version}`);
      } else {
        message.error(result.message || '保存失败');
      }
    } catch (error: any) {
      console.error('保存文档失败:', error);
      message.error(error.response?.data?.message || '保存失败，请稍后重试');
    } finally {
      setSaving(false);
    }
  }, [currentDocument, documentId, saving, updateDocument]);

  return (
    <div className="collaborative-editor">
      {/* 保存按钮工具栏 */}
      {!readOnly && (
        <div className="editor-toolbar">
          <Space>
            <Button
              type="primary"
              icon={<SaveOutlined />}
              onClick={handleSave}
              loading={saving}
              disabled={!currentDocument}
            >
              {saving ? '保存中...' : '保存'}
            </Button>
            {currentDocument && (
              <span className="version-info">版本: {currentDocument.version}</span>
            )}
          </Space>
        </div>
      )}
      <ReactQuill
        ref={quillRef}
        value={currentDocument?.content || ''}
        onChange={handleChange}
        onChangeSelection={handleSelectionChange}
        modules={modules}
        readOnly={readOnly}
        theme="snow"
        placeholder={readOnly ? '只读模式' : '开始编辑...'}
      />
      
      {/* 在线用户光标指示器 */}
      <div className="cursor-indicators">
        {onlineUsers
          .filter((user) => user.userId !== userId && user.cursorPosition !== undefined)
          .map((user) => {
            const pixelPos = cursorPositions.get(user.userId);
            if (!pixelPos) return null;
            
            return (
              <div
                key={user.userId}
                className="cursor-indicator"
                style={{
                  top: `${pixelPos.top}px`,
                  left: `${pixelPos.left}px`,
                  borderColor: user.color || '#667eea',
                }}
              >
                <span className="cursor-label" style={{ backgroundColor: user.color || '#667eea' }}>
                  {user.nickname || user.username}
                </span>
              </div>
            );
          })}
      </div>

      {/* 评论标记指示器 */}
      <div className="comment-markers">
        {comments
          .filter((comment) => comment.position !== undefined && comment.position > 0)
          .map((comment) => {
            const pixelPos = commentMarkers.get(comment.id);
            if (!pixelPos) return null;
            
            return (
              <div
                key={comment.id}
                className="comment-marker-indicator"
                style={{
                  top: `${pixelPos.top}px`,
                  left: `${pixelPos.left}px`,
                }}
                title={comment.content}
              >
                <span className="comment-marker-icon">💬</span>
                <span className="comment-marker-label">
                  {comment.user?.nickname || comment.user?.username}
                </span>
              </div>
            );
          })}
      </div>
    </div>
  );
};

export default CollaborativeEditor;
