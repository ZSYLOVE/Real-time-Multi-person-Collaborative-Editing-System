/**
 * 个人资料页面
 */

import React, { useEffect, useState } from 'react';
import { Card, Form, Input, Button, Avatar, Space, message, Spin, Descriptions, Tag } from 'antd';
import { UserOutlined, EditOutlined, SaveOutlined, CloseOutlined, CameraOutlined } from '@ant-design/icons';
import { apiService } from '@/services/api';
import useAuthStore from '@/stores/authStore';
import type { User } from '@/types';
import './Profile.css';

const Profile: React.FC = () => {
  const { user: currentUser, setUser } = useAuthStore();
  const [user, setUserInfo] = useState<User | null>(null);
  const [loading, setLoading] = useState(false);
  const [editing, setEditing] = useState(false);
  const [avatarLoading, setAvatarLoading] = useState(false);
  const [form] = Form.useForm();

  useEffect(() => {
    loadUserInfo();
  }, []);

  const loadUserInfo = async () => {
    if (!currentUser?.id) return;

    setLoading(true);
    try {
      const result = await apiService.getUserInfo();
      if (result.code === 200 && result.data) {
        setUserInfo(result.data);
        form.setFieldsValue({
          username: result.data.username,
          email: result.data.email,
          nickname: result.data.nickname || '',
          avatar: result.data.avatar || '',
        });
      } else {
        message.error(result.message || '获取用户信息失败');
      }
    } catch (error: any) {
      console.error('获取用户信息失败:', error);
      message.error(error.response?.data?.message || '获取用户信息失败');
    } finally {
      setLoading(false);
    }
  };

  const handleEdit = () => {
    setEditing(true);
  };

  const handleCancel = () => {
    setEditing(false);
    if (user) {
      form.setFieldsValue({
        username: user.username,
        email: user.email,
        nickname: user.nickname || '',
        avatar: user.avatar || '',
      });
    }
  };

  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      setLoading(true);
      
      // 调用后端API更新用户信息
      try {
        const result = await apiService.updateUserInfo({
          nickname: values.nickname,
          email: values.email,
          avatar: values.avatar,
        });
        
        if (result.code === 200 && result.data) {
          setUserInfo(result.data);
          setUser(result.data);
          setEditing(false);
          message.success('保存成功');
        } else {
          message.error(result.message || '保存失败');
        }
      } catch (apiError: any) {
        // 如果后端接口不存在，只更新本地状态
        console.warn('后端接口可能不存在，只更新本地状态:', apiError);
        const updatedUser = {
          ...user!,
          ...values,
        };
        setUserInfo(updatedUser);
        setUser(updatedUser);
        setEditing(false);
        message.success('保存成功（仅本地更新）');
      }
    } catch (error) {
      console.error('保存失败:', error);
      message.error('保存失败');
    } finally {
      setLoading(false);
    }
  };

  const getAvatarColor = (userId?: number) => {
    if (!userId) return '#1890ff';
    const colors = ['#667eea', '#764ba2', '#f093fb', '#4facfe', '#43e97b', '#fa709a'];
    return colors[userId % colors.length];
  };

  const handleAvatarChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const isImage = file.type.startsWith('image/');
    if (!isImage) {
      message.error('只能上传图片文件！');
      return;
    }
    const isLt2M = file.size / 1024 / 1024 < 2;
    if (!isLt2M) {
      message.error('图片大小不能超过 2MB！');
      return;
    }

    setAvatarLoading(true);
    try {
      // 上传文件到服务器
      const result = await apiService.uploadAvatar(file);
      if (result.code === 200 && result.data) {
        const avatarUrl = result.data.url;
        form.setFieldsValue({ avatar: avatarUrl });
        const updatedUser = {
          ...user!,
          avatar: avatarUrl,
        };
        setUserInfo(updatedUser);
        setUser(updatedUser);
        message.success('头像已上传，请点击保存按钮保存更改');
      } else {
        message.error(result.message || '头像上传失败');
      }
    } catch (error: any) {
      console.error('头像上传失败:', error);
      message.error(error.response?.data?.message || '头像上传失败');
    } finally {
      setAvatarLoading(false);
    }
  };

  if (loading && !user) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '400px' }}>
        <Spin size="large" />
      </div>
    );
  }

  if (!user) {
    return (
      <Card>
        <div style={{ textAlign: 'center', padding: '40px 0' }}>
          <p>无法加载用户信息</p>
        </div>
      </Card>
    );
  }

  return (
    <div className="profile-container">
      <Card className="profile-card">
        <div className="profile-header">
          <Space size="large" align="center">
            <div className="avatar-upload-wrapper">
              <Avatar
                size={100}
                src={user.avatar}
                style={{ backgroundColor: user.avatar ? 'transparent' : getAvatarColor(user.id) }}
                icon={<UserOutlined />}
              >
                {!user.avatar && (user.nickname?.[0] || user.username?.[0] || 'U')}
              </Avatar>
              {editing && (
                <div className="avatar-upload-overlay">
                  <label htmlFor="avatar-upload" className="avatar-upload-btn">
                    <CameraOutlined />
                    <input
                      id="avatar-upload"
                      type="file"
                      accept="image/*"
                      style={{ display: 'none' }}
                      onChange={handleAvatarChange}
                    />
                  </label>
                </div>
              )}
            </div>
            <div>
              <h2 style={{ margin: 0, marginBottom: 8 }}>
                {user.nickname || user.username}
              </h2>
              <p style={{ margin: 0, color: '#999' }}>@{user.username}</p>
            </div>
          </Space>
          {!editing && (
            <Button
              type="primary"
              icon={<EditOutlined />}
              onClick={handleEdit}
              className="edit-btn"
            >
              编辑资料
            </Button>
          )}
        </div>

        {editing ? (
          <Form
            form={form}
            layout="vertical"
            className="profile-form"
            onFinish={handleSave}
          >
            <Form.Item
              label="用户名"
              name="username"
              rules={[
                { required: true, message: '请输入用户名' },
                { min: 3, message: '用户名至少3个字符' },
                { max: 20, message: '用户名最多20个字符' },
              ]}
            >
              <Input disabled placeholder="用户名不可修改" />
            </Form.Item>

            <Form.Item
              label="昵称"
              name="nickname"
              rules={[
                { max: 50, message: '昵称最多50个字符' },
              ]}
            >
              <Input placeholder="请输入昵称" />
            </Form.Item>

            <Form.Item
              label="邮箱"
              name="email"
              rules={[
                { required: true, message: '请输入邮箱' },
                { type: 'email', message: '请输入有效的邮箱地址' },
              ]}
            >
              <Input placeholder="请输入邮箱" />
            </Form.Item>

            <Form.Item
              label="头像URL"
              name="avatar"
              extra="可以输入图片URL或上传本地图片"
            >
              <Input placeholder="请输入头像URL（或点击上方头像上传）" />
            </Form.Item>

            <Form.Item>
              <Space>
                <Button type="primary" htmlType="submit" icon={<SaveOutlined />} loading={loading}>
                  保存
                </Button>
                <Button onClick={handleCancel} icon={<CloseOutlined />}>
                  取消
                </Button>
              </Space>
            </Form.Item>
          </Form>
        ) : (
          <Descriptions
            column={1}
            bordered
            className="profile-descriptions"
          >
            <Descriptions.Item label="用户ID">
              <Tag color="blue">{user.id}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="用户名">
              {user.username}
            </Descriptions.Item>
            <Descriptions.Item label="昵称">
              {user.nickname || '未设置'}
            </Descriptions.Item>
            <Descriptions.Item label="邮箱">
              {user.email}
            </Descriptions.Item>
            <Descriptions.Item label="头像">
              {user.avatar ? (
                <Avatar src={user.avatar} size={64} />
              ) : (
                <span>未设置</span>
              )}
            </Descriptions.Item>
            <Descriptions.Item label="注册时间">
              {user.createdAt ? new Date(user.createdAt).toLocaleString('zh-CN') : '未知'}
            </Descriptions.Item>
          </Descriptions>
        )}
      </Card>
    </div>
  );
};

export default Profile;

