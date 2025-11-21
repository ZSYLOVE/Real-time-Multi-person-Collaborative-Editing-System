/**
 * 评论面板组件
 */

import React, { useEffect, useState, useCallback } from 'react';
import { List, Input, Button, Avatar, message, Popconfirm, Tag, Space } from 'antd';
import { SendOutlined, DeleteOutlined, UserOutlined, CheckOutlined, MessageOutlined } from '@ant-design/icons';
import { apiService } from '@/services/api';
import useDocumentStore from '@/stores/documentStore';
import useAuthStore from '@/stores/authStore';
import { websocketService } from '@/services/websocket';
import type { Comment, WebSocketMessage } from '@/types';
import './CommentPanel.css';

const { TextArea } = Input;

interface CommentPanelProps {
  documentId: number;
}

const CommentPanel: React.FC<CommentPanelProps> = ({ documentId }) => {
  const { user } = useAuthStore();
  const { comments, setComments, addComment, removeComment, currentDocument } = useDocumentStore();
  const [commentText, setCommentText] = useState('');
  const [replyText, setReplyText] = useState<{ [key: number]: string }>({});
  const [replyingTo, setReplyingTo] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    loadComments();
  }, [documentId]);

  // 监听WebSocket评论消息
  useEffect(() => {
    const handleCommentMessage = (wsMessage: WebSocketMessage) => {
      // 如果是自己发送的评论，不处理（因为已经在本地添加了）
      if (wsMessage.userId === user?.id) {
        return;
      }

      if (wsMessage.type === 'COMMENT' && wsMessage.documentId === documentId) {
        const commentData = wsMessage.data as Comment;
        if (commentData) {
          // 检查评论是否已存在（避免重复添加）
          const currentComments = useDocumentStore.getState().comments;
          const existingComment = currentComments.find(c => c.id === commentData.id);
          if (!existingComment) {
            addComment(commentData);
            message.success('收到新评论');
          }
        }
      } else if (wsMessage.type === 'COMMENT_DELETED' && wsMessage.documentId === documentId) {
        const commentId = (wsMessage.data as any)?.id;
        if (commentId) {
          removeComment(commentId);
        }
      } else if (wsMessage.type === 'COMMENT_UPDATED' && wsMessage.documentId === documentId) {
        const commentData = wsMessage.data as Comment;
        if (commentData) {
          const currentComments = useDocumentStore.getState().comments;
          setComments(currentComments.map(c => c.id === commentData.id ? commentData : c));
        }
      }
    };
    
    websocketService.onMessage('COMMENT', handleCommentMessage);
    websocketService.onMessage('COMMENT_DELETED', handleCommentMessage);
    websocketService.onMessage('COMMENT_UPDATED', handleCommentMessage);
    
    return () => {
      websocketService.offMessage('COMMENT', handleCommentMessage);
      websocketService.offMessage('COMMENT_DELETED', handleCommentMessage);
      websocketService.offMessage('COMMENT_UPDATED', handleCommentMessage);
    };
  }, [documentId, addComment, removeComment, setComments, user?.id]);

  const loadComments = async () => {
    try {
      const result = await apiService.getDocumentComments(documentId);
      if (result.code === 200 && result.data) {
        setComments(result.data);
      }
    } catch (error) {
      console.error('加载评论失败:', error);
    }
  };

  const handleSubmitComment = async (position?: number, parentId?: number) => {
    const text = parentId ? replyText[parentId] : commentText;
    if (!text?.trim()) {
      message.warning('请输入评论内容');
      return;
    }

    if (!user?.id) {
      message.error('用户信息未加载');
      return;
    }

    setLoading(true);
    try {
      // 确保position有默认值（0表示没有特定位置）
      const commentPosition = position !== undefined && position > 0 ? position : 0;
      const result = await apiService.addComment(documentId, user.id, text.trim(), commentPosition, parentId);
      if (result.code === 200 && result.data) {
        // 检查评论是否已存在（避免重复添加）
        const existingComment = comments.find(c => c.id === result.data.id);
        if (!existingComment) {
          addComment(result.data);
        }
        if (parentId) {
          setReplyText({ ...replyText, [parentId]: '' });
          setReplyingTo(null);
        } else {
          setCommentText('');
        }
        message.success('评论成功');
      } else {
        message.error(result.message || '评论失败');
      }
    } catch (error: any) {
      message.error(error.response?.data?.message || '评论失败');
    } finally {
      setLoading(false);
    }
  };

  const handleReply = (commentId: number) => {
    setReplyingTo(commentId);
    if (!replyText[commentId]) {
      setReplyText({ ...replyText, [commentId]: '' });
    }
  };

  const handleCancelReply = (commentId: number) => {
    setReplyingTo(null);
    setReplyText({ ...replyText, [commentId]: '' });
  };

  const handleResolveComment = async (commentId: number, isResolved: boolean) => {
    try {
      const result = await apiService.updateComment(commentId, { isResolved });
      if (result.code === 200 && result.data) {
        setComments(comments.map(c => c.id === commentId ? { ...c, isResolved } : c));
        message.success(isResolved ? '已标记为已解决' : '已取消解决标记');
      }
    } catch (error: any) {
      message.error(error.response?.data?.message || '操作失败');
    }
  };

  const handleDeleteComment = async (commentId: number) => {
    try {
      const result = await apiService.deleteComment(commentId);
      if (result.code === 200) {
        removeComment(commentId);
        message.success('删除成功');
      }
    } catch (error: any) {
      message.error(error.response?.data?.message || '删除失败');
    }
  };

  return (
    <div className="comment-panel">
      <div className="comment-panel-header">
        <h3>评论 ({comments.length})</h3>
      </div>

      <div className="comment-input">
        <TextArea
          rows={3}
          placeholder="添加评论..."
          value={commentText}
          onChange={(e) => setCommentText(e.target.value)}
          onPressEnter={(e) => {
            if (e.shiftKey) return;
            e.preventDefault();
            handleSubmitComment();
          }}
        />
        <Button
          type="primary"
          icon={<SendOutlined />}
          onClick={handleSubmitComment}
          loading={loading}
          style={{ marginTop: 8 }}
        >
          发送
        </Button>
      </div>

      <div className="comment-list">
        <List
          dataSource={comments.filter(c => !c.parentId)}
          renderItem={(comment) => {
            const replies = comments.filter(c => c.parentId === comment.id);
            return (
              <List.Item
                className={comment.isResolved ? 'comment-resolved' : ''}
                actions={[
                  <Button
                    key="reply"
                    type="text"
                    size="small"
                    icon={<MessageOutlined />}
                    onClick={() => handleReply(comment.id)}
                  >
                    回复
                  </Button>,
                  ...(comment.userId === user?.id || currentDocument?.creatorId === user?.id
                    ? [
                        <Button
                          key="resolve"
                          type="text"
                          size="small"
                          icon={<CheckOutlined />}
                          onClick={() => handleResolveComment(comment.id, !comment.isResolved)}
                        >
                          {comment.isResolved ? '取消解决' : '标记解决'}
                        </Button>,
                        <Popconfirm
                          key="delete"
                          title="确定删除这条评论吗？"
                          onConfirm={() => handleDeleteComment(comment.id)}
                          okText="确定"
                          cancelText="取消"
                        >
                          <Button
                            type="text"
                            danger
                            size="small"
                            icon={<DeleteOutlined />}
                          >
                            删除
                          </Button>
                        </Popconfirm>,
                      ]
                    : []),
                ]}
              >
                <div style={{ width: '100%' }}>
                  <List.Item.Meta
                    avatar={
                      <Avatar
                        icon={<UserOutlined />}
                        src={comment.user?.avatar}
                      >
                        {comment.user?.nickname?.[0] || comment.user?.username?.[0]}
                      </Avatar>
                    }
                    title={
                      <Space>
                        <span>{comment.user?.nickname || comment.user?.username}</span>
                        {comment.isResolved && <Tag color="green">已解决</Tag>}
                        {comment.position !== undefined && comment.position > 0 && (
                          <Tag color="blue">位置: {comment.position}</Tag>
                        )}
                        <span className="comment-time">
                          {comment.createdAt
                            ? new Date(comment.createdAt).toLocaleString('zh-CN')
                            : ''}
                        </span>
                      </Space>
                    }
                    description={comment.content}
                  />
                  
                  {/* 回复输入框 */}
                  {replyingTo === comment.id && (
                    <div className="comment-reply-input" style={{ marginTop: 12, marginLeft: 40 }}>
                      <TextArea
                        rows={2}
                        placeholder="输入回复..."
                        value={replyText[comment.id] || ''}
                        onChange={(e) => setReplyText({ ...replyText, [comment.id]: e.target.value })}
                      />
                      <Space style={{ marginTop: 8 }}>
                        <Button
                          type="primary"
                          size="small"
                          onClick={() => handleSubmitComment(comment.position, comment.id)}
                          loading={loading}
                        >
                          发送
                        </Button>
                        <Button
                          size="small"
                          onClick={() => handleCancelReply(comment.id)}
                        >
                          取消
                        </Button>
                      </Space>
                    </div>
                  )}
                  
                  {/* 显示回复 */}
                  {replies.length > 0 && (
                    <div className="comment-replies" style={{ marginTop: 12, marginLeft: 40 }}>
                      {replies.map((reply) => (
                        <div key={reply.id} className="comment-reply-item" style={{ marginBottom: 8, padding: 8, background: '#f5f5f5', borderRadius: 4 }}>
                          <Space>
                            <Avatar size="small" src={reply.user?.avatar}>
                              {reply.user?.nickname?.[0] || reply.user?.username?.[0]}
                            </Avatar>
                            <span style={{ fontWeight: 500 }}>
                              {reply.user?.nickname || reply.user?.username}
                            </span>
                            <span className="comment-time" style={{ fontSize: 12 }}>
                              {reply.createdAt
                                ? new Date(reply.createdAt).toLocaleString('zh-CN')
                                : ''}
                            </span>
                            {reply.userId === user?.id && (
                              <Popconfirm
                                title="确定删除这条回复吗？"
                                onConfirm={() => handleDeleteComment(reply.id)}
                                okText="确定"
                                cancelText="取消"
                              >
                                <Button
                                  type="text"
                                  danger
                                  size="small"
                                  icon={<DeleteOutlined />}
                                >
                                  删除
                                </Button>
                              </Popconfirm>
                            )}
                          </Space>
                          <div style={{ marginTop: 4, marginLeft: 32 }}>{reply.content}</div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </List.Item>
            );
          }}
        />
      </div>
    </div>
  );
};

export default CommentPanel;

