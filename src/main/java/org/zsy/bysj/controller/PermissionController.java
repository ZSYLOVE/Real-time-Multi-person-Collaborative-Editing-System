package org.zsy.bysj.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.zsy.bysj.dto.Result;
import org.zsy.bysj.model.DocumentPermission;
import org.zsy.bysj.service.PermissionService;

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

    /**
     * 添加文档权限
     */
    @PostMapping
    public ResponseEntity<Result<Void>> addPermission(@RequestBody Map<String, Object> request) {
        try {
            Long documentId = Long.valueOf(request.get("documentId").toString());
            Long userId = Long.valueOf(request.get("userId").toString());
            String permissionType = request.get("permissionType").toString();

            permissionService.addPermission(documentId, userId, permissionType);
            return ResponseEntity.ok(Result.success("权限添加成功", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    /**
     * 更新文档权限
     */
    @PutMapping
    public ResponseEntity<Result<Void>> updatePermission(@RequestBody Map<String, Object> request) {
        try {
            Long documentId = Long.valueOf(request.get("documentId").toString());
            Long userId = Long.valueOf(request.get("userId").toString());
            String permissionType = request.get("permissionType").toString();

            permissionService.updatePermission(documentId, userId, permissionType);
            return ResponseEntity.ok(Result.success("权限更新成功", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    /**
     * 删除文档权限
     */
    @DeleteMapping
    public ResponseEntity<Result<Void>> removePermission(@RequestParam Long documentId, @RequestParam Long userId) {
        try {
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
    public ResponseEntity<Result<List<DocumentPermission>>> getDocumentPermissions(@PathVariable Long documentId) {
        List<DocumentPermission> permissions = permissionService.getDocumentPermissions(documentId);
        return ResponseEntity.ok(Result.success(permissions));
    }

    /**
     * 检查用户权限
     */
    @GetMapping("/check")
    public ResponseEntity<Result<Map<String, Object>>> checkPermission(
            @RequestParam Long documentId,
            @RequestParam Long userId,
            @RequestParam String permissionType) {
        boolean hasPermission = permissionService.hasPermission(documentId, userId, permissionType);
        
        Map<String, Object> data = new HashMap<>();
        data.put("hasPermission", hasPermission);
        
        return ResponseEntity.ok(Result.success(data));
    }
}

