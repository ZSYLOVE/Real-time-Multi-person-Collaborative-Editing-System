package org.zsy.bysj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zsy.bysj.mapper.CommentMapper;
import org.zsy.bysj.model.Comment;
import org.zsy.bysj.service.CommentService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论服务实现类
 */
@Service
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {

    @Override
    public List<Comment> getDocumentComments(Long documentId) {
        QueryWrapper<Comment> wrapper = new QueryWrapper<>();
        wrapper.eq("document_id", documentId)
               .orderByAsc("created_at");
        return this.list(wrapper);
    }

    @Override
    public List<Comment> getRootComments(Long documentId) {
        QueryWrapper<Comment> wrapper = new QueryWrapper<>();
        wrapper.eq("document_id", documentId)
               .isNull("parent_id")
               .orderByAsc("created_at");
        return this.list(wrapper);
    }

    @Override
    public List<Comment> getReplies(Long parentId) {
        QueryWrapper<Comment> wrapper = new QueryWrapper<>();
        wrapper.eq("parent_id", parentId)
               .orderByAsc("created_at");
        return this.list(wrapper);
    }

    @Override
    @Transactional
    public Comment createComment(Long documentId, Long userId, String content, Integer position, Long parentId) {
        Comment comment = new Comment();
        comment.setDocumentId(documentId);
        comment.setUserId(userId);
        comment.setContent(content);
        comment.setPosition(position);
        comment.setParentId(parentId);
        comment.setIsResolved(false);
        comment.setCreatedAt(LocalDateTime.now());
        comment.setUpdatedAt(LocalDateTime.now());

        this.save(comment);
        return comment;
    }
}

