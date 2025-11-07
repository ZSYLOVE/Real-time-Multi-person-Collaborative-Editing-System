package org.zsy.bysj.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.zsy.bysj.dto.Result;
import org.zsy.bysj.model.Document;
import org.zsy.bysj.model.DocumentPermission;
import org.zsy.bysj.service.DocumentService;
import org.zsy.bysj.service.PermissionService;
import org.zsy.bysj.util.JwtUtil;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 权限控制器
 */
@RestController
@RequestMapping("/api/permissions")
public class PermissionController {

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 添加文档权限
     */
    @PostMapping
    public ResponseEntity<Result<Void>> addPermission(@RequestBody Map<String, Object> request, HttpServletRequest httpRequest) {
        try {
            Long currentUserId = getCurrentUserId(httpRequest);
            Long documentId = Long.valueOf(request.get("documentId").toString());
            Long targetUserId = Long.valueOf(request.get("userId").toString());
            String permissionType = request.get("permissionType").toString();

            // 检查当前用户是否有管理员权限
            if (!permissionService.hasPermission(documentId, currentUserId, "ADMIN")) {
                return ResponseEntity.status(403).body(Result.error("无权限管理文档权限"));
            }

            // 验证权限类型
            if (!isValidPermissionType(permissionType)) {
                return ResponseEntity.badRequest().body(Result.error("无效的权限类型"));
            }

            permissionService.addPermission(documentId, targetUserId, permissionType);
            return ResponseEntity.ok(Result.success("权限添加成功", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    /**
     * 更新文档权限
     */
    @PutMapping
    public ResponseEntity<Result<Void>> updatePermission(@RequestBody Map<String, Object> request, HttpServletRequest httpRequest) {
        try {
            Long currentUserId = getCurrentUserId(httpRequest);
            Long documentId = Long.valueOf(request.get("documentId").toString());
            Long targetUserId = Long.valueOf(request.get("userId").toString());
            String permissionType = request.get("permissionType").toString();

            // 检查当前用户是否有管理员权限
            if (!permissionService.hasPermission(documentId, currentUserId, "ADMIN")) {
                return ResponseEntity.status(403).body(Result.error("无权限管理文档权限"));
            }

            // 不允许修改创建者的权限
            Document document = documentService.getDocumentById(documentId);
            if (document != null && document.getCreatorId().equals(targetUserId)) {
                return ResponseEntity.badRequest().body(Result.error("不能修改文档创建者的权限"));
            }

            // 验证权限类型
            if (!isValidPermissionType(permissionType)) {
                return ResponseEntity.badRequest().body(Result.error("无效的权限类型"));
            }

            permissionService.updatePermission(documentId, targetUserId, permissionType);
            return ResponseEntity.ok(Result.success("权限更新成功", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    /**
     * 删除文档权限
     */
    @DeleteMapping
    public ResponseEntity<Result<Void>> removePermission(@RequestParam Long documentId, @RequestParam Long userId, HttpServletRequest httpRequest) {
        try {
            Long currentUserId = getCurrentUserId(httpRequest);

            // 检查当前用户是否有管理员权限
            if (!permissionService.hasPermission(documentId, currentUserId, "ADMIN")) {
                return ResponseEntity.status(403).body(Result.error("无权限管理文档权限"));
            }

            // 不允许删除创建者的权限
            Document document = documentService.getDocumentById(documentId);
            if (document != null && document.getCreatorId().equals(userId)) {
                return ResponseEntity.badRequest().body(Result.error("不能删除文档创建者的权限"));
            }

            permissionService.removePermission(documentId, userId);
            return ResponseEntity.ok(Result.success("权限删除成功", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    /**
     * 获取文档的所有权限
     */
    @GetMapping("/document/{documentId}")
    public ResponseEntity<Result<List<DocumentPermission>>> getDocumentPermissions(@PathVariable Long documentId, HttpServletRequest httpRequest) {
        try {
            Long currentUserId = getCurrentUserId(httpRequest);

            // 检查当前用户是否有管理员权限
            if (!permissionService.hasPermission(documentId, currentUserId, "ADMIN")) {
                return ResponseEntity.status(403).body(Result.error("无权限查看文档权限"));
            }

            List<DocumentPermission> permissions = permissionService.getDocumentPermissions(documentId);
            return ResponseEntity.ok(Result.success(permissions));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error("获取文档权限失败: " + e.getMessage()));
        }
    }

    /**
     * 检查用户权限
     */
    @GetMapping("/check")
    public ResponseEntity<Result<Map<String, Object>>> checkPermission(
            @RequestParam Long documentId,
            @RequestParam Long userId,
            @RequestParam String permissionType,
            HttpServletRequest httpRequest) {
        try {
            Long currentUserId = getCurrentUserId(httpRequest);

            // 检查当前用户是否有读取权限
            if (!permissionService.hasPermission(documentId, currentUserId, "READ")) {
                return ResponseEntity.status(403).body(Result.error("无权限查看权限信息"));
            }

            boolean hasPermission = permissionService.hasPermission(documentId, userId, permissionType);

            Map<String, Object> data = new HashMap<>();
            data.put("hasPermission", hasPermission);

            return ResponseEntity.ok(Result.success(data));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error("检查权限失败: " + e.getMessage()));
        }
    }

    /**
     * 获取用户的权限
     */
    @GetMapping("/user")
    public ResponseEntity<Result<List<DocumentPermission>>> getUserPermissions(HttpServletRequest httpRequest) {
        try {
            Long userId = getCurrentUserId(httpRequest);
            List<DocumentPermission> permissions = permissionService.getUserPermissions(userId);
            return ResponseEntity.ok(Result.success(permissions));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error("获取用户权限失败: " + e.getMessage()));
        }
    }

    private Long getCurrentUserId(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            return jwtUtil.getUserIdFromToken(token);
        }
        throw new RuntimeException("未登录或token无效");
    }

    private boolean isValidPermissionType(String permissionType) {
        return "READ".equals(permissionType) || "WRITE".equals(permissionType) || "ADMIN".equals(permissionType);
    }
}

