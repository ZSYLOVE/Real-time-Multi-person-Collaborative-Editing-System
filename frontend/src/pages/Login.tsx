import React, { useState, useEffect } from 'react';
import { Form, Input, Button, Card, message } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { apiService } from '@/services/api';
import useAuthStore from '@/stores/authStore';
import { websocketService } from '@/services/websocket';
import Captcha from '@/components/Captcha/Captcha';
import './Login.css';

const Login: React.FC = () => {
  const navigate = useNavigate();
  const { login, isAuthenticated, setUser } = useAuthStore();
  const [loading, setLoading] = useState(false);
  const [captchaValue, setCaptchaValue] = useState('');
  const [captchaId, setCaptchaId] = useState<string>('');

  // 如果已登录，跳转到文档列表
  useEffect(() => {
    if (isAuthenticated) {
      navigate('/documents');
    }
  }, [isAuthenticated, navigate]);

  const onFinish = async (values: { username: string; password: string; captcha?: string }) => {
    // 验证验证码
    if (!captchaValue || !captchaId) {
      message.error('请输入验证码');
      return;
    }

    setLoading(true);
    try {
      console.log('开始登录，用户名:', values.username);
      const result = await apiService.login({
        username: values.username,
        password: values.password,
        captchaId: captchaId,
        captchaCode: captchaValue,
      });
      console.log('登录响应:', result);
      
      if (result.code === 200 && result.data) {
        // 后端返回的数据结构：{ token, userId, username, nickname, email, avatar }
        const userData = result.data;
        const user: any = {
          id: userData.userId,
          username: userData.username,
          nickname: userData.nickname,
          email: userData.email || '',
          avatar: userData.avatar || '',
        };
        const token = userData.token;
        
        if (!token || !user.id) {
          message.error('登录响应数据格式错误');
          return;
        }
        
        login(user, token);
        
        // 登录成功后，再次获取完整的用户信息（确保数据最新，包括头像）
        try {
          const userInfoResult = await apiService.getUserInfo();
          if (userInfoResult.code === 200 && userInfoResult.data) {
            // 更新用户信息（包括最新的头像）
            setUser(userInfoResult.data);
            // 同时更新localStorage
            localStorage.setItem('user', JSON.stringify(userInfoResult.data));
          }
        } catch (error) {
          console.warn('获取用户信息失败，使用登录返回的信息:', error);
        }
        
        // 连接WebSocket（失败不影响登录）
        try {
          await websocketService.connect(token, user.id);
        } catch (wsError) {
          console.warn('WebSocket连接失败，但不影响登录:', wsError);
        }
        
        message.success('登录成功');
        setCaptchaValue(''); // 清空验证码
        navigate('/documents');
      } else {
        message.error(result.message || '登录失败');
        setCaptchaValue(''); // 登录失败也清空验证码
      }
    } catch (error: any) {
      console.error('登录错误:', error);
      const errorMessage = error.response?.data?.message || error.message || '登录失败，请检查网络连接和后端服务';
      message.error(errorMessage);
      setCaptchaValue(''); // 登录错误也清空验证码
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-container">
      <div className="login-content">
        <div className="login-react-icon">
          <svg
            width="200"
            height="200"
            viewBox="0 0 24 24"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
          >
            <circle cx="12" cy="12" r="2" fill="#61DAFB" />
            <ellipse cx="12" cy="12" rx="11" ry="4.2" stroke="#61DAFB" strokeWidth="1" fill="none" />
            <ellipse cx="12" cy="12" rx="11" ry="4.2" stroke="#61DAFB" strokeWidth="1" fill="none" transform="rotate(60 12 12)" />
            <ellipse cx="12" cy="12" rx="11" ry="4.2" stroke="#61DAFB" strokeWidth="1" fill="none" transform="rotate(120 12 12)" />
          </svg>
        </div>
        <Card 
          className="login-card" 
          title="实时协同编辑系统"
        >
        <Form
          name="login"
          onFinish={onFinish}
          autoComplete="off"
          size="large"
        >
          <Form.Item
            name="username"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input
              prefix={<UserOutlined />}
              placeholder="用户名"
            />
          </Form.Item>

          <Form.Item
            name="password"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              placeholder="密码"
            />
          </Form.Item>

          <Form.Item
            name="captcha"
            rules={[{ required: true, message: '请输入验证码' }]}
          >
            <Captcha
              value={captchaValue}
              onChange={(value) => setCaptchaValue(value)}
              onCaptchaIdChange={(id) => {
                setCaptchaId(id);
              }}
              onRefresh={() => {
                setCaptchaValue('');
              }}
            />
          </Form.Item>

          <Form.Item>
            <Button type="primary" htmlType="submit" block loading={loading}>
              登录
            </Button>
          </Form.Item>

          <Form.Item>
            <Button type="link" block onClick={() => navigate('/register')}>
              还没有账号？立即注册
            </Button>
          </Form.Item>
        </Form>
        </Card>
      </div>
    </div>
  );
};

export default Login;

