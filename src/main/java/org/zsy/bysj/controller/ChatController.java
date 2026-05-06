package org.zsy.bysj.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.zsy.bysj.dto.Result;
import org.zsy.bysj.model.ChatMessage;
import org.zsy.bysj.model.ChatRoom;
import org.zsy.bysj.service.ChatService;
import org.zsy.bysj.util.RequestUtil;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * 聊天 REST 接口
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    /**
     * 获取/创建与指定用户的私聊会话
     */
    @GetMapping("/room")
    public ResponseEntity<Result<ChatRoom>> getChatRoom(
            @RequestParam Long withUserId,
            HttpServletRequest httpRequest
    ) {
        try {
            Long currentUserId = RequestUtil.getUserId(httpRequest);
            if (currentUserId == null) {
                return ResponseEntity.badRequest().body(Result.error("未认证"));
            }

            ChatRoom room = chatService.getOrCreateRoom(currentUserId, withUserId);
            return ResponseEntity.ok(Result.success("获取/创建会话成功", room));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    /**
     * 获取会话历史消息（离线补发）
     */
    @GetMapping("/messages/{roomId}")
    public ResponseEntity<Result<List<ChatMessage>>> getChatMessages(
            @PathVariable Long roomId,
            @RequestParam(required = false) Long since,
            @RequestParam(required = false) Integer limit
    ) {
        try {
            // 前端目前只传 limit/since 不一致，这里 since 先不做严格过滤
            List<ChatMessage> messages = chatService.getLatestMessages(roomId, limit);
            return ResponseEntity.ok(Result.success(messages));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    /**
     * 获取更早历史消息（向上翻页）
     */
    @GetMapping("/messages/{roomId}/before")
    public ResponseEntity<Result<List<ChatMessage>>> getChatMessagesBefore(
            @PathVariable Long roomId,
            @RequestParam Long before,
            @RequestParam(required = false) Integer limit
    ) {
        try {
            // Chat.tsx 传的是“毫秒时间戳”
            LocalDateTime beforeTime = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(before),
                    ZoneId.of("Asia/Shanghai")
            );
            List<ChatMessage> messages = chatService.getMessagesBefore(roomId, beforeTime, limit);
            return ResponseEntity.ok(Result.success(messages));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    /**
     * 获取“最近联系人/会话列表”（含未读数、最后一条消息）
     */
    @GetMapping("/rooms")
    public ResponseEntity<Result<List<Map<String, Object>>>> getChatRooms(HttpServletRequest httpRequest) {
        try {
            Long currentUserId = RequestUtil.getUserId(httpRequest);
            if (currentUserId == null) {
                return ResponseEntity.badRequest().body(Result.error("未认证"));
            }
            return ResponseEntity.ok(Result.success(chatService.getChatRooms(currentUserId)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    /**
     * 将会话标记为已读（清零未读数）
     */
    @PostMapping("/rooms/{roomId}/read")
    public ResponseEntity<Result<Void>> markChatRoomRead(
            @PathVariable Long roomId,
            HttpServletRequest httpRequest
    ) {
        try {
            Long currentUserId = RequestUtil.getUserId(httpRequest);
            if (currentUserId == null) {
                return ResponseEntity.badRequest().body(Result.error("未认证"));
            }
            chatService.markRoomRead(currentUserId, roomId);
            return ResponseEntity.ok(Result.success("已标记为已读", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }
}

