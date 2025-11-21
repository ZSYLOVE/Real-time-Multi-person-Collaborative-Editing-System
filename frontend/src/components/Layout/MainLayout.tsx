/**
 * 主布局组件 - 包含可折叠侧边栏
 */

import React, { useState } from 'react';
import { Layout, Menu, Button, Avatar, Dropdown, Space, Modal } from 'antd';
import { useNavigate, useLocation } from 'react-router-dom';
import {
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  HomeOutlined,
  FileTextOutlined,
  UserOutlined,
  LogoutOutlined,
  SettingOutlined,
  SunOutlined,
  MoonOutlined,
} from '@ant-design/icons';
import useAuthStore from '@/stores/authStore';
import useThemeStore from '@/stores/themeStore';
import type { MenuProps } from 'antd';
import './MainLayout.css';

const { Header, Sider, Content } = Layout;

interface MainLayoutProps {
  children: React.ReactNode;
}

const MainLayout: React.FC<MainLayoutProps> = ({ children }) => {
  const [collapsed, setCollapsed] = useState(false);
  const [logoutMenuSelectedKeys, setLogoutMenuSelectedKeys] = useState<string[]>([]);
  const navigate = useNavigate();
  const location = useLocation();
  const { user, logout, getSessionDuration } = useAuthStore();
  const { theme, toggleTheme } = useThemeStore();

  // 格式化使用时长
  const formatDuration = (milliseconds: number): string => {
    const seconds = Math.floor(milliseconds / 1000);
    const minutes = Math.floor(seconds / 60);
    const hours = Math.floor(minutes / 60);
    const days = Math.floor(hours / 24);

    if (days > 0) {
      return `${days}天${hours % 24}小时${minutes % 60}分钟`;
    } else if (hours > 0) {
      return `${hours}小时${minutes % 60}分钟`;
    } else if (minutes > 0) {
      return `${minutes}分钟${seconds % 60}秒`;
    } else {
      return `${seconds}秒`;
    }
  };

  // 处理退出登录
  const handleLogout = () => {
    const duration = getSessionDuration();
    const durationText = formatDuration(duration);

    Modal.confirm({
      title: '确认退出登录',
      content: (
        <div>
          <p>确定要退出登录吗？</p>
          {duration > 0 && (
            <p style={{ marginTop: 8, color: '#666', fontSize: '14px' }}>
              本次使用时长：<strong>{durationText}</strong>
            </p>
          )}
        </div>
      ),
      okText: '确定退出',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: () => {
        // 先显示感谢信息
        Modal.success({
          title: '退出成功',
          content: (
            <div>
              <p style={{ fontSize: '16px', marginBottom: 8 }}>感谢使用，期待下次相遇！</p>
              {duration > 0 && (
                <p style={{ marginTop: 8, color: '#666', fontSize: '14px' }}>
                  本次使用时长：<strong style={{ color: '#1890ff' }}>{durationText}</strong>
                </p>
              )}
            </div>
          ),
          okText: '确定',
          onOk: () => {
            // 执行退出并跳转
            logout();
            navigate('/login');
          },
        });
      },
      onCancel: () => {
        // 点击取消时，清除退出按钮的选中状态
        setLogoutMenuSelectedKeys([]);
      },
    });
  };

  // 菜单项配置（不包含退出按钮，退出按钮单独放在底部）
  const menuItems: MenuProps['items'] = [
    {
      key: '/home',
      icon: <HomeOutlined />,
      label: '首页',
    },
    {
      key: '/documents',
      icon: <FileTextOutlined />,
      label: '我的文档',
    },
    {
      key: '/profile',
      icon: <UserOutlined />,
      label: '个人资料',
    },
  ];

  // 处理菜单点击
  const handleMenuClick = ({ key }: { key: string }) => {
    navigate(key);
  };

  // 获取当前选中的菜单项
  const getSelectedKey = () => {
    const path = location.pathname;
    if (path === '/home' || path === '/') {
      return '/home';
    }
    if (path.startsWith('/documents') && !path.includes('/documents/')) {
      return '/documents';
    }
    if (path === '/profile') {
      return '/profile';
    }
    return '';
  };

  // 用户下拉菜单
  const userMenuItems: MenuProps['items'] = [
    {
      key: 'profile',
      icon: <UserOutlined />,
      label: '个人资料',
      onClick: () => {
        navigate('/profile');
      },
    },
    {
      key: 'settings',
      icon: <SettingOutlined />,
      label: '设置',
      onClick: () => {
        // TODO: 实现设置页面
        console.log('设置');
      },
    },
    {
      type: 'divider',
    },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      danger: true,
      onClick: handleLogout,
    },
  ];

  return (
    <Layout className="main-layout">
      <Sider
        trigger={null}
        collapsible
        collapsed={collapsed}
        className="main-layout-sider"
        width={200}
      >
        <div className="logo">
          {collapsed ? (
            <div className="logo-icon">📝</div>
          ) : (
            <div className="logo-text">
              <span className="react-icon">
                <svg
                  width="20"
                  height="20"
                  viewBox="0 0 24 24"
                  fill="none"
                  xmlns="http://www.w3.org/2000/svg"
                >
                  <circle cx="12" cy="12" r="2" fill="#61DAFB" />
                  <ellipse cx="12" cy="12" rx="11" ry="4.2" stroke="#61DAFB" strokeWidth="1" fill="none" />
                  <ellipse cx="12" cy="12" rx="11" ry="4.2" stroke="#61DAFB" strokeWidth="1" fill="none" transform="rotate(60 12 12)" />
                  <ellipse cx="12" cy="12" rx="11" ry="4.2" stroke="#61DAFB" strokeWidth="1" fill="none" transform="rotate(120 12 12)" />
                </svg>
              </span>
              <span>实时多人协同编辑</span>
            </div>
          )}
        </div>
        <div className="menu-container">
          <Menu
            theme="dark"
            mode="inline"
            selectedKeys={[getSelectedKey()]}
            items={menuItems.filter(item => item?.key !== 'logout')}
            onClick={handleMenuClick}
            className="main-menu"
          />
          <div className="logout-menu-item">
            <Menu
              theme="dark"
              mode="inline"
              selectedKeys={logoutMenuSelectedKeys}
              items={[
                {
                  type: 'divider',
                },
                {
                  key: 'logout',
                  icon: <LogoutOutlined />,
                  label: '退出登录',
                  danger: true,
                  onClick: () => {
                    // 设置选中状态，然后显示确认对话框
                    setLogoutMenuSelectedKeys(['logout']);
                    handleLogout();
                  },
                },
              ]}
              className="logout-menu"
            />
          </div>
        </div>
      </Sider>
      <Layout className="main-layout-content">
        <Header className="main-layout-header">
          <div className="header-left">
            <Button
              type="text"
              icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              onClick={() => setCollapsed(!collapsed)}
              className="trigger-btn"
            />
          </div>
          <div className="header-right">
            <Space>
              <Button
                type="text"
                icon={theme === 'dark' ? <SunOutlined /> : <MoonOutlined />}
                onClick={toggleTheme}
                className="theme-toggle-btn"
                title={theme === 'dark' ? '切换到日间模式' : '切换到夜间模式'}
              />
              <span className="welcome-text">
                欢迎，{user?.nickname || user?.username}
              </span>
              <Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
                <Avatar
                  src={user?.avatar}
                  style={{ backgroundColor: user?.avatar ? 'transparent' : '#1890ff', cursor: 'pointer' }}
                  icon={<UserOutlined />}
                >
                  {!user?.avatar && (user?.nickname?.[0] || user?.username?.[0] || 'U')}
                </Avatar>
              </Dropdown>
            </Space>
          </div>
        </Header>
        <Content className="main-layout-body">
          {children}
        </Content>
      </Layout>
    </Layout>
  );
};

export default MainLayout;

