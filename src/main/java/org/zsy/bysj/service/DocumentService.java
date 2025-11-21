package org.zsy.bysj.service;

import org.zsy.bysj.model.Document;
import org.zsy.bysj.model.DocumentOperation;
import org.zsy.bysj.model.DocumentVersion;
import org.zsy.bysj.algorithm.Operation;
import java.util.List;

/**
 * 文档服务接口
 */
public interface DocumentService {
    
    /**
     * 创建文档
     */
    Document createDocument(String title, Long creatorId);
    
    /**
     * 根据ID获取文档
     */
    Document getDocumentById(Long documentId);
    
    /**
     * 更新文档内容
     */
    void updateDocumentContent(Long documentId, String content, Integer version);
    
    /**
     * 应用操作到文档
     */
    Document applyOperation(Long documentId, Operation operation, Long userId);
    
    /**
     * 获取文档的操作历史
     */
    List<DocumentOperation> getDocumentOperations(Long documentId, Integer fromVersion);
    
    /**
     * 获取用户的所有文档（创建的）
     */
    List<Document> getUserDocuments(Long userId);
    
    /**
     * 获取用户被共享的文档
     */
    List<Document> getSharedDocuments(Long userId);
    
    /**
     * 删除文档
     */
    void deleteDocument(Long documentId, Long userId);
    
    /**
     * 创建文档版本快照
     */
    void createVersionSnapshot(Long documentId, Integer version);

    /**
     * 获取文档版本列表
     */
    List<DocumentVersion> getDocumentVersions(Long documentId);

    /**
     * 根据版本号获取文档快照
     */
    DocumentVersion getVersionSnapshot(Long documentId, Integer version);

    /**
     * 回滚到指定版本
     */
    Document rollbackToVersion(Long documentId, Integer targetVersion, Long userId);
}

