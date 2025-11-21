package org.zsy.bysj.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.zsy.bysj.algorithm.Operation;
import org.zsy.bysj.dto.OperationDTO;
import org.zsy.bysj.dto.WebSocketMessage;
import org.zsy.bysj.model.Document;
import org.zsy.bysj.service.CollaborationService;
import org.zsy.bysj.service.DistributedLockService;
import org.zsy.bysj.service.DocumentService;
import org.zsy.bysj.service.OfflineSyncService;
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

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void handleOperation(WebSocketMessage message) {
        Long documentId = message.getDocumentId();
        Long userId = message.getUserId();

        System.out.println("开始处理操作: 用户" + userId + " 在文档" + documentId + " 中执行操作");

        // 检查用户是否在线，如果离线则保存到离线队列
        if (offlineSyncService.isUserOffline(documentId, userId)) {
            System.out.println("用户" + userId + "离线，保存操作到离线队列");
            Map<String, Object> dataMap = (Map<String, Object>) message.getData();
            OperationDTO opDTO = parseOperationDTO(dataMap);
            offlineSyncService.saveOfflineOperation(documentId, userId, opDTO);
            return;
        }
        
        // 尝试获取分布式锁，支持队列等待（最多等待2秒）
        boolean lockAcquired = distributedLockService.tryDocumentLockWithQueue(documentId, userId, 2000);
        System.out.println("尝试获取分布式锁: " + (lockAcquired ? "成功" : "失败"));
        if (!lockAcquired) {
            // 获取锁失败，将操作加入锁队列等待
            System.out.println("锁获取失败，将操作加入锁队列等待");
            try {
                // 将操作数据序列化为JSON字符串存储在队列中
                Map<String, Object> operationData = new HashMap<>();
                operationData.put("documentId", documentId);
                operationData.put("userId", userId);
                operationData.put("data", message.getData());
                operationData.put("timestamp", message.getTimestamp());

                String operationJson = objectMapper.writeValueAsString(operationData);
                distributedLockService.queueOperation(documentId, userId, operationJson);
            } catch (Exception e) {
                System.err.println("序列化操作失败: " + e.getMessage());
                // 如果序列化失败，回退到离线操作
                Map<String, Object> dataMap = (Map<String, Object>) message.getData();
                OperationDTO opDTO = parseOperationDTO(dataMap);
                offlineSyncService.saveOfflineOperation(documentId, userId, opDTO);
            }
            return;
        }
        
        try {
            // 解析操作
            Map<String, Object> dataMap = (Map<String, Object>) message.getData();
            System.out.println("解析操作数据: " + dataMap);
            OperationDTO opDTO = parseOperationDTO(dataMap);
            System.out.println("转换后的操作DTO: " + opDTO);
            System.out.println("操作类型检查: type=" + opDTO.getType() + ", equals FORMAT=" + "FORMAT".equals(opDTO.getType()));
            
            // FORMAT操作直接广播，不经过OT算法（格式信息在HTML中，不需要文本转换）
            if ("FORMAT".equals(opDTO.getType()) || "format".equalsIgnoreCase(opDTO.getType())) {
                System.out.println("检测到FORMAT操作，直接广播");
                // 获取操作序列号
                Long sequence = distributedLockService.getNextSequence(documentId);
                opDTO.setVersion(sequence.intValue());
                
                // 构建广播消息
                WebSocketMessage response = new WebSocketMessage();
                response.setType("OPERATION");
                response.setDocumentId(documentId);
                response.setUserId(userId);
                response.setTimestamp(System.currentTimeMillis());
                response.setData(opDTO);
                
                // 直接广播FORMAT操作，前端会直接应用格式
                broadcastToDocument(documentId, response, userId);
                System.out.println("FORMAT操作广播完成");
                return;
            }
            
            Operation operation = convertToOperation(opDTO);
            System.out.println("转换后的Operation: type=" + operation.getType() + ", data=" + operation.getData() + ", position=" + operation.getPosition());

            // 获取操作序列号
            Long sequence = distributedLockService.getNextSequence(documentId);
            opDTO.setVersion(sequence.intValue());
            System.out.println("获取操作序列号: " + sequence);

            // 应用操作到文档
            Document document = documentService.applyOperation(documentId, operation, userId);

            // 构建广播消息 - 使用 OPERATION 类型，包含操作数据
            WebSocketMessage response = new WebSocketMessage();
            response.setType("OPERATION");
            response.setDocumentId(documentId);
            response.setUserId(userId);
            response.setTimestamp(System.currentTimeMillis());

            // 将操作DTO作为data发送，这样其他客户端可以直接应用操作
            response.setData(opDTO);

            System.out.println("构建响应消息完成，开始广播...");

            // 广播给文档的所有用户（排除发送者，因为发送者已经应用了操作）
            broadcastToDocument(documentId, response, userId);
            System.out.println("消息广播完成");
        } catch (Exception e) {
            System.out.println("操作处理过程中发生错误: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // 释放锁并处理队列中的下一个操作
            distributedLockService.releaseDocumentLockAndProcessQueue(documentId, userId, new DistributedLockService.OperationHandler() {
                @Override
                public void handleOperation(String operationData) {
                    try {
                        // 反序列化操作数据
                        @SuppressWarnings("unchecked")
                        Map<String, Object> operationMap = objectMapper.readValue(operationData, Map.class);
                        Long queuedUserId = Long.valueOf(operationMap.get("userId").toString());
                        Long queuedDocumentId = Long.valueOf(operationMap.get("documentId").toString());

                        System.out.println("处理队列中的操作: userId=" + queuedUserId + ", documentId=" + queuedDocumentId);

                        // 构造WebSocketMessage并递归处理
                        WebSocketMessage queuedMessage = new WebSocketMessage();
                        queuedMessage.setType("OPERATION");
                        queuedMessage.setDocumentId(queuedDocumentId);
                        queuedMessage.setUserId(queuedUserId);
                        queuedMessage.setData(operationMap.get("data"));
                        queuedMessage.setTimestamp(Long.valueOf(operationMap.get("timestamp").toString()));

                        // 递归处理队列中的操作（直接调用，不通过OperationHandler）
                        CollaborationServiceImpl.this.handleOperation(queuedMessage);
                    } catch (Exception e) {
                        System.err.println("处理队列操作失败: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            });
            System.out.println("释放分布式锁并处理队列");
        }
    }

    @Override
    public void handleCursorMove(Long documentId, Long userId, Integer position) {
        // 保存用户光标位置
        String key = RedisKeyConstant.buildUserCursorKey(documentId, userId);
        redisTemplate.opsForValue().set(key, position, 5, TimeUnit.MINUTES);
        
        // 构建光标消息
        WebSocketMessage message = new WebSocketMessage();
        message.setType("CURSOR");
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
        message.setType("JOIN");
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
        
        // 释放锁并处理队列中的下一个操作（如果持有）
        distributedLockService.releaseDocumentLockAndProcessQueue(documentId, userId, new DistributedLockService.OperationHandler() {
            @Override
            public void handleOperation(String operationData) {
                try {
                    // 反序列化操作数据
                    @SuppressWarnings("unchecked")
                    Map<String, Object> operationMap = objectMapper.readValue(operationData, Map.class);
                    Long queuedUserId = Long.valueOf(operationMap.get("userId").toString());
                    Long queuedDocumentId = Long.valueOf(operationMap.get("documentId").toString());

                    System.out.println("用户离开时处理队列中的操作: userId=" + queuedUserId + ", documentId=" + queuedDocumentId);

                    // 构造WebSocketMessage并递归处理
                    WebSocketMessage queuedMessage = new WebSocketMessage();
                    queuedMessage.setType("OPERATION");
                    queuedMessage.setDocumentId(queuedDocumentId);
                    queuedMessage.setUserId(queuedUserId);
                    queuedMessage.setData(operationMap.get("data"));
                    queuedMessage.setTimestamp(Long.valueOf(operationMap.get("timestamp").toString()));

                    // 递归处理队列中的操作（直接调用，不通过OperationHandler）
                    CollaborationServiceImpl.this.handleOperation(queuedMessage);
                } catch (Exception e) {
                    System.err.println("用户离开时处理队列操作失败: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
        
        // 通知其他用户
        WebSocketMessage message = new WebSocketMessage();
        message.setType("LEAVE");
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
        System.out.println("广播消息到: " + destination);
        System.out.println("消息类型: " + message.getType());
        System.out.println("消息用户ID: " + message.getUserId());
        System.out.println("排除用户ID: " + excludeUserId);
        System.out.println("消息数据: " + message.getData());
        messagingTemplate.convertAndSend(destination, message);
        System.out.println("消息广播完成");
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
            case "FORMAT":
                // FORMAT操作不改变文本内容，只改变格式，直接返回RETAIN操作
                // 格式信息会通过OperationDTO传递，前端会直接应用格式
                return Operation.retain(opDTO.getPosition(), opDTO.getLength());
            default:
                throw new IllegalArgumentException("未知的操作类型: " + opDTO.getType());
        }
    }
}

