package org.zsy.bysj.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 评论实体类
 */
@Data
@TableName("comment")
public class Comment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long documentId;
    private Long userId;
    private String content;
    private Integer position;
    private Long parentId;
    private Boolean isResolved;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

