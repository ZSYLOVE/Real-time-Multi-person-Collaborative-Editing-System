/**
 * 首页组件
 */

import React, { useEffect, useState } from 'react';
import { Card, Row, Col, Statistic, Button, Space, Typography, List, Tag } from 'antd';
import {
  FileTextOutlined,
  TeamOutlined,
  EditOutlined,
  ClockCircleOutlined,
  PlusOutlined,
  ArrowRightOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { apiService } from '@/services/api';
import useAuthStore from '@/stores/authStore';
import type { Document } from '@/types';
import './Home.css';

const { Title, Paragraph } = Typography;

const Home: React.FC = () => {
  const navigate = useNavigate();
  const { user } = useAuthStore();
  const [documents, setDocuments] = useState<Document[]>([]);
  const [loading, setLoading] = useState(false);
  const [stats, setStats] = useState({
    totalDocuments: 0,
    sharedDocuments: 0,
    recentDocuments: 0,
  });

  useEffect(() => {
    if (user?.id) {
      loadDocuments();
    }
  }, [user?.id]);

  const loadDocuments = async () => {
    if (!user?.id) return;

    setLoading(true);
    try {
      const [myDocsResult, sharedDocsResult] = await Promise.all([
        apiService.getUserDocuments(user.id),
        apiService.getSharedDocuments(user.id),
      ]);

      const allDocuments: Document[] = [];

      if (myDocsResult.code === 200 && myDocsResult.data) {
        allDocuments.push(
          ...myDocsResult.data.map((doc: Document) => ({
            ...doc,
            isShared: false,
          }))
        );
      }

      if (sharedDocsResult.code === 200 && sharedDocsResult.data) {
        allDocuments.push(
          ...sharedDocsResult.data
            .filter((doc: Document) => doc.creatorId !== user.id)
            .map((doc: Document) => ({
              ...doc,
              isShared: true,
            }))
        );
      }

      // 按更新时间排序
      allDocuments.sort((a, b) => {
        const timeA = new Date(a.updatedAt || a.createdAt || '').getTime();
        const timeB = new Date(b.updatedAt || b.createdAt || '').getTime();
        return timeB - timeA;
      });

      setDocuments(allDocuments);
      setStats({
        totalDocuments: allDocuments.length,
        sharedDocuments: allDocuments.filter((doc) => doc.isShared).length,
        recentDocuments: allDocuments.slice(0, 5).length,
      });
    } catch (error: any) {
      console.error('加载文档失败:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleCreateDocument = () => {
    navigate('/documents');
  };

  const formatDate = (dateString?: string) => {
    if (!dateString) return '';
    const date = new Date(dateString);
    const now = new Date();
    const diff = now.getTime() - date.getTime();
    const days = Math.floor(diff / (1000 * 60 * 60 * 24));

    if (days === 0) {
      return '今天';
    } else if (days === 1) {
      return '昨天';
    } else if (days < 7) {
      return `${days}天前`;
    } else {
      return date.toLocaleDateString('zh-CN');
    }
  };

  const recentDocuments = documents.slice(0, 5);

  return (
    <div className="home-container">
      {/* 欢迎区域 */}
      <div className="home-welcome">
        <Title level={2}>欢迎回来，{user?.nickname || user?.username}！</Title>
        <Paragraph type="secondary">
          开始创建新文档，或继续编辑您的工作
        </Paragraph>
      </div>

      {/* 统计卡片 */}
      <Row gutter={[16, 16]} className="home-stats">
        <Col xs={24} sm={12} lg={8}>
          <Card>
            <Statistic
              title="总文档数"
              value={stats.totalDocuments}
              prefix={<FileTextOutlined />}
              valueStyle={{ color: '#1890ff' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={8}>
          <Card>
            <Statistic
              title="共享文档"
              value={stats.sharedDocuments}
              prefix={<TeamOutlined />}
              valueStyle={{ color: '#52c41a' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={8}>
          <Card>
            <Statistic
              title="最近文档"
              value={stats.recentDocuments}
              prefix={<ClockCircleOutlined />}
              valueStyle={{ color: '#faad14' }}
            />
          </Card>
        </Col>
      </Row>

      {/* 快速操作 */}
      <Card title="快速操作" className="home-actions">
        <Space size="large">
          <Button
            type="primary"
            size="large"
            icon={<PlusOutlined />}
            onClick={handleCreateDocument}
          >
            新建文档
          </Button>
          <Button
            size="large"
            icon={<FileTextOutlined />}
            onClick={() => navigate('/documents')}
          >
            查看所有文档
          </Button>
        </Space>
      </Card>

      {/* 最近文档 */}
      <Card
        title="最近文档"
        className="home-recent-docs"
        extra={
          <Button type="link" onClick={() => navigate('/documents')}>
            查看全部 <ArrowRightOutlined />
          </Button>
        }
      >
        {loading ? (
          <div style={{ textAlign: 'center', padding: '40px 0' }}>加载中...</div>
        ) : recentDocuments.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '40px 0', color: '#999' }}>
            暂无文档，<Button type="link" onClick={handleCreateDocument}>创建第一个文档</Button>
          </div>
        ) : (
          <List
            dataSource={recentDocuments}
            renderItem={(doc) => (
              <List.Item
                className="recent-doc-item"
                actions={[
                  <Button
                    key="edit"
                    type="link"
                    icon={<EditOutlined />}
                    onClick={() => navigate(`/documents/${doc.id}`)}
                  >
                    编辑
                  </Button>,
                ]}
              >
                <List.Item.Meta
                  title={
                    <Space>
                      <span>{doc.title}</span>
                      {doc.isShared && <Tag color="blue">共享</Tag>}
                    </Space>
                  }
                  description={
                    <Space>
                      <span>版本: {doc.version}</span>
                      <span>•</span>
                      <span>更新于: {formatDate(doc.updatedAt || doc.createdAt)}</span>
                    </Space>
                  }
                />
              </List.Item>
            )}
          />
        )}
      </Card>
    </div>
  );
};

export default Home;

