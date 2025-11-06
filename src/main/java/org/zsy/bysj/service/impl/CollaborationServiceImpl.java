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

    @Override
    public void handleOperation(WebSocketMessage message) {
        Long documentId = message.getDocumentId();
        Long userId = message.getUserId();
        
        // 解析操作
        Map<String, Object> dataMap = (Map<String, Object>) message.getData();
        OperationDTO opDTO = parseOperationDTO(dataMap);
        Operation operation = convertToOperation(opDTO);
        
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
        responseData.put("content", document.getContent());
        response.setData(responseData);
        
        // 广播给文档的所有用户（除了发送者）
        broadcastToDocument(documentId, response, userId);
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
     * 解析操作DTO
     */
    private OperationDTO parseOperationDTO(Map<String, Object> dataMap) {
        OperationDTO opDTO = new OperationDTO();
        opDTO.setType((String) dataMap.get("type"));
        opDTO.setData((String) dataMap.get("data"));
        opDTO.setPosition(((Number) dataMap.get("position")).intValue());
        opDTO.setLength(dataMap.get("length") != null ? 
                ((Number) dataMap.get("length")).intValue() : 0);
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

