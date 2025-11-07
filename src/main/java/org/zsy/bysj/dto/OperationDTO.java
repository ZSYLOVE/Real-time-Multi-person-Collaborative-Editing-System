package org.zsy.bysj.dto;

import lombok.Data;
import java.util.Map;

/**
 * 操作DTO（用于OT算法）
 * 支持纯文本和富文本操作
 */
@Data
public class OperationDTO {
    private String type; // INSERT/DELETE/RETAIN/FORMAT
    private String data; // 插入的文本内容
    private Integer position; // 操作位置
    private Integer length; // 操作长度
    private Long timestamp; // 时间戳
    private Integer version; // 操作时的文档版本
    
    // 富文本相关属性
    private Map<String, Object> attributes; // 格式属性（粗体、斜体、颜色等）
    private String formatType; // 格式类型：BOLD, ITALIC, COLOR, FONT_SIZE等
    private Object formatValue; // 格式值
}

