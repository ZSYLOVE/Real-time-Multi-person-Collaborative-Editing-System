package org.zsy.bysj.service;

import org.zsy.bysj.dto.OperationDTO;
import java.util.List;

/**
 * 离线同步服务接口
 */
public interface OfflineSyncService {
    
    /**
     * 保存离线操作到队列
     */
    void saveOfflineOperation(Long documentId, Long userId, OperationDTO operation);
    
    /**
     * 获取用户的离线操作队列
     */
    List<OperationDTO> getOfflineOperations(Long documentId, Long userId);
    
    /**
     * 清除用户的离线操作队列
     */
    void clearOfflineOperations(Long documentId, Long userId);
    
    /**
     * 标记用户为离线状态
     */
    void markUserOffline(Long documentId, Long userId);
    
    /**
     * 标记用户为在线状态
     */
    void markUserOnline(Long documentId, Long userId);
    
    /**
     * 检查用户是否离线
     */
    boolean isUserOffline(Long documentId, Long userId);
    
    /**
     * 同步离线操作到服务器
     * 返回同步后的操作列表（经过OT转换）
     */
    List<OperationDTO> syncOfflineOperations(Long documentId, Long userId);
    
    /**
     * 处理离线编辑冲突
     * 当用户离线期间有其他用户编辑了文档，需要解决冲突
     */
    List<OperationDTO> resolveOfflineConflicts(Long documentId, Long userId, 
                                                List<OperationDTO> offlineOps, 
                                                Integer serverVersion);
}

