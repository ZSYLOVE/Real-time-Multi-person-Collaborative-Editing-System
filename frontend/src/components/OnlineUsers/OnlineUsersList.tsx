/**
 * 在线用户列表组件
 */

import React from 'react';
import { Avatar, List, Tag } from 'antd';
import { UserOutlined } from '@ant-design/icons';
import useDocumentStore from '@/stores/documentStore';
import './OnlineUsersList.css';

const OnlineUsersList: React.FC = () => {
  const { onlineUsers } = useDocumentStore();

  const getAvatarColor = (userId: number) => {
    const colors = ['#667eea', '#764ba2', '#f093fb', '#4facfe', '#43e97b', '#fa709a'];
    return colors[userId % colors.length];
  };

  return (
    <div className="online-users-list">
      <div className="online-users-header">
        <h3>在线用户 ({onlineUsers.length})</h3>
      </div>
      <List
        dataSource={onlineUsers}
        renderItem={(user) => (
          <List.Item>
            <List.Item.Meta
              avatar={
                <Avatar
                  style={{ backgroundColor: user.color || getAvatarColor(user.userId) }}
                  icon={<UserOutlined />}
                />
              }
              title={
                <span>
                  {user.nickname || user.username}
                  {user.cursorPosition !== undefined && (
                    <Tag color="blue" style={{ marginLeft: 8 }}>
                      编辑中
                    </Tag>
                  )}
                </span>
              }
            />
          </List.Item>
        )}
      />
    </div>
  );
};

export default OnlineUsersList;

