package org.zsy.bysj.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.zsy.bysj.algorithm.Operation;
import org.zsy.bysj.algorithm.OTAlgorithm;
import org.zsy.bysj.constant.RedisKeyConstant;
import org.zsy.bysj.dto.OperationDTO;
import org.zsy.bysj.model.Document;
import org.zsy.bysj.model.DocumentOperation;
import org.zsy.bysj.mapper.DocumentOperationMapper;
import org.zsy.bysj.service.DocumentService;
import org.zsy.bysj.service.OfflineSyncService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 离线同步服务实现类
 */
@Service
public class OfflineSyncServiceImpl implements OfflineSyncService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DocumentOperationMapper documentOperationMapper;

    @Autowired
    private ObjectMapper objectMapper;

    private static final int OFFLINE_OPERATIONS_TTL_HOURS = 48; // 离线操作保留48小时
    private static final int OFFLINE_STATUS_TTL_MINUTES = 5; // 离线状态标记5分钟

    @Override
    public void saveOfflineOperation(Long documentId, Long userId, OperationDTO operation) {
        String key = RedisKeyConstant.buildOfflineOperationsKey(documentId, userId);
        
        try {
            // 获取现有操作列表
            List<OperationDTO> operations = getOfflineOperations(documentId, userId);
            if (operations == null) {
                operations = new ArrayList<>();
            }
            
            // 添加新操作
            operations.add(operation);
            
            // 保存到Redis
            String json = objectMapper.writeValueAsString(operations);
            redisTemplate.opsForValue().set(key, json, OFFLINE_OPERATIONS_TTL_HOURS, TimeUnit.HOURS);
            
            // 标记用户为离线状态
            markUserOffline(documentId, userId);
        } catch (Exception e) {
            throw new RuntimeException("保存离线操作失败", e);
        }
    }

    @Override
    public List<OperationDTO> getOfflineOperations(Long documentId, Long userId) {
        String key = RedisKeyConstant.buildOfflineOperationsKey(documentId, userId);
        String json = (String) redisTemplate.opsForValue().get(key);
        
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        
        try {
            return objectMapper.readValue(json, new TypeReference<List<OperationDTO>>() {});
        } catch (Exception e) {
            throw new RuntimeException("解析离线操作失败", e);
        }
    }

    @Override
    public void clearOfflineOperations(Long documentId, Long userId) {
        String key = RedisKeyConstant.buildOfflineOperationsKey(documentId, userId);
        redisTemplate.delete(key);
    }

    @Override
    public void markUserOffline(Long documentId, Long userId) {
        String key = RedisKeyConstant.buildUserOfflineKey(documentId, userId);
        redisTemplate.opsForValue().set(key, "1", OFFLINE_STATUS_TTL_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public void markUserOnline(Long documentId, Long userId) {
        String key = RedisKeyConstant.buildUserOfflineKey(documentId, userId);
        redisTemplate.delete(key);
    }

    @Override
    public boolean isUserOffline(Long documentId, Long userId) {
        String key = RedisKeyConstant.buildUserOfflineKey(documentId, userId);
        return redisTemplate.hasKey(key);
    }

    @Override
    public List<OperationDTO> syncOfflineOperations(Long documentId, Long userId) {
        // 获取离线操作
        List<OperationDTO> offlineOps = getOfflineOperations(documentId, userId);
        if (offlineOps.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 获取当前文档版本
        Document document = documentService.getDocumentById(documentId);
        if (document == null) {
            clearOfflineOperations(documentId, userId);
            return new ArrayList<>();
        }
        
        // 获取用户离线时的版本（从最后一个离线操作中获取，或从Redis中获取）
        Integer offlineVersion = getOfflineVersion(documentId, userId);
        Integer currentVersion = document.getVersion();
        
        // 如果版本不一致，说明有冲突，需要解决
        if (offlineVersion != null && !offlineVersion.equals(currentVersion)) {
        // 解决冲突
        offlineOps = resolveOfflineConflicts(documentId, userId, offlineOps, currentVersion);
        }
        
        // 应用离线操作
        List<OperationDTO> syncedOps = new ArrayList<>();
        for (OperationDTO opDTO : offlineOps) {
            try {
                Operation operation = convertToOperation(opDTO);
                documentService.applyOperation(documentId, operation, userId);
                syncedOps.add(opDTO);
            } catch (Exception e) {
                // 操作失败，记录日志但继续处理其他操作
                System.err.println("同步离线操作失败: " + e.getMessage());
            }
        }
        
        // 清除离线操作队列
        clearOfflineOperations(documentId, userId);
        
        // 标记用户为在线
        markUserOnline(documentId, userId);
        
        return syncedOps;
    }

    @Override
    public List<OperationDTO> resolveOfflineConflicts(Long documentId, Long userId, 
                                                       List<OperationDTO> offlineOps, 
                                                       Integer serverVersion) {
        // 获取服务器在用户离线期间的操作
        Document document = documentService.getDocumentById(documentId);
        Integer offlineVersion = getOfflineVersion(documentId, userId);
        
        if (offlineVersion == null) {
            offlineVersion = document.getVersion() - offlineOps.size();
        }
        
        List<DocumentOperation> serverOps = getServerOperations(documentId, offlineVersion, serverVersion);
        
        // 将服务器操作转换为Operation对象
        List<Operation> serverOperations = new ArrayList<>();
        for (DocumentOperation docOp : serverOps) {
            try {
                OperationDTO opDTO = objectMapper.readValue(docOp.getOperationData(), OperationDTO.class);
                serverOperations.add(convertToOperation(opDTO));
            } catch (Exception e) {
                System.err.println("解析服务器操作失败: " + e.getMessage());
            }
        }
        
        // 对每个离线操作，相对于所有服务器操作进行OT转换
        List<OperationDTO> resolvedOps = new ArrayList<>();
        for (OperationDTO offlineOp : offlineOps) {
            Operation op = convertToOperation(offlineOp);
            
            // 相对于所有服务器操作进行转换
            for (Operation serverOp : serverOperations) {
                op = OTAlgorithm.transform(op, serverOp);
            }
            
            // 转换回DTO
            resolvedOps.add(convertToDTO(op));
        }
        
        return resolvedOps;
    }

    /**
     * 获取用户离线时的文档版本
     */
    private Integer getOfflineVersion(Long documentId, Long userId) {
        String key = RedisKeyConstant.buildUserOfflineKey(documentId, userId) + ":version";
        Object version = redisTemplate.opsForValue().get(key);
        return version != null ? Integer.valueOf(version.toString()) : null;
    }

    /**
     * 获取服务器在指定版本范围内的操作
     */
    private List<DocumentOperation> getServerOperations(Long documentId, Integer fromVersion, Integer toVersion) {
        // 从数据库查询指定版本范围内的操作
        return documentOperationMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<DocumentOperation>()
                .eq("document_id", documentId)
                .ge("version", fromVersion)
                .lt("version", toVersion)
                .orderByAsc("version")
        );
    }

    /**
     * 将OperationDTO转换为Operation
     */
    private Operation convertToOperation(OperationDTO opDTO) {
        switch (opDTO.getType()) {
            case "INSERT":
                return Operation.insert(opDTO.getData(), opDTO.getPosition());
            case "DELETE":
                return Operation.delete(opDTO.getPosition(), opDTO.getLength());
            case "RETAIN":
                return Operation.retain(opDTO.getPosition(), opDTO.getLength());
            default:
                throw new IllegalArgumentException("未知的操作类型: " + opDTO.getType());
        }
    }

    /**
     * 将Operation转换为OperationDTO
     */
    private OperationDTO convertToDTO(Operation op) {
        OperationDTO dto = new OperationDTO();
        dto.setType(op.getType());
        dto.setData(op.getData());
        dto.setPosition(op.getPosition());
        dto.setLength(op.getLength());
        return dto;
    }
}

