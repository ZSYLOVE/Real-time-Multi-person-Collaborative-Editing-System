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
        const userData: { token: string; userId: number; username: string; nickname?: string; email?: string; avatar?: string } = result.data;
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
        
        // 登录前先清除旧的认证信息（login 方法内部也会清除，但这里提前清除更安全）
        console.log('Login: 准备登录，用户ID:', user.id);
        const { logout } = useAuthStore.getState();
        logout(); // 先清除旧的认证信息
        
        // 先设置登录状态
        login(user, token);
        console.log('Login: login() 调用完成，检查 isAuthenticated:', useAuthStore.getState().isAuthenticated);
        
        // 登录成功后，再次获取完整的用户信息（确保数据最新，包括头像）
        // 注意：即使失败也不影响登录状态
        // 在获取用户信息之前，确保 token 已保存
        const tokenBeforeGetUserInfo = sessionStorage.getItem('token');
        if (!tokenBeforeGetUserInfo) {
          console.warn('Login: Token在获取用户信息前丢失，重新设置');
          sessionStorage.setItem('token', token);
        }
        
        try {
          const userInfoResult = await apiService.getUserInfo();
          if (userInfoResult.code === 200 && userInfoResult.data) {
            // 更新用户信息（包括最新的头像）
            // 在 setUser 之前，再次确保 token 存在
            const tokenBeforeSetUser = sessionStorage.getItem('token');
            if (!tokenBeforeSetUser) {
              console.warn('Login: Token在setUser前丢失，重新设置');
              sessionStorage.setItem('token', token);
            }
            
            // setUser 会自动更新 sessionStorage，不需要手动设置
            setUser(userInfoResult.data);
            
            // setUser 后，再次验证 token
            const tokenAfterSetUser = sessionStorage.getItem('token');
            if (!tokenAfterSetUser) {
              console.error('Login: setUser后Token丢失，重新设置');
              sessionStorage.setItem('token', token);
            }
            
            // 如果 setUser 导致 isAuthenticated 变为 false（不应该发生），重新设置
            if (!useAuthStore.getState().isAuthenticated) {
              console.warn('Login: setUser 后 isAuthenticated 变为 false，重新设置');
              login(userInfoResult.data, token);
            }
            console.log('Login: 用户信息已更新，token存在:', !!sessionStorage.getItem('token'));
          }
        } catch (error) {
          console.warn('获取用户信息失败，使用登录返回的信息:', error);
          // 确保登录状态仍然有效
          const currentAuth = useAuthStore.getState();
          if (!currentAuth.isAuthenticated) {
            console.warn('Login: 获取用户信息失败后状态异常，重新设置登录状态');
            login(user, token);
          }
          // 确保 token 仍然存在
          if (!sessionStorage.getItem('token')) {
            console.warn('Login: 获取用户信息失败后Token丢失，重新设置');
            sessionStorage.setItem('token', token);
          }
        }
        
        // 连接WebSocket（失败不影响登录）
        try {
          await websocketService.connect(token, user.id);
        } catch (wsError) {
          console.warn('WebSocket连接失败，但不影响登录:', wsError);
        }
        
        message.success('登录成功');
        setCaptchaValue(''); // 清空验证码
        
        // 确保状态已更新后再导航
        // 使用 navigate 而不是 window.location.href，避免页面刷新导致状态丢失
        // 等待状态更新完成，并验证 token 已保存
        await new Promise(resolve => setTimeout(resolve, 150));
        
        // 最终验证：确保 token 和用户信息都存在
        let savedToken = sessionStorage.getItem('token');
        const currentAuth = useAuthStore.getState();
        
        // 如果 token 不存在，强制重新设置
        if (!savedToken) {
          console.warn('Login: Token在导航前丢失，重新设置');
          // 如果用户信息存在，只更新 token
          const { user: currentUser } = useAuthStore.getState();
          if (currentUser) {
            // 使用 setToken 方法，它会自动处理 sessionStorage
            const { setToken } = useAuthStore.getState();
            setToken(token);
            useAuthStore.setState({ token, isAuthenticated: true });
          } else {
            // 如果用户信息不存在，需要重新登录
            login(user, token);
            await new Promise(resolve => setTimeout(resolve, 100));
          }
          savedToken = sessionStorage.getItem('token');
        }
        
        // 验证用户信息
        if (!currentAuth.user) {
          console.warn('Login: 用户信息在导航前丢失，重新设置');
          login(user, token);
          await new Promise(resolve => setTimeout(resolve, 100));
        }
        
        // 等待一小段时间，确保所有异步操作完成
        await new Promise(resolve => setTimeout(resolve, 100));
        
        // 最终验证：多次检查，确保 token 稳定存在
        let finalToken = sessionStorage.getItem('token');
        if (!finalToken) {
          console.warn('Login: 最终验证时Token丢失，最后一次设置');
          // 如果用户信息存在，只更新 token
          const currentUser = useAuthStore.getState().user;
          if (currentUser) {
            // 使用 setToken 方法，它会自动处理 sessionStorage
            const { setToken } = useAuthStore.getState();
            setToken(token);
            useAuthStore.setState({ token, isAuthenticated: true });
          } else {
            // 如果用户信息不存在，需要重新登录
            login(user, token);
            await new Promise(resolve => setTimeout(resolve, 100));
          }
          finalToken = sessionStorage.getItem('token');
        }
        
        const finalAuth = useAuthStore.getState();
        console.log('Login: 准备导航，最终验证 - isAuthenticated:', finalAuth.isAuthenticated, 'user:', finalAuth.user?.id, 'token已保存:', !!finalToken);
        
        if (finalAuth.isAuthenticated && finalAuth.user && finalToken) {
          // 使用 navigate 跳转，replace: true 避免历史记录问题
          navigate('/documents', { replace: true });
        } else {
          console.error('Login: 最终验证失败', {
            isAuthenticated: finalAuth.isAuthenticated,
            user: finalAuth.user,
            tokenSaved: !!finalToken
          });
          message.error('登录状态保存失败，请重试');
        }
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

