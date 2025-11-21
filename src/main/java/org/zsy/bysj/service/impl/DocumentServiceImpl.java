package org.zsy.bysj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zsy.bysj.algorithm.OTAlgorithm;
import org.zsy.bysj.algorithm.Operation;
import org.zsy.bysj.mapper.DocumentMapper;
import org.zsy.bysj.mapper.DocumentOperationMapper;
import org.zsy.bysj.mapper.DocumentVersionMapper;
import org.zsy.bysj.model.Document;
import org.zsy.bysj.model.DocumentOperation;
import org.zsy.bysj.model.DocumentVersion;
import org.zsy.bysj.model.DocumentPermission;
import org.zsy.bysj.service.DocumentService;
import org.zsy.bysj.service.PermissionService;
import org.zsy.bysj.constant.RedisKeyConstant;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 文档服务实现类
 */
@Service
public class DocumentServiceImpl implements DocumentService {

    @Autowired
    private DocumentMapper documentMapper;

    @Autowired
    private DocumentOperationMapper documentOperationMapper;

    @Autowired
    private DocumentVersionMapper documentVersionMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final int CACHE_EXPIRE_HOURS = 24;

    @Override
    @Transactional
    public Document createDocument(String title, Long creatorId) {
        Document document = new Document();
        document.setTitle(title);
        document.setContent("[]"); // 初始内容为空数组（JSON格式）
        document.setCreatorId(creatorId);
        document.setVersion(1);
        document.setIsDeleted(false);
        
        documentMapper.insert(document);
        
        // 自动为创建者添加 ADMIN 权限
        permissionService.addPermission(document.getId(), creatorId, "ADMIN");
        
        // 缓存文档
        cacheDocument(document);
        
        return document;
    }

    @Override
    public Document getDocumentById(Long documentId) {
        // 先从缓存获取
        Object cached = redisTemplate.opsForValue()
                .get(RedisKeyConstant.buildDocumentCacheKey(documentId));
        
        Document document = null;
        if (cached != null) {
            // 如果缓存的是 LinkedHashMap（旧数据），需要手动转换
            if (cached instanceof Document) {
                document = (Document) cached;
            } else if (cached instanceof java.util.Map) {
                // 手动转换 LinkedHashMap 到 Document
                try {
                    document = objectMapper.convertValue(cached, Document.class);
                } catch (Exception e) {
                    // 转换失败，清除缓存，从数据库重新加载
                    redisTemplate.delete(RedisKeyConstant.buildDocumentCacheKey(documentId));
                    document = null;
                }
            }
        }
        
        if (document == null) {
            document = documentMapper.selectById(documentId);
            if (document != null) {
                cacheDocument(document);
            }
        }
        
        return document;
    }

    @Override
    @Transactional
    public void updateDocumentContent(Long documentId, String content, Integer version) {
        // 先更新文档内容（添加版本检查，避免并发更新冲突）
        UpdateWrapper<Document> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", documentId)
                     .eq("version", version) // 添加版本检查，避免并发更新冲突
                     .set("content", content)
                     .set("version", version + 1)
                     .set("updated_at", LocalDateTime.now());
        int updateCount = documentMapper.update(null, updateWrapper);
        
        // 检查更新是否成功（版本号匹配）
        if (updateCount == 0) {
            throw new RuntimeException("文档版本已变更，更新失败，请重试");
        }
        
        // 注意：快照创建已移除，只在保存时创建快照（见 DocumentController.updateDocumentContent）
        
        // 更新缓存
        Document document = documentMapper.selectById(documentId);
        if (document != null) {
            cacheDocument(document);
        }
    }
    
    /**
     * 更新文档内容并创建快照（用于保存操作）
     */
    @Transactional
    public Document updateDocumentContentWithSnapshot(Long documentId, String content, Integer version) {
        // 先更新文档内容
        updateDocumentContent(documentId, content, version);
        
        // 创建当前版本的快照（使用更新前的版本号）
        if (version > 0) {
            try {
                createVersionSnapshot(documentId, version);
            } catch (Exception e) {
                // 快照创建失败不影响主流程，记录日志即可
            }
        }
        
        // 返回更新后的文档
        return getDocumentById(documentId);
    }

