package org.zsy.bysj.service;

import org.zsy.bysj.model.ChatMessage;
import org.zsy.bysj.model.ChatRoom;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 在线聊天服务
 */
public interface ChatService {

    /**
     * 获取/创建一对一会话
     */
    ChatRoom getOrCreateRoom(Long currentUserId, Long withUserId);

    /**
     * 获取会话历史消息（离线补发/初始化）
     */
    List<ChatMessage> getLatestMessages(Long roomId, Integer limit);

    /**
     * 向上翻页：获取更早消息
     */
    List<ChatMessage> getMessagesBefore(Long roomId, LocalDateTime before, Integer limit);

    /**
     * 最近联系人/会话列表
     */
    List<Map<String, Object>> getChatRooms(Long currentUserId);

    /**
     * 将会话标记为已读（清零未读数）
     */
    void markRoomRead(Long currentUserId, Long roomId);

    /**
     * 保存并返回消息（WebSocket发送方会触发广播）
     */
    ChatMessage createMessage(Long roomId, Long senderId, String content);
}

