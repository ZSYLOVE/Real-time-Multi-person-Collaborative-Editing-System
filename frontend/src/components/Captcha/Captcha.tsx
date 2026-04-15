import React, { useEffect, useState } from 'react';
import { Input, Space, Button } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { apiService } from '@/services/api';
import './Captcha.css';

interface CaptchaProps {
  value?: string;
  onChange?: (value: string) => void;
  onRefresh?: () => void;
  onCaptchaIdChange?: (captchaId: string) => void; // 通知父组件验证码ID变化
  /**
   * 自动刷新验证码（毫秒）
   * 不传则不自动刷新，由用户点击/交互触发
   */
  autoRefreshIntervalMs?: number;
}

const Captcha: React.FC<CaptchaProps> = ({ value, onChange, onRefresh, onCaptchaIdChange, autoRefreshIntervalMs }) => {
  const [captchaId, setCaptchaId] = useState<string>('');
  const [imageUrl, setImageUrl] = useState<string>('');
  const [loading, setLoading] = useState(false);

  // 加载验证码
  const loadCaptcha = async () => {
    setLoading(true);
    try {
      // 释放旧的图片URL
      if (imageUrl) {
        URL.revokeObjectURL(imageUrl);
      }

      const result = await apiService.getCaptcha();
      setCaptchaId(result.captchaId);
      setImageUrl(result.imageUrl);
      
      if (onCaptchaIdChange) {
        onCaptchaIdChange(result.captchaId);
      }
      if (onChange) {
        onChange('');
      }
    } catch (error) {
      console.error('加载验证码失败:', error);
    } finally {
      setLoading(false);
    }
  };

  // 刷新验证码
  const refreshCaptcha = () => {
    loadCaptcha();
    if (onRefresh) {
      onRefresh();
    }
  };

  // 初始化验证码
  useEffect(() => {
    loadCaptcha();
    // 组件卸载时释放图片URL
    return () => {
      if (imageUrl) {
        URL.revokeObjectURL(imageUrl);
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 自动刷新验证码（用于“验证码过期自动刷新”的体验）
  useEffect(() => {
    if (!autoRefreshIntervalMs || autoRefreshIntervalMs <= 0) return;

    const timer = window.setInterval(() => {
      // 加载验证码失败由 loadCaptcha 内部处理
      loadCaptcha();
    }, autoRefreshIntervalMs);

    return () => {
      window.clearInterval(timer);
    };
  }, [autoRefreshIntervalMs]);

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const inputValue = e.target.value.toUpperCase();
    if (onChange) {
      onChange(inputValue);
    }
  };

  return (
    <div className="captcha-container">
      <Space.Compact style={{ width: '100%' }}>
        <Input
          placeholder="请输入验证码"
          value={value}
          onChange={handleInputChange}
          maxLength={4}
          style={{ flex: 1 }}
        />
        <div className="captcha-canvas-wrapper">
          {loading ? (
            <div className="captcha-loading">加载中...</div>
          ) : (
            <img
              src={imageUrl}
              alt="验证码"
              className="captcha-image"
              onClick={refreshCaptcha}
              title="点击刷新验证码"
            />
          )}
          <Button
            type="text"
            icon={<ReloadOutlined />}
            onClick={refreshCaptcha}
            className="captcha-refresh-btn"
            title="刷新验证码"
            loading={loading}
          />
        </div>
      </Space.Compact>
    </div>
  );
};

export default Captcha;

