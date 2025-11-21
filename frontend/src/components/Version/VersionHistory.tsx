/**
 * 版本历史组件
 * 显示文档的所有版本，支持查看和回退版本
 */

import React, { useEffect, useState } from 'react';
import { Card, List, Button, Modal, message, Tag, Popconfirm, Spin, Empty, Space, Tooltip } from 'antd';
import { HistoryOutlined, RollbackOutlined, EyeOutlined, ClockCircleOutlined, FileTextOutlined } from '@ant-design/icons';
import { apiService } from '@/services/api';
import useDocumentStore from '@/stores/documentStore';
import useAuthStore from '@/stores/authStore';
import type { DocumentVersion } from '@/types';
import './VersionHistory.css';

interface VersionHistoryProps {
  documentId: number;
}

const VersionHistory: React.FC<VersionHistoryProps> = ({ documentId }) => {
  const { currentDocument, setCurrentDocument, updateDocument } = useDocumentStore();
  const { user } = useAuthStore();
  const [versions, setVersions] = useState<DocumentVersion[]>([]);
  const [loading, setLoading] = useState(false);
  const [previewVisible, setPreviewVisible] = useState(false);
  const [previewVersion, setPreviewVersion] = useState<DocumentVersion | null>(null);
  const [rollingBack, setRollingBack] = useState(false);

  useEffect(() => {
    if (documentId) {
      loadVersions();
    }
  }, [documentId, currentDocument?.version]); // 当文档版本变化时重新加载版本列表

  const loadVersions = async () => {
    setLoading(true);
    try {
      const result = await apiService.getDocumentVersions(documentId);
      if (result.code === 200 && result.data) {
        setVersions(result.data);
      } else {
        message.error(result.message || '加载版本历史失败');
      }
    } catch (error: any) {
      console.error('加载版本历史失败:', error);
      message.error('加载版本历史失败');
    } finally {
      setLoading(false);
    }
  };

  const handlePreview = async (version: DocumentVersion) => {
    setPreviewVersion(version);
    setPreviewVisible(true);
  };

  const handleRollback = async (targetVersion: number) => {
    if (!currentDocument) return;

    // 检查权限：只有文档创建者可以回退
    if (currentDocument.creatorId !== user?.id) {
      message.error('只有文档创建者可以回退版本');
      return;
    }

    // 确认回退
    Modal.confirm({
      title: '确认回退版本',
      content: `确定要回退到版本 ${targetVersion} 吗？当前版本的内容将被保存为快照。`,
      okText: '确认',
      cancelText: '取消',
      onOk: async () => {
        setRollingBack(true);
        try {
          const result = await apiService.rollbackToVersion(documentId, targetVersion);
          if (result.code === 200 && result.data) {
            message.success(`已回退到版本 ${result.data.version}`);
            // 更新当前文档
            updateDocument(result.data);
            // 重新加载版本列表
            await loadVersions();
            // 关闭预览
            setPreviewVisible(false);
          } else {
            message.error(result.message || '回退版本失败');
          }
        } catch (error: any) {
          console.error('回退版本失败:', error);
          message.error(error.response?.data?.message || '回退版本失败');
        } finally {
          setRollingBack(false);
        }
      },
    });
  };

  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    const now = new Date();
    const diff = now.getTime() - date.getTime();
    const days = Math.floor(diff / (1000 * 60 * 60 * 24));
    const hours = Math.floor(diff / (1000 * 60 * 60));
    const minutes = Math.floor(diff / (1000 * 60));

    if (minutes < 1) {
      return '刚刚';
    } else if (minutes < 60) {
      return `${minutes}分钟前`;
    } else if (hours < 24) {
      return `${hours}小时前`;
    } else if (days < 7) {
      return `${days}天前`;
    } else {
      return date.toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
      });
    }
  };

  const formatFullDate = (dateString: string) => {
    const date = new Date(dateString);
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  const isCurrentVersion = (version: number) => {
    return currentDocument?.version === version;
  };

  const canRollback = (version: number) => {
    // 只有文档创建者可以回退，且不能回退到当前版本
    if (!currentDocument || !user) {
      return false;
    }
    
    // 检查是否是文档创建者
    if (currentDocument.creatorId !== user.id) {
      return false;
    }
    
    // 不能回退到当前版本
    if (isCurrentVersion(version)) {
      return false;
    }
    
    // 可以回退到任何历史版本（包括版本号大于当前版本的版本）
    // 因为版本快照表中的版本号代表文档的历史版本，可能存在大于当前版本的快照
    // 例如：文档曾经是版本36，然后回退到版本33，那么版本34、35、36的快照仍然存在
    return true; // 只要不是当前版本，就可以回退
  };

  return (
    <Card
      title={
        <span>
          <HistoryOutlined /> 版本历史
        </span>
      }
      className="version-history-card"
      extra={
        <Button size="small" onClick={loadVersions} loading={loading}>
          刷新
        </Button>
      }
    >
      <Spin spinning={loading}>
        <div className="version-list-container">
          {versions.length === 0 ? (
            <Empty description="暂无版本历史" />
          ) : (
            <List
              dataSource={versions}
              renderItem={(version) => (
              <List.Item
                className={`version-item ${isCurrentVersion(version.version) ? 'current-version' : ''}`}
              >
                <div className="version-item-content">
                  {/* 左上角：版本号和时间 */}
                  <div className="version-header">
                    <Space>
                      <span className="version-number">v{version.version}</span>
                      {isCurrentVersion(version.version) && (
                        <Tag color="success" size="small" icon={<FileTextOutlined />}>
                          当前
                        </Tag>
                      )}
                      <Tooltip title={formatFullDate(version.createdAt)}>
                        <span className="version-time">
                          <ClockCircleOutlined style={{ marginRight: 4 }} />
                          {formatDate(version.createdAt)}
                        </span>
                      </Tooltip>
                    </Space>
                  </div>
                  
                  {/* 中间部分：内容预览 */}
                  {version.content && (
                    <div className="version-content-preview">
                      <div
                        dangerouslySetInnerHTML={{
                          __html: version.content,
                        }}
                      />
                    </div>
                  )}
                  
                  {/* 右下角：预览和回退按钮 */}
                  <div className="version-actions">
                    <Space>
                      <Tooltip title="预览版本内容">
                        <Button
                          type="text"
                          size="small"
                          icon={<EyeOutlined />}
                          onClick={() => handlePreview(version)}
                          className="version-action-btn"
                        >
                          预览
                        </Button>
                      </Tooltip>
                      {canRollback(version.version) && (
                        <Popconfirm
                          title={`确定要回退到版本 ${version.version} 吗？`}
                          description="当前版本的内容将被保存为快照"
                          onConfirm={() => handleRollback(version.version)}
                          okText="确认"
                          cancelText="取消"
                        >
                          <Tooltip title="回退到此版本">
                            <Button
                              type="text"
                              size="small"
                              danger
                              icon={<RollbackOutlined />}
                              loading={rollingBack}
                              className="version-action-btn"
                            >
                              回退
                            </Button>
                          </Tooltip>
                        </Popconfirm>
                      )}
                    </Space>
                  </div>
                </div>
              </List.Item>
            )}
            />
          )}
        </div>
      </Spin>

      {/* 版本预览模态框 */}
      <Modal
        title={`版本 ${previewVersion?.version} 预览`}
        open={previewVisible}
        onCancel={() => setPreviewVisible(false)}
        footer={[
          <Button key="close" onClick={() => setPreviewVisible(false)}>
            关闭
          </Button>,
          previewVersion &&
            canRollback(previewVersion.version) && (
              <Popconfirm
                key="rollback"
                title={`确定要回退到版本 ${previewVersion.version} 吗？`}
                onConfirm={() => handleRollback(previewVersion.version)}
                okText="确认"
                cancelText="取消"
              >
                <Button
                  type="primary"
                  danger
                  icon={<RollbackOutlined />}
                  loading={rollingBack}
                >
                  回退到此版本
                </Button>
              </Popconfirm>
            ),
        ].filter(Boolean)}
        width={800}
      >
        {previewVersion && (
          <div
            className="version-preview-content"
            dangerouslySetInnerHTML={{ __html: previewVersion.content || '' }}
          />
        )}
      </Modal>
    </Card>
  );
};

export default VersionHistory;

