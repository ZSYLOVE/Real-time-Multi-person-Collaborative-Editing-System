# 浏览器测试指南

## 导出功能测试

### 1. 准备工作

确保应用已启动：
```bash
# 启动应用
mvnw spring-boot:run
```

应用默认运行在：`http://localhost:8080`

### 2. 获取文档ID

首先需要获取一个文档ID，可以通过以下方式：

#### 方式一：使用 Postman 获取文档列表
```
GET http://localhost:8080/api/documents/user/{userId}
```

#### 方式二：使用 Postman 创建文档
```
POST http://localhost:8080/api/documents
Headers:
  Authorization: Bearer {your_token}
Body:
{
  "title": "测试文档"
}
```

### 3. 浏览器测试导出

#### 方法一：直接在浏览器地址栏输入URL

**导出为 PDF：**
```
http://localhost:8080/api/export/pdf/{documentId}
```

**导出为 Word：**
```
http://localhost:8080/api/export/word/{documentId}
```

**导出为 Markdown：**
```
http://localhost:8080/api/export/markdown/{documentId}
```

**注意：** 如果接口需要认证，需要先登录获取 Token，然后在请求头中添加：
```
Authorization: Bearer {your_token}
```

#### 方法二：使用浏览器开发者工具

1. 打开浏览器（推荐 Chrome 或 Firefox）
2. 按 `F12` 打开开发者工具
3. 切换到 `Network`（网络）标签
4. 在地址栏输入导出URL
5. 查看响应头中的 `Content-Disposition`，确认文件名是否正确

#### 方法三：创建简单的HTML测试页面

创建一个 `test-export.html` 文件：

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>导出功能测试</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            max-width: 800px;
            margin: 50px auto;
            padding: 20px;
        }
        .form-group {
            margin-bottom: 20px;
        }
        label {
            display: block;
            margin-bottom: 5px;
            font-weight: bold;
        }
        input {
            width: 100%;
            padding: 8px;
            box-sizing: border-box;
        }
        button {
            background-color: #4CAF50;
            color: white;
            padding: 10px 20px;
            border: none;
            cursor: pointer;
            margin-right: 10px;
            margin-bottom: 10px;
        }
        button:hover {
            background-color: #45a049;
        }
        .info {
            background-color: #f0f0f0;
            padding: 15px;
            border-radius: 5px;
            margin-top: 20px;
        }
    </style>
</head>
<body>
    <h1>文档导出功能测试</h1>
    
    <div class="form-group">
        <label for="documentId">文档ID：</label>
        <input type="number" id="documentId" placeholder="请输入文档ID" value="1">
    </div>
    
    <div class="form-group">
        <label for="token">Token（如果需要认证）：</label>
        <input type="text" id="token" placeholder="请输入Token（可选）">
    </div>
    
    <div class="form-group">
        <label>导出格式：</label>
        <button onclick="exportDocument('pdf')">导出为 PDF</button>
        <button onclick="exportDocument('word')">导出为 Word</button>
        <button onclick="exportDocument('markdown')">导出为 Markdown</button>
    </div>
    
    <div class="info">
        <h3>使用说明：</h3>
        <ol>
            <li>输入文档ID（必填）</li>
            <li>如果接口需要认证，输入Token（可选）</li>
            <li>点击相应的导出按钮</li>
            <li>浏览器会自动下载文件，检查文件名是否正确显示中文</li>
        </ol>
    </div>
    
    <script>
        function exportDocument(format) {
            const documentId = document.getElementById('documentId').value;
            const token = document.getElementById('token').value;
            
            if (!documentId) {
                alert('请输入文档ID');
                return;
            }
            
            const url = `http://localhost:8080/api/export/${format}/${documentId}`;
            
            // 创建隐藏的 iframe 来触发下载
            const iframe = document.createElement('iframe');
            iframe.style.display = 'none';
            document.body.appendChild(iframe);
            
            // 如果需要认证，使用 fetch API
            if (token) {
                fetch(url, {
                    method: 'GET',
                    headers: {
                        'Authorization': `Bearer ${token}`
                    }
                })
                .then(response => {
                    if (!response.ok) {
                        throw new Error('导出失败');
                    }
                    return response.blob();
                })
                .then(blob => {
                    // 从响应头获取文件名
                    const contentDisposition = response.headers.get('Content-Disposition');
                    let fileName = `document.${format === 'word' ? 'docx' : format === 'markdown' ? 'md' : 'pdf'}`;
                    
                    if (contentDisposition) {
                        const fileNameMatch = contentDisposition.match(/filename\*=UTF-8''(.+)/);
                        if (fileNameMatch) {
                            fileName = decodeURIComponent(fileNameMatch[1]);
                        } else {
                            const filenameMatch = contentDisposition.match(/filename="(.+)"/);
                            if (filenameMatch) {
                                fileName = decodeURIComponent(filenameMatch[1]);
                            }
                        }
                    }
                    
                    // 创建下载链接
                    const url = window.URL.createObjectURL(blob);
                    const a = document.createElement('a');
                    a.href = url;
                    a.download = fileName;
                    document.body.appendChild(a);
                    a.click();
                    document.body.removeChild(a);
                    window.URL.revokeObjectURL(url);
                })
                .catch(error => {
                    alert('导出失败：' + error.message);
                });
            } else {
                // 不需要认证，直接使用 iframe
                iframe.src = url;
            }
        }
    </script>
