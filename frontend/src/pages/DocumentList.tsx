import React, { useEffect, useState } from 'react';
import { List, Button, Input, Card, message, Modal, Space, Tag } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, ShareAltOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { apiService } from '@/services/api';
import { websocketService } from '@/services/websocket';
import useAuthStore from '@/stores/authStore';
import ShareModal from '@/components/Share/ShareModal';
import type { Document } from '@/types';
import dayjs from 'dayjs';
import './DocumentList.css';

const { Search } = Input;

const DocumentList: React.FC = () => {
  const navigate = useNavigate();
  const { user } = useAuthStore();
  const [documents, setDocuments] = useState<Document[]>([]);
  const [deletedDocuments, setDeletedDocuments] = useState<Document[]>([]);
  const [loading, setLoading] = useState(false);
  const [deletedLoading, setDeletedLoading] = useState(false);
  const [searchText, setSearchText] = useState('');
  const [shareModalVisible, setShareModalVisible] = useState(false);
  const [selectedDocumentId, setSelectedDocumentId] = useState<number | null>(null);
  const [renameModalVisible, setRenameModalVisible] = useState(false);
  const [renameTarget, setRenameTarget] = useState<Document | null>(null);
  const [renameTitle, setRenameTitle] = useState('');
  const [renaming, setRenaming] = useState(false);

  useEffect(() => {
    if (user?.id) {
      // 确保 token 存在后再加载文档
      const checkAndLoad = () => {
        const token = sessionStorage.getItem('token');
        if (token) {
          loadDocuments();
          loadDeletedDocuments();
          return true;
        }
        return false;
      };
      
      // 立即检查
      if (!checkAndLoad()) {
        // 如果 token 不存在，等待后重试（可能是登录过程中的时序问题）
        console.warn('DocumentList: Token不存在，等待后重试');
        let retryCount = 0;
        const maxRetries = 5; // 最多重试5次
        const retryInterval = 200; // 每次间隔200ms
        
        const retryTimer = setInterval(() => {
          retryCount++;
          if (checkAndLoad()) {
            clearInterval(retryTimer);
          } else if (retryCount >= maxRetries) {
            clearInterval(retryTimer);
            console.error('DocumentList: Token在多次重试后仍然不存在，无法加载文档');
            message.error('认证信息丢失，请重新登录');
          }
        }, retryInterval);
        
        return () => clearInterval(retryTimer);
      }
    }
  }, [user?.id]);

  // 监听标题更新：让“共享用户”在列表页也能实时看到最新标题
  useEffect(() => {
    if (!user?.id) return;

    const handleTitleUpdate = (message: any) => {
      if (!message || message.type !== 'DOCUMENT_TITLE_UPDATED') return;
      const docId = message.documentId;
      const newTitle = message.data?.title;
      if (!docId || typeof newTitle !== 'string') return;

      setDocuments((prev) =>
        prev.map((d) => (d.id === docId ? { ...d, title: newTitle } : d))
      );
      setDeletedDocuments((prev) =>
        prev.map((d) => (d.id === docId ? { ...d, title: newTitle } : d))
      );
    };

    websocketService.onMessage('DOCUMENT_TITLE_UPDATED', handleTitleUpdate);
    return () => {
      websocketService.offMessage('DOCUMENT_TITLE_UPDATED', handleTitleUpdate);
    };
  }, [user?.id]);

  const loadDocuments = async () => {
    if (!user?.id) {
      message.error('用户信息未加载');
      return;
    }
    
    setLoading(true);
    try {
      // 同时加载自己创建的文档和共享给我的文档
      const [myDocsResult, sharedDocsResult] = await Promise.all([
        apiService.getUserDocuments(user.id),
        apiService.getSharedDocuments(user.id),
      ]);
      
      const allDocuments: Document[] = [];
      
      // 获取我的权限信息（用于所有文档）
      const permissionsResult = await apiService.getUserPermissions();
      const permissionsMap = new Map<number, string>();
      if (permissionsResult.code === 200 && permissionsResult.data) {
        permissionsResult.data.forEach((perm: any) => {
          if (perm.documentId) {
            permissionsMap.set(perm.documentId, perm.permissionType);
          }
        });
      }
      
      // 添加自己创建的文档（创建者拥有管理员权限）
      if (myDocsResult.code === 200 && myDocsResult.data) {
        myDocsResult.data.forEach(doc => {
          allDocuments.push({ 
            ...doc, 
            isShared: false,
            permissionType: 'ADMIN' as any, // 创建者默认是管理员
          });
        });
      }
      
      // 添加共享给我的文档
      if (sharedDocsResult.code === 200 && sharedDocsResult.data) {
        sharedDocsResult.data.forEach(doc => {
          // 排除自己创建的文档（避免重复）
          if (doc.creatorId !== user.id) {
            allDocuments.push({
              ...doc,
              isShared: true,
              permissionType: permissionsMap.get(doc.id) as any,
            });
          }
        });
      }
      
      // 按更新时间排序
      allDocuments.sort((a, b) => {
        const timeA = new Date(a.updatedAt || a.createdAt || '').getTime();
        const timeB = new Date(b.updatedAt || b.createdAt || '').getTime();
        return timeB - timeA;
      });
      
      // 调试信息
      console.log('加载的文档列表:', allDocuments);
      console.log('共享文档数量:', allDocuments.filter(doc => doc.isShared).length);
      
      setDocuments(allDocuments);
    } catch (error: any) {
      console.error('加载文档列表失败:', error);
      const errorMessage = error.response?.data?.message || error.message || '加载文档列表失败';
      message.error(errorMessage);
    } finally {
      setLoading(false);
    }
  };

  const loadDeletedDocuments = async (): Promise<number> => {
    if (!user?.id) return 0;
    setDeletedLoading(true);
    try {
      const result = await apiService.getDeletedDocuments();
      if (result.code === 200 && result.data) {
        setDeletedDocuments(result.data);
        console.log('回收站文档列表:', result.data);
        return result.data.length || 0;
      }
      return 0;
    } catch (error: any) {
      console.error('加载回收站文档失败:', error);
      message.error(error.response?.data?.message || '加载回收站失败');
      return 0;
    } finally {
      setDeletedLoading(false);
    }
  };

  const handleRestoreDocument = (doc: Document) => {
    Modal.confirm({
      title: '确认恢复文档',
      content: `是否恢复 "${doc.title}"？`,
      okText: '恢复',
      cancelText: '取消',
      onOk: async () => {
        try {
          const result = await apiService.restoreDocument(doc.id);
          if (result.code === 200) {
            message.success('恢复成功');
            await loadDocuments();
            await loadDeletedDocuments();
          } else {
            message.error(result.message || '恢复失败');
          }
        } catch (error: any) {
          message.error(error.response?.data?.message || '恢复失败');
        }
      },
    });
  };

  const handleForceDeleteDocument = (doc: Document) => {
    Modal.confirm({
      title: '彻底删除文档',
      content: `文档 "${doc.title}" 将被彻底删除，无法恢复。确认删除吗？`,
      okText: '彻底删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        try {
          const result = await apiService.forceDeleteDocument(doc.id);
          if (result.code === 200) {
            message.success('删除成功（已彻底删除）');
            await loadDocuments();
            await loadDeletedDocuments();
          } else {
            message.error(result.message || '删除失败');
          }
        } catch (error: any) {
          message.error(error.response?.data?.message || '删除失败');
        }
      },
    });
  };

  const openRenameModal = (doc: Document) => {
    setRenameTarget(doc);
    setRenameTitle(doc.title);
    setRenameModalVisible(true);
  };

  const confirmRename = async () => {
    if (!renameTarget) return;
    const title = renameTitle.trim();
    if (!title) {
      message.warning('标题不能为空');
      return;
    }
    setRenaming(true);
    try {
      const result = await apiService.updateDocumentTitle(renameTarget.id, title);
      if (result.code === 200) {
        message.success('标题修改成功');
        setRenameModalVisible(false);
        setRenameTarget(null);
        await loadDocuments();
        await loadDeletedDocuments();
      } else {
        message.error(result.message || '标题修改失败');
      }
    } catch (error: any) {
      message.error(error.response?.data?.message || '标题修改失败');
    } finally {
      setRenaming(false);
    }
  };

  const handleCreateDocument = async () => {
    Modal.confirm({
      title: '创建新文档',
      content: (
        <Input
          placeholder="请输入文档标题"
          id="document-title-input"
          onPressEnter={(e) => {
            const title = (e.target as HTMLInputElement).value;
            if (title.trim()) {
              createDocument(title.trim());
              Modal.destroyAll();
            }
          }}
        />
      ),
      onOk: () => {
        const input = document.getElementById('document-title-input') as HTMLInputElement;
        const title = input?.value?.trim();
        if (title) {
          createDocument(title);
        }
      },
    });
  };

  const createDocument = async (title: string) => {
    try {
      const result = await apiService.createDocument(title);
      if (result.code === 200 && result.data) {
        message.success('文档创建成功');
        navigate(`/documents/${result.data.id}`);
      }
    } catch (error: any) {
      message.error(error.response?.data?.message || '创建文档失败');
    }
  };

  const handleDeleteDocument = (doc: Document) => {
    if (!user?.id) {
      message.error('用户信息未加载');
      return;
    }
    
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除文档"${doc.title}"吗？删除后将放入回收站，可恢复。`,
      onOk: async () => {
        try {
          const result = await apiService.deleteDocument(doc.id, user.id);
          if (result.code === 200) {
            message.success('删除成功（已加入回收站）');
            await loadDocuments();
            const deletedCount = await loadDeletedDocuments();
            if (deletedCount === 0) {
              message.warning('删除成功了，但回收站列表仍为空，请稍后刷新或检查后端日志。');
            }
          } else {
            message.error(result.message || '删除失败');
          }
        } catch (error: any) {
          message.error(error.response?.data?.message || '删除失败');
        }
      },
    });
  };

  const filteredDocuments = documents.filter((doc) =>
    doc.title.toLowerCase().includes(searchText.toLowerCase())
  );

  const formatDate = (dateString?: string) => {
    if (!dateString) return '';
    // 后端返回格式通常是 "yyyy-MM-dd HH:mm:ss"（有空格），JS 的 new Date() 解析可能不稳定
    const parsed = dayjs(dateString, 'YYYY-MM-DD HH:mm:ss', 'zh-cn', true);
    const date = parsed.isValid() ? parsed : dayjs(dateString);

    if (!date.isValid()) return dateString;

    const now = dayjs();
    const diffDays = now.startOf('day').diff(date.startOf('day'), 'day');

    if (diffDays === 0) return `今天 ${date.format('HH:mm')}`;
    if (diffDays === 1) return `昨天 ${date.format('HH:mm')}`;
    if (diffDays < 7) return `${diffDays}天前 ${date.format('HH:mm')}`;
    return date.format('YYYY-MM-DD HH:mm');
  };

  return (
    <div className="document-list-container">
      <h1>我的文档</h1>
      <Card>
        <Space direction="vertical" style={{ width: '100%' }} size="large" className="document-list-space">
            <Space className="search-action-space" size="middle" wrap>
              <Search
                placeholder="搜索文档"
                allowClear
                className="document-search-input"
                onSearch={setSearchText}
                onChange={(e) => setSearchText(e.target.value)}
              />
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={handleCreateDocument}
                className="create-document-btn"
              >
                新建文档
              </Button>
            </Space>

            {filteredDocuments.length === 0 && !loading ? (
              <div style={{ textAlign: 'center', padding: '60px 20px', color: '#999' }}>
                <p style={{ fontSize: '16px', marginBottom: '16px' }}>
                  {searchText ? '没有找到匹配的文档' : '暂无文档'}
                </p>
                {!searchText && (
                  <Button
                    type="primary"
                    icon={<PlusOutlined />}
                    onClick={handleCreateDocument}
                  >
                    创建第一个文档
                  </Button>
                )}
              </div>
            ) : (
              <div className="document-list-scroll">
                <List
                  loading={loading}
                  dataSource={filteredDocuments}
                  renderItem={(doc) => (
                    <List.Item
                      className="document-list-item"
                      actions={[
                        <div key="buttons" className="document-buttons-right">
                          <Button
                            key="share"
                            type="default"
                            icon={<ShareAltOutlined />}
                            onClick={() => {
                              setSelectedDocumentId(doc.id);
                              setShareModalVisible(true);
                            }}
                            disabled={doc.creatorId !== user?.id}
                            className="document-action-btn"
                          >
                            共享
                          </Button>
                          <Button
                            key="edit"
                            type="primary"
                            icon={<EditOutlined />}
                            onClick={() => navigate(`/documents/${doc.id}`)}
                            className="document-action-btn"
                          >
                            编辑
                          </Button>
                          {doc.creatorId === user?.id && (
                            <Button
                              key="rename"
                              type="default"
                              icon={<EditOutlined />}
                              onClick={() => openRenameModal(doc)}
                              className="document-action-btn"
                            >
                              改标题
                            </Button>
                          )}
                          {doc.creatorId === user?.id && (
                            <Button
                              key="delete"
                              type="default"
                              danger
                              icon={<DeleteOutlined />}
                              onClick={() => handleDeleteDocument(doc)}
                              className="document-action-btn"
                            >
                              删除
                            </Button>
                          )}
                        </div>,
                      ]}
                    >
                      <List.Item.Meta
                        title={
                          <div className="document-title-with-tags">
                            <span className="document-title">{doc.title}</span>
                            <div className="document-tags-inline">
                              {doc.isShared && (
                                <Tag color="blue" className="document-tag">共享</Tag>
                              )}
                              {doc.permissionType && (
                                <Tag 
                                  color={
                                    doc.permissionType === 'ADMIN' ? 'red' :
                                    doc.permissionType === 'WRITE' ? 'green' : 'default'
                                  }
                                  className="document-tag"
                                >
                                  {doc.permissionType === 'ADMIN' ? '管理员' :
                                   doc.permissionType === 'WRITE' ? '编辑' : '只读'}
                                </Tag>
                              )}
                            </div>
                          </div>
                        }
                        description={
                          <Space wrap split={<span>•</span>} className="doc-meta-info">
                            <span>版本: {doc.version}</span>
                            <span>更新于: {formatDate(doc.updatedAt || doc.createdAt)}</span>
                          </Space>
                        }
                      />
                    </List.Item>
                  )}
                />
              </div>
            )}
          </Space>
        </Card>

        <Card
          title="回收站（可恢复）"
          style={{ marginTop: 16 }}
          loading={deletedLoading}
        >
          {deletedDocuments.length === 0 && !deletedLoading ? (
            <div style={{ textAlign: 'center', padding: '40px 0', color: '#999' }}>
              暂无已删除文档
            </div>
          ) : (
            <div className="document-deleted-scroll document-list-scroll">
              <List
                dataSource={deletedDocuments}
                renderItem={(doc) => (
                  <List.Item className="document-list-item">
                    <List.Item.Meta
                      title={doc.title}
                      description={
                        <Space wrap={false} split={<span>•</span>} className="doc-meta-info">
                          <span>版本: {doc.version}</span>
                          <span>更新于: {formatDate(doc.updatedAt || doc.createdAt)}</span>
                        </Space>
                      }
                    />
                    <div className="document-buttons-right" style={{ marginTop: 0 }}>
                      <Button
                        key="restore"
                        type="primary"
                        onClick={() => handleRestoreDocument(doc)}
                        className="document-action-btn"
                      >
                        恢复
                      </Button>
                      <Button
                        key="force-delete"
                        type="primary"
                        danger
                        onClick={() => handleForceDeleteDocument(doc)}
                        className="document-action-btn"
                      >
                        彻底删除
                      </Button>
                    </div>
                  </List.Item>
                )}
              />
            </div>
          )}
        </Card>
      
      {/* 共享模态框 */}
      {selectedDocumentId && (
        <ShareModal
          documentId={selectedDocumentId}
          visible={shareModalVisible}
          onCancel={() => {
            setShareModalVisible(false);
            setSelectedDocumentId(null);
          }}
          isCreator={documents.find(doc => doc.id === selectedDocumentId)?.creatorId === user?.id || false}
        />
      )}

      <Modal
        title="修改文档标题"
        visible={renameModalVisible}
        confirmLoading={renaming}
        onOk={confirmRename}
        onCancel={() => {
          setRenameModalVisible(false);
          setRenameTarget(null);
        }}
        okText="保存"
        cancelText="取消"
      >
        <Input
          value={renameTitle}
          onChange={(e) => setRenameTitle(e.target.value)}
          placeholder="请输入新标题"
          autoFocus
        />
      </Modal>
    </div>
  );
};

export default DocumentList;

