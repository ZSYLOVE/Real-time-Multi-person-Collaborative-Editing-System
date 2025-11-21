/**
 * WebSocket服务
 * 使用SockJS + STOMP.js连接后端WebSocket
 */

// @ts-ignore - STOMP.js类型定义可能不完整
import { Client, IMessage } from '@stomp/stompjs';
// @ts-ignore - SockJS类型定义可能不完整
import SockJS from 'sockjs-client';
import type { WebSocketMessage, OperationDTO } from '@/types';

type MessageHandler = (message: WebSocketMessage) => void;

class WebSocketService {
  private client: Client | null = null;
  private documentId: number | null = null;
  private userId: number | null = null;
  private messageHandlers: Map<string, MessageHandler[]> = new Map();
  private isConnected: boolean = false;
  private subscriptions: Map<number, any> = new Map(); // 存储订阅引用

  /**
   * 连接WebSocket
   */
  connect(token: string, userId: number): Promise<void> {
    return new Promise((resolve, reject) => {
      this.userId = userId;

      // 使用SockJS创建WebSocket连接
      // 在URL中添加token参数，确保握手时能获取到token
      const baseUrl = import.meta.env.VITE_WS_URL || 'http://localhost:8080/ws';
      const wsUrl = `${baseUrl}?token=${encodeURIComponent(token)}`;
      const socket = new SockJS(wsUrl);
      
      this.client = new Client({
        webSocketFactory: () => socket,
        reconnectDelay: 5000,
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
        debug: (str: string) => {
          // 只记录错误和重要事件，忽略常规消息
          if (str.includes('ERROR') || str.includes('CONNECTED') || str.includes('DISCONNECT')) {
            console.log('[STOMP]', str);
          }
        },
        onConnect: () => {
          console.log('WebSocket连接成功');
          this.isConnected = true;
          resolve();
        },
        onStompError: (frame: any) => {
          console.error('STOMP错误:', frame);
          this.isConnected = false;
          reject(new Error(frame.headers?.['message'] || 'WebSocket连接失败'));
        },
        onWebSocketClose: () => {
          console.log('WebSocket连接关闭');
          this.isConnected = false;
        },
        onDisconnect: () => {
          console.log('WebSocket断开连接');
          this.isConnected = false;
        },
      });

      // 同时设置连接头（传递JWT Token），用于STOMP CONNECT帧
      this.client.configure({
        connectHeaders: {
          Authorization: `Bearer ${token}`,
        },
      });

      this.client.activate();
    });
  }

  /**
   * 断开连接
   */
  disconnect(): void {
    // 取消所有订阅
    this.subscriptions.forEach((subscription) => {
      try {
        subscription.unsubscribe();
      } catch (error) {
        // 忽略取消订阅时的错误
      }
    });
    this.subscriptions.clear();
    
    if (this.client) {
      this.client.deactivate();
      this.client = null;
    }
    this.isConnected = false;
    this.documentId = null;
    this.messageHandlers.clear();
  }

  /**
   * 加入文档编辑
   */
  joinDocument(documentId: number): void {
    if (!this.client || !this.isConnected) {
      console.error('WebSocket未连接');
      return;
    }

    this.documentId = documentId;

    // 发送加入消息
    const joinMessage = {
      type: 'JOIN',
      documentId,
      userId: this.userId,
      timestamp: Date.now(),
    };
    this.client.publish({
      destination: '/app/document/join',
      body: JSON.stringify(joinMessage),
    });

    // 订阅文档更新（避免重复订阅）
    if (this.subscriptions.has(documentId)) {
      return; // 已经订阅过，跳过
    }
    
    const subscription = this.client.subscribe(`/topic/document/${documentId}`, (message: IMessage) => {
      try {
        const data: WebSocketMessage = JSON.parse(message.body);
        this.handleMessage(data);
      } catch (error) {
        console.error('解析WebSocket消息失败:', error);
      }
    });
    
    // 保存订阅引用
    this.subscriptions.set(documentId, subscription);
  }

  /**
   * 离开文档编辑
   */
  leaveDocument(documentId: number): void {
    if (!this.client || !this.isConnected) {
      return;
    }

    this.client.publish({
      destination: '/app/document/leave',
      body: JSON.stringify({
        type: 'LEAVE',
        documentId,
        userId: this.userId,
        timestamp: Date.now(),
      }),
    });

    // 取消订阅
    const subscription = this.subscriptions.get(documentId);
    if (subscription) {
      subscription.unsubscribe();
      this.subscriptions.delete(documentId);
    }

    this.documentId = null;
  }

  /**
   * 发送操作
   */
  sendOperation(operation: OperationDTO): void {
    if (!this.client || !this.isConnected || !this.documentId) {
      console.error('WebSocket未连接或未加入文档', {
        client: !!this.client,
        connected: this.isConnected,
        documentId: this.documentId,
      });
      return;
    }

    const message = {
      type: 'OPERATION',
      documentId: this.documentId,
      userId: this.userId,
      data: operation,
      timestamp: Date.now(),
    };
    this.client.publish({
      destination: '/app/document/operation',
      body: JSON.stringify(message),
    });
  }

  /**
   * 发送光标位置
   */
  sendCursorPosition(position: number): void {
    if (!this.client || !this.isConnected || !this.documentId) {
      console.warn('无法发送光标位置，WebSocket未连接或未加入文档');
      return;
    }

    const message = {
      type: 'CURSOR',
      documentId: this.documentId,
      userId: this.userId,
      data: { position },
      timestamp: Date.now(),
    };
    this.client.publish({
      destination: '/app/document/cursor',
      body: JSON.stringify(message),
    });
  }

  /**
   * 注册消息处理器
   */
  onMessage(type: string, handler: MessageHandler): void {
    if (!this.messageHandlers.has(type)) {
      this.messageHandlers.set(type, []);
    }
    // 避免重复注册相同的处理器
    const handlers = this.messageHandlers.get(type)!;
    if (!handlers.includes(handler)) {
      handlers.push(handler);
    }
  }

  /**
   * 移除消息处理器
   */
  offMessage(type: string, handler: MessageHandler): void {
    const handlers = this.messageHandlers.get(type);
    if (handlers) {
      const index = handlers.indexOf(handler);
      if (index > -1) {
        handlers.splice(index, 1);
        if (handlers.length === 0) {
          this.messageHandlers.delete(type);
        }
      }
    }
  }


  /**
   * 处理接收到的消息
   */
  private handleMessage(message: WebSocketMessage): void {
    const handlers = this.messageHandlers.get(message.type);
    if (handlers && handlers.length > 0) {
      handlers.forEach((handler) => {
        try {
          handler(message);
        } catch (error) {
          console.error('消息处理器执行失败:', error);
        }
      });
    }
  }

  /**
   * 获取连接状态
   */
  getConnected(): boolean {
    return this.isConnected;
  }

  /**
   * 获取当前文档ID
   */
  getDocumentId(): number | null {
    return this.documentId;
  }
}

export const websocketService = new WebSocketService();

