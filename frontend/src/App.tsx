import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ConfigProvider, theme } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { useEffect, useState, useRef } from 'react';
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

  // 如果用户已登录，验证token有效性并获取最新的用户信息
  // 使用 useRef 来跟踪是否已经验证过，避免无限循环
  const hasVerifiedRef = useRef(false);
  const lastVerifiedUserIdRef = useRef<number | null>(null);
  const verificationInProgressRef = useRef(false);
  
  useEffect(() => {
    // 如果用户未登录，重置验证标志
    if (!isAuthenticated || !token || !user) {
      hasVerifiedRef.current = false;
      lastVerifiedUserIdRef.current = null;
      verificationInProgressRef.current = false;
      return;
    }
    
    // 如果正在验证中，跳过（防止并发验证）
    if (verificationInProgressRef.current) {
      return;
    }
    
    // 如果已经验证过且用户ID没有变化，跳过
    if (hasVerifiedRef.current && lastVerifiedUserIdRef.current === user.id) {
      return;
    }
    
    // 标记为正在验证
    verificationInProgressRef.current = true;
    hasVerifiedRef.current = true;
    lastVerifiedUserIdRef.current = user.id;
    
    // 保存当前用户ID和token，用于验证
    const currentUserId = user.id;
    const currentToken = token;
    
    console.log('App.tsx: 开始验证用户身份，用户ID:', currentUserId);
    
    apiService.getUserInfo()
      .then((result) => {
        verificationInProgressRef.current = false;
        
        if (result.code === 200 && result.data) {
          const newUser = result.data;
          
          // 关键验证1：检查返回的用户ID是否与当前用户ID一致
          // 如果不一致，说明token是其他用户的，清除所有认证信息
          if (newUser.id !== currentUserId) {
            console.error('App.tsx: 严重安全错误！用户ID不匹配！检测到token属于其他用户。', {
              当前用户ID: currentUserId,
              返回用户ID: newUser.id,
              当前token: currentToken?.substring(0, 20) + '...',
            });
            
            // 立即清除所有认证信息
            // 统一退出（通知后端释放锁 + 清理前端 + 跳转）
            import('@/utils/signOut').then(({ signOut }) => {
              signOut({ redirectTo: '/login' });
            });
            return;
          }
          
          // 关键验证2：检查当前store中的token是否仍然是刚才验证时使用的token
          // 如果token被其他标签页更改了，说明用户可能在其他标签页登录了，需要重新验证
          const { token: currentStoreToken } = useAuthStore.getState();
          if (currentStoreToken !== currentToken) {
            console.warn('App.tsx: Token已被其他标签页更改，需要重新验证');
            hasVerifiedRef.current = false;
            lastVerifiedUserIdRef.current = null;
            return; // 返回，让effect重新触发
          }
          
          // 用户ID一致且token未变，更新用户信息（但只在数据真正变化时更新，避免循环）
          const { user: currentUser } = useAuthStore.getState();
          if (!currentUser || JSON.stringify(currentUser) !== JSON.stringify(newUser)) {
            const { setUser } = useAuthStore.getState();
            // setUser 会自动更新 sessionStorage，不需要手动设置
            setUser(newUser);
            console.log('App.tsx: 用户信息已更新，用户ID:', newUser.id);
          }
        } else {
          // 如果获取用户信息失败（可能是token过期），清除认证信息
          // 但如果是网络错误或其他非401错误，不要立即清除，给一个重试机会
          console.warn('App.tsx: 获取用户信息失败，code:', result.code, 'message:', result.message);
          hasVerifiedRef.current = false;
          lastVerifiedUserIdRef.current = null;
          
          // 只有在明确是认证失败时才清除状态
          if (result.code === 401 || result.code === 403) {
            import('@/utils/signOut').then(({ signOut }) => {
              signOut({ redirectTo: '/login' });
            });
          }
        }
      })
      .catch((error) => {
        verificationInProgressRef.current = false;
        console.warn('App.tsx: 获取用户信息失败:', error);
        
        // 如果是401错误（未授权），清除认证信息
        if (error.response?.status === 401 || error.response?.data?.code === 401) {
          console.warn('App.tsx: Token已过期或无效，清除认证信息');
          hasVerifiedRef.current = false;
          lastVerifiedUserIdRef.current = null;
          import('@/utils/signOut').then(({ signOut }) => {
            signOut({ redirectTo: '/login' });
          });
        } else {
          // 网络错误或其他错误，不立即清除状态，给一个重试机会
          // 重置验证标志，让下次可以重试
          hasVerifiedRef.current = false;
          lastVerifiedUserIdRef.current = null;
          console.warn('App.tsx: 网络错误，保留登录状态，稍后重试');
        }
      });
  }, [isAuthenticated, token, user?.id]); // 只依赖 user.id，而不是整个 user 对象

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
            element={isAuthenticated ? <Navigate to="/documents" replace /> : <Login />}
          />
          <Route
            path="/register"
            element={isAuthenticated ? <Navigate to="/documents" replace /> : <Register />}
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

