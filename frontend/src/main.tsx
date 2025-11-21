import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App.tsx'
import './index.css'

// 错误边界处理
window.addEventListener('error', (event) => {
  console.error('全局错误:', event.error);
});

window.addEventListener('unhandledrejection', (event) => {
  console.error('未处理的Promise拒绝:', event.reason);
});

const rootElement = document.getElementById('root');

if (!rootElement) {
  throw new Error('找不到root元素');
}

try {
  ReactDOM.createRoot(rootElement).render(
    <App />
  );
} catch (error) {
  console.error('渲染失败:', error);
  rootElement.innerHTML = `
    <div style="padding: 20px; text-align: center;">
      <h2>应用加载失败</h2>
      <p>请检查浏览器控制台查看详细错误信息</p>
      <pre>${error}</pre>
    </div>
  `;
}