    @Override
    @Transactional
    public Document applyOperation(Long documentId, Operation operation, Long userId) {
        Document document = getDocumentById(documentId);
        if (document == null) {
            throw new RuntimeException("文档不存在");
        }

                    // 检查用户是否有编辑权限
                    boolean hasWritePermission = permissionService.hasPermission(documentId, userId, "WRITE");
                    System.out.println("权限检查: 用户" + userId + " 对文档" + documentId + " 的写权限 = " + hasWritePermission);
                    if (!hasWritePermission) {
                        throw new RuntimeException("无权限编辑此文档");
                    }

        // 获取自用户上次操作以来的所有操作（使用MyBatis Plus的QueryWrapper）
        QueryWrapper<DocumentOperation> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("document_id", documentId)
                   .gt("version", document.getVersion())
                   .orderByAsc("timestamp");
        List<DocumentOperation> operations = documentOperationMapper.selectList(queryWrapper);

        // 对操作进行转换（OT算法）
        Operation transformedOp = operation;
        for (DocumentOperation op : operations) {
            Operation op2 = parseOperation(op);
            transformedOp = OTAlgorithm.transform(transformedOp, op2);
        }

        // 应用转换后的操作
        String currentContent = document.getContent();
        String newContent = OTAlgorithm.apply(currentContent, transformedOp);
        
        // 更新文档
        updateDocumentContent(documentId, newContent, document.getVersion());

        // 保存操作记录
        saveOperation(documentId, userId, transformedOp, document.getVersion());

        // 返回更新后的文档
        return getDocumentById(documentId);
    }

    @Override
    public List<DocumentOperation> getDocumentOperations(Long documentId, Integer fromVersion) {
        QueryWrapper<DocumentOperation> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("document_id", documentId)
                   .gt("version", fromVersion)
                   .orderByAsc("timestamp");
        return documentOperationMapper.selectList(queryWrapper);
    }

    @Override
    public List<Document> getUserDocuments(Long userId) {
        QueryWrapper<Document> wrapper = new QueryWrapper<>();
        wrapper.eq("creator_id", userId)
               .eq("is_deleted", 0)
               .orderByDesc("updated_at");
        return documentMapper.selectList(wrapper);
    }

