package org.zsy.bysj.algorithm;

import java.util.ArrayList;
import java.util.List;

/**
 * 操作转换（Operational Transformation）算法
 * 用于解决多人协同编辑中的冲突问题
 */
public class OTAlgorithm {

    /**
     * 转换操作：将操作op1相对于操作op2进行转换
     * 返回转换后的操作
     */
    public static Operation transform(Operation op1, Operation op2) {
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
            default:
                return op1;
        }
    }

    /**
     * 转换插入操作
     */
    private static Operation transformInsert(Operation insert, Operation op2) {
        if (op2.getType().equals("INSERT")) {
            // 如果op2也是插入，且插入位置在insert之前，需要调整insert的位置
            if (op2.getPosition() <= insert.getPosition()) {
                return Operation.insert(insert.getData(), insert.getPosition() + op2.getLength());
            }
            return insert;
        } else if (op2.getType().equals("DELETE")) {
            // 如果op2是删除，且删除位置在insert之前，需要调整insert的位置
            if (op2.getPosition() <= insert.getPosition()) {
                return Operation.insert(insert.getData(), insert.getPosition() - op2.getLength());
            }
            return insert;
        }
        return insert;
    }

    /**
     * 转换删除操作
     */
    private static Operation transformDelete(Operation delete, Operation op2) {
        if (op2.getType().equals("INSERT")) {
            // 如果op2是插入，且插入位置在delete范围内，需要调整delete的范围
            if (op2.getPosition() <= delete.getPosition()) {
                return Operation.delete(delete.getPosition() + op2.getLength(), delete.getLength());
            } else if (op2.getPosition() < delete.getPosition() + delete.getLength()) {
                // 插入位置在删除范围内，需要分割删除操作
                // 计算删除操作的前后两部分
                int beforeLength = op2.getPosition() - delete.getPosition();
                int afterStart = op2.getPosition() + op2.getLength();
                int afterLength = delete.getPosition() + delete.getLength() - afterStart;
                
                // 如果前后都有内容，返回第一部分（第二部分会在后续处理中处理）
                // 这里返回第一部分，实际应用中可能需要返回操作列表
                if (beforeLength > 0) {
                    return Operation.delete(delete.getPosition() + op2.getLength(), beforeLength);
                } else {
                    // 如果第一部分为空，返回第二部分
                    return Operation.delete(delete.getPosition() + op2.getLength(), afterLength);
                }
            }
            return delete;
        } else if (op2.getType().equals("DELETE")) {
            // 如果op2也是删除，需要调整delete的范围
            if (op2.getPosition() + op2.getLength() <= delete.getPosition()) {
                return Operation.delete(delete.getPosition() - op2.getLength(), delete.getLength());
            } else if (op2.getPosition() <= delete.getPosition()) {
                int overlap = Math.min(op2.getLength(), delete.getPosition() + delete.getLength() - op2.getPosition());
                return Operation.delete(delete.getPosition() - (op2.getLength() - overlap), delete.getLength() - overlap);
            }
            return delete;
        }
        return delete;
    }

    /**
     * 转换保留操作
     */
    private static Operation transformRetain(Operation retain, Operation op2) {
        // RETAIN操作通常用于表示跳过字符，转换逻辑类似DELETE
        return transformDelete(Operation.delete(retain.getPosition(), retain.getLength()), op2);
    }

    /**
     * 合并多个操作
     * 将操作列表合并，确保操作的顺序和正确性
     */
    public static List<Operation> compose(List<Operation> ops1, List<Operation> ops2) {
        List<Operation> result = new ArrayList<>();
        
        // 将第一个操作列表加入结果
        result.addAll(ops1);
        
        // 对第二个操作列表中的每个操作，相对于第一个操作列表进行转换
        for (Operation op2 : ops2) {
            Operation transformedOp = op2;
            // 相对于第一个操作列表中的所有操作进行转换
            for (Operation op1 : ops1) {
                transformedOp = transform(transformedOp, op1);
            }
            result.add(transformedOp);
        }
        
        // 优化：合并相邻的相同类型操作
        return optimizeOperations(result);
    }

    /**
     * 优化操作列表：合并相邻的相同类型操作
     */
    private static List<Operation> optimizeOperations(List<Operation> operations) {
        if (operations.isEmpty()) {
            return operations;
        }
        
        List<Operation> optimized = new ArrayList<>();
        Operation current = operations.get(0);
        
        for (int i = 1; i < operations.size(); i++) {
            Operation next = operations.get(i);
            
            // 如果相邻操作类型相同且可以合并
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
    private static boolean canMerge(Operation op1, Operation op2) {
        if (!op1.getType().equals(op2.getType())) {
            return false;
        }
        
        // INSERT操作：如果op2的位置正好是op1的结束位置
        if ("INSERT".equals(op1.getType())) {
            return op2.getPosition() == op1.getPosition() + op1.getLength();
        }
        
        // DELETE操作：如果op2的位置正好是op1的结束位置
        if ("DELETE".equals(op1.getType())) {
            return op2.getPosition() == op1.getPosition() + op1.getLength();
        }
        
        // RETAIN操作：如果op2的位置正好是op1的结束位置
        if ("RETAIN".equals(op1.getType())) {
            return op2.getPosition() == op1.getPosition() + op1.getLength();
        }
        
        return false;
    }

    /**
     * 合并两个操作
     */
    private static Operation mergeOperations(Operation op1, Operation op2) {
        if ("INSERT".equals(op1.getType())) {
            return Operation.insert(op1.getData() + op2.getData(), op1.getPosition());
        } else if ("DELETE".equals(op1.getType())) {
            return Operation.delete(op1.getPosition(), op1.getLength() + op2.getLength());
        } else {
            return Operation.retain(op1.getPosition(), op1.getLength() + op2.getLength());
        }
    }

    /**
     * 应用操作到文档
     */
    public static String apply(String document, Operation op) {
        if (op == null || document == null) {
            return document;
        }

        StringBuilder sb = new StringBuilder(document);
        int docLength = sb.length();

        switch (op.getType()) {
            case "INSERT":
                // 确保插入位置在有效范围内
                int insertPos = Math.max(0, Math.min(op.getPosition(), docLength));
                sb.insert(insertPos, op.getData() != null ? op.getData() : "");
                break;
            case "DELETE":
                // 确保删除位置和长度在有效范围内
                int deletePos = Math.max(0, Math.min(op.getPosition(), docLength));
                int deleteLength = Math.max(0, Math.min(op.getLength(), docLength - deletePos));
                if (deleteLength > 0) {
                    sb.delete(deletePos, deletePos + deleteLength);
                }
                break;
            case "RETAIN":
                // RETAIN操作不改变文档内容
                break;
        }
        return sb.toString();
    }

    /**
     * 应用操作列表到文档
     */
    public static String apply(String document, List<Operation> operations) {
        String result = document;
        for (Operation op : operations) {
            result = apply(result, op);
        }
        return result;
    }
}

