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
  const { currentDocument, updateDocumentContent, updateDocument, onlineUsers, updateUserCursor, comments } = useDocumentStore();

  // Quill工具栏配置
  const modules = {
    toolbar: readOnly
      ? false
      : [
          [{ header: [1, 2, 3, false] }],
          ['bold', 'italic', 'underline', 'strike'],
          [{ color: [] }, { background: [] }],
          [{ list: 'ordered' }, { list: 'bullet' }],
          ['link', 'image'],
          ['clean'],
        ],
  };

  // 注册评论标记Blot（用于在文档中高亮显示评论位置）
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
        } else if (currentDocument.content) {
          // 如果是Delta格式，使用setContents（需要是Delta对象）
          try {
            const deltaContent = typeof currentDocument.content === 'string' 
              ? JSON.parse(currentDocument.content) 
              : currentDocument.content;
            quill.setContents(deltaContent, 'silent');
            initializedContentRef.current = contentString;
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
          }
        } catch (e) {
          console.error('备用初始化方法也失败:', e);
          // 最后的备用方案：直接设置innerHTML
          if (typeof currentDocument.content === 'string') {
            quill.root.innerHTML = currentDocument.content;
            initializedContentRef.current = contentString;
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
    }
  }, [currentDocument?.content, isInitialized]);

  // 应用远程操作
  const applyRemoteOperation = useCallback((operation: OperationDTO) => {
    const quill = quillRef.current?.getEditor();
    if (!quill) {
      return;
    }

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
      
      // 操作应用后，更新文档内容状态（不触发 onChange）
      const content = quill.root.innerHTML;
      updateDocumentContent(content);
      
      // 更新所有用户的光标位置（延迟执行，确保DOM已更新）
      setTimeout(() => {
        const editorElement = quill.root;
        const collaborativeEditor = editorElement.closest('.collaborative-editor');
        if (!collaborativeEditor) return;
        
        const editorRect = editorElement.getBoundingClientRect();
        const containerRect = (collaborativeEditor as HTMLElement).getBoundingClientRect();
        
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
      }, 10);
    } catch (error) {
      console.error('应用远程操作失败:', error, operation);
    } finally {
      // 立即恢复本地变更标志（不使用延迟，避免影响后续操作）
      setIsLocalChange(wasLocalChange);
    }
  }, [onlineUsers, userId, updateDocumentContent, isLocalChange]);

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
        updateUserCursor(message.userId, position);
        
        // 更新光标像素位置（延迟执行，确保DOM已更新）
        setTimeout(() => {
          const quill = quillRef.current?.getEditor();
          if (quill && position !== undefined && position !== null) {
            try {
              const bounds = quill.getBounds(position, 0);
              if (bounds) {
                // 获取 .ql-editor 元素和 .collaborative-editor 容器
                const editorElement = quill.root;
                const collaborativeEditor = editorElement.closest('.collaborative-editor');
                if (collaborativeEditor) {
                  const editorRect = editorElement.getBoundingClientRect();
                  const containerRect = (collaborativeEditor as HTMLElement).getBoundingClientRect();
                  
                  setCursorPositions((prev) => {
                    const newMap = new Map(prev);
                    // 计算相对于 .collaborative-editor 的位置
                    newMap.set(message.userId, { 
                      top: bounds.top + editorRect.top - containerRect.top,
                      left: bounds.left + editorRect.left - containerRect.left
                    });
                    return newMap;
                  });
                }
              }
            } catch (error) {
              console.error('获取光标位置失败:', error);
            }
          }
        }, 10);
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
            
            // 更新光标位置
            setTimeout(() => {
              users.forEach((user: any) => {
                if (user.userId !== userId && user.cursorPosition !== undefined) {
                  updateUserCursor(user.userId, user.cursorPosition);
                }
              });
            }, 100);
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
            
            // 更新光标位置
            setTimeout(() => {
              users.forEach((user: any) => {
                if (user.userId !== userId && user.cursorPosition !== undefined) {
                  updateUserCursor(user.userId, user.cursorPosition);
                }
              });
            }, 100);
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

      // 将Quill Delta转换为操作DTO
      const operations = convertDeltaToOperations(delta);
      
      // 发送操作到服务器
      if (operations.length > 0) {
        console.log('发送操作到服务器:', operations);
        operations.forEach((op) => {
          websocketService.sendOperation(op);
        });
      }

      // 更新本地状态（标记为本地更新，避免触发内容重置）
      isLocalUpdateRef.current = true;
      updateDocumentContent(content);
    },
    [isLocalChange, convertDeltaToOperations, updateDocumentContent]
  );

  // 处理光标选择变化
  const handleSelectionChange = useCallback((range: any) => {
    if (range && !readOnly && isInitialized) {
      // 只有在初始化完成且 WebSocket 已连接时才发送光标位置
      if (websocketService.getConnected()) {
        websocketService.sendCursorPosition(range.index);
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
