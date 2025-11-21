package org.zsy.bysj.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.zsy.bysj.annotation.RequirePermission;
import org.zsy.bysj.dto.Result;
import org.zsy.bysj.dto.WebSocketMessage;
import org.zsy.bysj.model.Document;
import org.zsy.bysj.algorithm.Operation;
import org.zsy.bysj.service.CollaborationService;
import org.zsy.bysj.service.DocumentService;
import org.zsy.bysj.util.RequestUtil;

import java.util.HashMap;
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

    @Autowired
    private CollaborationService collaborationService;

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
     * 获取用户的所有文档（创建的）
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<Result<List<Document>>> getUserDocuments(@PathVariable Long userId) {
        List<Document> documents = documentService.getUserDocuments(userId);
        return ResponseEntity.ok(Result.success(documents));
    }

    /**
     * 获取用户被共享的文档
     */
    @GetMapping("/shared/{userId}")
    public ResponseEntity<Result<List<Document>>> getSharedDocuments(@PathVariable Long userId) {
        List<Document> documents = documentService.getSharedDocuments(userId);
        return ResponseEntity.ok(Result.success(documents));
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
     * 更新文档内容（直接更新）
     */
    @RequirePermission("WRITE")
    @PutMapping("/{id}/content")
    public ResponseEntity<Result<Document>> updateDocumentContent(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        try {
            Long userId = RequestUtil.getUserId(httpRequest);
            if (userId == null) {
                return ResponseEntity.badRequest().body(Result.error("未认证"));
            }
            
            String content = request.get("content").toString();
            Document document = documentService.getDocumentById(id);
            if (document == null) {
                return ResponseEntity.badRequest().body(Result.error("文档不存在"));
            }
            
            // 使用带快照的方法更新文档（保存时创建快照）
            Document updatedDocument = ((org.zsy.bysj.service.impl.DocumentServiceImpl) documentService)
                    .updateDocumentContentWithSnapshot(id, content, document.getVersion());
            
            // 广播文档更新消息给所有在线用户
            WebSocketMessage updateMessage = new WebSocketMessage();
            updateMessage.setType("DOCUMENT_UPDATED");
            updateMessage.setDocumentId(id);
            updateMessage.setUserId(userId);
            updateMessage.setTimestamp(System.currentTimeMillis());
            Map<String, Object> updateData = new HashMap<>();
            updateData.put("content", updatedDocument.getContent());
            updateData.put("version", updatedDocument.getVersion());
            updateMessage.setData(updateData);
            collaborationService.broadcastToDocument(id, updateMessage);
            
            return ResponseEntity.ok(Result.success("文档内容更新成功", updatedDocument));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    /**
     * 应用操作到文档（使用OT算法）
     */
    @RequirePermission("WRITE")
    @PostMapping("/{id}/operation")
    public ResponseEntity<Result<Document>> applyOperation(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        try {
            Long userId = RequestUtil.getUserId(httpRequest);
            if (userId == null) {
                return ResponseEntity.badRequest().body(Result.error("未认证"));
            }
            
            // 解析操作
            @SuppressWarnings("unchecked")
            Map<String, Object> opMap = (Map<String, Object>) request.get("operation");
            String type = opMap.get("type").toString();
            String data = opMap.get("data") != null ? opMap.get("data").toString() : null;
            Integer position = ((Number) opMap.get("position")).intValue();
            Integer length = opMap.get("length") != null ? 
                ((Number) opMap.get("length")).intValue() : 0;
            
            Operation operation;
            if ("INSERT".equals(type)) {
                operation = Operation.insert(data, position);
            } else if ("DELETE".equals(type)) {
                operation = Operation.delete(position, length);
            } else if ("RETAIN".equals(type)) {
                operation = Operation.retain(position, length);
            } else {
                return ResponseEntity.badRequest().body(Result.error("不支持的操作类型"));
            }
            
            Document document = documentService.applyOperation(id, operation, userId);
            return ResponseEntity.ok(Result.success("操作应用成功", document));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
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

