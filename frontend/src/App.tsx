import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ConfigProvider, theme } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { useEffect, useState } from 'react';
import Login from './pages/Login';
import Register from './pages/Register';
import Home from './pages/Home';
import DocumentList from './pages/DocumentList';
import DocumentEditor from './pages/DocumentEditor';
import Profile from './pages/Profile';
import MainLayout from './components/Layout/MainLayout';
import useAuthStore from './stores/authStore';
import useThemeStore from './stores/themeStore';
import { websocketService } from './services/websocket';
import { apiService } from './services/api';
import './App.css';

function App() {
  const { isAuthenticated, init, user, token } = useAuthStore();
  const { theme: themeMode, init: initTheme } = useThemeStore();
  const [isInitialized, setIsInitialized] = useState(false);

  // 初始化：恢复登录状态、主题并连接WebSocket
  useEffect(() => {
    try {
      init();
      initTheme();
      setIsInitialized(true);
    } catch (error) {
      console.error('初始化失败:', error);
      setIsInitialized(true);
    }
  }, [init, initTheme]);

  // 如果用户已登录，获取最新的用户信息（包括头像）
  useEffect(() => {
    if (isAuthenticated && token) {
      apiService.getUserInfo()
        .then((result) => {
          if (result.code === 200 && result.data) {
            const { setUser } = useAuthStore.getState();
            setUser(result.data);
            // 同时更新localStorage
            localStorage.setItem('user', JSON.stringify(result.data));
          }
        })
        .catch((error) => {
          console.warn('获取用户信息失败:', error);
        });
    }
  }, [isAuthenticated, token]);

  // 应用主题类名到根元素
  useEffect(() => {
    document.documentElement.classList.toggle('dark-theme', themeMode === 'dark');
  }, [themeMode]);

  // 如果用户已登录，尝试连接WebSocket
  useEffect(() => {
    if (isAuthenticated && user && token && !websocketService.getConnected()) {
      console.log('用户已登录，尝试连接WebSocket');
      websocketService.connect(token, user.id).catch((error) => {
        console.warn('WebSocket连接失败:', error);
      });
    }
  }, [isAuthenticated, user, token]);

  // 等待初始化完成
  if (!isInitialized) {
    return (
      <ConfigProvider locale={zhCN}>
        <div style={{ 
          display: 'flex', 
          justifyContent: 'center', 
          alignItems: 'center', 
          height: '100vh',
          fontSize: '16px'
        }}>
          加载中...
        </div>
      </ConfigProvider>
    );
  }

  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        algorithm: themeMode === 'dark' ? theme.darkAlgorithm : theme.defaultAlgorithm,
      }}
    >
      <div className={`app ${themeMode}-theme`}>
        <BrowserRouter
        future={{
          v7_startTransition: true,
          v7_relativeSplatPath: true,
        }}
      >
        <Routes>
          <Route
            path="/login"
            element={isAuthenticated ? <Navigate to="/home" replace /> : <Login />}
          />
          <Route
            path="/register"
            element={isAuthenticated ? <Navigate to="/home" replace /> : <Register />}
          />
          <Route
            path="/"
            element={
              isAuthenticated ? (
                <MainLayout>
                  <Home />
                </MainLayout>
              ) : (
                <Navigate to="/login" replace />
              )
            }
          />
          <Route
            path="/home"
            element={
              isAuthenticated ? (
                <MainLayout>
                  <Home />
                </MainLayout>
              ) : (
                <Navigate to="/login" replace />
              )
            }
          />
          <Route
            path="/documents"
            element={
              isAuthenticated ? (
                <MainLayout>
                  <DocumentList />
                </MainLayout>
              ) : (
                <Navigate to="/login" replace />
              )
            }
          />
          <Route
            path="/documents/:id"
            element={isAuthenticated ? <DocumentEditor /> : <Navigate to="/login" replace />}
          />
          <Route
            path="/profile"
            element={
              isAuthenticated ? (
                <MainLayout>
                  <Profile />
                </MainLayout>
              ) : (
                <Navigate to="/login" replace />
              )
            }
          />
        </Routes>
      </BrowserRouter>
      </div>
    </ConfigProvider>
  );
}

export default App;

