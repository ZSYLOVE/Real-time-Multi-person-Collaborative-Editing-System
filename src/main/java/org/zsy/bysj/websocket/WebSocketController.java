package org.zsy.bysj.websocket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.zsy.bysj.dto.WebSocketMessage;
import org.zsy.bysj.service.CollaborationService;
import org.zsy.bysj.util.JwtUtil;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * WebSocket消息控制器
 * 处理客户端发送的WebSocket消息
 */
@Controller
public class WebSocketController {

    @Autowired
    private CollaborationService collaborationService;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 处理用户操作消息
     * 客户端发送路径: /app/document/operation
     */
    @MessageMapping("/document/operation")
    public void handleOperation(@Payload WebSocketMessage message, SimpMessageHeaderAccessor headerAccessor) {
        try {
            // 从session attributes中获取用户ID
            Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
            if (sessionAttributes == null) {
                System.err.println("Session attributes为空，无法获取用户信息");
                return;
            }

            Long userId = (Long) sessionAttributes.get("userId");
            if (userId == null) {
                // 尝试从token中解析用户ID
                String token = (String) sessionAttributes.get("token");
                if (token != null && !token.isEmpty()) {
                    try {
                        userId = jwtUtil.getUserIdFromToken(token);
                        sessionAttributes.put("userId", userId);
                    } catch (Exception e) {
                        System.err.println("从token解析用户ID失败: " + e.getMessage());
                        return;
                    }
                } else {
                    System.err.println("无法获取用户ID，token为空");
                    return;
                }
            }

            // 设置消息中的用户ID
            message.setUserId(userId);
            
            System.out.println("收到操作消息: 用户" + userId + " 在文档" + message.getDocumentId() + " 中执行操作");
            
            // 处理操作
            collaborationService.handleOperation(message);
        } catch (Exception e) {
            System.err.println("处理操作消息失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 处理用户加入文档消息
     * 客户端发送路径: /app/document/join
     */
    @MessageMapping("/document/join")
    public void handleJoin(@Payload WebSocketMessage message, SimpMessageHeaderAccessor headerAccessor) {
        try {
            Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
            if (sessionAttributes == null) {
                System.err.println("Session attributes为空");
                return;
            }

            Long userId = (Long) sessionAttributes.get("userId");
            if (userId == null) {
                String token = (String) sessionAttributes.get("token");
                if (token != null && !token.isEmpty()) {
                    try {
                        userId = jwtUtil.getUserIdFromToken(token);
                        sessionAttributes.put("userId", userId);
                    } catch (Exception e) {
                        System.err.println("从token解析用户ID失败: " + e.getMessage());
                        return;
                    }
                } else {
                    System.err.println("无法获取用户ID");
                    return;
                }
            }

            message.setUserId(userId);
            
            // 支持同一连接加入多个文档：用Set保存所有加入过的documentId
            @SuppressWarnings("unchecked")
            Set<Long> documentIds = (Set<Long>) sessionAttributes.get("documentIds");
            if (documentIds == null) {
                documentIds = new HashSet<>();
                sessionAttributes.put("documentIds", documentIds);
            }
            documentIds.add(message.getDocumentId());
            
            System.out.println("用户 " + userId + " 加入文档 " + message.getDocumentId());
            collaborationService.userJoinDocument(message.getDocumentId(), userId);
        } catch (Exception e) {
            System.err.println("处理加入消息失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 处理用户离开文档消息
     * 客户端发送路径: /app/document/leave
     */
    @MessageMapping("/document/leave")
    public void handleLeave(@Payload WebSocketMessage message, SimpMessageHeaderAccessor headerAccessor) {
        try {
            Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
            if (sessionAttributes == null) {
                return;
            }

            Long userId = (Long) sessionAttributes.get("userId");
            if (userId == null) {
                String token = (String) sessionAttributes.get("token");
                if (token != null && !token.isEmpty()) {
                    try {
                        userId = jwtUtil.getUserIdFromToken(token);
                        sessionAttributes.put("userId", userId);
                    } catch (Exception e) {
                        System.err.println("从token解析用户ID失败: " + e.getMessage());
                        return;
                    }
                } else {
                    return;
                }
            }

            message.setUserId(userId);
            
            // 当用户主动离开文档时，从session的文档ID集合中移除
            @SuppressWarnings("unchecked")
            Set<Long> documentIds = (Set<Long>) sessionAttributes.get("documentIds");
            if (documentIds != null) {
                documentIds.remove(message.getDocumentId());
                System.out.println("用户 " + userId + " 从session移除了文档 " + message.getDocumentId());
            }

            System.out.println("用户 " + userId + " 离开文档 " + message.getDocumentId());
            collaborationService.userLeaveDocument(message.getDocumentId(), userId);
        } catch (Exception e) {
            System.err.println("处理离开消息失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 处理光标移动消息
     * 客户端发送路径: /app/document/cursor
     */
    @MessageMapping("/document/cursor")
    public void handleCursor(@Payload WebSocketMessage message, SimpMessageHeaderAccessor headerAccessor) {
        try {
            Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
            if (sessionAttributes == null) {
                return;
            }

            Long userId = (Long) sessionAttributes.get("userId");
            if (userId == null) {
                String token = (String) sessionAttributes.get("token");
                if (token != null && !token.isEmpty()) {
                    try {
                        userId = jwtUtil.getUserIdFromToken(token);
                        sessionAttributes.put("userId", userId);
                    } catch (Exception e) {
                        System.err.println("从token解析用户ID失败: " + e.getMessage());
                        return;
                    }
                } else {
                    return;
                }
            }

            // 从消息数据中获取光标位置
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) message.getData();
            if (data != null && data.containsKey("position")) {
                Integer position = ((Number) data.get("position")).intValue();
                collaborationService.handleCursorMove(message.getDocumentId(), userId, position);
            }
        } catch (Exception e) {
            System.err.println("处理光标消息失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

