import React, { useState } from 'react';
import { Form, Input, Button, Card, message } from 'antd';
import { UserOutlined, MailOutlined, LockOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { apiService } from '@/services/api';
import Captcha from '@/components/Captcha/Captcha';
import './Register.css';

const Register: React.FC = () => {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [captchaValue, setCaptchaValue] = useState('');
  const [captchaId, setCaptchaId] = useState<string>('');
  const [captchaRefreshNonce, setCaptchaRefreshNonce] = useState(0);

  const isCaptchaExpiredError = (msg: string) => {
    return (
      !!msg &&
      msg.includes('验证码') &&
      (msg.includes('过期') || msg.includes('已过期'))
    );
  };

  const onFinish = async (values: {
    username: string;
    email: string;
    password: string;
    nickname?: string;
  }) => {
    // 验证验证码
    if (!captchaValue || !captchaId) {
      message.error('请输入验证码');
      return;
    }

    setLoading(true);
    try {
      const result = await apiService.register({
        ...values,
        captchaId: captchaId,
        captchaCode: captchaValue,
      });
      if (result.code === 200) {
        message.success('注册成功，请登录');
        setCaptchaValue(''); // 清空验证码
        navigate('/login');
      } else {
        const msg = result.message || '注册失败';
        message.error(msg);
        if (isCaptchaExpiredError(msg)) {
          // 验证码过期：清空输入并强制刷新验证码
          setCaptchaValue('');
          setCaptchaId('');
          setCaptchaRefreshNonce((v) => v + 1);
        } else {
          setCaptchaValue(''); // 注册失败也清空验证码
        }
      }
    } catch (error: any) {
      const errorMessage = error.response?.data?.message || '注册失败，请检查网络连接';
      message.error(errorMessage);
      if (isCaptchaExpiredError(errorMessage)) {
        // 验证码过期：自动刷新验证码
        setCaptchaValue('');
        setCaptchaId('');
        setCaptchaRefreshNonce((v) => v + 1);
      } else {
        setCaptchaValue(''); // 注册错误也清空验证码
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="register-container">
      <div className="register-content">
        <div className="register-react-icon">
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
          className="register-card" 
          title="用户注册"
        >
        <Form
          name="register"
          onFinish={onFinish}
          autoComplete="off"
          size="large"
        >
          <Form.Item
            name="username"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input prefix={<UserOutlined />} placeholder="用户名" />
          </Form.Item>

          <Form.Item
            name="email"
            rules={[
              { required: true, message: '请输入邮箱' },
              { type: 'email', message: '请输入有效的邮箱地址' },
            ]}
          >
            <Input prefix={<MailOutlined />} placeholder="邮箱" />
          </Form.Item>

          <Form.Item
            name="password"
            rules={[
              { required: true, message: '请输入密码' },
              { min: 6, message: '密码至少6位' },
            ]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="密码" />
          </Form.Item>

          <Form.Item name="nickname">
            <Input placeholder="昵称（可选）" />
          </Form.Item>

          <Form.Item
            name="captcha"
            rules={[{ required: true, message: '请输入验证码' }]}
          >
            <Captcha
              key={captchaRefreshNonce}
              value={captchaValue}
              onChange={(value) => setCaptchaValue(value)}
              onCaptchaIdChange={(id) => {
                setCaptchaId(id);
              }}
              onRefresh={() => {
                setCaptchaValue('');
              }}
              autoRefreshIntervalMs={60000}
            />
          </Form.Item>

          <Form.Item>
            <Button type="primary" htmlType="submit" block loading={loading}>
              注册
            </Button>
          </Form.Item>

          <Form.Item>
            <Button type="link" block onClick={() => navigate('/login')}>
              已有账号？立即登录
            </Button>
          </Form.Item>
        </Form>
        </Card>
      </div>
    </div>
  );
};

export default Register;

