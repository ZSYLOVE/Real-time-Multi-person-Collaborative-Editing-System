package org.zsy.bysj.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.zsy.bysj.dto.Result;
import org.zsy.bysj.model.Comment;
import org.zsy.bysj.service.CommentService;

import java.util.List;
import java.util.Map;

/**
 * 评论控制器
 */
@RestController
@RequestMapping("/api/comments")
public class CommentController {

    @Autowired
    private CommentService commentService;

    /**
     * 获取文档的所有评论
     */
    @GetMapping("/document/{documentId}")
    public ResponseEntity<Result<List<Comment>>> getDocumentComments(@PathVariable Long documentId) {
        List<Comment> comments = commentService.getDocumentComments(documentId);
        return ResponseEntity.ok(Result.success(comments));
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
    public ResponseEntity<Result<Comment>> createComment(@RequestBody Map<String, Object> request) {
        try {
            Long documentId = Long.valueOf(request.get("documentId").toString());
            Long userId = Long.valueOf(request.get("userId").toString());
            String content = request.get("content").toString();
            Integer position = request.get("position") != null ? 
                Integer.valueOf(request.get("position").toString()) : 0;
            Long parentId = request.get("parentId") != null ? 
                Long.valueOf(request.get("parentId").toString()) : null;

            Comment comment = commentService.createComment(documentId, userId, content, position, parentId);
            return ResponseEntity.ok(Result.success("评论创建成功", comment));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    /**
     * 更新评论
     */
    @PutMapping("/{id}")
    public ResponseEntity<Result<Comment>> updateComment(
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
            return ResponseEntity.ok(Result.success("评论更新成功", comment));
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
            commentService.removeById(id);
            return ResponseEntity.ok(Result.success("评论删除成功", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }
}

