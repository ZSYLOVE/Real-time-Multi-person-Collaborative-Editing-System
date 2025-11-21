import React, { useEffect, useState, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Layout, Button, message, Spin, Dropdown } from 'antd';
import { ArrowLeftOutlined, DownloadOutlined, FilePdfOutlined, FileWordOutlined, FileTextOutlined } from '@ant-design/icons';
import type { MenuProps } from 'antd';
import { apiService } from '@/services/api';
import useDocumentStore from '@/stores/documentStore';
import useAuthStore from '@/stores/authStore';
import CollaborativeEditor from '@/components/Editor/CollaborativeEditor';
import OnlineUsersList from '@/components/OnlineUsers/OnlineUsersList';
import CommentPanel from '@/components/Comment/CommentPanel';
import VersionHistory from '@/components/Version/VersionHistory';
import ShareModal from '@/components/Share/ShareModal';
import './DocumentEditor.css';

const { Header, Content } = Layout;

const DocumentEditor: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const documentId = id ? parseInt(id) : 0;
  const { user } = useAuthStore();
  const { currentDocument, setCurrentDocument, setLoading, loading, comments } = useDocumentStore();
  const [initialized, setInitialized] = useState(false);
  const [shareModalVisible, setShareModalVisible] = useState(false);
  const [exporting, setExporting] = useState(false);
  const sidebarSectionRef = useRef<HTMLDivElement>(null);
  const onlineUsersRef = useRef<HTMLDivElement>(null);
  const versionHistoryRef = useRef<HTMLDivElement>(null);
  const commentWrapperRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (documentId && user) {
      loadDocument();
    }
    return () => {
      // 清理工作
      useDocumentStore.getState().reset();
    };
  }, [documentId, user]);

  // 计算版本历史容器高度，使其底部与评论区底部对齐
  useEffect(() => {
    const syncHeight = () => {
      if (sidebarSectionRef.current && onlineUsersRef.current && versionHistoryRef.current && commentWrapperRef.current) {
        // 获取评论区的底部位置（相对于页面）
        const commentBottom = commentWrapperRef.current.getBoundingClientRect().bottom;
        // 获取在线用户列表的底部位置（相对于页面）
        const onlineUsersBottom = onlineUsersRef.current.getBoundingClientRect().bottom;
        // 获取gap（16px）
        const gap = 16;
        
        // 计算版本历史容器应该达到的底部位置（与评论区底部对齐）
        const targetBottom = commentBottom;
        // 计算版本历史容器的顶部位置（在线用户列表底部 + gap）
        const versionHistoryTop = onlineUsersBottom + gap;
        // 计算版本历史容器的高度 = 目标底部位置 - 容器顶部位置
        const versionHistoryHeight = targetBottom - versionHistoryTop;
        
        // 设置版本历史容器的高度
        if (versionHistoryHeight > 0) {
          versionHistoryRef.current.style.height = `${versionHistoryHeight}px`;
          versionHistoryRef.current.style.maxHeight = `${versionHistoryHeight}px`;
        }
      }
    };

    // 初始同步
    const timer = setTimeout(syncHeight, 100);

    // 监听窗口大小变化和评论变化
    window.addEventListener('resize', syncHeight);
    const interval = setInterval(syncHeight, 200);

    return () => {
      clearTimeout(timer);
      window.removeEventListener('resize', syncHeight);
      clearInterval(interval);
    };
  }, [comments, initialized]);

  const loadDocument = async () => {
    if (!documentId) return;
    
    setLoading(true);
    try {
      // 重置初始化状态，确保重新加载时能正确初始化编辑器
      setInitialized(false);
      
      const result = await apiService.getDocument(documentId);
      if (result.code === 200 && result.data) {
        setCurrentDocument(result.data);
        // 延迟设置初始化状态，确保编辑器组件已经挂载
        setTimeout(() => {
          setInitialized(true);
        }, 100);
      } else {
        message.error(result.message || '加载文档失败');
        navigate('/documents');
      }
    } catch (error: any) {
      message.error(error.response?.data?.message || '加载文档失败');
      navigate('/documents');
    } finally {
      setLoading(false);
    }
  };

  // 导出文档
  const handleExport = async (format: 'pdf' | 'word' | 'markdown') => {
    if (!documentId || !currentDocument) {
      message.error('文档信息不完整');
      return;
    }

    setExporting(true);
    try {
      let blob: Blob;
      let fileName: string;

      switch (format) {
        case 'pdf':
          blob = await apiService.exportPDF(documentId);
          fileName = `${currentDocument.title || '未命名文档'}.pdf`;
          break;
        case 'word':
          blob = await apiService.exportWord(documentId);
          fileName = `${currentDocument.title || '未命名文档'}.docx`;
          break;
        case 'markdown':
          blob = await apiService.exportMarkdown(documentId);
          fileName = `${currentDocument.title || '未命名文档'}.md`;
          break;
        default:
          message.error('不支持的导出格式');
          return;
      }

      // 创建下载链接
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = fileName;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);

      message.success('导出成功');
    } catch (error: any) {
      console.error('导出失败:', error);
      message.error(error.response?.data?.message || '导出失败，请稍后重试');
    } finally {
      setExporting(false);
    }
  };

  // 导出菜单项
  const exportMenuItems: MenuProps['items'] = [
    {
      key: 'pdf',
      icon: <FilePdfOutlined />,
      label: '导出为PDF',
      onClick: () => handleExport('pdf'),
    },
    {
      key: 'word',
      icon: <FileWordOutlined />,
      label: '导出为Word',
      onClick: () => handleExport('word'),
    },
    {
      key: 'markdown',
      icon: <FileTextOutlined />,
      label: '导出为Markdown',
      onClick: () => handleExport('markdown'),
    },
  ];

  if (!user || !documentId) {
    return null;
  }

  return (
    <Layout className="document-editor-layout">
      <Header className="document-editor-header">
        <div className="header-left">
          <Button
            icon={<ArrowLeftOutlined />}
            onClick={() => navigate('/documents')}
          >
            返回
          </Button>
          <h2>{currentDocument?.title || '加载中...'}</h2>
        </div>
        <div className="header-right">
          <Dropdown menu={{ items: exportMenuItems }} placement="bottomRight">
            <Button
              icon={<DownloadOutlined />}
              loading={exporting}
              disabled={!currentDocument}
            >
              导出
            </Button>
          </Dropdown>
        </div>
      </Header>
      <Content className="document-editor-content">
        {loading && !initialized ? (
          <Spin size="large" style={{ display: 'block', margin: '50px auto' }} />
        ) : (
          <div className="editor-layout-container">
            <div className="editor-main-section">
              <div className="editor-wrapper">
                <CollaborativeEditor
                  documentId={documentId}
                  userId={user.id}
                  readOnly={false}
                />
              </div>
              <div className="comment-wrapper" ref={commentWrapperRef}>
                <CommentPanel documentId={documentId} />
              </div>
            </div>
            <div className="editor-sidebar-section" ref={sidebarSectionRef}>
              <div className="online-users-wrapper" ref={onlineUsersRef}>
                <OnlineUsersList />
              </div>
              <div className="version-history-wrapper" ref={versionHistoryRef}>
                <VersionHistory documentId={documentId} />
              </div>
            </div>
          </div>
        )}
      </Content>
      
      {/* 共享模态框 */}
      <ShareModal
        documentId={documentId}
        visible={shareModalVisible}
        onCancel={() => setShareModalVisible(false)}
        isCreator={currentDocument?.creatorId === user?.id || false}
      />
    </Layout>
  );
};

export default DocumentEditor;

