package org.zsy.bysj.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.zsy.bysj.annotation.RequirePermission;
import org.zsy.bysj.dto.Result;
import org.zsy.bysj.model.Document;
import org.zsy.bysj.service.DocumentService;
import org.zsy.bysj.util.RequestUtil;

import java.util.List;
import java.util.Map;

/**
 * 文档控制器
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    @Autowired
    private DocumentService documentService;

    /**
     * 创建文档
     */
    @PostMapping
    public ResponseEntity<Result<Document>> createDocument(@RequestBody Map<String, Object> request, 
                                                           HttpServletRequest httpRequest) {
        try {
            String title = request.get("title").toString();
            // 从request attribute中获取userId（由JWT拦截器设置）
            Long creatorId = RequestUtil.getUserId(httpRequest);
            if (creatorId == null) {
                return ResponseEntity.badRequest().body(Result.error("未认证"));
            }
            
            Document document = documentService.createDocument(title, creatorId);
            return ResponseEntity.ok(Result.success("文档创建成功", document));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    /**
     * 获取文档
     */
    @RequirePermission("READ")
    @GetMapping("/{id}")
    public ResponseEntity<Result<Document>> getDocument(@PathVariable Long id) {
        Document document = documentService.getDocumentById(id);
        if (document != null) {
            return ResponseEntity.ok(Result.success(document));
        }
        return ResponseEntity.badRequest().body(Result.error("文档不存在"));
    }

    /**
     * 获取用户的所有文档
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<Result<List<Document>>> getUserDocuments(@PathVariable Long userId) {
        List<Document> documents = documentService.getUserDocuments(userId);
        return ResponseEntity.ok(Result.success(documents));
    }

    /**
     * 删除文档
     */
    @RequirePermission("ADMIN")
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> deleteDocument(@PathVariable Long id, @RequestParam Long userId) {
        try {
            documentService.deleteDocument(id, userId);
            return ResponseEntity.ok(Result.success("文档删除成功", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }
}

