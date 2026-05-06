import React, { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { Button, Input, List, Card, message, Spin, Typography } from 'antd';
import useAuthStore from '@/stores/authStore';
import { apiService } from '@/services/api';
import { websocketService } from '@/services/websocket';
import type { ChatMessage, ChatRoom, User, WebSocketMessage } from '@/types';

const { Text } = Typography;

const Chat: React.FC = () => {
  const { user } = useAuthStore();

  const [keyword, setKeyword] = useState('');
  const [loadingUsers, setLoadingUsers] = useState(false);
  const [users, setUsers] = useState<User[]>([]);

  const [toUser, setToUser] = useState<User | null>(null);
  const [room, setRoom] = useState<ChatRoom | null>(null);
  const [recentRooms, setRecentRooms] = useState<Array<any>>([]);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [loadingMessages, setLoadingMessages] = useState(false);
  const [sending, setSending] = useState(false);
  const [content, setContent] = useState('');

  const listEndRef = useRef<HTMLDivElement | null>(null);
  const messagesScrollRef = useRef<HTMLDivElement | null>(null);

  const roomId = room?.id ?? null;
  const activeRoomIdRef = useRef<number | null>(null);

  const [loadingOlder, setLoadingOlder] = useState(false);
  const [hasMoreHistory, setHasMoreHistory] = useState(true);
  const [earliestCreatedAtMs, setEarliestCreatedAtMs] = useState<number | null>(null);

  const parseCreatedAtMs = (v: any): number => {
    if (!v) return 0;
    if (typeof v === 'number') return v;
    const s = String(v);
    // 支持 'yyyy-MM-dd HH:mm:ss' -> 'yyyy-MM-ddTHH:mm:ss'
    const normalized = s.includes(' ') ? s.replace(' ', 'T') : s;
    const t = new Date(normalized).getTime();
    return Number.isFinite(t) ? t : 0;
  };

  useEffect(() => {
    // 列表滚到底部
    listEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages.length]);

  // 使用 useLayoutEffect：减少“房间切换瞬间 websocket 推送先到”的竞态窗口
  useLayoutEffect(() => {
    activeRoomIdRef.current = roomId;
  }, [roomId]);

  const searchUsers = async () => {
    const trimmed = keyword.trim();
    if (!trimmed) {
      setUsers([]);
      return;
    }
    setLoadingUsers(true);
    try {
      const result = await apiService.searchUsers(trimmed);
      if (result.code === 200 && result.data) {
        setUsers(result.data);
      } else {
        setUsers([]);
      }
    } catch (e) {
      message.error('搜索用户失败');
    } finally {
      setLoadingUsers(false);
    }
  };

  // 模糊查询：输入变化后自动（debounce）查询，避免每次按键都请求
  useEffect(() => {
    const t = window.setTimeout(() => {
      // 复用 searchUsers 的逻辑
      searchUsers();
    }, 300);

    return () => {
      window.clearTimeout(t);
    };
    // 只依赖 keyword；searchUsers 会引用 keyword，所以不把它放进依赖数组
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [keyword]);

  const openRoomWithUser = async (target: User) => {
    if (!user?.id) return;
    setToUser(target);
    setRoom(null);
    setMessages([]);
    try {
      const res = await apiService.getChatRoom(target.id);
      if (res.code === 200 && res.data) {
        // 兼容后端返回字段
        const d: any = res.data;
        setRoom({
          id: d.id ?? d.roomId,
          userAId: d.userAId,
          userBId: d.userBId,
          createdAt: d.createdAt,
        });
      } else {
        message.error(res.message || '获取会话失败');
      }
    } catch (e) {
      message.error('获取会话失败');
    }
  };

  const loadHistory = async (rid: number) => {
    setLoadingMessages(true);
    try {
      const res = await apiService.getChatMessages(rid, { limit: 50 });
      if (res.code === 200) {
        const history = res.data ?? [];
        if (!Array.isArray(history)) {
          setMessages([]);
          return;
        }
        setMessages(prev => {
          // 避免“订阅先到、历史后到”导致实时消息被历史覆盖
          const existing = new Set(prev.map(x => x.id));
          const merged = [...prev];
          for (const msg of history as ChatMessage[]) {
            if (!existing.has(msg.id)) {
              merged.push(msg);
            }
          }
          merged.sort((a, b) => {
            const ta = parseCreatedAtMs(a.createdAt);
            const tb = parseCreatedAtMs(b.createdAt);
            return ta - tb;
          });
          return merged;
        });

        // 初始化“最早消息时间”，用于向上翻页
        const min = (history as ChatMessage[]).reduce<number>((acc, m) => {
          const t = parseCreatedAtMs(m.createdAt);
          if (!acc || t < acc) return t;
          return acc;
        }, 0);
        if (min > 0) setEarliestCreatedAtMs(min);
      } else {
        setMessages([]);
      }
    } catch (e) {
      message.error('加载消息失败');
    } finally {
      setLoadingMessages(false);
    }
  };

  // 用户级订阅：保证“未打开会话时”也能更新未读与最近联系人
  useEffect(() => {
    if (!user?.id) return;

    const handler = (m: WebSocketMessage) => {
      if (m.type !== 'CHAT_MESSAGE') return;
      if (!m.documentId) return;

      const rid = m.documentId;
      const dataAny: any = m.data;
      if (!dataAny) return;

      const incoming: ChatMessage = {
        id: dataAny.id ?? 0,
        roomId: rid,
        senderId: dataAny.senderId ?? m.userId,
        content: dataAny.content ?? '',
        createdAt: dataAny.createdAt,
      };

      // 直接用当前 state 的 roomId 判断“正在查看该会话”
      if (roomId && rid === roomId) {
        // 当前打开的会话：直接追加到消息列表（未读保持0）
        setMessages(prev => {
          // 兜底去重：避免同一条消息因多次广播/重复触发而重复渲染
          // 优先用 id + roomId（createdAt 在不同序列化形式下可能不稳定）
          const hasExisting = prev.some((m) => m.roomId === incoming.roomId && m.id === incoming.id);
          if (hasExisting) return prev;
          return [...prev, incoming];
        });
        return;
      }

      // 未打开会话：更新最近联系人未读数与最后一条消息
      setRecentRooms(prev => {
        const next = [...prev];
        const idx = next.findIndex(x => x.roomId === rid);
        const isMe = user.id === incoming.senderId;

        const unreadInc = isMe ? 0 : 1;
        if (idx >= 0) {
          const old = next[idx];
          const oldUnread = Number(old.unreadCount ?? 0);
          next[idx] = {
            ...old,
            lastMessage: incoming.content,
            lastMessageCreatedAt: incoming.createdAt,
            unreadCount: oldUnread + unreadInc,
          };
        } else {
          // 如果最近列表里不存在该会话，兜底创建一个条目
          next.push({
            roomId: rid,
            withUserId: incoming.senderId,
            withUsername: '',
            withNickname: '',
            withAvatar: '',
            unreadCount: unreadInc,
            lastMessage: incoming.content,
            lastMessageCreatedAt: incoming.createdAt,
          });
        }

        // 重新排序：按最后消息时间
        next.sort((a, b) => {
          const ta = a.lastMessageCreatedAt ? new Date(a.lastMessageCreatedAt).getTime() : 0;
          const tb = b.lastMessageCreatedAt ? new Date(b.lastMessageCreatedAt).getTime() : 0;
          return tb - ta;
        });
        return next;
      });
    };

    websocketService.onMessage('CHAT_MESSAGE', handler);

    websocketService.joinUserChat();
    return () => {
      websocketService.offMessage('CHAT_MESSAGE', handler);
    };
  }, [user?.id, roomId]);

  // 打开会话后：拉取历史 + 标记已读（清零未读）
  useEffect(() => {
    if (!roomId) return;

    // 乐观清零：避免“切换到会话后”的短暂窗口期内，收到新消息先把未读数加回来
    setRecentRooms(prev =>
      prev.map(x => (x.roomId === roomId ? { ...x, unreadCount: 0 } : x))
    );

    loadHistory(roomId);
    setHasMoreHistory(true);
    setEarliestCreatedAtMs(null);
    apiService.markChatRoomRead(roomId)
      .then(() => {
        setRecentRooms(prev =>
          prev.map(x => (x.roomId === roomId ? { ...x, unreadCount: 0 } : x))
        );
      })
      .catch(() => {
        // 不阻塞
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roomId]);

  const loadOlder = async () => {
    if (!roomId) return;
    if (loadingOlder) return;
    if (!hasMoreHistory) return;
    const before = earliestCreatedAtMs;
    // 没有 earliest，就无法翻页
    if (!before) return;

    setLoadingOlder(true);
    try {
      const container = messagesScrollRef.current;
      const prevScrollHeight = container?.scrollHeight ?? 0;
      const prevScrollTop = container?.scrollTop ?? 0;

      const res = await apiService.getChatMessagesBefore(roomId, { before, limit: 50 });
      if (res.code !== 200) return;

      const history = (res.data ?? []) as ChatMessage[];
      if (!Array.isArray(history) || history.length === 0) {
        setHasMoreHistory(false);
        return;
      }

      setMessages(prev => {
        const existing = new Set(prev.map(x => x.id));
        const merged = [...prev];
        for (const msg of history) {
          if (!existing.has(msg.id)) {
            merged.unshift(msg);
          }
        }
        merged.sort((a, b) => parseCreatedAtMs(a.createdAt) - parseCreatedAtMs(b.createdAt));
        return merged;
      });

      // 更新 earliest
      const min = history.reduce<number>((acc, m) => {
        const t = parseCreatedAtMs(m.createdAt);
        if (!acc || t < acc) return t;
        return acc;
      }, 0);
      if (min > 0) setEarliestCreatedAtMs(min);

      if (history.length < 50) {
        setHasMoreHistory(false);
      }

      // 等待 DOM 更新后，保持滚动位置不跳动
      await new Promise(resolve => requestAnimationFrame(() => resolve(null)));
      await new Promise(resolve => requestAnimationFrame(() => resolve(null)));
      if (container) {
        const nextScrollHeight = container.scrollHeight;
        const delta = nextScrollHeight - prevScrollHeight;
        container.scrollTop = prevScrollTop + delta;
      }
    } catch (e) {
      // 静默失败
    } finally {
      setLoadingOlder(false);
    }
  };

  const onMessagesScroll = () => {
    const el = messagesScrollRef.current;
    if (!el) return;
    if (el.scrollTop <= 0) {
      loadOlder();
    }
  };

  // 初始化最近联系人列表
  useEffect(() => {
    if (!user?.id) return;
    apiService.getChatRooms().then(res => {
      if (res.code === 200 && Array.isArray(res.data)) {
        setRecentRooms(res.data);
      } else {
        setRecentRooms([]);
      }
    }).catch(() => setRecentRooms([]));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user?.id]);

  const send = async () => {
    if (!roomId) return;
    const trimmed = content.trim();
    if (!trimmed) return;
    setSending(true);
    try {
      websocketService.sendChatMessage(roomId, trimmed);
      setContent('');
    } finally {
      setSending(false);
    }
  };

  const filteredUsers = useMemo(() => {
    if (!user?.id) return users;
    return users.filter(u => u.id !== user.id);
  }, [users, user?.id]);

  return (
    <div
      style={{
        display: 'flex',
        gap: 16,
        padding: 16,
        height: 'calc(100vh - 120px)',
        overflow: 'hidden',
      }}
    >
      <Card style={{ width: 320, minHeight: 0, height: '100%', overflow: 'hidden' }} title="选择用户">
        <Input.Search
          placeholder="搜索用户（用户名/昵称）"
          value={keyword}
          onChange={e => setKeyword(e.target.value)}
          loading={loadingUsers}
          allowClear
        />

        <div style={{ marginTop: 12, overflow: 'hidden' }}>
          <div style={{ marginBottom: 8, fontWeight: 600 }}>最近联系人</div>
          <List
            bordered
            dataSource={recentRooms}
            locale={{ emptyText: '暂无聊天记录' }}
            renderItem={(item) => (
              <List.Item
                style={{ cursor: 'pointer' }}
                onClick={() => {
                  // 用会话里的 withUserId 重新打开
                  // 由于最近列表可能没有头像/昵称（兜底场景），这里走 withUserId -> 获取会话用户兜底
                  const withId = item.withUserId as number;
                  if (!withId || !user?.id) return;
                  // 简化：直接用当前会话roomId打开
                  setRoom({ id: item.roomId } as any);
                  setToUser({
                    id: withId,
                    username: item.withUsername ?? '',
                    nickname: item.withNickname ?? '',
                    email: '',
                    avatar: item.withAvatar ?? '',
                  });
                  // 拉取会话信息/历史会在房间 effect 中完成；如果需要完整用户信息可再补接口
                }}
              >
                <List.Item.Meta
                  title={
                    <span>
                      {item.withNickname || item.withUsername || '未知用户'}
                    </span>
                  }
                  description={item.lastMessage || '暂无消息'}
                />
              </List.Item>
            )}
          />

          {keyword.trim() ? (
            <>
              <div style={{ marginTop: 12, marginBottom: 8, fontWeight: 600 }}>搜索用户</div>
              <div style={{ overflowY: 'auto', maxHeight: 320 }}>
                <List
                  bordered
                  dataSource={filteredUsers}
                  locale={{ emptyText: '暂无结果' }}
                  renderItem={(item) => (
                    <List.Item
                      style={{ cursor: 'pointer' }}
                      onClick={() => openRoomWithUser(item)}
                    >
                      <List.Item.Meta
                        title={item.nickname || item.username}
                        description={item.email}
                      />
                    </List.Item>
                  )}
                />
              </div>
            </>
          ) : null}
        </div>
      </Card>

      <Card
        style={{ flex: 1, minHeight: 0, overflow: 'hidden', height: '100%' }}
        bodyStyle={{ padding: 0, display: 'flex', flexDirection: 'column', minHeight: 0, height: '100%' }}
        title={
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
            {toUser ? ('与 ' + (toUser.nickname || toUser.username) + ' 聊天') : '实时聊天'}
          </span>
        }
        headStyle={{ borderBottom: 'none' }}
      >
        {!roomId ? (
          <div style={{ padding: 24 }}>
            <Text type="secondary">左侧选择一个用户，即可开始一对一聊天。</Text>
          </div>
        ) : (
          <>
            {loadingMessages && <Spin />}
            <div
              ref={messagesScrollRef}
              style={{ flex: 1, minHeight: 0, overflow: 'auto', padding: '0 8px' }}
              onScroll={onMessagesScroll}
            >
              <List
                dataSource={messages}
                split={false}
                renderItem={(m) => {
                  const isMe = user?.id === m.senderId;
                  return (
                    <List.Item
                      style={{ justifyContent: isMe ? 'flex-end' : 'flex-start', borderBottom: 'none' }}
                    >
                      <div
                        style={{
                          maxWidth: 520,
                          background: isMe ? '#1890ff' : '#f5f5f5',
                          color: isMe ? '#fff' : '#000',
                          borderRadius: 12,
                          padding: '10px 12px',
                        }}
                      >
                        <div style={{ whiteSpace: 'pre-wrap' }}>{m.content}</div>
                        {m.createdAt && (
                          <div style={{ fontSize: 12, marginTop: 6, opacity: 0.8 }}>
                            {String(m.createdAt)}
                          </div>
                        )}
                      </div>
                    </List.Item>
                  );
                }}
              />
              <div ref={listEndRef} />
            </div>

            <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
              <Input
                placeholder="输入消息..."
                value={content}
                onChange={(e) => setContent(e.target.value)}
                onPressEnter={send}
              />
              <Button type="primary" onClick={send} loading={sending}>
                发送
              </Button>
            </div>
          </>
        )}
      </Card>
    </div>
  );
};

export default Chat;

