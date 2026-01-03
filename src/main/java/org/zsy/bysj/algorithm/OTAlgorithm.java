package org.zsy.bysj.algorithm;

import java.util.ArrayList;
import java.util.List;

/**
 * 操作转换（Operational Transformation）算法
 * 用于解决多人协同编辑中的冲突问题
 * 
 * 核心思想：当两个操作并发执行时，通过转换操作使其能够正确合并
 */
public class OTAlgorithm {

    /**
     * 转换操作：将操作op1相对于操作op2进行转换
     * 返回转换后的操作，使得 op1' 和 op2 可以按任意顺序应用
     * 
     * @param op1 需要转换的操作
     * @param op2 参考操作（已经应用的操作）
     * @return 转换后的op1
     */
    public static Operation transform(Operation op1, Operation op2) {
        if (op1 == null || op2 == null) {
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
     * 
     * 场景1: INSERT vs INSERT
     * - 如果op2在op1之前插入，op1的位置需要后移
     * - 如果op2在op1之后插入，op1位置不变
     * - 如果op2和op1在同一位置，使用tie-break规则（op2优先）
     * 
     * 场景2: INSERT vs DELETE
     * - 如果删除在插入之前，插入位置需要前移
     * - 如果删除在插入之后，插入位置不变
     * - 如果删除和插入位置相同，插入位置不变（删除的是插入位置之前的内容）
     */
    private static Operation transformInsert(Operation insert, Operation op2) {
        if (op2.getType().equals("INSERT")) {
            // INSERT vs INSERT
            int insertPos = insert.getPosition();
            int op2Pos = op2.getPosition();
            int op2Length = op2.getLength(); // INSERT的length是插入文本的长度
            
            if (op2Pos < insertPos) {
                // op2在insert之前插入，insert位置后移
                return Operation.insert(insert.getData(), insertPos + op2Length);
            } else if (op2Pos == insertPos) {
                // 同一位置插入，使用tie-break：op2优先（已应用的操作优先）
                // insert位置后移
                return Operation.insert(insert.getData(), insertPos + op2Length);
            } else {
                // op2在insert之后插入，insert位置不变
                return insert;
            }
        } else if (op2.getType().equals("DELETE")) {
            // INSERT vs DELETE
            int insertPos = insert.getPosition();
            int deletePos = op2.getPosition();
            int deleteLength = op2.getLength();
            int deleteEnd = deletePos + deleteLength;
            
            if (deleteEnd <= insertPos) {
                // 删除在插入之前，插入位置前移
                return Operation.insert(insert.getData(), insertPos - deleteLength);
            } else if (deletePos < insertPos) {
                // 删除范围包含插入位置，插入位置移动到删除起点
                return Operation.insert(insert.getData(), deletePos);
            } else {
                // 删除在插入之后，插入位置不变
                return insert;
            }
        } else if (op2.getType().equals("RETAIN")) {
            // INSERT vs RETAIN: RETAIN不影响插入位置
            return insert;
        }
        return insert;
    }

    /**
     * 转换删除操作
     * 
     * 场景1: DELETE vs INSERT
     * - 如果插入在删除之前，删除位置后移
     * - 如果插入在删除范围内，删除长度增加
     * - 如果插入在删除之后，删除位置和长度不变
     * 
     * 场景2: DELETE vs DELETE
     * - 如果两个删除不重叠，调整位置
     * - 如果两个删除重叠，需要合并或分割删除范围
     */
    private static Operation transformDelete(Operation delete, Operation op2) {
        if (op2.getType().equals("INSERT")) {
            // DELETE vs INSERT
            int deletePos = delete.getPosition();
            int deleteLength = delete.getLength();
            int deleteEnd = deletePos + deleteLength;
            int insertPos = op2.getPosition();
            int insertLength = op2.getLength();
            
            if (insertPos <= deletePos) {
                // 插入在删除之前，删除位置后移
                return Operation.delete(deletePos + insertLength, deleteLength);
            } else if (insertPos < deleteEnd) {
                // 插入在删除范围内，删除长度增加
                return Operation.delete(deletePos, deleteLength + insertLength);
            } else {
                // 插入在删除之后，删除位置和长度不变
                return delete;
            }
        } else if (op2.getType().equals("DELETE")) {
            // DELETE vs DELETE
            int delete1Pos = delete.getPosition();
            int delete1Length = delete.getLength();
            int delete1End = delete1Pos + delete1Length;
            
            int delete2Pos = op2.getPosition();
            int delete2Length = op2.getLength();
            int delete2End = delete2Pos + delete2Length;
            
            if (delete2End <= delete1Pos) {
                // delete2完全在delete1之前，delete1位置前移
                return Operation.delete(delete1Pos - delete2Length, delete1Length);
            } else if (delete2Pos >= delete1End) {
                // delete2完全在delete1之后，delete1位置和长度不变
                return delete;
            } else if (delete2Pos <= delete1Pos && delete2End >= delete1End) {
                // delete2完全包含delete1，删除操作无效（返回长度为0的删除）
                return Operation.delete(delete1Pos, 0);
            } else if (delete1Pos <= delete2Pos && delete1End >= delete2End) {
                // delete1完全包含delete2，需要减去重叠部分
                int overlap = delete2Length;
                return Operation.delete(delete1Pos, delete1Length - overlap);
            } else if (delete2Pos < delete1Pos) {
                // delete2与delete1重叠，delete2在前
                int overlap = delete1End - delete2End;
                int newPos = delete1Pos - (delete2Length - (delete2End - delete1Pos));
                return Operation.delete(newPos, overlap);
            } else {
                // delete1与delete2重叠，delete1在前
                // 计算重叠部分：从delete2开始到delete1结束的部分
                int overlapStart = delete2Pos;
                int overlapEnd = Math.min(delete1End, delete2End);
                int overlap = Math.max(0, overlapEnd - overlapStart);
                // 新的删除长度 = 原长度 - 重叠部分
                int newLength = delete1Length - overlap;
                return Operation.delete(delete1Pos, newLength);
            }
        } else if (op2.getType().equals("RETAIN")) {
            // DELETE vs RETAIN: RETAIN不影响删除操作
            return delete;
        }
        return delete;
    }

    /**
     * 转换保留操作
     * RETAIN操作表示跳过字符，转换逻辑类似DELETE
     */
    private static Operation transformRetain(Operation retain, Operation op2) {
        // RETAIN操作可以视为对删除操作的转换
        Operation deleteOp = Operation.delete(retain.getPosition(), retain.getLength());
        Operation transformedDelete = transformDelete(deleteOp, op2);
        return Operation.retain(transformedDelete.getPosition(), transformedDelete.getLength());
    }

    /**
     * 合并多个操作
     * 将操作列表合并，确保操作的顺序和正确性
     * 
     * @param ops1 第一个操作列表
     * @param ops2 第二个操作列表（需要相对于ops1进行转换）
     * @return 合并后的操作列表
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
            return Operation.insert(op1.getData() + (op2.getData() != null ? op2.getData() : ""), op1.getPosition());
        } else if ("DELETE".equals(op1.getType())) {
            return Operation.delete(op1.getPosition(), op1.getLength() + op2.getLength());
        } else {
            return Operation.retain(op1.getPosition(), op1.getLength() + op2.getLength());
        }
    }

    /**
     * 应用操作到文档
     * 
     * @param document 文档内容
     * @param op 要应用的操作
     * @return 应用操作后的文档内容
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
                String insertData = op.getData() != null ? op.getData() : "";
                sb.insert(insertPos, insertData);
                break;
            case "DELETE":
                // 确保删除位置和长度在有效范围内
                int deletePos = Math.max(0, Math.min(op.getPosition(), docLength));
                int deleteEnd = Math.min(deletePos + op.getLength(), docLength);
                if (deleteEnd > deletePos) {
                    sb.delete(deletePos, deleteEnd);
                }
                break;
            case "RETAIN":
                // RETAIN操作不改变文档内容，只是跳过字符
                break;
        }
        return sb.toString();
    }

    /**
     * 应用操作列表到文档
     * 
     * @param document 文档内容
     * @param operations 操作列表
     * @return 应用所有操作后的文档内容
     */
    public static String apply(String document, List<Operation> operations) {
        if (operations == null || operations.isEmpty()) {
            return document;
        }
        
        String result = document;
        for (Operation op : operations) {
            result = apply(result, op);
        }
        return result;
    }

    /**
     * 转换操作列表：将操作列表op1相对于操作列表op2进行转换
     * 
     * @param op1 需要转换的操作列表
     * @param op2 参考操作列表（已经应用的操作）
     * @return 转换后的操作列表
     */
    public static List<Operation> transformOperations(List<Operation> op1, List<Operation> op2) {
        if (op1 == null || op1.isEmpty()) {
            return op1;
        }
        if (op2 == null || op2.isEmpty()) {
            return op1;
        }
        
        List<Operation> result = new ArrayList<>();
        for (Operation op : op1) {
            Operation transformed = op;
            for (Operation refOp : op2) {
                transformed = transform(transformed, refOp);
            }
            result.add(transformed);
        }
        return result;
    }
}

