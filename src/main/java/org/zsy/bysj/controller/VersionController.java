package org.zsy.bysj.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.zsy.bysj.annotation.RequirePermission;
import org.zsy.bysj.dto.Result;
import org.zsy.bysj.model.Document;
import org.zsy.bysj.model.DocumentVersion;
import org.zsy.bysj.service.DocumentService;
import org.zsy.bysj.util.RequestUtil;

import java.util.List;

/**
 * 文档版本控制器
 */
@RestController
@RequestMapping("/api/versions")
public class VersionController {

    @Autowired
    private DocumentService documentService;

    /**
     * 获取文档的所有版本
     */
    @RequirePermission("READ")
    @GetMapping("/document/{documentId}")
    public ResponseEntity<Result<List<DocumentVersion>>> getDocumentVersions(@PathVariable Long documentId) {
        List<DocumentVersion> versions = documentService.getDocumentVersions(documentId);
        return ResponseEntity.ok(Result.success(versions));
    }

    /**
     * 获取指定版本的快照
     */
    @RequirePermission("READ")
    @GetMapping("/document/{documentId}/version/{version}")
    public ResponseEntity<Result<DocumentVersion>> getVersionSnapshot(
            @PathVariable Long documentId,
            @PathVariable Integer version) {
        DocumentVersion versionSnapshot = documentService.getVersionSnapshot(documentId, version);
        if (versionSnapshot != null) {
            return ResponseEntity.ok(Result.success(versionSnapshot));
        }
        return ResponseEntity.badRequest().body(Result.error("版本快照不存在"));
    }

    /**
     * 创建当前版本的快照
     */
    @RequirePermission("WRITE")
    @PostMapping("/document/{documentId}/snapshot")
    public ResponseEntity<Result<Void>> createVersionSnapshot(
            @PathVariable Long documentId,
            @RequestParam Integer version,
            HttpServletRequest request) {
        try {
            Long userId = RequestUtil.getUserId(request);
            if (userId == null) {
                return ResponseEntity.badRequest().body(Result.error("未认证"));
            }
            
            documentService.createVersionSnapshot(documentId, version);
            return ResponseEntity.ok(Result.success("版本快照创建成功", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    /**
     * 回滚到指定版本
     */
    @RequirePermission("ADMIN")
    @PostMapping("/document/{documentId}/rollback")
    public ResponseEntity<Result<Document>> rollbackToVersion(
            @PathVariable Long documentId,
            @RequestParam Integer targetVersion,
            HttpServletRequest request) {
        try {
            Long userId = RequestUtil.getUserId(request);
            if (userId == null) {
                return ResponseEntity.badRequest().body(Result.error("未认证"));
            }
            
            Document document = documentService.rollbackToVersion(documentId, targetVersion, userId);
            return ResponseEntity.ok(Result.success("文档回滚成功", document));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }
}

