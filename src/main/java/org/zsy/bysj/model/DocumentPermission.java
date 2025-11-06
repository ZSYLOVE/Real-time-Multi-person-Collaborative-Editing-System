package org.zsy.bysj.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 文档权限实体类
 */
@Data
@TableName("document_permission")
public class DocumentPermission {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long documentId;
    private Long userId;
    private String permissionType; // READ/WRITE/ADMIN
    private LocalDateTime createdAt;
}

