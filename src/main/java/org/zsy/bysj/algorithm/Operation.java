package org.zsy.bysj.algorithm;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * OT算法中的操作类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Operation {
    private String type; // INSERT/DELETE/RETAIN
    private String data; // 对于INSERT操作，存储插入的文本
    private Integer position; // 操作位置
    private Integer length; // 操作长度（对于RETAIN和DELETE）

    /**
     * 创建插入操作
     */
    public static Operation insert(String text, int position) {
        return new Operation("INSERT", text, position, text.length());
    }

    /**
     * 创建删除操作
     */
    public static Operation delete(int position, int length) {
        return new Operation("DELETE", null, position, length);
    }

    /**
     * 创建保留操作（跳过字符）
     */
    public static Operation retain(int position, int length) {
        return new Operation("RETAIN", null, position, length);
    }
}

