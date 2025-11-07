package org.zsy.bysj.algorithm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 富文本操作转换（OT）算法
 * 扩展OT算法以支持富文本格式操作
 */
public class RichTextOTAlgorithm {

    /**
     * 转换富文本操作
     */
    public static RichTextOperation transform(RichTextOperation op1, RichTextOperation op2) {
        if (op1 == null || op2 == null) {
            return op1;
        }

        // 如果两个操作不重叠，直接返回
        if (op1.getPosition() + op1.getLength() <= op2.getPosition() ||
            op2.getPosition() + op2.getLength() <= op1.getPosition()) {
            return op1;
        }

        // 根据操作类型进行转换
        switch (op1.getType()) {
            case "INSERT":
                return transformInsert(op1, op2);
            case "DELETE":
                return transformDelete(op1, op2);
            case "RETAIN":
                return transformRetain(op1, op2);
            case "FORMAT":
                return transformFormat(op1, op2);
            default:
                return op1;
        }
    }

    /**
     * 转换插入操作
     */
    private static RichTextOperation transformInsert(RichTextOperation insert, RichTextOperation op2) {
        if (op2.getType().equals("INSERT")) {
            if (op2.getPosition() <= insert.getPosition()) {
                return RichTextOperation.insert(
                    insert.getData(),
                    insert.getPosition() + op2.getLength(),
                    insert.getAttributes()
                );
            }
            return insert;
        } else if (op2.getType().equals("DELETE")) {
            if (op2.getPosition() <= insert.getPosition()) {
                return RichTextOperation.insert(
                    insert.getData(),
                    insert.getPosition() - op2.getLength(),
                    insert.getAttributes()
                );
            }
            return insert;
        } else if (op2.getType().equals("FORMAT")) {
            // 格式操作不影响插入操作的位置
            return insert;
        }
        return insert;
    }

    /**
     * 转换删除操作
     */
    private static RichTextOperation transformDelete(RichTextOperation delete, RichTextOperation op2) {
        if (op2.getType().equals("INSERT")) {
            if (op2.getPosition() <= delete.getPosition()) {
                return RichTextOperation.delete(delete.getPosition() + op2.getLength(), delete.getLength());
            } else if (op2.getPosition() < delete.getPosition() + delete.getLength()) {
                // 插入位置在删除范围内，需要调整删除范围
                int beforeLength = op2.getPosition() - delete.getPosition();
                int afterStart = op2.getPosition() + op2.getLength();
                int afterLength = delete.getPosition() + delete.getLength() - afterStart;
                
                if (beforeLength > 0) {
                    return RichTextOperation.delete(delete.getPosition() + op2.getLength(), beforeLength);
                } else {
                    return RichTextOperation.delete(delete.getPosition() + op2.getLength(), afterLength);
                }
            }
            return delete;
        } else if (op2.getType().equals("DELETE")) {
            if (op2.getPosition() + op2.getLength() <= delete.getPosition()) {
                return RichTextOperation.delete(delete.getPosition() - op2.getLength(), delete.getLength());
            } else if (op2.getPosition() <= delete.getPosition()) {
                int overlap = Math.min(op2.getLength(), delete.getPosition() + delete.getLength() - op2.getPosition());
                return RichTextOperation.delete(
                    delete.getPosition() - (op2.getLength() - overlap),
                    delete.getLength() - overlap
                );
            }
            return delete;
        } else if (op2.getType().equals("FORMAT")) {
            // 格式操作不影响删除操作
            return delete;
        }
        return delete;
    }

    /**
     * 转换保留操作
     */
    private static RichTextOperation transformRetain(RichTextOperation retain, RichTextOperation op2) {
        return transformDelete(RichTextOperation.delete(retain.getPosition(), retain.getLength()), op2);
    }

    /**
     * 转换格式操作
     */
    private static RichTextOperation transformFormat(RichTextOperation format, RichTextOperation op2) {
        if (op2.getType().equals("INSERT")) {
            if (op2.getPosition() <= format.getPosition()) {
                return RichTextOperation.format(
                    format.getPosition() + op2.getLength(),
                    format.getLength(),
                    format.getAttributes()
                );
            } else if (op2.getPosition() < format.getPosition() + format.getLength()) {
                // 插入位置在格式范围内，需要分割格式操作
                int beforeLength = op2.getPosition() - format.getPosition();
                int afterStart = op2.getPosition() + op2.getLength();
                int afterLength = format.getPosition() + format.getLength() - afterStart;
                
                // 返回前半部分格式
                if (beforeLength > 0) {
                    return RichTextOperation.format(
                        format.getPosition() + op2.getLength(),
                        beforeLength,
                        format.getAttributes()
                    );
                } else {
                    return RichTextOperation.format(
                        format.getPosition() + op2.getLength(),
                        afterLength,
                        format.getAttributes()
                    );
                }
            }
            return format;
        } else if (op2.getType().equals("DELETE")) {
            if (op2.getPosition() + op2.getLength() <= format.getPosition()) {
                return RichTextOperation.format(
                    format.getPosition() - op2.getLength(),
                    format.getLength(),
                    format.getAttributes()
                );
            } else if (op2.getPosition() <= format.getPosition()) {
                int overlap = Math.min(op2.getLength(), format.getPosition() + format.getLength() - op2.getPosition());
                return RichTextOperation.format(
                    format.getPosition() - (op2.getLength() - overlap),
                    format.getLength() - overlap,
                    format.getAttributes()
                );
            }
            return format;
        } else if (op2.getType().equals("FORMAT")) {
            // 两个格式操作：合并格式属性
            Map<String, Object> mergedAttributes = new HashMap<>(format.getAttributes());
            if (op2.getAttributes() != null) {
                mergedAttributes.putAll(op2.getAttributes());
            }
            return RichTextOperation.format(format.getPosition(), format.getLength(), mergedAttributes);
        }
        return format;
    }

