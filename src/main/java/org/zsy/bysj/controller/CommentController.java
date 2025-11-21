package org.zsy.bysj.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import org.zsy.bysj.dto.Result;
import org.zsy.bysj.dto.WebSocketMessage;
import org.zsy.bysj.model.Comment;
import org.zsy.bysj.model.User;
import org.zsy.bysj.service.CommentService;
import org.zsy.bysj.service.UserService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 评论控制器
 */
@RestController
@RequestMapping("/api/comments")
public class CommentController {

    @Autowired
    private CommentService commentService;

    @Autowired
    private UserService userService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * 获取文档的所有评论
     */
    @GetMapping("/document/{documentId}")
    public ResponseEntity<Result<List<Map<String, Object>>>> getDocumentComments(@PathVariable Long documentId) {
        List<Comment> comments = commentService.getDocumentComments(documentId);
        List<Map<String, Object>> commentsWithUsers = comments.stream()
            .map(comment -> {
                Map<String, Object> commentMap = new HashMap<>();
                commentMap.put("id", comment.getId());
                commentMap.put("documentId", comment.getDocumentId());
                commentMap.put("userId", comment.getUserId());
                commentMap.put("content", comment.getContent());
                commentMap.put("position", comment.getPosition());
                commentMap.put("parentId", comment.getParentId());
                commentMap.put("isResolved", comment.getIsResolved());
                commentMap.put("createdAt", comment.getCreatedAt());
                commentMap.put("updatedAt", comment.getUpdatedAt());
                
                // 获取用户信息
                User user = userService.getById(comment.getUserId());
                if (user != null) {
                    Map<String, Object> userMap = new HashMap<>();
                    userMap.put("id", user.getId());
                    userMap.put("username", user.getUsername());
                    userMap.put("nickname", user.getNickname());
                    userMap.put("avatar", user.getAvatar());
                    commentMap.put("user", userMap);
                }
                
                return commentMap;
            })
            .collect(Collectors.toList());
        return ResponseEntity.ok(Result.success(commentsWithUsers));
    }

    /**
     * 获取根评论
     */
    @GetMapping("/document/{documentId}/root")
    public ResponseEntity<Result<List<Comment>>> getRootComments(@PathVariable Long documentId) {
        List<Comment> comments = commentService.getRootComments(documentId);
        return ResponseEntity.ok(Result.success(comments));
    }

    /**
     * 获取回复
     */
    @GetMapping("/{parentId}/replies")
    public ResponseEntity<Result<List<Comment>>> getReplies(@PathVariable Long parentId) {
        List<Comment> replies = commentService.getReplies(parentId);
        return ResponseEntity.ok(Result.success(replies));
    }

