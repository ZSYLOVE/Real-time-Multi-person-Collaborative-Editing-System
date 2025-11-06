package org.zsy.bysj.dto;

import lombok.Data;

/**
 * 操作DTO（用于OT算法）
 */
@Data
public class OperationDTO {
    private String type; // INSERT/DELETE/RETAIN
    private String data; // 插入的文本内容
    private Integer position; // 操作位置
    private Integer length; // 操作长度
    private Long timestamp; // 时间戳
    private Integer version; // 操作时的文档版本
}

