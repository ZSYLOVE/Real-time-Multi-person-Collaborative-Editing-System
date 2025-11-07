package org.zsy.bysj.algorithm;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.util.Map;

/**
 * 富文本操作类
 * 扩展Operation以支持富文本格式
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RichTextOperation {
    private String type; // INSERT/DELETE/RETAIN/FORMAT
    private String data; // 插入的文本内容
    private Integer position; // 操作位置
    private Integer length; // 操作长度
    private Map<String, Object> attributes; // 格式属性
    
    /**
     * 创建格式操作（用于应用格式，如粗体、斜体等）
     */
    public static RichTextOperation format(int position, int length, Map<String, Object> attributes) {
        return new RichTextOperation("FORMAT", null, position, length, attributes);
    }
    
    /**
     * 创建带格式的插入操作
     */
    public static RichTextOperation insert(String text, int position, Map<String, Object> attributes) {
        return new RichTextOperation("INSERT", text, position, text.length(), attributes);
    }
    
    /**
     * 创建删除操作
     */
    public static RichTextOperation delete(int position, int length) {
        return new RichTextOperation("DELETE", null, position, length, null);
    }
    
    /**
     * 创建保留操作
     */
    public static RichTextOperation retain(int position, int length) {
        return new RichTextOperation("RETAIN", null, position, length, null);
    }
    
    /**
     * 转换为普通Operation（用于兼容纯文本操作）
     */
    public Operation toPlainOperation() {
        switch (type) {
            case "INSERT":
                return Operation.insert(data, position);
            case "DELETE":
                return Operation.delete(position, length);
            case "RETAIN":
                return Operation.retain(position, length);
            default:
                return Operation.retain(position, length);
        }
    }
}