    @Override
    public List<Document> getSharedDocuments(Long userId) {
        // 获取用户的所有权限记录
        List<org.zsy.bysj.model.DocumentPermission> permissions = permissionService.getUserPermissions(userId);
        if (permissions == null || permissions.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        
        // 提取文档ID列表
        List<Long> documentIds = permissions.stream()
            .map(org.zsy.bysj.model.DocumentPermission::getDocumentId)
            .distinct()
            .collect(java.util.stream.Collectors.toList());
        
        if (documentIds.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        
        // 查询这些文档（排除已删除的）
        QueryWrapper<Document> wrapper = new QueryWrapper<>();
        wrapper.in("id", documentIds)
               .eq("is_deleted", 0)
               .orderByDesc("updated_at");
        return documentMapper.selectList(wrapper);
    }

    @Override
    @Transactional
    public void deleteDocument(Long documentId, Long userId) {
        Document document = getDocumentById(documentId);
        if (document == null || !document.getCreatorId().equals(userId)) {
            throw new RuntimeException("无权删除该文档");
        }
        
        documentMapper.deleteById(documentId);
        
        // 清除缓存
        redisTemplate.delete(RedisKeyConstant.buildDocumentCacheKey(documentId));
    }

    @Override
    @Transactional
    public void createVersionSnapshot(Long documentId, Integer version) {
        Document document = getDocumentById(documentId);
        if (document == null) {
            throw new RuntimeException("文档不存在");
        }

        // 检查该版本是否已存在快照（可能有重复记录）
        QueryWrapper<DocumentVersion> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("document_id", documentId)
                   .eq("version", version)
                   .orderByDesc("created_at"); // 按创建时间降序，最新的在前
        List<DocumentVersion> existingList = documentVersionMapper.selectList(queryWrapper);

        if (existingList != null && !existingList.isEmpty()) {
            // 如果存在多条记录，先删除所有旧记录
            if (existingList.size() > 1) {
                List<Long> idsToDelete = existingList.stream()
                    .map(DocumentVersion::getId)
                    .collect(java.util.stream.Collectors.toList());
                documentVersionMapper.deleteBatchIds(idsToDelete);
                
                // 创建新的快照
                DocumentVersion documentVersion = new DocumentVersion();
                documentVersion.setDocumentId(documentId);
                documentVersion.setVersion(version);
                documentVersion.setContent(document.getContent());
                documentVersion.setSnapshot(buildSnapshot(document));
                documentVersion.setCreatedBy(document.getCreatorId());
                documentVersion.setCreatedAt(LocalDateTime.now());
                documentVersionMapper.insert(documentVersion);
            } else {
                // 只有一条记录，更新即可
                DocumentVersion existing = existingList.get(0);
                existing.setContent(document.getContent());
                existing.setSnapshot(buildSnapshot(document));
                documentVersionMapper.updateById(existing);
            }
        } else {
            // 创建新的版本快照
            DocumentVersion documentVersion = new DocumentVersion();
            documentVersion.setDocumentId(documentId);
            documentVersion.setVersion(version);
            documentVersion.setContent(document.getContent());
            documentVersion.setSnapshot(buildSnapshot(document));
            documentVersion.setCreatedBy(document.getCreatorId());
            documentVersion.setCreatedAt(LocalDateTime.now());
            documentVersionMapper.insert(documentVersion);
        }
    }

    /**
     * 构建文档快照（JSON格式，包含文档元数据）
     */
    private String buildSnapshot(Document document) {
        try {
            // 构建快照对象，包含文档的完整信息
            java.util.Map<String, Object> snapshot = new java.util.HashMap<>();
            snapshot.put("id", document.getId());
            snapshot.put("title", document.getTitle());
            snapshot.put("content", document.getContent());
            snapshot.put("creatorId", document.getCreatorId());
            snapshot.put("version", document.getVersion());
            snapshot.put("createdAt", document.getCreatedAt());
            snapshot.put("updatedAt", document.getUpdatedAt());
            
            // 获取该版本的所有操作
            QueryWrapper<DocumentOperation> opWrapper = new QueryWrapper<>();
            opWrapper.eq("document_id", document.getId())
                    .eq("version", document.getVersion())
                    .orderByAsc("timestamp");
            List<DocumentOperation> operations = documentOperationMapper.selectList(opWrapper);
            snapshot.put("operations", operations);
            
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new RuntimeException("构建文档快照失败: " + e.getMessage());
        }
    }

    @Override
    public List<DocumentVersion> getDocumentVersions(Long documentId) {
        QueryWrapper<DocumentVersion> wrapper = new QueryWrapper<>();
        wrapper.eq("document_id", documentId)
               .orderByDesc("version");
        return documentVersionMapper.selectList(wrapper);
    }

    @Override
    public DocumentVersion getVersionSnapshot(Long documentId, Integer version) {
        QueryWrapper<DocumentVersion> wrapper = new QueryWrapper<>();
        wrapper.eq("document_id", documentId)
               .eq("version", version)
               .orderByDesc("created_at") // 按创建时间降序，获取最新的
               .last("LIMIT 1"); // 只取第一条
        List<DocumentVersion> list = documentVersionMapper.selectList(wrapper);
        return list != null && !list.isEmpty() ? list.get(0) : null;
    }

    @Override
    @Transactional
    public Document rollbackToVersion(Long documentId, Integer targetVersion, Long userId) {
        Document document = getDocumentById(documentId);
        if (document == null) {
            throw new RuntimeException("文档不存在");
        }

        // 检查权限（只有创建者或管理员可以回滚）
        if (!document.getCreatorId().equals(userId)) {
            throw new RuntimeException("无权回滚该文档");
        }

        // 获取目标版本的快照
        DocumentVersion targetVersionSnapshot = getVersionSnapshot(documentId, targetVersion);
        if (targetVersionSnapshot == null) {
            throw new RuntimeException("目标版本不存在");
        }

        // 创建当前版本的快照（保存当前状态）
        createVersionSnapshot(documentId, document.getVersion());

        // 回滚到目标版本
        UpdateWrapper<Document> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", documentId)
                     .set("content", targetVersionSnapshot.getContent())
                     .set("version", targetVersion + 1) // 新版本号
                     .set("updated_at", LocalDateTime.now());
        documentMapper.update(null, updateWrapper);

        // 清除缓存
        redisTemplate.delete(RedisKeyConstant.buildDocumentCacheKey(documentId));

        // 返回更新后的文档
        return getDocumentById(documentId);
    }

    /**
     * 保存操作记录
     */
    private void saveOperation(Long documentId, Long userId, Operation operation, Integer version) {
        DocumentOperation docOp = new DocumentOperation();
        docOp.setDocumentId(documentId);
        docOp.setUserId(userId);
        docOp.setOperationType(operation.getType());
        docOp.setOperationData(operation.getData());
        docOp.setPosition(operation.getPosition());
        docOp.setLength(operation.getLength());
        docOp.setTimestamp(System.currentTimeMillis());
        docOp.setVersion(version);
        
        documentOperationMapper.insert(docOp);
    }

    /**
     * 解析操作
     */
    private Operation parseOperation(DocumentOperation docOp) {
        if ("INSERT".equals(docOp.getOperationType())) {
            return Operation.insert(docOp.getOperationData(), docOp.getPosition());
        } else if ("DELETE".equals(docOp.getOperationType())) {
            return Operation.delete(docOp.getPosition(), docOp.getLength());
        } else {
            return Operation.retain(docOp.getPosition(), docOp.getLength());
        }
    }

    /**
     * 缓存文档
     */
    private void cacheDocument(Document document) {
        redisTemplate.opsForValue().set(
                RedisKeyConstant.buildDocumentCacheKey(document.getId()),
                document,
                CACHE_EXPIRE_HOURS,
                TimeUnit.HOURS
        );
    }
}

