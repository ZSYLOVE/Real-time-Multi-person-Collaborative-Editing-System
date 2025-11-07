package org.zsy.bysj.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.zsy.bysj.dto.OperationDTO;
import org.zsy.bysj.dto.Result;
import org.zsy.bysj.service.OfflineSyncService;
import org.zsy.bysj.util.RequestUtil;

import java.util.List;
import java.util.Map;

/**
 * 离线同步控制器
 */
@RestController
@RequestMapping("/api/offline")
public class OfflineSyncController {

    @Autowired
    private OfflineSyncService offlineSyncService;

    /**
     * 保存离线操作
     */
    @PostMapping("/operations")
    public ResponseEntity<Result<Void>> saveOfflineOperation(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        try {
            Long userId = RequestUtil.getUserId(httpRequest);
            if (userId == null) {
                return ResponseEntity.badRequest().body(Result.error("未认证"));
            }
            
            Long documentId = Long.valueOf(request.get("documentId").toString());
            @SuppressWarnings("unchecked")
            Map<String, Object> operationMap = (Map<String, Object>) request.get("operation");
            
            OperationDTO operation = new OperationDTO();
            operation.setType((String) operationMap.get("type"));
            operation.setData((String) operationMap.get("data"));
            operation.setPosition(((Number) operationMap.get("position")).intValue());
            operation.setLength(operationMap.get("length") != null ? 
                    ((Number) operationMap.get("length")).intValue() : 0);
            if (operationMap.get("attributes") != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> attributes = (Map<String, Object>) operationMap.get("attributes");
                operation.setAttributes(attributes);
            }
            
            offlineSyncService.saveOfflineOperation(documentId, userId, operation);
            return ResponseEntity.ok(Result.success("离线操作保存成功", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    /**
     * 获取离线操作列表
     */
    @GetMapping("/operations/{documentId}")
    public ResponseEntity<Result<List<OperationDTO>>> getOfflineOperations(
            @PathVariable Long documentId,
            HttpServletRequest httpRequest) {
        try {
            Long userId = RequestUtil.getUserId(httpRequest);
            if (userId == null) {
                return ResponseEntity.badRequest().body(Result.error("未认证"));
            }
            
            List<OperationDTO> operations = offlineSyncService.getOfflineOperations(documentId, userId);
            return ResponseEntity.ok(Result.success(operations));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    /**
     * 同步离线操作
     */
    @PostMapping("/sync/{documentId}")
    public ResponseEntity<Result<List<OperationDTO>>> syncOfflineOperations(
            @PathVariable Long documentId,
            HttpServletRequest httpRequest) {
        try {
            Long userId = RequestUtil.getUserId(httpRequest);
            if (userId == null) {
                return ResponseEntity.badRequest().body(Result.error("未认证"));
            }
            
            List<OperationDTO> syncedOps = offlineSyncService.syncOfflineOperations(documentId, userId);
            return ResponseEntity.ok(Result.success("离线操作同步成功", syncedOps));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    /**
     * 检查用户离线状态
     */
    @GetMapping("/status/{documentId}")
    public ResponseEntity<Result<Map<String, Object>>> getOfflineStatus(
            @PathVariable Long documentId,
            HttpServletRequest httpRequest) {
        try {
            Long userId = RequestUtil.getUserId(httpRequest);
            if (userId == null) {
                return ResponseEntity.badRequest().body(Result.error("未认证"));
            }
            
            boolean isOffline = offlineSyncService.isUserOffline(documentId, userId);
            List<OperationDTO> operations = offlineSyncService.getOfflineOperations(documentId, userId);
            
            Map<String, Object> status = new java.util.HashMap<>();
            status.put("isOffline", isOffline);
            status.put("offlineOperationCount", operations.size());
            
            return ResponseEntity.ok(Result.success(status));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }
}