</body>
</html>
```

### 4. 测试步骤

1. **打开测试页面**：在浏览器中打开 `test-export.html`
2. **输入文档ID**：输入要导出的文档ID
3. **输入Token**（如果需要）：如果接口需要认证，输入Token
4. **点击导出按钮**：点击相应的导出格式按钮
5. **检查下载的文件**：
   - 查看浏览器下载的文件名是否正确显示中文
   - 打开文件检查内容是否正确

### 5. 验证文件名

下载文件后，检查：
- ✅ 文件名应显示为中文（如：`我的第一个文档.docx`）
- ✅ 不应显示URL编码（如：`%E6%88%91%E7%9A%84...`）
- ✅ 文件扩展名正确（`.pdf`、`.docx`、`.md`）

### 6. 常见问题

#### 问题1：文件名显示为URL编码
**原因**：浏览器不支持 RFC 5987 格式
**解决**：使用 Chrome 或 Firefox 浏览器（推荐）

#### 问题2：提示需要认证
**原因**：导出接口需要登录
**解决**：
1. 先调用登录接口获取Token
2. 在请求头中添加 `Authorization: Bearer {token}`

#### 问题3：下载失败
**原因**：可能是跨域问题或接口错误
**解决**：
1. 检查应用是否正常运行
2. 检查浏览器控制台是否有错误信息
3. 检查网络请求的响应状态码

### 7. 使用 Postman 测试（备选方案）

如果浏览器测试不方便，也可以使用 Postman：

1. **发送请求**：
   - Method: `GET`
   - URL: `http://localhost:8080/api/export/word/{documentId}`
   - Headers: `Authorization: Bearer {token}`（如果需要）

2. **下载文件**：
   - 点击 `Send and Download` 按钮
   - 或点击 `Save Response` → `Save to a file`

3. **检查文件名**：
   - Postman 可能会显示编码后的文件名
   - 但下载后的文件在文件系统中应该显示正确的中文文件名

### 8. 推荐测试浏览器

- ✅ **Chrome**：完全支持 RFC 5987，推荐使用
- ✅ **Firefox**：完全支持 RFC 5987，推荐使用
- ⚠️ **Edge**：支持 RFC 5987
- ❌ **IE**：不支持 RFC 5987，不推荐

### 9. 调试技巧

如果文件名仍然不正确，可以：

1. **查看响应头**：
   - 打开浏览器开发者工具（F12）
   - 切换到 Network 标签
   - 点击导出请求
   - 查看 Response Headers 中的 `Content-Disposition`

2. **检查编码**：
   - `Content-Disposition` 应包含 `filename*=UTF-8''...`
   - 这是 RFC 5987 格式，现代浏览器会自动解码

3. **测试不同浏览器**：
   - 在不同浏览器中测试，确认是否是浏览器兼容性问题

