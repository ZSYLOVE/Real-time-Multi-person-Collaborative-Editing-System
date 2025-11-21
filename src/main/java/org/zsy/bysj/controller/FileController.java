package org.zsy.bysj.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.zsy.bysj.dto.Result;
import org.zsy.bysj.service.FileService;
import org.zsy.bysj.util.RequestUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * 文件上传控制器
 */
@RestController
@RequestMapping("/api/file")
public class FileController {

    @Autowired
    private FileService fileService;

    @Value("${server.port:8080}")
    private String serverPort;

    /**
     * 上传头像
     */
    @PostMapping("/avatar")
    public ResponseEntity<Result<Map<String, String>>> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        try {
            Long userId = RequestUtil.getUserId(request);
            if (userId == null) {
                return ResponseEntity.badRequest().body(Result.error("未认证"));
            }

            // 验证文件类型
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ResponseEntity.badRequest().body(Result.error("只能上传图片文件"));
            }

            // 验证文件大小（2MB）
            if (file.getSize() > 2 * 1024 * 1024) {
                return ResponseEntity.badRequest().body(Result.error("图片大小不能超过2MB"));
            }

            // 保存文件
            String filePath = fileService.saveAvatar(file, userId);
            
            // 构建文件URL（filePath格式为 "avatars/filename.jpg"）
            String baseUrl = getBaseUrl(request);
            String fileUrl = baseUrl + "/uploads/" + filePath.replace("\\", "/");

            Map<String, String> data = new HashMap<>();
            data.put("url", fileUrl);
            data.put("path", filePath);

            return ResponseEntity.ok(Result.success("上传成功", data));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error("上传失败: " + e.getMessage()));
        }
    }

    /**
     * 获取基础URL
     */
    private String getBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int port = request.getServerPort();
        String contextPath = request.getContextPath();
        
        StringBuilder url = new StringBuilder();
        url.append(scheme).append("://").append(serverName);
        if (port != 80 && port != 443) {
            url.append(":").append(port);
        }
        if (contextPath != null && !contextPath.isEmpty()) {
            url.append(contextPath);
        }
        return url.toString();
    }
}