    /**
     * 创建评论
     */
    @PostMapping
    public ResponseEntity<Result<Map<String, Object>>> createComment(@RequestBody Map<String, Object> request) {
        try {
            // 参数验证
            if (request.get("documentId") == null || request.get("userId") == null || request.get("content") == null) {
                return ResponseEntity.badRequest().body(Result.error("缺少必要参数"));
            }
            
            Long documentId = Long.valueOf(request.get("documentId").toString());
            Long userId = Long.valueOf(request.get("userId").toString());
            String content = request.get("content").toString().trim();
            
            if (content.isEmpty()) {
                return ResponseEntity.badRequest().body(Result.error("评论内容不能为空"));
            }
            
            Integer position = request.get("position") != null && !request.get("position").toString().isEmpty() ? 
                Integer.valueOf(request.get("position").toString()) : 0;
            Long parentId = request.get("parentId") != null && !request.get("parentId").toString().isEmpty() ? 
                Long.valueOf(request.get("parentId").toString()) : null;

            Comment comment = commentService.createComment(documentId, userId, content, position, parentId);
            
            // 获取用户信息并添加到评论对象
            User user = userService.getById(userId);
            Map<String, Object> commentMap = new HashMap<>();
            commentMap.put("id", comment.getId());
            commentMap.put("documentId", comment.getDocumentId());
            commentMap.put("userId", comment.getUserId());
            commentMap.put("content", comment.getContent());
            commentMap.put("position", comment.getPosition());
            commentMap.put("parentId", comment.getParentId());
            commentMap.put("isResolved", comment.getIsResolved());
            commentMap.put("createdAt", comment.getCreatedAt());
            commentMap.put("updatedAt", comment.getUpdatedAt());
            
            if (user != null) {
                Map<String, Object> userMap = new HashMap<>();
                userMap.put("id", user.getId());
                userMap.put("username", user.getUsername());
                userMap.put("nickname", user.getNickname());
                userMap.put("avatar", user.getAvatar());
                commentMap.put("user", userMap);
            }
            
            // 广播评论创建消息
            WebSocketMessage message = new WebSocketMessage();
            message.setType("COMMENT");
            message.setDocumentId(documentId);
            message.setUserId(userId);
            message.setTimestamp(System.currentTimeMillis());
            message.setData(commentMap);
            messagingTemplate.convertAndSend("/topic/document/" + documentId, message);
            
            return ResponseEntity.ok(Result.success("评论创建成功", commentMap));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    /**
     * 更新评论
     */
    @PutMapping("/{id}")
    public ResponseEntity<Result<Map<String, Object>>> updateComment(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        try {
            Comment comment = commentService.getById(id);
            if (comment == null) {
                return ResponseEntity.badRequest().body(Result.error("评论不存在"));
            }

            if (request.containsKey("content")) {
                comment.setContent(request.get("content").toString());
            }
            if (request.containsKey("isResolved")) {
                comment.setIsResolved(Boolean.valueOf(request.get("isResolved").toString()));
            }

            commentService.updateById(comment);
            
            // 获取用户信息并添加到评论对象
            User user = userService.getById(comment.getUserId());
            Map<String, Object> commentMap = new HashMap<>();
            commentMap.put("id", comment.getId());
            commentMap.put("documentId", comment.getDocumentId());
            commentMap.put("userId", comment.getUserId());
            commentMap.put("content", comment.getContent());
            commentMap.put("position", comment.getPosition());
            commentMap.put("parentId", comment.getParentId());
            commentMap.put("isResolved", comment.getIsResolved());
            commentMap.put("createdAt", comment.getCreatedAt());
            commentMap.put("updatedAt", comment.getUpdatedAt());
            
            if (user != null) {
                Map<String, Object> userMap = new HashMap<>();
                userMap.put("id", user.getId());
                userMap.put("username", user.getUsername());
                userMap.put("nickname", user.getNickname());
                userMap.put("avatar", user.getAvatar());
                commentMap.put("user", userMap);
            }
            
            // 广播评论更新消息
            WebSocketMessage message = new WebSocketMessage();
            message.setType("COMMENT_UPDATED");
            message.setDocumentId(comment.getDocumentId());
            message.setUserId(comment.getUserId());
            message.setTimestamp(System.currentTimeMillis());
            message.setData(commentMap);
            messagingTemplate.convertAndSend("/topic/document/" + comment.getDocumentId(), message);
            
            return ResponseEntity.ok(Result.success("评论更新成功", commentMap));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    /**
     * 删除评论
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> deleteComment(@PathVariable Long id) {
        try {
            Comment comment = commentService.getById(id);
            if (comment != null) {
                Long documentId = comment.getDocumentId();
                commentService.removeById(id);
                
                // 广播评论删除消息
                WebSocketMessage message = new WebSocketMessage();
                message.setType("COMMENT_DELETED");
                message.setDocumentId(documentId);
                message.setUserId(comment.getUserId());
                message.setTimestamp(System.currentTimeMillis());
                message.setData(Map.of("id", id));
                messagingTemplate.convertAndSend("/topic/document/" + documentId, message);
            }
            return ResponseEntity.ok(Result.success("评论删除成功", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }
}

