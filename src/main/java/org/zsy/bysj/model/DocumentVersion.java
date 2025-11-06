package org.zsy.bysj.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 文档版本实体类
 */
@Data
@TableName("document_version")
public class DocumentVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long documentId;
    private Integer version;
    private String content;
    private String snapshot; // JSON格式的快照
    private Long createdBy;
    private LocalDateTime createdAt;
}

