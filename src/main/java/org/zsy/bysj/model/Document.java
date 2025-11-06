package org.zsy.bysj.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 文档实体类
 */
@Data
@TableName("document")
public class Document {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String content; // JSON格式存储文档内容
    private Long creatorId;
    private Integer version;
    @TableLogic
    private Boolean isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