    /**
     * 合并多个富文本操作
     */
    public static List<RichTextOperation> compose(List<RichTextOperation> ops1, List<RichTextOperation> ops2) {
        List<RichTextOperation> result = new ArrayList<>();
        result.addAll(ops1);
        
        for (RichTextOperation op2 : ops2) {
            RichTextOperation transformedOp = op2;
            for (RichTextOperation op1 : ops1) {
                transformedOp = transform(transformedOp, op1);
            }
            result.add(transformedOp);
        }
        
        return optimizeOperations(result);
    }

    /**
     * 优化操作列表
     */
    private static List<RichTextOperation> optimizeOperations(List<RichTextOperation> operations) {
        if (operations.isEmpty()) {
            return operations;
        }
        
        List<RichTextOperation> optimized = new ArrayList<>();
        RichTextOperation current = operations.get(0);
        
        for (int i = 1; i < operations.size(); i++) {
            RichTextOperation next = operations.get(i);
            
            if (canMerge(current, next)) {
                current = mergeOperations(current, next);
            } else {
                optimized.add(current);
                current = next;
            }
        }
        optimized.add(current);
        
        return optimized;
    }

    /**
     * 判断两个操作是否可以合并
     */
    private static boolean canMerge(RichTextOperation op1, RichTextOperation op2) {
        if (!op1.getType().equals(op2.getType())) {
            return false;
        }
        
        // 格式操作需要属性相同才能合并
        if ("FORMAT".equals(op1.getType())) {
            if (!attributesEqual(op1.getAttributes(), op2.getAttributes())) {
                return false;
            }
        }
        
        // 插入操作需要属性相同才能合并
        if ("INSERT".equals(op1.getType())) {
            if (!attributesEqual(op1.getAttributes(), op2.getAttributes())) {
                return false;
            }
        }
        
        return op2.getPosition() == op1.getPosition() + op1.getLength();
    }

    /**
     * 判断两个属性映射是否相等
     */
    private static boolean attributesEqual(Map<String, Object> attrs1, Map<String, Object> attrs2) {
        if (attrs1 == null && attrs2 == null) {
            return true;
        }
        if (attrs1 == null || attrs2 == null) {
            return false;
        }
        return attrs1.equals(attrs2);
    }

    /**
     * 合并两个操作
     */
    private static RichTextOperation mergeOperations(RichTextOperation op1, RichTextOperation op2) {
        if ("INSERT".equals(op1.getType())) {
            return RichTextOperation.insert(
                op1.getData() + op2.getData(),
                op1.getPosition(),
                op1.getAttributes()
            );
        } else if ("DELETE".equals(op1.getType())) {
            return RichTextOperation.delete(op1.getPosition(), op1.getLength() + op2.getLength());
        } else if ("FORMAT".equals(op1.getType())) {
            return RichTextOperation.format(
                op1.getPosition(),
                op1.getLength() + op2.getLength(),
                op1.getAttributes()
            );
        } else {
            return RichTextOperation.retain(op1.getPosition(), op1.getLength() + op2.getLength());
        }
    }

    /**
     * 应用富文本操作到文档
     */
    public static String apply(String document, RichTextOperation op) {
        if (op == null) {
            return document;
        }

        StringBuilder sb = new StringBuilder(document);
        switch (op.getType()) {
            case "INSERT":
                sb.insert(op.getPosition(), op.getData());
                break;
            case "DELETE":
                sb.delete(op.getPosition(), op.getPosition() + op.getLength());
                break;
            case "RETAIN":
            case "FORMAT":
                // RETAIN和FORMAT操作不改变文档文本内容
                break;
        }
        return sb.toString();
    }

    /**
     * 应用操作列表到文档
     */
    public static String apply(String document, List<RichTextOperation> operations) {
        String result = document;
        for (RichTextOperation op : operations) {
            result = apply(result, op);
        }
        return result;
    }
}

