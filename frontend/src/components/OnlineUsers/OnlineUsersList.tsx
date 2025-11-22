/**
 * 在线用户列表组件
 */

import React from 'react';
import { Avatar, List, Tag } from 'antd';
import { UserOutlined } from '@ant-design/icons';
import useDocumentStore from '@/stores/documentStore';
import useAuthStore from '@/stores/authStore';
import './OnlineUsersList.css';

const OnlineUsersList: React.FC = () => {
  const { onlineUsers } = useDocumentStore();
  const { user: currentUser } = useAuthStore();

  // 调试日志
  console.log('在线用户列表组件渲染，用户数量:', onlineUsers.length);
  console.log('在线用户数据:', onlineUsers);
  console.log('当前用户:', currentUser);

  // 显示所有在线用户，不进行过滤
  // 当前用户会显示"当前用户"标签
  const filteredUsers = onlineUsers;
  
  console.log('显示的用户数量:', filteredUsers.length);
  console.log('显示的用户列表:', filteredUsers);

  const getAvatarColor = (userId: number) => {
    const colors = ['#667eea', '#764ba2', '#f093fb', '#4facfe', '#43e97b', '#fa709a'];
    return colors[userId % colors.length];
  };

  // 如果没有用户，显示空状态
  if (filteredUsers.length === 0) {
    return (
      <div className="online-users-list">
        <div className="online-users-header">
          <h3>在线用户 (0)</h3>
        </div>
        <div style={{ padding: '16px', textAlign: 'center', color: '#999' }}>
          暂无其他在线用户
        </div>
      </div>
    );
  }

  return (
    <div className="online-users-list">
      <div className="online-users-header">
        <h3>在线用户 ({filteredUsers.length})</h3>
      </div>
      <List
        dataSource={filteredUsers}
        renderItem={(user) => {
          const isCurrentUser = currentUser && user.userId === currentUser.id;
          return (
            <List.Item>
              <List.Item.Meta
                avatar={
                  user.avatar ? (
                    <Avatar src={user.avatar} alt={user.nickname || user.username} />
                  ) : (
                    <Avatar
                      style={{ backgroundColor: user.color || getAvatarColor(user.userId) }}
                      icon={<UserOutlined />}
                    />
                  )
                }
                title={
                  <span className="online-user-title">
                    {user.nickname || user.username}
                    {isCurrentUser && (
                      <Tag color="green" style={{ marginLeft: 8, fontSize: '12px' }}>
                        当前用户
                      </Tag>
                    )}
                    {user.cursorPosition !== undefined && !isCurrentUser && (
                      <Tag color="blue" style={{ marginLeft: 8, fontSize: '12px' }}>
                        编辑中
                      </Tag>
                    )}
                  </span>
                }
              />
            </List.Item>
          );
        }}
      />
    </div>
  );
};

export default OnlineUsersList;

