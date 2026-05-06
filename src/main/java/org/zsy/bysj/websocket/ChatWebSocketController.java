package org.zsy.bysj.websocket;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.zsy.bysj.constant.RedisKeyConstant;
import org.zsy.bysj.dto.WebSocketMessage;
import org.zsy.bysj.model.ChatMessage;
import org.zsy.bysj.model.ChatRoom;
import org.zsy.bysj.mapper.ChatRoomMapper;
import org.zsy.bysj.service.ChatService;
import org.zsy.bysj.util.JwtUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 聊天 WebSocket（STOMP）入口：
 * - 客户端发送：/app/chat/send
 * - 服务端推送：/topic/chat/user/{userId}（前端最近联系人/未读更新）
 * - 兼容推送：/topic/chat/{roomId}（如果未来需要“房间内订阅”）
 */
@Controller
public class ChatWebSocketController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private ChatRoomMapper chatRoomMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private JwtUtil jwtUtil;

    @MessageMapping("/chat/send")
    public void handleChatSend(@Payload WebSocketMessage message, SimpMessageHeaderAccessor headerAccessor) {
        try {
            if (message == null) return;
            if (!"CHAT_MESSAGE".equalsIgnoreCase(message.getType())) return;

            Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
            if (sessionAttributes == null) return;

            Long userId = (Long) sessionAttributes.get("userId");
            if (userId == null) {
                String token = (String) sessionAttributes.get("token");
                if (token != null && !token.isEmpty()) {
                    userId = jwtUtil.getUserIdFromToken(token);
                    sessionAttributes.put("userId", userId);
                }
            }
            if (userId == null) return;

            Long roomId = message.getDocumentId(); // 前端约定 documentId = roomId
            if (roomId == null) return;

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) message.getData();
            if (data == null) return;
            Object contentObj = data.get("content");
            if (!(contentObj instanceof String content) || content.trim().isEmpty()) return;

            // 校验当前用户是会话参与者
            QueryWrapper<ChatRoom> roomWrapper = new QueryWrapper<>();
            roomWrapper.eq("id", roomId).last("LIMIT 1");
            ChatRoom room = chatRoomMapper.selectOne(roomWrapper);
            if (room == null) return;

            if (!Objects.equals(room.getUserAId(), userId) && !Objects.equals(room.getUserBId(), userId)) return;

            Long withUserId = Objects.equals(room.getUserAId(), userId) ? room.getUserBId() : room.getUserAId();
            if (withUserId == null) return;

            ChatMessage saved = chatService.createMessage(roomId, userId, content);

            // 构造前端可解析的数据结构
            Map<String, Object> chatData = new HashMap<>();
            chatData.put("id", saved.getId());
            chatData.put("roomId", saved.getRoomId());
            chatData.put("senderId", saved.getSenderId());
            chatData.put("content", saved.getContent());
            chatData.put("createdAt", saved.getCreatedAt());

            WebSocketMessage response = new WebSocketMessage();
            response.setType("CHAT_MESSAGE");
            response.setDocumentId(roomId);
            response.setUserId(userId);
            response.setTimestamp(System.currentTimeMillis());
            response.setData(chatData);

            // 发送给发送者（保证“已发送”也能刷新列表/消息）
            messagingTemplate.convertAndSend("/topic/chat/user/" + userId, response);

            // 发送给对方
            messagingTemplate.convertAndSend("/topic/chat/user/" + withUserId, response);
            messagingTemplate.convertAndSend("/topic/chat/" + roomId, response);

            // 更新未读数：仅给接收方增加
            if (!Objects.equals(withUserId, userId)) {
                String unreadKey = RedisKeyConstant.buildChatUnreadKey(withUserId, roomId);
                Object old = redisTemplate.opsForValue().get(unreadKey);
                long next = old == null ? 1L : (Long.parseLong(old.toString()) + 1L);
                redisTemplate.opsForValue().set(unreadKey, next);
            }
        } catch (Exception e) {
            System.err.println("处理聊天消息失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

