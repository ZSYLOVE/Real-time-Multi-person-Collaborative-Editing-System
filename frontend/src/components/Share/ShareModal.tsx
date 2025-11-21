/**
 * 共享文档组件
 * 允许文档创建者添加其他用户的权限
 */

import React, { useState, useEffect } from 'react';
import { Modal, Input, Button, Select, Table, message, Space, Tag, Popconfirm } from 'antd';
import { ShareAltOutlined, UserAddOutlined, DeleteOutlined } from '@ant-design/icons';
import { apiService } from '@/services/api';
import type { User, DocumentPermission, PermissionType } from '@/types';
import './ShareModal.css';

const { Search } = Input;
const { Option } = Select;

interface ShareModalProps {
  documentId: number;
  visible: boolean;
  onCancel: () => void;
  isCreator: boolean;
}

const ShareModal: React.FC<ShareModalProps> = ({ documentId, visible, onCancel, isCreator }) => {
  const [searchKeyword, setSearchKeyword] = useState('');
  const [searchResults, setSearchResults] = useState<User[]>([]);
  const [searching, setSearching] = useState(false);
  const [selectedUser, setSelectedUser] = useState<User | null>(null);
  const [permissionType, setPermissionType] = useState<PermissionType>('READ');
  const [permissions, setPermissions] = useState<DocumentPermission[]>([]);
  const [loading, setLoading] = useState(false);
  const [adding, setAdding] = useState(false);

  useEffect(() => {
    if (visible && isCreator) {
      loadPermissions();
    }
  }, [visible, documentId, isCreator]);

  const loadPermissions = async () => {
    setLoading(true);
    try {
      const result = await apiService.getDocumentPermissions(documentId);
      if (result.code === 200 && result.data) {
        // 为每个权限加载用户信息
        const permissionsWithUsers = await Promise.all(
          result.data.map(async (permission) => {
            try {
              const userResult = await apiService.getUser(permission.userId);
              if (userResult.code === 200 && userResult.data) {
                return { ...permission, user: userResult.data };
              }
            } catch (error) {
              console.error(`加载用户 ${permission.userId} 信息失败:`, error);
            }
            return permission;
          })
        );
        setPermissions(permissionsWithUsers);
      } else {
        message.error(result.message || '加载权限列表失败');
      }
    } catch (error: any) {
      console.error('加载权限列表失败:', error);
      message.error('加载权限列表失败');
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = async (value: string) => {
    if (!value || value.trim().length === 0) {
      setSearchResults([]);
      return;
    }

    setSearching(true);
    try {
      const result = await apiService.searchUsers(value.trim());
      if (result.code === 200 && result.data) {
        setSearchResults(result.data);
      } else {
        message.error(result.message || '搜索用户失败');
      }
    } catch (error: any) {
      console.error('搜索用户失败:', error);
      message.error('搜索用户失败');
    } finally {
      setSearching(false);
    }
  };

  const handleAddPermission = async () => {
    if (!selectedUser) {
      message.warning('请先选择一个用户');
      return;
    }

    setAdding(true);
    try {
      const result = await apiService.addPermission(documentId, selectedUser.id, permissionType);
      if (result.code === 200) {
        message.success('权限添加成功');
        setSelectedUser(null);
        setSearchKeyword('');
        setSearchResults([]);
        await loadPermissions();
      } else {
        message.error(result.message || '添加权限失败');
      }
    } catch (error: any) {
      console.error('添加权限失败:', error);
      message.error(error.response?.data?.message || '添加权限失败');
    } finally {
      setAdding(false);
    }
  };

  const handleDeletePermission = async (userId: number) => {
    try {
      const result = await apiService.deletePermission(documentId, userId);
      if (result.code === 200) {
        message.success('权限删除成功');
        await loadPermissions();
      } else {
        message.error(result.message || '删除权限失败');
      }
    } catch (error: any) {
      console.error('删除权限失败:', error);
      message.error(error.response?.data?.message || '删除权限失败');
    }
  };

  const getPermissionTypeText = (type: PermissionType) => {
    const map: Record<PermissionType, string> = {
      READ: '只读',
      WRITE: '编辑',
      ADMIN: '管理员',
    };
    return map[type];
  };

  const getPermissionTypeColor = (type: PermissionType) => {
    const map: Record<PermissionType, string> = {
      READ: 'default',
      WRITE: 'blue',
      ADMIN: 'red',
    };
    return map[type];
  };

  const columns = [
    {
      title: '用户',
      dataIndex: 'user',
      key: 'user',
      render: (user: User | undefined) => {
        if (!user) return '未知用户';
        return (
          <div>
            <div>{user.nickname || user.username}</div>
            <div style={{ fontSize: '12px', color: '#999' }}>@{user.username}</div>
          </div>
        );
      },
    },
    {
      title: '权限',
      dataIndex: 'permissionType',
      key: 'permissionType',
      render: (type: PermissionType) => (
        <Tag color={getPermissionTypeColor(type)}>{getPermissionTypeText(type)}</Tag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: DocumentPermission) => (
        <Popconfirm
          title="确定要删除该用户的权限吗？"
          onConfirm={() => handleDeletePermission(record.userId)}
          okText="确认"
          cancelText="取消"
        >
          <Button type="link" danger size="small" icon={<DeleteOutlined />}>
            删除
          </Button>
        </Popconfirm>
      ),
    },
  ];

  return (
    <Modal
      title={
        <span>
          <ShareAltOutlined /> 共享文档
        </span>
      }
      open={visible}
      onCancel={onCancel}
      footer={null}
      width={700}
    >
      {!isCreator ? (
        <div style={{ textAlign: 'center', padding: '40px 0' }}>
          <p>只有文档创建者可以管理文档权限</p>
        </div>
      ) : (
        <div className="share-modal-content">
          {/* 添加权限 */}
          <div className="add-permission-section">
            <h3>添加用户权限</h3>
            <Space direction="vertical" style={{ width: '100%' }} size="middle">
              <Search
                placeholder="搜索用户名、邮箱或昵称"
                value={searchKeyword}
                onChange={(e) => {
                  setSearchKeyword(e.target.value);
                  if (e.target.value.trim().length === 0) {
                    setSearchResults([]);
                  }
                }}
                onSearch={handleSearch}
                loading={searching}
                allowClear
              />
              
              {searchResults.length > 0 && (
                <div className="search-results">
                  <Select
                    style={{ width: '100%' }}
                    placeholder="选择用户"
                    value={selectedUser?.id}
                    onChange={(userId) => {
                      const user = searchResults.find((u) => u.id === userId);
                      setSelectedUser(user || null);
                    }}
                    showSearch
                    filterOption={false}
                  >
                    {searchResults.map((user) => (
                      <Option key={user.id} value={user.id}>
                        {user.nickname || user.username} (@{user.username})
                      </Option>
                    ))}
                  </Select>
                </div>
              )}

              {selectedUser && (
                <Space>
                  <span>权限类型：</span>
                  <Select
                    value={permissionType}
                    onChange={setPermissionType}
                    style={{ width: 120 }}
                  >
                    <Option value="READ">只读</Option>
                    <Option value="WRITE">编辑</Option>
                    <Option value="ADMIN">管理员</Option>
                  </Select>
                  <Button
                    type="primary"
                    icon={<UserAddOutlined />}
                    onClick={handleAddPermission}
                    loading={adding}
                  >
                    添加权限
                  </Button>
                </Space>
              )}
            </Space>
          </div>

          {/* 权限列表 */}
          <div className="permissions-list-section">
            <h3>已共享用户</h3>
            <Table
              columns={columns}
              dataSource={permissions}
              rowKey="id"
              loading={loading}
              pagination={false}
              size="small"
              locale={{
                emptyText: '暂无共享用户',
              }}
            />
          </div>
        </div>
      )}
    </Modal>
  );
};

export default ShareModal;

