package org.zsy.bysj.websocket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.zsy.bysj.service.CollaborationService;
import org.zsy.bysj.constant.RedisKeyConstant;

import java.util.Map;

/**
 * WebSocket事件监听器
 */
@Component
public class WebSocketEventListener {

    @Autowired
    private SimpMessageSendingOperations messagingTemplate;

    @Autowired
    private CollaborationService collaborationService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        
        // 记录连接信息
        String sessionId = headerAccessor.getSessionId();
        if (sessionAttributes != null) {
            sessionAttributes.put("sessionId", sessionId);
            sessionAttributes.put("connectedAt", System.currentTimeMillis());
        }
        
        System.out.println("收到新的WebSocket连接，Session ID: " + sessionId);
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        
        if (sessionAttributes != null) {
            // 获取用户信息和文档信息
            Long userId = (Long) sessionAttributes.get("userId");
            Long documentId = (Long) sessionAttributes.get("documentId");
            
            // 处理用户断开连接时的清理工作
            if (userId != null && documentId != null) {
                // 从在线用户列表中移除
                String onlineKey = RedisKeyConstant.buildOnlineUsersKey(documentId);
                redisTemplate.opsForSet().remove(onlineKey, userId);
                
                // 清除用户光标位置
                String cursorKey = RedisKeyConstant.buildUserCursorKey(documentId, userId);
                redisTemplate.delete(cursorKey);
                
                // 通知其他用户该用户已离开
                collaborationService.userLeaveDocument(documentId, userId);
                
                System.out.println("用户 " + userId + " 从文档 " + documentId + " 断开连接");
            }
        }
        
        System.out.println("WebSocket连接断开，Session ID: " + headerAccessor.getSessionId());
    }
}

