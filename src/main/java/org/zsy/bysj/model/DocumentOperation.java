package org.zsy.bysj.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 文档操作实体类（用于OT算法）
 */
@Data
@TableName("document_operation")
public class DocumentOperation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long documentId;
    private Long userId;
    private String operationType; // INSERT/DELETE/RETAIN
    private String operationData; // JSON格式
    private Integer position;
    private Integer length;
    private Long timestamp;
    private Integer version;
    private LocalDateTime createdAt;
}

