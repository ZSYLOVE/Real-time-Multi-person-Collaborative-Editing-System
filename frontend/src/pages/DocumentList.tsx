import React, { useEffect, useState } from 'react';
import { List, Button, Input, Card, message, Modal, Space, Tag } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, ShareAltOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { apiService } from '@/services/api';
import useAuthStore from '@/stores/authStore';
import ShareModal from '@/components/Share/ShareModal';
import type { Document } from '@/types';
import './DocumentList.css';

const { Search } = Input;

const DocumentList: React.FC = () => {
  const navigate = useNavigate();
  const { user } = useAuthStore();
  const [documents, setDocuments] = useState<Document[]>([]);
  const [loading, setLoading] = useState(false);
  const [searchText, setSearchText] = useState('');
  const [shareModalVisible, setShareModalVisible] = useState(false);
  const [selectedDocumentId, setSelectedDocumentId] = useState<number | null>(null);

  useEffect(() => {
    if (user?.id) {
      loadDocuments();
    }
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
      content: `确定要删除文档"${doc.title}"吗？`,
      onOk: async () => {
        try {
          const result = await apiService.deleteDocument(doc.id, user.id);
          if (result.code === 200) {
            message.success('删除成功');
            loadDocuments();
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

  return (
    <div className="document-list-container">
      <h1>我的文档</h1>
      <Card>
        <Space direction="vertical" style={{ width: '100%' }} size="large">
            <Space>
              <Search
                placeholder="搜索文档"
                allowClear
                style={{ width: 300 }}
                onSearch={setSearchText}
                onChange={(e) => setSearchText(e.target.value)}
              />
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={handleCreateDocument}
              >
                新建文档
              </Button>
            </Space>

            <List
              loading={loading}
              dataSource={filteredDocuments}
              renderItem={(doc) => (
                <List.Item
                  actions={[
                    <Button
                      key="share"
                      type="link"
                      icon={<ShareAltOutlined />}
                      onClick={() => {
                        setSelectedDocumentId(doc.id);
                        setShareModalVisible(true);
                      }}
                      disabled={doc.creatorId !== user?.id}
                    >
                      共享
                    </Button>,
                    <Button
                      key="edit"
                      type="link"
                      icon={<EditOutlined />}
                      onClick={() => navigate(`/documents/${doc.id}`)}
                    >
                      编辑
                    </Button>,
                    // 只有创建者可以删除
                    doc.creatorId === user?.id && (
                      <Button
                        key="delete"
                        type="link"
                        danger
                        icon={<DeleteOutlined />}
                        onClick={() => handleDeleteDocument(doc)}
                      >
                        删除
                      </Button>
                    ),
                  ]}
                >
                  <List.Item.Meta
                    title={
                      <Space>
                        <span>{doc.title}</span>
                        {doc.isShared && (
                          <Tag color="blue">共享</Tag>
                        )}
                        {doc.permissionType && (
                          <Tag color={
                            doc.permissionType === 'ADMIN' ? 'red' :
                            doc.permissionType === 'WRITE' ? 'green' : 'default'
                          }>
                            {doc.permissionType === 'ADMIN' ? '管理员' :
                             doc.permissionType === 'WRITE' ? '编辑' : '只读'}
                          </Tag>
                        )}
                      </Space>
                    }
                    description={`版本: ${doc.version} | 更新于: ${doc.updatedAt}`}
                  />
                </List.Item>
              )}
            />
          </Space>
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
    </div>
  );
};

export default DocumentList;

