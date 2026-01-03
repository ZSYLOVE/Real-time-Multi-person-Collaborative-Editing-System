package org.zsy.bysj.websocket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.zsy.bysj.service.CollaborationService;
import org.zsy.bysj.constant.RedisKeyConstant;
import org.zsy.bysj.util.JwtUtil;

import java.util.Map;
import java.util.Set;

/**
 * WebSocket事件监听器
 * 监听WebSocket连接和断开事件
 */
@Component
public class WebSocketEventListener {

    @Autowired
    private CollaborationService collaborationService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 处理WebSocket连接事件
     */
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        
        // 记录连接信息
        String sessionId = headerAccessor.getSessionId();
        if (sessionAttributes != null) {
            sessionAttributes.put("sessionId", sessionId);
            sessionAttributes.put("connectedAt", System.currentTimeMillis());
            
            // 尝试从token中解析用户ID
            String token = (String) sessionAttributes.get("token");
            if (token != null && !token.isEmpty()) {
                try {
                    Long userId = jwtUtil.getUserIdFromToken(token);
                    sessionAttributes.put("userId", userId);
                    System.out.println("WebSocket连接：用户ID " + userId + " 已解析");
                } catch (Exception e) {
                    System.err.println("WebSocket连接：从token解析用户ID失败: " + e.getMessage());
                }
            }
        }
        
        System.out.println("收到新的WebSocket连接，Session ID: " + sessionId);
    }

    /**
     * 处理WebSocket断开连接事件
     * 当用户断开连接时，清理该用户在所有加入过的文档中的在线状态
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        
        if (sessionAttributes != null) {
            // 获取用户信息
            Long userId = (Long) sessionAttributes.get("userId");
            if (userId == null) {
                // 尝试从token中解析
                String token = (String) sessionAttributes.get("token");
                if (token != null && !token.isEmpty()) {
                    try {
                        userId = jwtUtil.getUserIdFromToken(token);
                    } catch (Exception e) {
                        System.err.println("断开连接时解析用户ID失败: " + e.getMessage());
                    }
                }
            }
            
            // 获取用户加入过的所有文档ID
            @SuppressWarnings("unchecked")
            Set<Long> documentIds = (Set<Long>) sessionAttributes.get("documentIds");
            
            if (userId != null) {
                if (documentIds != null && !documentIds.isEmpty()) {
                    // 遍历所有文档，清理用户状态
                    for (Long documentId : documentIds) {
                        try {
                            // 从在线用户列表中移除
                            String onlineKey = RedisKeyConstant.buildOnlineUsersKey(documentId);
                            redisTemplate.opsForSet().remove(onlineKey, userId);
                            
                            // 清除用户光标位置
                            String cursorKey = RedisKeyConstant.buildUserCursorKey(documentId, userId);
                            redisTemplate.delete(cursorKey);
                            
                            // 通知其他用户该用户已离开
                            collaborationService.userLeaveDocument(documentId, userId);
                            
                            System.out.println("用户 " + userId + " 从文档 " + documentId + " 断开连接");
                        } catch (Exception e) {
                            System.err.println("清理文档 " + documentId + " 的用户状态失败: " + e.getMessage());
                        }
                    }
                } else {
                    // 如果没有documentIds集合，尝试从单个documentId获取（兼容旧代码）
                    Long documentId = (Long) sessionAttributes.get("documentId");
                    if (documentId != null) {
                        try {
                            String onlineKey = RedisKeyConstant.buildOnlineUsersKey(documentId);
                            redisTemplate.opsForSet().remove(onlineKey, userId);
                            
                            String cursorKey = RedisKeyConstant.buildUserCursorKey(documentId, userId);
                            redisTemplate.delete(cursorKey);
                            
                            collaborationService.userLeaveDocument(documentId, userId);
                            
                            System.out.println("用户 " + userId + " 从文档 " + documentId + " 断开连接");
                        } catch (Exception e) {
                            System.err.println("清理文档状态失败: " + e.getMessage());
                        }
                    }
                }
            }
        }
        
        System.out.println("WebSocket连接断开，Session ID: " + headerAccessor.getSessionId());
    }
}

