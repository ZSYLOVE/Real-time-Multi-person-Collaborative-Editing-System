package org.zsy.bysj.websocket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.zsy.bysj.dto.WebSocketMessage;
import org.zsy.bysj.service.CollaborationService;
import org.zsy.bysj.util.JwtUtil;

import java.util.List;
import java.util.Map;

/**
 * WebSocket消息控制器
 */
@Controller
public class WebSocketController {

    @Autowired
    private CollaborationService collaborationService;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 处理用户操作
     */
    @MessageMapping("/document/operation")
    public void handleOperation(@Payload WebSocketMessage message,
                               SimpMessageHeaderAccessor headerAccessor) {
        // 从JWT token获取用户ID
        Long userId = getUserIdFromToken(headerAccessor);
        if (userId == null) {
            return; // 认证失败，忽略消息
        }
        message.setUserId(userId);
        
        collaborationService.handleOperation(message);
    }

    /**
     * 处理光标移动
     */
    @MessageMapping("/document/cursor")
    public void handleCursor(@Payload WebSocketMessage message,
                            SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromToken(headerAccessor);
        if (userId == null) {
            return;
        }
        
        Map<String, Object> data = (Map<String, Object>) message.getData();
        Integer position = ((Number) data.get("position")).intValue();
        
        collaborationService.handleCursorMove(message.getDocumentId(), userId, position);
    }

    /**
     * 用户加入文档
     */
    @MessageMapping("/document/join")
    public void handleJoin(@Payload WebSocketMessage message,
                          SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromToken(headerAccessor);
        if (userId == null) {
            return;
        }
        
        collaborationService.userJoinDocument(message.getDocumentId(), userId);
    }

    /**
     * 用户离开文档
     */
    @MessageMapping("/document/leave")
    public void handleLeave(@Payload WebSocketMessage message,
                           SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromToken(headerAccessor);
        if (userId == null) {
            return;
        }
        
        collaborationService.userLeaveDocument(message.getDocumentId(), userId);
    }

    /**
     * 从WebSocket连接中获取用户ID（从JWT token）
     */
    private Long getUserIdFromToken(SimpMessageHeaderAccessor headerAccessor) {
        try {
            // 从连接参数中获取token
            Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
            String token = (String) sessionAttributes.get("token");
            
            // 如果session中没有token，尝试从连接参数获取
            if (token == null) {
                List<String> nativeHeaders = headerAccessor.getNativeHeader("Authorization");
                if (nativeHeaders != null && !nativeHeaders.isEmpty()) {
                    String authHeader = nativeHeaders.get(0);
                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        token = authHeader.substring(7);
                    }
                }
            }
            
            // 如果还是没有token，尝试从session属性中获取
            if (token == null) {
                token = (String) sessionAttributes.get("Authorization");
                if (token != null && token.startsWith("Bearer ")) {
                    token = token.substring(7);
                }
            }
            
            if (token != null && jwtUtil.validateToken(token)) {
                Long userId = jwtUtil.getUserIdFromToken(token);
                // 将userId存入session，供后续使用
                sessionAttributes.put("userId", userId);
                return userId;
            }
            
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}

