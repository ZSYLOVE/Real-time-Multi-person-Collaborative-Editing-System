package org.zsy.bysj.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.zsy.bysj.model.Comment;
import java.util.List;

/**
 * 评论服务接口
 */
public interface CommentService extends IService<Comment> {
    
    /**
     * 获取文档的所有评论
     */
    List<Comment> getDocumentComments(Long documentId);
    
    /**
     * 获取根评论（不包含回复）
     */
    List<Comment> getRootComments(Long documentId);
    
    /**
     * 获取回复评论
     */
    List<Comment> getReplies(Long parentId);
    
    /**
     * 创建评论
     */
    Comment createComment(Long documentId, Long userId, String content, Integer position, Long parentId);
}

