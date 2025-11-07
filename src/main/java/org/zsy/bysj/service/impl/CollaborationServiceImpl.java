package org.zsy.bysj.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.zsy.bysj.algorithm.Operation;
import org.zsy.bysj.dto.OperationDTO;
import org.zsy.bysj.dto.WebSocketMessage;
import org.zsy.bysj.model.Document;
import org.zsy.bysj.service.CollaborationService;
import org.zsy.bysj.service.DocumentService;
import org.zsy.bysj.constant.RedisKeyConstant;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 协同编辑服务实现类
 */
@Service
public class CollaborationServiceImpl implements CollaborationService {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private org.zsy.bysj.service.DistributedLockService distributedLockService;

    @Autowired
    private org.zsy.bysj.service.OfflineSyncService offlineSyncService;

    @Override
    public void handleOperation(WebSocketMessage message) {
        Long documentId = message.getDocumentId();
        Long userId = message.getUserId();
        
        // 检查用户是否在线，如果离线则保存到离线队列
        if (offlineSyncService.isUserOffline(documentId, userId)) {
            Map<String, Object> dataMap = (Map<String, Object>) message.getData();
            OperationDTO opDTO = parseOperationDTO(dataMap);
            offlineSyncService.saveOfflineOperation(documentId, userId, opDTO);
            return;
        }
        
        // 尝试获取分布式锁（最多等待100ms）
        boolean lockAcquired = distributedLockService.tryDocumentLock(documentId, userId, 100, java.util.concurrent.TimeUnit.MILLISECONDS);
        if (!lockAcquired) {
            // 获取锁失败，保存为离线操作
            Map<String, Object> dataMap = (Map<String, Object>) message.getData();
            OperationDTO opDTO = parseOperationDTO(dataMap);
            offlineSyncService.saveOfflineOperation(documentId, userId, opDTO);
            return;
        }
        
        try {
            // 解析操作
            Map<String, Object> dataMap = (Map<String, Object>) message.getData();
            OperationDTO opDTO = parseOperationDTO(dataMap);
            Operation operation = convertToOperation(opDTO);
            
            // 获取操作序列号
            Long sequence = distributedLockService.getNextSequence(documentId);
            opDTO.setVersion(sequence.intValue());
            
            // 应用操作到文档
            Document document = documentService.applyOperation(documentId, operation, userId);
            
            // 构建响应消息
            WebSocketMessage response = new WebSocketMessage();
            response.setType("OPERATION_APPLIED");
            response.setDocumentId(documentId);
            response.setUserId(userId);
            response.setTimestamp(System.currentTimeMillis());
            
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("version", document.getVersion());
            responseData.put("sequence", sequence);
            responseData.put("content", document.getContent());
            response.setData(responseData);
            
            // 广播给文档的所有用户（除了发送者）
            broadcastToDocument(documentId, response, userId);
        } finally {
            // 释放锁
            distributedLockService.releaseDocumentLock(documentId, userId);
        }
    }

    @Override
    public void handleCursorMove(Long documentId, Long userId, Integer position) {
        // 保存用户光标位置
        String key = RedisKeyConstant.buildUserCursorKey(documentId, userId);
        redisTemplate.opsForValue().set(key, position, 5, TimeUnit.MINUTES);
        
        // 构建光标消息
        WebSocketMessage message = new WebSocketMessage();
        message.setType("CURSOR_MOVE");
        message.setDocumentId(documentId);
        message.setUserId(userId);
        message.setTimestamp(System.currentTimeMillis());
        
        Map<String, Object> data = new HashMap<>();
        data.put("position", position);
        message.setData(data);
        
        // 广播给其他用户
        broadcastToDocument(documentId, message, userId);
    }

    @Override
    public List<Map<String, Object>> getOnlineUsers(Long documentId) {
        String key = RedisKeyConstant.buildOnlineUsersKey(documentId);
        Set<Object> userIds = redisTemplate.opsForSet().members(key);
        
        List<Map<String, Object>> users = new ArrayList<>();
        if (userIds != null) {
            for (Object userIdObj : userIds) {
                Long userId = Long.valueOf(userIdObj.toString());
                
                // 获取用户光标位置
                String cursorKey = RedisKeyConstant.buildUserCursorKey(documentId, userId);
                Integer position = (Integer) redisTemplate.opsForValue().get(cursorKey);
                
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("userId", userId);
                userInfo.put("position", position != null ? position : 0);
                users.add(userInfo);
            }
        }
        
        return users;
    }

