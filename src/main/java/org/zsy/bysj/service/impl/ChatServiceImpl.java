package org.zsy.bysj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zsy.bysj.constant.RedisKeyConstant;
import org.zsy.bysj.mapper.ChatMessageMapper;
import org.zsy.bysj.mapper.ChatRoomMapper;
import org.zsy.bysj.model.ChatMessage;
import org.zsy.bysj.model.ChatRoom;
import org.zsy.bysj.service.ChatService;
import org.zsy.bysj.service.UserService;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 在线聊天服务实现
 */
@Service
public class ChatServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage> implements ChatService {

    @Autowired
    private ChatRoomMapper chatRoomMapper;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private UserService userService;

    @Override
    @Transactional
    public ChatRoom getOrCreateRoom(Long currentUserId, Long withUserId) {
        if (currentUserId == null || withUserId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        if (Objects.equals(currentUserId, withUserId)) {
            throw new IllegalArgumentException("不能与自己创建会话");
        }

        Long userA = Math.min(currentUserId, withUserId);
        Long userB = Math.max(currentUserId, withUserId);

        QueryWrapper<ChatRoom> wrapper = new QueryWrapper<>();
        wrapper.eq("user_a_id", userA).eq("user_b_id", userB).last("LIMIT 1");
        ChatRoom room = chatRoomMapper.selectOne(wrapper);

        if (room != null) {
            return room;
        }

        ChatRoom newRoom = new ChatRoom();
        newRoom.setUserAId(userA);
        newRoom.setUserBId(userB);
        newRoom.setCreatedAt(LocalDateTime.now());
        chatRoomMapper.insert(newRoom);
        return newRoom;
    }

    @Override
    public List<ChatMessage> getLatestMessages(Long roomId, Integer limit) {
        if (roomId == null) return Collections.emptyList();
        int safeLimit = limit == null || limit <= 0 ? 50 : limit;

        QueryWrapper<ChatMessage> wrapper = new QueryWrapper<>();
        wrapper.eq("room_id", roomId)
                .orderByDesc("created_at")
                .last("LIMIT " + safeLimit);

        return chatMessageMapper.selectList(wrapper);
    }

    @Override
    public List<ChatMessage> getMessagesBefore(Long roomId, LocalDateTime before, Integer limit) {
        if (roomId == null || before == null) return Collections.emptyList();
        int safeLimit = limit == null || limit <= 0 ? 50 : limit;

        QueryWrapper<ChatMessage> wrapper = new QueryWrapper<>();
        wrapper.eq("room_id", roomId)
                .lt("created_at", before)
                .orderByDesc("created_at")
                .last("LIMIT " + safeLimit);

        return chatMessageMapper.selectList(wrapper);
    }

    @Override
    public List<Map<String, Object>> getChatRooms(Long currentUserId) {
        if (currentUserId == null) return Collections.emptyList();

        QueryWrapper<ChatRoom> roomWrapper = new QueryWrapper<>();
        roomWrapper.and(w -> w.eq("user_a_id", currentUserId).or().eq("user_b_id", currentUserId));
        // created_at 不能完全代表“最近消息”，后面会按 lastMessageCreatedAt 在内存里排序
        List<ChatRoom> rooms = chatRoomMapper.selectList(roomWrapper);

        if (rooms == null || rooms.isEmpty()) return Collections.emptyList();

        List<Map<String, Object>> result = new ArrayList<>();

        for (ChatRoom room : rooms) {
            if (room == null || room.getId() == null) continue;
            Long roomId = room.getId();

            // 找到“另一个用户”
            Long withUserId = Objects.equals(room.getUserAId(), currentUserId) ? room.getUserBId() : room.getUserAId();

            // 最新消息
            QueryWrapper<ChatMessage> msgWrapper = new QueryWrapper<>();
            msgWrapper.eq("room_id", roomId)
                    .orderByDesc("created_at")
                    .last("LIMIT 1");
            ChatMessage last = chatMessageMapper.selectOne(msgWrapper);

            String unreadKey = RedisKeyConstant.buildChatUnreadKey(currentUserId, roomId);
            Object unreadObj = redisTemplate.opsForValue().get(unreadKey);
            long unreadCount = unreadObj == null ? 0L : Long.parseLong(unreadObj.toString());

            // 兜底：没有消息也能显示会话
            Map<String, Object> map = new HashMap<>();
            map.put("roomId", roomId);
            map.put("withUserId", withUserId);

            // 前端会优先读取这些字段（用于最近列表展示）
            if (withUserId != null) {
                var withUser = userService.getById(withUserId);
                if (withUser != null) {
                    map.put("withUsername", withUser.getUsername());
                    map.put("withNickname", withUser.getNickname());
                    map.put("withAvatar", withUser.getAvatar());
                } else {
                    map.put("withUsername", "");
                    map.put("withNickname", "");
                    map.put("withAvatar", "");
                }
            } else {
                map.put("withUsername", "");
                map.put("withNickname", "");
                map.put("withAvatar", "");
            }

            map.put("unreadCount", unreadCount);
            map.put("lastMessage", last != null ? last.getContent() : "");
            map.put("lastMessageCreatedAt", last != null ? last.getCreatedAt() : null);

            result.add(map);
        }

        // 按最后消息时间倒序
        result.sort((a, b) -> {
            Object ta = a.get("lastMessageCreatedAt");
            Object tb = b.get("lastMessageCreatedAt");
            long va = ta == null ? 0 : toMs(ta);
            long vb = tb == null ? 0 : toMs(tb);
            return Long.compare(vb, va);
        });

        return result;
    }

    @Override
    public void markRoomRead(Long currentUserId, Long roomId) {
        if (currentUserId == null || roomId == null) return;
        String unreadKey = RedisKeyConstant.buildChatUnreadKey(currentUserId, roomId);
        redisTemplate.opsForValue().set(unreadKey, 0L);
    }

    @Override
    @Transactional
    public ChatMessage createMessage(Long roomId, Long senderId, String content) {
        if (roomId == null || senderId == null) {
            throw new IllegalArgumentException("roomId/senderId不能为空");
        }
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }

        ChatMessage message = new ChatMessage();
        message.setRoomId(roomId);
        message.setSenderId(senderId);
        message.setContent(content.trim());
        message.setCreatedAt(LocalDateTime.now());

        chatMessageMapper.insert(message);
        return message;
    }

    private long toMs(Object v) {
        if (v instanceof LocalDateTime ldt) {
            // LocalDateTime 与时区无关，这里按 JVM 默认时区转换
            return ldt.atZone(TimeZone.getDefault().toZoneId()).toInstant().toEpochMilli();
        }
        return 0L;
    }
}

