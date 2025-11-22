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
  async joinDocument(documentId: number): Promise<void> {
    if (!this.client) {
      console.error('WebSocket客户端未初始化');
      return;
    }

    // 等待STOMP连接完全建立
    let retries = 0;
    const maxRetries = 10;
    while ((!this.isConnected || !this.client.connected) && retries < maxRetries) {
      await new Promise(resolve => setTimeout(resolve, 100));
      retries++;
    }

    if (!this.isConnected || !this.client.connected) {
      console.error('WebSocket未连接或STOMP连接未建立', {
        isConnected: this.isConnected,
        clientConnected: this.client?.connected,
      });
      return;
    }

    this.documentId = documentId;

    try {
      // 先订阅文档更新（避免重复订阅）
      if (this.subscriptions.has(documentId)) {
        console.log('已经订阅过该文档，跳过重复订阅');
      } else {
        console.log(`订阅文档更新: /topic/document/${documentId}`);
        const subscription = this.client.subscribe(`/topic/document/${documentId}`, (message: IMessage) => {
          try {
            console.log('收到WebSocket消息:', message.body);
            const data: WebSocketMessage = JSON.parse(message.body);
            console.log('解析后的消息:', data);
            this.handleMessage(data);
          } catch (error) {
            console.error('解析WebSocket消息失败:', error, message.body);
          }
        });
        
        // 保存订阅引用
        this.subscriptions.set(documentId, subscription);
        console.log('文档订阅成功，订阅ID:', documentId);
        
        // 等待订阅完全建立（手机端可能需要更多时间，使用更长的延迟）
        // STOMP订阅是异步的，需要等待订阅确认
        await new Promise(resolve => setTimeout(resolve, 300));
      }

      // 订阅完成后再发送加入消息
      const joinMessage = {
        type: 'JOIN',
        documentId,
        userId: this.userId,
        timestamp: Date.now(),
      };
      console.log('发送加入文档消息:', joinMessage);
      this.client.publish({
        destination: '/app/document/join',
        body: JSON.stringify(joinMessage),
      });
      console.log('加入文档消息已发送');
    } catch (error) {
      console.error('加入文档失败:', error);
      throw error;
    }
  }

  /**
   * 离开文档编辑
   */
  leaveDocument(documentId: number): void {
    if (!this.client || !this.isConnected || !this.client.connected) {
      return;
    }

    try {
      this.client.publish({
        destination: '/app/document/leave',
        body: JSON.stringify({
          type: 'LEAVE',
          documentId,
          userId: this.userId,
          timestamp: Date.now(),
        }),
      });
    } catch (error) {
      console.error('发送离开消息失败:', error);
    }

    // 取消订阅
    const subscription = this.subscriptions.get(documentId);
    if (subscription) {
      try {
        subscription.unsubscribe();
      } catch (error) {
        console.error('取消订阅失败:', error);
      }
      this.subscriptions.delete(documentId);
    }

    this.documentId = null;
  }

  /**
   * 发送操作
   */
  sendOperation(operation: OperationDTO): void {
    if (!this.client || !this.isConnected || !this.client.connected || !this.documentId) {
      console.error('WebSocket未连接或未加入文档', {
        client: !!this.client,
        connected: this.isConnected,
        clientConnected: this.client?.connected,
        documentId: this.documentId,
      });
      return;
    }

    try {
      const message = {
        type: 'OPERATION',
        documentId: this.documentId,
        userId: this.userId,
        data: operation,
        timestamp: Date.now(),
      };
      console.log('发送编辑操作:', message);
      this.client.publish({
        destination: '/app/document/operation',
        body: JSON.stringify(message),
      });
      console.log('编辑操作已发送');
    } catch (error) {
      console.error('发送操作失败:', error);
    }
  }

  /**
   * 发送光标位置
   */
  sendCursorPosition(position: number): void {
    if (!this.client || !this.isConnected || !this.client.connected || !this.documentId) {
      console.warn('无法发送光标位置，WebSocket未连接或未加入文档', {
        client: !!this.client,
        connected: this.isConnected,
        clientConnected: this.client?.connected,
        documentId: this.documentId,
      });
      return;
    }

    try {
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
    } catch (error) {
      console.error('发送光标位置失败:', error);
    }
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