    @Override
    public void userJoinDocument(Long documentId, Long userId) {
        String key = RedisKeyConstant.buildOnlineUsersKey(documentId);
        redisTemplate.opsForSet().add(key, userId);
        redisTemplate.expire(key, 30, TimeUnit.MINUTES);
        
        // 标记用户为在线
        offlineSyncService.markUserOnline(documentId, userId);
        
        // 尝试同步离线操作
        try {
            List<org.zsy.bysj.dto.OperationDTO> syncedOps = offlineSyncService.syncOfflineOperations(documentId, userId);
            if (!syncedOps.isEmpty()) {
                // 通知客户端离线操作已同步
                WebSocketMessage syncMessage = new WebSocketMessage();
                syncMessage.setType("OFFLINE_SYNC_COMPLETE");
                syncMessage.setDocumentId(documentId);
                syncMessage.setUserId(userId);
                syncMessage.setTimestamp(System.currentTimeMillis());
                Map<String, Object> syncData = new HashMap<>();
                syncData.put("syncedCount", syncedOps.size());
                syncMessage.setData(syncData);
                broadcastToDocument(documentId, syncMessage, null);
            }
        } catch (Exception e) {
            System.err.println("同步离线操作失败: " + e.getMessage());
        }
        
        // 通知其他用户
        WebSocketMessage message = new WebSocketMessage();
        message.setType("USER_JOINED");
        message.setDocumentId(documentId);
        message.setUserId(userId);
        message.setTimestamp(System.currentTimeMillis());
        
        broadcastToDocument(documentId, message, userId);
    }

    @Override
    public void userLeaveDocument(Long documentId, Long userId) {
        String key = RedisKeyConstant.buildOnlineUsersKey(documentId);
        redisTemplate.opsForSet().remove(key, userId);
        
        // 清除光标位置
        String cursorKey = RedisKeyConstant.buildUserCursorKey(documentId, userId);
        redisTemplate.delete(cursorKey);
        
        // 标记用户为离线
        offlineSyncService.markUserOffline(documentId, userId);
        
        // 释放锁（如果持有）
        distributedLockService.releaseDocumentLock(documentId, userId);
        
        // 通知其他用户
        WebSocketMessage message = new WebSocketMessage();
        message.setType("USER_LEFT");
        message.setDocumentId(documentId);
        message.setUserId(userId);
        message.setTimestamp(System.currentTimeMillis());
        
        broadcastToDocument(documentId, message, userId);
    }

    @Override
    public void broadcastToDocument(Long documentId, WebSocketMessage message) {
        broadcastToDocument(documentId, message, null);
    }

    /**
     * 广播消息给文档的所有用户（可选排除某个用户）
     */
    private void broadcastToDocument(Long documentId, WebSocketMessage message, Long excludeUserId) {
        String destination = "/topic/document/" + documentId;
        messagingTemplate.convertAndSend(destination, message);
    }

    /**
     * 解析操作DTO（支持富文本）
     */
    private OperationDTO parseOperationDTO(Map<String, Object> dataMap) {
        OperationDTO opDTO = new OperationDTO();
        opDTO.setType((String) dataMap.get("type"));
        opDTO.setData((String) dataMap.get("data"));
        opDTO.setPosition(((Number) dataMap.get("position")).intValue());
        opDTO.setLength(dataMap.get("length") != null ? 
                ((Number) dataMap.get("length")).intValue() : 0);
        
        // 富文本属性
        if (dataMap.get("attributes") != null) {
            opDTO.setAttributes((Map<String, Object>) dataMap.get("attributes"));
        }
        if (dataMap.get("formatType") != null) {
            opDTO.setFormatType((String) dataMap.get("formatType"));
        }
        if (dataMap.get("formatValue") != null) {
            opDTO.setFormatValue(dataMap.get("formatValue"));
        }
        
        opDTO.setTimestamp(System.currentTimeMillis());
        return opDTO;
    }

    /**
     * 转换为Operation对象
     */
    private Operation convertToOperation(OperationDTO opDTO) {
        switch (opDTO.getType()) {
            case "INSERT":
                return Operation.insert(opDTO.getData(), opDTO.getPosition());
            case "DELETE":
                return Operation.delete(opDTO.getPosition(), opDTO.getLength());
            case "RETAIN":
                return Operation.retain(opDTO.getPosition(), opDTO.getLength());
            default:
                throw new IllegalArgumentException("未知的操作类型: " + opDTO.getType());
        }
    }
}

